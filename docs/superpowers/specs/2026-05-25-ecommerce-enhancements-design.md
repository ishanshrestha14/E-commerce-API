# E-Commerce API Enhancements — Design Spec
**Date:** 2026-05-25
**Branch:** main
**Approach:** Option A — Minimal-touch, targeted additions

---

## Overview

Five prioritized enhancements to an existing Spring Boot 3.4.1 e-commerce REST API. Each section is independently committable. A bonus sixth enhancement (Actuator) is included as it costs one dependency and enables docker-compose health checks.

**Delivery order matches commit order:**
1. Docker + docker-compose
2. Redis caching
3. Unit + integration tests
4. GitHub Actions CI
5. Pagination + search
6. (Bonus) Spring Actuator health endpoint

---

## 1. Docker + docker-compose

### Dockerfile
Multi-stage build:
- **Stage 1 (`build`):** `maven:3.9-eclipse-temurin-17` — runs `mvn clean package -DskipTests`
- **Stage 2 (`runtime`):** `eclipse-temurin:17-jre-alpine` — copies the built JAR, exposes port 8080

No secrets baked into the image. All config injected at runtime via environment variables.

### docker-compose.yml
Three services:

| Service | Image | Port | Notes |
|---------|-------|------|-------|
| `db` | `mysql:8.0` | 3306 | Health-checked via `mysqladmin ping` |
| `redis` | `redis:7-alpine` | 6379 | Lightweight, no persistence config needed |
| `app` | Built from `Dockerfile` | 8080 | `depends_on` db + redis with `condition: service_healthy` |

The `app` service reads all config from environment variables (sourced from `.env`).

### .env.example additions
```
DB_USERNAME=root
DB_PASSWORD=Password
DB_NAME=store_api
REDIS_HOST=redis
REDIS_PORT=6379
JWT_SECRET=changeme
STRIPE_SECRET_KEY=
STRIPE_WEBHOOK_SECRET_KEY=
WEBSITE_URL=http://localhost:8080
```

### application.yaml additions
```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
  datasource:
    url: jdbc:mysql://${DB_HOST:localhost}:3306/${DB_NAME:store_api}?createDatabaseIfNotExist=true
```

---

## 2. Redis Caching

### Dependencies added to pom.xml
- `spring-boot-starter-data-redis`

### CacheConfig bean
A `RedisCacheManager` bean configured with:
- Default TTL: **10 minutes**
- Cache serialization: JSON (via `GenericJackson2JsonRedisSerializer`) for human-readable Redis keys

### ProductService (new class)
Extracted from `ProductController` which currently calls `ProductRepository` directly. The service layer is where `@Cacheable` must live.

**Methods:**
```java
@Cacheable(value = "products", key = "#pageable.pageNumber + '-' + #pageable.pageSize + '-' + #categoryId + '-' + #search")
Page<ProductDto> getAllProducts(Byte categoryId, String search, Pageable pageable)

@CacheEvict(value = "products", allEntries = true)
ProductDto createProduct(ProductDto dto)

@CacheEvict(value = "products", allEntries = true)
ProductDto updateProduct(Long id, ProductDto dto)

@CacheEvict(value = "products", allEntries = true)
void deleteProduct(Long id)
```

`allEntries = true` on writes is intentional — cache key includes pagination params, so we cannot know which cached pages are stale without evicting all.

### ProductController changes
- Constructor injection changes from `ProductRepository + CategoryRepository` to `ProductService`
- All methods delegate to `ProductService`

---

## 3. Tests

### Dependencies added to pom.xml
```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.testcontainers</groupId>
      <artifactId>testcontainers-bom</artifactId>
      <version>1.20.4</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>

<!-- test scope -->
testcontainers:junit-jupiter
testcontainers:mysql
testcontainers:testcontainers (for GenericContainer / Redis)
```

### Unit Tests (Mockito — no Spring context)

**`CartServiceTest`**
- `createCart()` — saves a cart, returns DTO
- `addToCart()` — cart not found throws `CartNotFoundException`
- `addToCart()` — product not found throws `ProductNotFoundException`
- `addToCart()` — happy path saves and returns item DTO
- `updateItem()` — item not in cart throws `ProductNotFoundException`
- `removeItem()` — delegates to cart domain and saves

**`ProductServiceTest`**
- `getAllProducts()` without category — calls `findAllWithCategory(pageable)`
- `getAllProducts()` with categoryId — calls `findByCategoryId` variant
- `createProduct()` with invalid categoryId — returns bad request
- `deleteProduct()` with unknown id — throws `ProductNotFoundException`

### Integration Test (Testcontainers)

**`ProductControllerIntegrationTest`** annotated with `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `@Testcontainers`

Containers:
```java
@Container static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");
@Container static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
```

Dynamic property source wires container ports into Spring context.

**Scenarios tested:**
- `GET /products` → 200 with paginated body (`totalElements`, `content`)
- `POST /products` (admin token) → 201, cache is evicted, subsequent `GET` reflects new product
- `GET /products?search=foo` → filters by name substring

---

## 4. GitHub Actions CI

**File:** `.github/workflows/ci.yml`

**Triggers:** `push` and `pull_request` targeting `main`

**Job: `test`** runs on `ubuntu-latest` (Docker is available; Testcontainers manages its own containers)

**Steps:**
1. `actions/checkout@v4`
2. `actions/setup-java@v4` — distribution `temurin`, version `17`
3. `actions/cache@v4` — caches `~/.m2/repository` keyed on `pom.xml` hash
4. `mvn test -B` — `-B` (batch mode) suppresses color output noise in CI logs

No service containers needed — Testcontainers handles MySQL and Redis within the test JVM.

**Environment variables for CI:** Dummy values for `JWT_SECRET`, `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET_KEY` set in the workflow (not secrets — tests don't hit Stripe).

---

## 5. Pagination + Search

### ProductRepository changes
Replace `List`-returning methods with `Page`-returning equivalents:

```java
@EntityGraph(attributePaths = "category")
Page<Product> findAllWithCategory(Pageable pageable);

@EntityGraph(attributePaths = "category")
Page<Product> findByCategoryIdAndNameContainingIgnoreCase(Byte categoryId, String name, Pageable pageable);

@EntityGraph(attributePaths = "category")
Page<Product> findByNameContainingIgnoreCase(String name, Pageable pageable);
```

### PagedResponse<T> (new DTO)
```java
public record PagedResponse<T>(
    List<T> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {}
```

### ProductController.getAllProducts() new signature
```
GET /products?page=0&size=20&sort=name,asc&search=&categoryId=
```
Returns `PagedResponse<ProductDto>`.

### Bug fix
Current line 28 discards the filtered `products` list and calls `productRepository.findAll()` unconditionally. Fixed by routing through `ProductService` which uses the correct filtered/paginated query.

---

## 6. Bonus: Spring Actuator

### Dependency
`spring-boot-starter-actuator`

### Configuration
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health
  endpoint:
    health:
      show-details: when-authorized
```

`/actuator/health` is used as the docker-compose `app` service health check. All other actuator endpoints remain unexposed.

---

## Files Created / Modified Summary

| File | Action |
|------|--------|
| `Dockerfile` | Create |
| `docker-compose.yml` | Create |
| `.env.example` | Modify |
| `src/main/resources/application.yaml` | Modify |
| `pom.xml` | Modify (add redis, testcontainers, actuator deps) |
| `src/main/java/.../products/ProductService.java` | Create |
| `src/main/java/.../products/ProductController.java` | Modify |
| `src/main/java/.../products/ProductRepository.java` | Modify |
| `src/main/java/.../products/PagedResponse.java` | Create |
| `src/main/java/.../common/CacheConfig.java` | Create |
| `src/test/java/.../CartServiceTest.java` | Create |
| `src/test/java/.../ProductServiceTest.java` | Create |
| `src/test/java/.../ProductControllerIntegrationTest.java` | Create |
| `.github/workflows/ci.yml` | Create |

---

## Constraints & Decisions

- **No ProductRepository in controller**: service layer enforces cache correctness
- **`allEntries = true` on cache eviction**: necessary because cache keys encode pagination params; targeted eviction is not feasible without a cache-key registry
- **Testcontainers over H2**: chosen by user for test realism; requires Docker in CI (available on `ubuntu-latest`)
- **Actuator health only**: avoids exposing metrics/env/beans endpoints to the internet
- **Multi-stage Docker build**: keeps the runtime image small (~200MB vs ~500MB with full JDK)
