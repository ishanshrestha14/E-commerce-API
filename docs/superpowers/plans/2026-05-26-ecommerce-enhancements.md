# E-Commerce API Enhancements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Docker, Redis caching, unit/integration tests, GitHub Actions CI, and pagination+search to a Spring Boot 3.4.1 e-commerce REST API.

**Architecture:** Extract a thin `ProductService` from `ProductController` (which currently calls the repository directly) to own caching. All five enhancements produce isolated, independently committable changes. Tasks build on each other in dependency order: Docker first (infrastructure), then Redis (service layer), then Tests, then CI, then Pagination (upgrades the service layer).

**Tech Stack:** Spring Boot 3.4.1 · Java 17 · MySQL 8 · Redis 7 · Testcontainers 1.20.4 · JUnit 5 · Mockito · GitHub Actions

---

## File Map

| File | Action | Task |
|------|--------|------|
| `Dockerfile` | Create | 1 |
| `docker-compose.yml` | Create | 1 |
| `.env.example` | Modify | 1 |
| `src/main/resources/application.yaml` | Modify | 1, 2 |
| `pom.xml` | Modify | 1, 2, 3 |
| `src/main/java/com/codewithmosh/store/common/CacheConfig.java` | Create | 2 |
| `src/main/java/com/codewithmosh/store/products/ProductNotFoundException.java` | Modify | 2 |
| `src/main/java/com/codewithmosh/store/products/ProductService.java` | Create | 2, 5 |
| `src/main/java/com/codewithmosh/store/products/ProductController.java` | Modify | 2, 5 |
| `src/main/java/com/codewithmosh/store/products/ProductRepository.java` | Modify | 5 |
| `src/main/java/com/codewithmosh/store/products/PagedResponse.java` | Create | 5 |
| `src/test/java/com/codewithmosh/store/carts/CartServiceTest.java` | Create | 3 |
| `src/test/java/com/codewithmosh/store/products/ProductServiceTest.java` | Create | 3 |
| `src/test/java/com/codewithmosh/store/products/ProductControllerIntegrationTest.java` | Create | 3, 5 |
| `.github/workflows/ci.yml` | Create | 4 |

---

## Task 1: Docker + Spring Actuator

**Files:**
- Modify: `pom.xml`
- Modify: `src/main/resources/application.yaml`
- Modify: `.env.example`
- Create: `Dockerfile`
- Create: `docker-compose.yml`

- [ ] **Step 1: Add Spring Actuator dependency to pom.xml**

  In `pom.xml`, add inside `<dependencies>` before the closing tag:

  ```xml
  <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-actuator</artifactId>
  </dependency>
  ```

- [ ] **Step 2: Update application.yaml**

  Replace the full contents of `src/main/resources/application.yaml` with:

  ```yaml
  spring:
    application:
      name: spring-store
    datasource:
      url: jdbc:mysql://${DB_HOST:localhost}:3306/${DB_NAME:store_api}?createDatabaseIfNotExist=true
      username: ${DB_USERNAME:root}
      password: ${DB_PASSWORD:Password}
    jpa:
      show-sql: true
    jwt:
      secret: ${SPRING_JWT_SECRET:dev-secret-key-change-in-production}
      accessTokenExpiration: 15000
      refreshTokenExpiration: 604800
    data:
      redis:
        host: ${REDIS_HOST:localhost}
        port: ${REDIS_PORT:6379}
  stripe:
    secretKey: ${STRIPE_SECRET_KEY:}
    webhookSecretKey: ${STRIPE_WEBHOOK_SECRET_KEY:}
  websiteUrl: ${WEBSITE_URL:http://localhost:8080}
  management:
    endpoints:
      web:
        exposure:
          include: health
    endpoint:
      health:
        show-details: when-authorized
  ```

- [ ] **Step 3: Update .env.example**

  Replace the full contents of `.env.example` with:

  ```env
  # Database
  DB_HOST=localhost
  DB_NAME=store_api
  DB_USERNAME=root
  DB_PASSWORD=Password

  # Redis
  REDIS_HOST=localhost
  REDIS_PORT=6379

  # JWT (must be ≥32 chars for HMAC-SHA256)
  SPRING_JWT_SECRET=change-me-to-a-secret-at-least-32-chars-long

  # Stripe
  STRIPE_SECRET_KEY=
  STRIPE_WEBHOOK_SECRET_KEY=

  # App
  WEBSITE_URL=http://localhost:8080
  ```

- [ ] **Step 4: Create Dockerfile**

  Create `Dockerfile` at the project root:

  ```dockerfile
  FROM maven:3.9-eclipse-temurin-17 AS build
  WORKDIR /app
  COPY pom.xml .
  RUN mvn dependency:go-offline -B
  COPY src ./src
  RUN mvn clean package -DskipTests -B

  FROM eclipse-temurin:17-jre-alpine
  WORKDIR /app
  COPY --from=build /app/target/store-0.0.1-SNAPSHOT.jar app.jar
  EXPOSE 8080
  ENTRYPOINT ["java", "-jar", "app.jar"]
  ```

- [ ] **Step 5: Create docker-compose.yml**

  Create `docker-compose.yml` at the project root:

  ```yaml
  services:
    db:
      image: mysql:8.0
      environment:
        MYSQL_ROOT_PASSWORD: ${DB_PASSWORD}
        MYSQL_DATABASE: ${DB_NAME:-store_api}
      ports:
        - "3306:3306"
      healthcheck:
        test: ["CMD", "mysqladmin", "ping", "-h", "localhost", "-u", "root", "-p${DB_PASSWORD}"]
        interval: 10s
        timeout: 5s
        retries: 5

    redis:
      image: redis:7-alpine
      ports:
        - "6379:6379"
      healthcheck:
        test: ["CMD", "redis-cli", "ping"]
        interval: 10s
        timeout: 5s
        retries: 5

    app:
      build: .
      ports:
        - "8080:8080"
      environment:
        DB_HOST: db
        DB_NAME: ${DB_NAME:-store_api}
        DB_USERNAME: ${DB_USERNAME:-root}
        DB_PASSWORD: ${DB_PASSWORD}
        REDIS_HOST: redis
        REDIS_PORT: 6379
        SPRING_JWT_SECRET: ${SPRING_JWT_SECRET}
        STRIPE_SECRET_KEY: ${STRIPE_SECRET_KEY:-}
        STRIPE_WEBHOOK_SECRET_KEY: ${STRIPE_WEBHOOK_SECRET_KEY:-}
        WEBSITE_URL: ${WEBSITE_URL:-http://localhost:8080}
      depends_on:
        db:
          condition: service_healthy
        redis:
          condition: service_healthy
  ```

- [ ] **Step 6: Verify the app still compiles**

  ```bash
  ./mvnw compile -q
  ```

  Expected: BUILD SUCCESS with no errors.

- [ ] **Step 7: Commit**

  ```bash
  git add Dockerfile docker-compose.yml .env.example src/main/resources/application.yaml pom.xml
  git commit -m "feat: add Docker multi-stage build, docker-compose, and Spring Actuator health endpoint"
  ```

---

## Task 2: Redis Caching

**Files:**
- Modify: `pom.xml`
- Create: `src/main/java/com/codewithmosh/store/common/CacheConfig.java`
- Modify: `src/main/java/com/codewithmosh/store/products/ProductNotFoundException.java`
- Create: `src/main/java/com/codewithmosh/store/products/ProductService.java`
- Modify: `src/main/java/com/codewithmosh/store/products/ProductController.java`

- [ ] **Step 1: Add Redis dependency to pom.xml**

  In `pom.xml`, add inside `<dependencies>`:

  ```xml
  <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-data-redis</artifactId>
  </dependency>
  ```

- [ ] **Step 2: Create CacheConfig.java**

  Create `src/main/java/com/codewithmosh/store/common/CacheConfig.java`:

  ```java
  package com.codewithmosh.store.common;

  import org.springframework.cache.annotation.EnableCaching;
  import org.springframework.context.annotation.Bean;
  import org.springframework.context.annotation.Configuration;
  import org.springframework.data.redis.cache.RedisCacheConfiguration;
  import org.springframework.data.redis.cache.RedisCacheManager;
  import org.springframework.data.redis.connection.RedisConnectionFactory;
  import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
  import org.springframework.data.redis.serializer.RedisSerializationContext;

  import java.time.Duration;

  @Configuration
  @EnableCaching
  public class CacheConfig {

      @Bean
      public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
          var config = RedisCacheConfiguration.defaultCacheConfig()
                  .entryTtl(Duration.ofMinutes(10))
                  .serializeValuesWith(
                          RedisSerializationContext.SerializationPair.fromSerializer(
                                  new GenericJackson2JsonRedisSerializer()
                          )
                  );
          return RedisCacheManager.builder(connectionFactory)
                  .cacheDefaults(config)
                  .build();
      }
  }
  ```

- [ ] **Step 3: Add @ResponseStatus to ProductNotFoundException**

  The `GlobalExceptionHandler` does not handle `ProductNotFoundException`, so Spring would return 500 without this annotation. Replace the full contents of `src/main/java/com/codewithmosh/store/products/ProductNotFoundException.java`:

  ```java
  package com.codewithmosh.store.products;

  import org.springframework.http.HttpStatus;
  import org.springframework.web.bind.annotation.ResponseStatus;

  @ResponseStatus(HttpStatus.NOT_FOUND)
  public class ProductNotFoundException extends RuntimeException {
  }
  ```

- [ ] **Step 4: Create ProductService.java**

  Create `src/main/java/com/codewithmosh/store/products/ProductService.java`:

  ```java
  package com.codewithmosh.store.products;

  import lombok.AllArgsConstructor;
  import org.springframework.cache.annotation.CacheEvict;
  import org.springframework.cache.annotation.Cacheable;
  import org.springframework.stereotype.Service;

  import java.util.List;

  @Service
  @AllArgsConstructor
  public class ProductService {
      private final ProductRepository productRepository;
      private final CategoryRepository categoryRepository;
      private final ProductMapper productMapper;

      @Cacheable(value = "products", key = "'list-' + #categoryId")
      public List<ProductDto> getAllProducts(Byte categoryId) {
          if (categoryId != null) {
              return productRepository.findByCategoryId(categoryId)
                      .stream().map(productMapper::toDto).toList();
          }
          return productRepository.findAllWithCategory()
                  .stream().map(productMapper::toDto).toList();
      }

      @CacheEvict(value = "products", allEntries = true)
      public ProductDto createProduct(ProductDto productDto) {
          var category = categoryRepository.findById(productDto.getCategoryId()).orElse(null);
          if (category == null) return null;

          var product = productMapper.toEntity(productDto);
          product.setCategory(category);
          productRepository.save(product);
          productDto.setId(product.getId());
          return productDto;
      }

      @CacheEvict(value = "products", allEntries = true)
      public ProductDto updateProduct(Long id, ProductDto productDto) {
          var category = categoryRepository.findById(productDto.getCategoryId()).orElse(null);
          if (category == null) return null;

          var product = productRepository.findById(id).orElseThrow(ProductNotFoundException::new);
          productMapper.update(productDto, product);
          product.setCategory(category);
          productRepository.save(product);
          productDto.setId(product.getId());
          return productDto;
      }

      @CacheEvict(value = "products", allEntries = true)
      public boolean deleteProduct(Long id) {
          var product = productRepository.findById(id).orElse(null);
          if (product == null) return false;
          productRepository.delete(product);
          return true;
      }
  }
  ```

  > `deleteProduct` returns `boolean` so the controller can return 404 without relying on the exception handler, keeping the existing HTTP contract intact.

- [ ] **Step 5: Replace ProductController.java**

  Replace the full contents of `src/main/java/com/codewithmosh/store/products/ProductController.java`:

  ```java
  package com.codewithmosh.store.products;

  import lombok.AllArgsConstructor;
  import org.springframework.http.ResponseEntity;
  import org.springframework.web.bind.annotation.*;
  import org.springframework.web.util.UriComponentsBuilder;

  import java.util.List;

  @AllArgsConstructor
  @RestController
  @RequestMapping("/products")
  public class ProductController {
      private final ProductService productService;

      @GetMapping
      public List<ProductDto> getAllProducts(
              @RequestParam(required = false) Byte categoryId
      ) {
          return productService.getAllProducts(categoryId);
      }

      @PostMapping
      public ResponseEntity<ProductDto> createProduct(
              @RequestBody ProductDto productDto,
              UriComponentsBuilder uriBuilder
      ) {
          var result = productService.createProduct(productDto);
          if (result == null) return ResponseEntity.badRequest().build();
          var uri = uriBuilder.path("/products/{id}").buildAndExpand(result.getId()).toUri();
          return ResponseEntity.created(uri).body(result);
      }

      @PutMapping("/{id}")
      public ResponseEntity<ProductDto> updateProduct(
              @PathVariable Long id,
              @RequestBody ProductDto productDto
      ) {
          var result = productService.updateProduct(id, productDto);
          if (result == null) return ResponseEntity.badRequest().build();
          return ResponseEntity.ok(result);
      }

      @DeleteMapping("/{id}")
      public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
          if (!productService.deleteProduct(id)) return ResponseEntity.notFound().build();
          return ResponseEntity.noContent().build();
      }
  }
  ```

  > This also fixes the existing bug where line 28 of the old controller always called `productRepository.findAll()` regardless of the `categoryId` filter.

- [ ] **Step 6: Verify compilation**

  ```bash
  ./mvnw compile -q
  ```

  Expected: BUILD SUCCESS.

- [ ] **Step 7: Commit**

  ```bash
  git add pom.xml \
    src/main/java/com/codewithmosh/store/common/CacheConfig.java \
    src/main/java/com/codewithmosh/store/products/ProductNotFoundException.java \
    src/main/java/com/codewithmosh/store/products/ProductService.java \
    src/main/java/com/codewithmosh/store/products/ProductController.java
  git commit -m "feat: add Redis caching with 10-min TTL on product listings via ProductService"
  ```

---

## Task 3: Unit & Integration Tests

**Files:**
- Modify: `pom.xml`
- Create: `src/test/java/com/codewithmosh/store/carts/CartServiceTest.java`
- Create: `src/test/java/com/codewithmosh/store/products/ProductServiceTest.java`
- Create: `src/test/java/com/codewithmosh/store/products/ProductControllerIntegrationTest.java`

- [ ] **Step 1: Add Testcontainers BOM and modules to pom.xml**

  In `pom.xml`, add a `<dependencyManagement>` block (place it before `<dependencies>`):

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
  ```

  Then add inside `<dependencies>`:

  ```xml
  <dependency>
      <groupId>org.testcontainers</groupId>
      <artifactId>junit-jupiter</artifactId>
      <scope>test</scope>
  </dependency>
  <dependency>
      <groupId>org.testcontainers</groupId>
      <artifactId>mysql</artifactId>
      <scope>test</scope>
  </dependency>
  ```

- [ ] **Step 2: Create CartServiceTest.java**

  Create `src/test/java/com/codewithmosh/store/carts/CartServiceTest.java`:

  ```java
  package com.codewithmosh.store.carts;

  import com.codewithmosh.store.products.Product;
  import com.codewithmosh.store.products.ProductNotFoundException;
  import com.codewithmosh.store.products.ProductRepository;
  import org.junit.jupiter.api.Test;
  import org.junit.jupiter.api.extension.ExtendWith;
  import org.mockito.InjectMocks;
  import org.mockito.Mock;
  import org.mockito.junit.jupiter.MockitoExtension;

  import java.util.Optional;
  import java.util.UUID;

  import static org.assertj.core.api.Assertions.assertThat;
  import static org.assertj.core.api.Assertions.assertThatThrownBy;
  import static org.mockito.ArgumentMatchers.any;
  import static org.mockito.Mockito.*;

  @ExtendWith(MockitoExtension.class)
  class CartServiceTest {

      @Mock CartRepository cartRepository;
      @Mock CartMapper cartMapper;
      @Mock ProductRepository productRepository;
      @InjectMocks CartService cartService;

      @Test
      void createCart_savesNewCartAndReturnsDto() {
          var expectedDto = new CartDto();
          when(cartMapper.toDto(any(Cart.class))).thenReturn(expectedDto);

          var result = cartService.createCart();

          verify(cartRepository).save(any(Cart.class));
          assertThat(result).isEqualTo(expectedDto);
      }

      @Test
      void addToCart_cartNotFound_throwsCartNotFoundException() {
          var cartId = UUID.randomUUID();
          when(cartRepository.getCartWithItems(cartId)).thenReturn(Optional.empty());

          assertThatThrownBy(() -> cartService.addToCart(cartId, 1L))
                  .isInstanceOf(CartNotFoundException.class);
          verify(cartRepository, never()).save(any());
      }

      @Test
      void addToCart_productNotFound_throwsProductNotFoundException() {
          var cartId = UUID.randomUUID();
          when(cartRepository.getCartWithItems(cartId)).thenReturn(Optional.of(new Cart()));
          when(productRepository.findById(1L)).thenReturn(Optional.empty());

          assertThatThrownBy(() -> cartService.addToCart(cartId, 1L))
                  .isInstanceOf(ProductNotFoundException.class);
          verify(cartRepository, never()).save(any());
      }

      @Test
      void addToCart_happyPath_savesCartAndReturnsItemDto() {
          var cartId = UUID.randomUUID();
          var cart = mock(Cart.class);
          var product = new Product();
          var cartItem = new CartItem();
          var expectedDto = new CartItemDto();

          when(cartRepository.getCartWithItems(cartId)).thenReturn(Optional.of(cart));
          when(productRepository.findById(1L)).thenReturn(Optional.of(product));
          when(cart.addItem(product)).thenReturn(cartItem);
          when(cartMapper.toDto(cartItem)).thenReturn(expectedDto);

          var result = cartService.addToCart(cartId, 1L);

          verify(cartRepository).save(cart);
          assertThat(result).isEqualTo(expectedDto);
      }

      @Test
      void updateItem_itemNotInCart_throwsProductNotFoundException() {
          var cartId = UUID.randomUUID();
          var cart = mock(Cart.class);
          when(cartRepository.getCartWithItems(cartId)).thenReturn(Optional.of(cart));
          when(cart.getItem(1L)).thenReturn(null);

          assertThatThrownBy(() -> cartService.updateItem(cartId, 1L, 3))
                  .isInstanceOf(ProductNotFoundException.class);
          verify(cartRepository, never()).save(any());
      }

      @Test
      void removeItem_cartFound_removesItemAndSavesCart() {
          var cartId = UUID.randomUUID();
          var cart = mock(Cart.class);
          when(cartRepository.getCartWithItems(cartId)).thenReturn(Optional.of(cart));

          cartService.removeItem(cartId, 1L);

          verify(cart).removeItem(1L);
          verify(cartRepository).save(cart);
      }
  }
  ```

- [ ] **Step 3: Run CartServiceTest to verify it passes**

  ```bash
  ./mvnw test -pl . -Dtest=CartServiceTest -q
  ```

  Expected: `Tests run: 6, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 4: Create ProductServiceTest.java**

  Create `src/test/java/com/codewithmosh/store/products/ProductServiceTest.java`:

  ```java
  package com.codewithmosh.store.products;

  import org.junit.jupiter.api.Test;
  import org.junit.jupiter.api.extension.ExtendWith;
  import org.mockito.InjectMocks;
  import org.mockito.Mock;
  import org.mockito.junit.jupiter.MockitoExtension;

  import java.util.List;
  import java.util.Optional;

  import static org.assertj.core.api.Assertions.assertThat;
  import static org.mockito.ArgumentMatchers.any;
  import static org.mockito.Mockito.*;

  @ExtendWith(MockitoExtension.class)
  class ProductServiceTest {

      @Mock ProductRepository productRepository;
      @Mock CategoryRepository categoryRepository;
      @Mock ProductMapper productMapper;
      @InjectMocks ProductService productService;

      @Test
      void getAllProducts_noCategoryFilter_callsFindAllWithCategory() {
          var product = new Product();
          var dto = new ProductDto();
          when(productRepository.findAllWithCategory()).thenReturn(List.of(product));
          when(productMapper.toDto(product)).thenReturn(dto);

          var result = productService.getAllProducts(null);

          assertThat(result).containsExactly(dto);
          verify(productRepository).findAllWithCategory();
          verify(productRepository, never()).findByCategoryId(any());
      }

      @Test
      void getAllProducts_withCategoryFilter_callsFindByCategoryId() {
          var categoryId = (byte) 1;
          var product = new Product();
          var dto = new ProductDto();
          when(productRepository.findByCategoryId(categoryId)).thenReturn(List.of(product));
          when(productMapper.toDto(product)).thenReturn(dto);

          var result = productService.getAllProducts(categoryId);

          assertThat(result).containsExactly(dto);
          verify(productRepository).findByCategoryId(categoryId);
          verify(productRepository, never()).findAllWithCategory();
      }

      @Test
      void createProduct_invalidCategoryId_returnsNull() {
          var dto = new ProductDto();
          dto.setCategoryId((byte) 99);
          when(categoryRepository.findById((byte) 99)).thenReturn(Optional.empty());

          var result = productService.createProduct(dto);

          assertThat(result).isNull();
          verify(productRepository, never()).save(any());
      }

      @Test
      void createProduct_validCategory_savesAndReturnsDto() {
          var category = new Category();
          var dto = new ProductDto();
          dto.setCategoryId((byte) 1);
          var product = new Product();

          when(categoryRepository.findById((byte) 1)).thenReturn(Optional.of(category));
          when(productMapper.toEntity(dto)).thenReturn(product);

          var result = productService.createProduct(dto);

          verify(productRepository).save(product);
          assertThat(result).isEqualTo(dto);
      }

      @Test
      void deleteProduct_productNotFound_returnsFalse() {
          when(productRepository.findById(999L)).thenReturn(Optional.empty());

          var result = productService.deleteProduct(999L);

          assertThat(result).isFalse();
          verify(productRepository, never()).delete(any());
      }

      @Test
      void deleteProduct_productFound_deletesAndReturnsTrue() {
          var product = new Product();
          when(productRepository.findById(1L)).thenReturn(Optional.of(product));

          var result = productService.deleteProduct(1L);

          assertThat(result).isTrue();
          verify(productRepository).delete(product);
      }
  }
  ```

- [ ] **Step 5: Run ProductServiceTest to verify it passes**

  ```bash
  ./mvnw test -pl . -Dtest=ProductServiceTest -q
  ```

  Expected: `Tests run: 6, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 6: Create ProductControllerIntegrationTest.java**

  This test loads the full Spring context against real MySQL and Redis containers. It tests what's currently available — `GET /products` returns a JSON array. Task 5 will add a step to update this test for the paginated response.

  Create `src/test/java/com/codewithmosh/store/products/ProductControllerIntegrationTest.java`:

  ```java
  package com.codewithmosh.store.products;

  import org.junit.jupiter.api.Test;
  import org.springframework.beans.factory.annotation.Autowired;
  import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
  import org.springframework.boot.test.context.SpringBootTest;
  import org.springframework.security.test.context.support.WithMockUser;
  import org.springframework.test.context.DynamicPropertyRegistry;
  import org.springframework.test.context.DynamicPropertySource;
  import org.springframework.test.context.TestPropertySource;
  import org.springframework.test.web.servlet.MockMvc;
  import org.testcontainers.containers.GenericContainer;
  import org.testcontainers.containers.MySQLContainer;
  import org.testcontainers.junit.jupiter.Container;
  import org.testcontainers.junit.jupiter.Testcontainers;

  import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
  import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
  import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

  @SpringBootTest
  @AutoConfigureMockMvc
  @Testcontainers
  @TestPropertySource(properties = {
      "stripe.secretKey=sk_test_dummy",
      "stripe.webhookSecretKey=whsec_dummy",
      "spring.jwt.secret=test-secret-key-that-is-long-enough-32+"
  })
  class ProductControllerIntegrationTest {

      @Container
      static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
              .withDatabaseName("store_api")
              .withUsername("test")
              .withPassword("test");

      @SuppressWarnings("resource")
      @Container
      static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
              .withExposedPorts(6379);

      @DynamicPropertySource
      static void configureProperties(DynamicPropertyRegistry registry) {
          registry.add("spring.datasource.url", mysql::getJdbcUrl);
          registry.add("spring.datasource.username", mysql::getUsername);
          registry.add("spring.datasource.password", mysql::getPassword);
          registry.add("spring.data.redis.host", redis::getHost);
          registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
      }

      @Autowired
      MockMvc mockMvc;

      @Test
      @WithMockUser
      void getAllProducts_returnsOkWithJsonArray() throws Exception {
          mockMvc.perform(get("/products"))
                  .andExpect(status().isOk())
                  .andExpect(jsonPath("$").isArray());
      }

      @Test
      @WithMockUser
      void getAllProducts_withCategoryId_returnsOk() throws Exception {
          mockMvc.perform(get("/products").param("categoryId", "1"))
                  .andExpect(status().isOk())
                  .andExpect(jsonPath("$").isArray());
      }

      @Test
      void getAllProducts_unauthenticated_returns401() throws Exception {
          mockMvc.perform(get("/products"))
                  .andExpect(status().isUnauthorized());
      }
  }
  ```

- [ ] **Step 7: Run all tests to verify they pass**

  ```bash
  ./mvnw test -q
  ```

  Expected: All tests pass. The integration test will take ~30s on first run while Testcontainers pulls the images.

- [ ] **Step 8: Commit**

  ```bash
  git add pom.xml \
    src/test/java/com/codewithmosh/store/carts/CartServiceTest.java \
    src/test/java/com/codewithmosh/store/products/ProductServiceTest.java \
    src/test/java/com/codewithmosh/store/products/ProductControllerIntegrationTest.java
  git commit -m "test: add unit tests for CartService and ProductService, integration test with Testcontainers"
  ```

---

## Task 4: GitHub Actions CI

**Files:**
- Create: `.github/workflows/ci.yml`

- [ ] **Step 1: Create the workflow directory and file**

  ```bash
  mkdir -p .github/workflows
  ```

  Create `.github/workflows/ci.yml`:

  ```yaml
  name: CI

  on:
    push:
      branches: [main]
    pull_request:
      branches: [main]

  jobs:
    test:
      runs-on: ubuntu-latest

      env:
        SPRING_JWT_SECRET: test-secret-key-that-is-long-enough-32plus
        STRIPE_SECRET_KEY: sk_test_dummy
        STRIPE_WEBHOOK_SECRET_KEY: whsec_dummy

      steps:
        - uses: actions/checkout@v4

        - name: Set up Java 17
          uses: actions/setup-java@v4
          with:
            java-version: '17'
            distribution: 'temurin'

        - name: Cache Maven packages
          uses: actions/cache@v4
          with:
            path: ~/.m2/repository
            key: ${{ runner.os }}-maven-${{ hashFiles('**/pom.xml') }}
            restore-keys: ${{ runner.os }}-maven-

        - name: Run tests
          run: ./mvnw test -B
  ```

  > Testcontainers manages its own MySQL and Redis containers within the test JVM. Docker is available on `ubuntu-latest` runners, so no `services:` block is needed.

- [ ] **Step 2: Commit**

  ```bash
  git add .github/workflows/ci.yml
  git commit -m "ci: add GitHub Actions workflow to run tests on push and pull request"
  ```

---

## Task 5: Pagination + Search

**Files:**
- Create: `src/main/java/com/codewithmosh/store/products/PagedResponse.java`
- Modify: `src/main/java/com/codewithmosh/store/products/ProductRepository.java`
- Modify: `src/main/java/com/codewithmosh/store/products/ProductService.java`
- Modify: `src/main/java/com/codewithmosh/store/products/ProductController.java`
- Modify: `src/test/java/com/codewithmosh/store/products/ProductControllerIntegrationTest.java`

- [ ] **Step 1: Create PagedResponse.java**

  Create `src/main/java/com/codewithmosh/store/products/PagedResponse.java`:

  ```java
  package com.codewithmosh.store.products;

  import org.springframework.data.domain.Page;

  import java.util.List;

  public record PagedResponse<T>(
          List<T> content,
          int page,
          int size,
          long totalElements,
          int totalPages
  ) {
      public static <T> PagedResponse<T> from(Page<T> page) {
          return new PagedResponse<>(
                  page.getContent(),
                  page.getNumber(),
                  page.getSize(),
                  page.getTotalElements(),
                  page.getTotalPages()
          );
      }
  }
  ```

- [ ] **Step 2: Update ProductRepository.java**

  Replace the full contents of `src/main/java/com/codewithmosh/store/products/ProductRepository.java`:

  ```java
  package com.codewithmosh.store.products;

  import org.springframework.data.domain.Page;
  import org.springframework.data.domain.Pageable;
  import org.springframework.data.jpa.repository.EntityGraph;
  import org.springframework.data.jpa.repository.JpaRepository;
  import org.springframework.data.jpa.repository.Query;

  import java.util.List;

  public interface ProductRepository extends JpaRepository<Product, Long> {

      @EntityGraph(attributePaths = "category")
      List<Product> findByCategoryId(Byte categoryId);

      @EntityGraph(attributePaths = "category")
      @Query("SELECT p FROM Product p")
      List<Product> findAllWithCategory();

      @EntityGraph(attributePaths = "category")
      @Query("SELECT p FROM Product p")
      Page<Product> findAllWithCategory(Pageable pageable);

      @EntityGraph(attributePaths = "category")
      Page<Product> findByCategoryId(Byte categoryId, Pageable pageable);

      @EntityGraph(attributePaths = "category")
      Page<Product> findByNameContainingIgnoreCase(String name, Pageable pageable);

      @EntityGraph(attributePaths = "category")
      Page<Product> findByCategoryIdAndNameContainingIgnoreCase(Byte categoryId, String name, Pageable pageable);
  }
  ```

- [ ] **Step 3: Update ProductService.java**

  Replace the full contents of `src/main/java/com/codewithmosh/store/products/ProductService.java`:

  ```java
  package com.codewithmosh.store.products;

  import lombok.AllArgsConstructor;
  import org.springframework.cache.annotation.CacheEvict;
  import org.springframework.cache.annotation.Cacheable;
  import org.springframework.data.domain.Page;
  import org.springframework.data.domain.PageRequest;
  import org.springframework.data.domain.Pageable;
  import org.springframework.data.domain.Sort;
  import org.springframework.stereotype.Service;

  @Service
  @AllArgsConstructor
  public class ProductService {
      private final ProductRepository productRepository;
      private final CategoryRepository categoryRepository;
      private final ProductMapper productMapper;

      @Cacheable(
          value = "products",
          key = "#page + '-' + #size + '-' + #sortBy + '-' + #sortDir + '-' + #categoryId + '-' + #search"
      )
      public PagedResponse<ProductDto> getAllProducts(
              Byte categoryId, String search, int page, int size, String sortBy, String sortDir
      ) {
          var sort = sortDir != null && sortDir.equalsIgnoreCase("desc")
                  ? Sort.by(sortBy != null ? sortBy : "name").descending()
                  : Sort.by(sortBy != null ? sortBy : "name").ascending();
          var pageable = PageRequest.of(page, size, sort);

          Page<Product> products;
          boolean hasCategory = categoryId != null;
          boolean hasSearch = search != null && !search.isBlank();

          if (hasCategory && hasSearch) {
              products = productRepository.findByCategoryIdAndNameContainingIgnoreCase(categoryId, search, pageable);
          } else if (hasCategory) {
              products = productRepository.findByCategoryId(categoryId, pageable);
          } else if (hasSearch) {
              products = productRepository.findByNameContainingIgnoreCase(search, pageable);
          } else {
              products = productRepository.findAllWithCategory(pageable);
          }

          return PagedResponse.from(products.map(productMapper::toDto));
      }

      @CacheEvict(value = "products", allEntries = true)
      public ProductDto createProduct(ProductDto productDto) {
          var category = categoryRepository.findById(productDto.getCategoryId()).orElse(null);
          if (category == null) return null;

          var product = productMapper.toEntity(productDto);
          product.setCategory(category);
          productRepository.save(product);
          productDto.setId(product.getId());
          return productDto;
      }

      @CacheEvict(value = "products", allEntries = true)
      public ProductDto updateProduct(Long id, ProductDto productDto) {
          var category = categoryRepository.findById(productDto.getCategoryId()).orElse(null);
          if (category == null) return null;

          var product = productRepository.findById(id).orElseThrow(ProductNotFoundException::new);
          productMapper.update(productDto, product);
          product.setCategory(category);
          productRepository.save(product);
          productDto.setId(product.getId());
          return productDto;
      }

      @CacheEvict(value = "products", allEntries = true)
      public boolean deleteProduct(Long id) {
          var product = productRepository.findById(id).orElse(null);
          if (product == null) return false;
          productRepository.delete(product);
          return true;
      }
  }
  ```

- [ ] **Step 4: Update ProductController.java**

  Replace the full contents of `src/main/java/com/codewithmosh/store/products/ProductController.java`:

  ```java
  package com.codewithmosh.store.products;

  import lombok.AllArgsConstructor;
  import org.springframework.http.ResponseEntity;
  import org.springframework.web.bind.annotation.*;
  import org.springframework.web.util.UriComponentsBuilder;

  @AllArgsConstructor
  @RestController
  @RequestMapping("/products")
  public class ProductController {
      private final ProductService productService;

      @GetMapping
      public PagedResponse<ProductDto> getAllProducts(
              @RequestParam(required = false) Byte categoryId,
              @RequestParam(required = false) String search,
              @RequestParam(defaultValue = "0") int page,
              @RequestParam(defaultValue = "20") int size,
              @RequestParam(defaultValue = "name") String sortBy,
              @RequestParam(defaultValue = "asc") String sortDir
      ) {
          return productService.getAllProducts(categoryId, search, page, size, sortBy, sortDir);
      }

      @PostMapping
      public ResponseEntity<ProductDto> createProduct(
              @RequestBody ProductDto productDto,
              UriComponentsBuilder uriBuilder
      ) {
          var result = productService.createProduct(productDto);
          if (result == null) return ResponseEntity.badRequest().build();
          var uri = uriBuilder.path("/products/{id}").buildAndExpand(result.getId()).toUri();
          return ResponseEntity.created(uri).body(result);
      }

      @PutMapping("/{id}")
      public ResponseEntity<ProductDto> updateProduct(
              @PathVariable Long id,
              @RequestBody ProductDto productDto
      ) {
          var result = productService.updateProduct(id, productDto);
          if (result == null) return ResponseEntity.badRequest().build();
          return ResponseEntity.ok(result);
      }

      @DeleteMapping("/{id}")
      public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
          if (!productService.deleteProduct(id)) return ResponseEntity.notFound().build();
          return ResponseEntity.noContent().build();
      }
  }
  ```

- [ ] **Step 5: Update the integration test for paginated response**

  Replace the full contents of `src/test/java/com/codewithmosh/store/products/ProductControllerIntegrationTest.java`:

  ```java
  package com.codewithmosh.store.products;

  import org.junit.jupiter.api.Test;
  import org.springframework.beans.factory.annotation.Autowired;
  import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
  import org.springframework.boot.test.context.SpringBootTest;
  import org.springframework.security.test.context.support.WithMockUser;
  import org.springframework.test.context.DynamicPropertyRegistry;
  import org.springframework.test.context.DynamicPropertySource;
  import org.springframework.test.context.TestPropertySource;
  import org.springframework.test.web.servlet.MockMvc;
  import org.testcontainers.containers.GenericContainer;
  import org.testcontainers.containers.MySQLContainer;
  import org.testcontainers.junit.jupiter.Container;
  import org.testcontainers.junit.jupiter.Testcontainers;

  import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
  import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
  import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

  @SpringBootTest
  @AutoConfigureMockMvc
  @Testcontainers
  @TestPropertySource(properties = {
      "stripe.secretKey=sk_test_dummy",
      "stripe.webhookSecretKey=whsec_dummy",
      "spring.jwt.secret=test-secret-key-that-is-long-enough-32+"
  })
  class ProductControllerIntegrationTest {

      @Container
      static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
              .withDatabaseName("store_api")
              .withUsername("test")
              .withPassword("test");

      @SuppressWarnings("resource")
      @Container
      static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
              .withExposedPorts(6379);

      @DynamicPropertySource
      static void configureProperties(DynamicPropertyRegistry registry) {
          registry.add("spring.datasource.url", mysql::getJdbcUrl);
          registry.add("spring.datasource.username", mysql::getUsername);
          registry.add("spring.datasource.password", mysql::getPassword);
          registry.add("spring.data.redis.host", redis::getHost);
          registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
      }

      @Autowired
      MockMvc mockMvc;

      @Test
      @WithMockUser
      void getAllProducts_returnsPagedResponse() throws Exception {
          mockMvc.perform(get("/products"))
                  .andExpect(status().isOk())
                  .andExpect(jsonPath("$.content").isArray())
                  .andExpect(jsonPath("$.totalElements").isNumber())
                  .andExpect(jsonPath("$.totalPages").isNumber())
                  .andExpect(jsonPath("$.page").value(0))
                  .andExpect(jsonPath("$.size").value(20));
      }

      @Test
      @WithMockUser
      void getAllProducts_withSearch_returnsFilteredResults() throws Exception {
          mockMvc.perform(get("/products").param("search", "nonexistent_xyz"))
                  .andExpect(status().isOk())
                  .andExpect(jsonPath("$.totalElements").value(0))
                  .andExpect(jsonPath("$.content").isArray());
      }

      @Test
      @WithMockUser
      void getAllProducts_withCustomPageSize_respectsPageSize() throws Exception {
          mockMvc.perform(get("/products").param("size", "5").param("page", "0"))
                  .andExpect(status().isOk())
                  .andExpect(jsonPath("$.size").value(5));
      }

      @Test
      void getAllProducts_unauthenticated_returns401() throws Exception {
          mockMvc.perform(get("/products"))
                  .andExpect(status().isUnauthorized());
      }
  }
  ```

- [ ] **Step 6: Update ProductServiceTest for the new getAllProducts signature**

  The `getAllProducts` signature changed from `(Byte categoryId)` to `(Byte categoryId, String search, int page, int size, String sortBy, String sortDir)`. Update the two `getAllProducts` test methods in `src/test/java/com/codewithmosh/store/products/ProductServiceTest.java`:

  Replace the two existing `getAllProducts_*` test methods with:

  ```java
  @Test
  void getAllProducts_noCategoryNoSearch_callsFindAllWithCategory() {
      var product = new Product();
      var dto = new ProductDto();
      when(productRepository.findAllWithCategory(any(Pageable.class)))
              .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(product)));
      when(productMapper.toDto(product)).thenReturn(dto);

      var result = productService.getAllProducts(null, null, 0, 20, "name", "asc");

      assertThat(result.content()).containsExactly(dto);
      assertThat(result.totalElements()).isEqualTo(1);
      verify(productRepository).findAllWithCategory(any(Pageable.class));
  }

  @Test
  void getAllProducts_withCategoryFilter_callsFindByCategoryId() {
      var categoryId = (byte) 1;
      var product = new Product();
      var dto = new ProductDto();
      when(productRepository.findByCategoryId(eq(categoryId), any(Pageable.class)))
              .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(product)));
      when(productMapper.toDto(product)).thenReturn(dto);

      var result = productService.getAllProducts(categoryId, null, 0, 20, "name", "asc");

      assertThat(result.content()).containsExactly(dto);
      verify(productRepository).findByCategoryId(eq(categoryId), any(Pageable.class));
      verify(productRepository, never()).findAllWithCategory(any(Pageable.class));
  }
  ```

  Also add the required import at the top of the file:

  ```java
  import org.springframework.data.domain.Pageable;
  import static org.mockito.ArgumentMatchers.eq;
  ```

- [ ] **Step 7: Run all tests**

  ```bash
  ./mvnw test -q
  ```

  Expected: All tests pass.

- [ ] **Step 8: Commit**

  ```bash
  git add \
    src/main/java/com/codewithmosh/store/products/PagedResponse.java \
    src/main/java/com/codewithmosh/store/products/ProductRepository.java \
    src/main/java/com/codewithmosh/store/products/ProductService.java \
    src/main/java/com/codewithmosh/store/products/ProductController.java \
    src/test/java/com/codewithmosh/store/products/ProductControllerIntegrationTest.java \
    src/test/java/com/codewithmosh/store/products/ProductServiceTest.java
  git commit -m "feat: add pagination and search to product listings (page, size, sortBy, sortDir, search params)"
  ```

---

## Self-Review Checklist (completed)

- **Spec §1 Docker**: Dockerfile (multi-stage), docker-compose (3 services with healthchecks), .env.example — all in Task 1 ✓
- **Spec §2 Redis**: CacheConfig, ProductService with @Cacheable/@CacheEvict, controller update — Task 2 ✓
- **Spec §3 Tests**: CartServiceTest (6 tests), ProductServiceTest (6 tests), integration test with Testcontainers — Task 3 ✓
- **Spec §4 CI**: .github/workflows/ci.yml — Task 4 ✓
- **Spec §5 Pagination**: PagedResponse, updated repository methods, updated service with Pageable, updated controller — Task 5 ✓
- **Spec §6 Actuator**: health endpoint — Task 1 application.yaml ✓
- **Bug fix**: `ProductController` line 28 (always called `findAll()`) — fixed in Task 2 controller replacement ✓
- **Type consistency**: `PagedResponse<ProductDto>` used in `ProductService`, `ProductController` — matches record definition ✓
- **`getAllProducts` signature**: Updated in both service and controller, and both test files updated in Task 5 ✓
- **No placeholders or TBDs** ✓
