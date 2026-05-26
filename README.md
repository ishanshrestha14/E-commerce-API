# Spring Store - E-Commerce REST API

![CI](https://github.com/ishanshrestha14/E-commerce-API/actions/workflows/ci.yml/badge.svg)

A comprehensive e-commerce REST API built with Spring Boot, featuring user authentication, product management, shopping cart functionality, payment processing with Stripe, and order management.

## 🚀 Features

### Authentication & Authorization

- JWT-based authentication with access and refresh tokens
- Secure login/logout functionality
- Token refresh mechanism
- Role-based access control

### Product Management

- CRUD operations for products and categories
- Paginated product listing with configurable page size
- Search by product name (case-insensitive substring match)
- Filter by category
- Redis caching with 10-minute TTL — cache is invalidated on any write

### Shopping Cart

- Create and manage shopping carts
- Add/remove items from cart
- Update item quantities
- Clear cart functionality

### Payment Processing

- Stripe integration for secure payments
- Webhook handling for payment events
- Checkout session management

### Order Management

- Order creation and tracking
- Order history for users
- Order status management

### User Management

- User registration and profile management
- User addresses and preferences
- Wishlist functionality

## 🛠 Technology Stack

### Backend Framework

- **Spring Boot 3.4.1** - Main application framework
- **Java 17** - Programming language
- **Maven** - Dependency management

### Database & Persistence

- **MySQL 8** - Primary database
- **Spring Data JPA** - Data access layer
- **Flyway** - Database migration tool

### Caching

- **Redis 7** - Response caching for product listings

### Security

- **Spring Security** - Authentication and authorization
- **JWT (JSON Web Tokens)** - Stateless authentication
- **BCrypt** - Password hashing

### Payment Processing

- **Stripe Java SDK** - Payment gateway integration

### Additional Tools

- **MapStruct** - Object mapping
- **Lombok** - Code generation
- **SpringDoc OpenAPI** - API documentation (Swagger UI)
- **Thymeleaf** - Template engine (for admin views)
- **Spring Validation** - Input validation
- **Testcontainers** - Integration tests with real MySQL and Redis

## 📋 Prerequisites

- Java 17
- Maven 3.6+
- Docker and Docker Compose (for running the full stack or integration tests)
- Stripe account (for payment processing)

## ⚙️ Setup and Installation

### Option A — Docker Compose (recommended)

The fastest way to get everything running:

```bash
git clone https://github.com/ishanshrestha14/E-commerce-API.git
cd E-commerce-API

# Copy and fill in your secrets
cp .env.example .env
# Edit .env — set DB_PASSWORD, SPRING_JWT_SECRET, and Stripe keys

docker compose up --build
```

The app, MySQL, and Redis will all start together. The app waits for MySQL and Redis to be healthy before starting.

Access the API at `http://localhost:8080`

### Option B — Run locally (manual setup)

**1. Clone the repository**

```bash
git clone https://github.com/ishanshrestha14/E-commerce-API.git
cd E-commerce-API
```

**2. Start MySQL and Redis**

You can use Docker to spin up just the dependencies:

```bash
docker compose up db redis -d
```

Or connect to your own existing MySQL and Redis instances.

**3. Configure environment**

Copy `.env.example` to `.env` and fill in your values:

```env
DB_HOST=localhost
DB_NAME=store_api
DB_USERNAME=root
DB_PASSWORD=your_password

REDIS_HOST=localhost
REDIS_PORT=6379

# Must be at least 32 characters
SPRING_JWT_SECRET=change-me-to-a-secret-at-least-32-chars-long

STRIPE_SECRET_KEY=sk_test_...
STRIPE_WEBHOOK_SECRET_KEY=whsec_...

WEBSITE_URL=http://localhost:8080
```

**4. Run the application**

```bash
./mvnw spring-boot:run
```

The application will start on `http://localhost:8080`. Flyway will run database migrations automatically on startup.

## 🧪 Testing

Integration tests use Testcontainers and spin up real MySQL and Redis containers — Docker must be running.

```bash
./mvnw test
```

Tests include:
- Unit tests for `CartService` and `ProductService` (Mockito, no Spring context)
- Integration tests for `ProductController` against real containers

## 📚 API Documentation

Swagger UI is available at `http://localhost:8080/swagger-ui.html` once the app is running.

### Authentication

#### Login

```http
POST /auth/login
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "password123"
}
```

#### Refresh Token

```http
POST /auth/refresh
Cookie: refreshToken=your_refresh_token
```

#### Get Current User

```http
GET /auth/me
Authorization: Bearer your_access_token
```

### Products

#### List Products (paginated)

```http
GET /products
```

Query parameters:

| Parameter | Default | Description |
|-----------|---------|-------------|
| `page` | `0` | Page number (zero-based) |
| `size` | `20` | Items per page |
| `sortBy` | `name` | Field to sort by |
| `sortDir` | `asc` | `asc` or `desc` |
| `search` | — | Name substring filter |
| `categoryId` | — | Filter by category |

Example:

```http
GET /products?search=shoes&categoryId=2&page=0&size=10&sortBy=price&sortDir=desc
```

Response:

```json
{
  "content": [...],
  "page": 0,
  "size": 10,
  "totalElements": 42,
  "totalPages": 5
}
```

#### Create Product

```http
POST /products
Authorization: Bearer your_access_token
Content-Type: application/json

{
  "name": "Product Name",
  "description": "Product description",
  "price": 29.99,
  "categoryId": 1
}
```

#### Update Product

```http
PUT /products/{id}
Authorization: Bearer your_access_token
Content-Type: application/json

{
  "name": "Updated Name",
  "description": "Updated description",
  "price": 34.99,
  "categoryId": 1
}
```

#### Delete Product

```http
DELETE /products/{id}
Authorization: Bearer your_access_token
```

### Cart

#### Create Cart

```http
POST /carts
```

#### Get Cart

```http
GET /carts/{cartId}
```

#### Add Item to Cart

```http
POST /carts/{cartId}/items
Content-Type: application/json

{
  "productId": 1
}
```

#### Update Cart Item

```http
PUT /carts/{cartId}/items/{productId}
Content-Type: application/json

{
  "quantity": 3
}
```

#### Remove Item from Cart

```http
DELETE /carts/{cartId}/items/{productId}
```

#### Clear Cart

```http
DELETE /carts/{cartId}/items
```

### Checkout & Payment

#### Create Checkout Session

```http
POST /checkout
Authorization: Bearer your_access_token
Content-Type: application/json

{
  "cartId": "cart-uuid",
  "customerEmail": "customer@example.com"
}
```

#### Stripe Webhook

```http
POST /checkout/webhook
Stripe-Signature: webhook_signature
```

### Orders

#### Get My Orders

```http
GET /orders
Authorization: Bearer your_access_token
```

#### Get Order by ID

```http
GET /orders/{orderId}
Authorization: Bearer your_access_token
```

## 🗄️ Database Schema

- **users** — User accounts and credentials
- **profiles** — Extended user information
- **addresses** — User shipping addresses
- **categories** — Product categories
- **products** — Product catalog
- **carts** — Shopping carts
- **cart_items** — Cart line items
- **orders** — Order records
- **order_items** — Order line items
- **wishlist** — User wishlists

## 🔧 Configuration Reference

All configuration is driven by environment variables. See `.env.example` for the full list.

Key settings in `src/main/resources/application.yaml`:

```yaml
spring:
  datasource:
    url: jdbc:mysql://${DB_HOST:localhost}:3306/${DB_NAME:store_api}?createDatabaseIfNotExist=true
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
  jwt:
    secret: ${SPRING_JWT_SECRET}
management:
  endpoints:
    web:
      exposure:
        include: health   # /actuator/health
```

## 📦 Build

```bash
# Create JAR
./mvnw clean package -DskipTests

# Run JAR directly
java -jar target/store-0.0.1-SNAPSHOT.jar
```

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/your-feature`)
3. Commit your changes
4. Push to the branch and open a Pull Request

## 📄 License

This project is licensed under the MIT License.

---
