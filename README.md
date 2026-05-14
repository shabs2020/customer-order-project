# Customer-service-project

## Architecture

The architecture consists of two individual SpringBoot services namely:
1. **Customer Order Service** | manages customer orders. An order moves through a defined lifecycle, where some transitions are valid and some aren't, and where editing is allowed only in certain states.
2. **Product Catalog Service** | owns product offerings. The order service consults it to validate that referenced offerings actually exist.

Both services have their own databases and communicate only via REST APIs.

## What was Built vs. Cut

### In Scope

*   **Transactional Idempotency**: For create order, a first Key check using a dedicated `idempotency_key` table.
*   **Request Hashing**: payload integrity verification ensures a key isn't "hijacked" for a different order.
*   **Persistence**: JPA usage for both Order and Idempotency entities.
*   **Inter-Service Communication**: customer-order-service connects with product-catalog-service for validation of `productOfferingIds`.
*   **Performance**: Avoiding N+1 problem during productOfferingIds validation for all order items within a customer-order.
*   **Containerization**: Dockerfile (using `eclipse-temurin:21.0.9_10-jre-alpine-3.23`) and Maven build integration for both services.

### Out of Scope (The Cuts)
*   **Redis Caching**: While faster, we prioritized **strict consistency** via PostgreSQL for this implementation to ensure no race conditions during the "lock" phase.
*   **Auth/RBAC**: Security headers are assumed to be validated by an upstream API Gateway.
*   **Distributed Tracing**: Standard logging is used instead of Zipkin/Jaeger for simplicity.

### Idempotency Strategies
* **Decision:** Combined `Idempotency-Key` (header) with a SHA-256 `request_hash`.
* **Why:** To prevent "Key Recycling" errors where a client reuses a key but changes the body.
* **Tradeoff:** If the first request fails mid-transaction, a subsequent request might see a "Processing" state. I used a `TOO_MANY_REQUESTS` (429) status to signal the client to back off until the initial transaction resolves.
*   **Decision** : Instead of a simple "if-exists" check in Java (which is prone to race conditions), we use the Database Entity for persistence.
*   **Why**: By running `saveAndFlush` on a new record with a `UNIQUE` constraint on the `key` column, the database effectively acts as a distributed lock.
*   **Tradeoff**: This adds a write operation even for potentially duplicate requests, but it is the only way to guarantee 100% safety in a multi-instance deployment.

### Validation Placement
* **Decision:** Business validation (Payment method, Catalog check) occurs **before** we attempt to write to the idempotency table.
*   **Why**: To prevent "spending" a client's idempotency key if the request itself is malformed or logically invalid.
*   **Tradeoff**: If the service crashes after validation but before the lock, the client can safely retry.
* **Direct Debit Validation:** The validation currently checks for a non-blank IBAN string. IBAN format is not checked.

### N+1 Prevention strategy
* All requestedIds are collected into a set with 1 DB call and the validation of the `productOfferingId` is done on this set instead of fetching one item at a time.

### PATCH Semantics & Order Immutability
* Orders in `SUBMITTED` state only allow state changes; `CONFIRMED` orders are strictly immutable.
* Logic is kept in the `@Service` layer to ensure cross-entity validation (checking the DB state before applying the patch).

### Persistence & Error Shape
A `ControllerAdvice` is utilized in both services to catch all exceptions including `DataIntegrityViolationException`. This keeps the service logic clean of nested `try-catch` blocks where possible and ensures the client receives a structured JSON error response rather than a stack trace.

### Testing Suite
The project includes comprehensive JUnit 5 tests to verify the transactional integrity of the idempotency logic of create order and the state transition during patch.

### Inter-service Communication
* **Decision:** Synchronous validation via `CatalogClient`.
* **Why:** Ordering an item that doesn't exist is a "hard" business failure. The `POST` request therefore fails immediately even before a DB Fetch is performed.

### Status Code Choices

#### Create-order
*   **201 Created**: First time the order is successfully processed.
*   **200 OK + `isReplay: true`**: When a duplicate key is detected. This tells the client "We already did this, here is your result."
*   **409 Conflict**: If the same key is sent with a **different payload** (Hash mismatch).
*   **429 Too Many Requests**: If a second request arrives while the first is still being processed (Key exists but has no `order_id` yet).

#### Patch-order
*  **201 Created**: First time the order is successfully processed.
* **400 Bad Request**: Triggered by an invalid state transition or an attempt to modify data (other than state) after an order is SUBMITTED
* **400 Bad Request**:  When any change is requested for a confirmed order.
* **404 Not Found**: The order UUID does not exist in the database.


### Assumptions
1.  **Client Responsibility**: It is assumed the client generates a unique `Idempotency-Key` (string) for every unique intent.
2.  **Clock Sync**: It is assumed the server clock is reliable for the `expiryDate` calculation (default 24h).

### Known Limitations
*   **Orphaned Keys**: If a transaction fails after the idempotency record is saved but before the order is saved, the key stays in the DB with a `null` order ID.
*   **Cleanup**: Currently, there is no automatic cleanup. In a production environment, a `Scheduled` task should delete expired keys to prevent the table from growing indefinitely.

---


## How to Run

### Prerequisites

*   **Docker & Docker Compose**: Ensure the Docker daemon is running.
*   **Java 21 & Maven 3.8+**: If you wish to build the artifact outside of a container.

### Quick Start

To build the project and start the full stack (Services + MariaDBs):

*  In customer-order-project folder:
    `mvn clean install`
* Create docker network (if not exists)
  `docker network create pyur-network`
* Go to integration folder
  `cd integration`
  `docker compose up`

### Useful Commands for Review

```bash
# Check if the image was built correctly
docker images | grep customer-order-service

# Follow application logs
docker-compose logs -f app

# Reset the environment (Wipe DB and Containers)
docker-compose down -v


### Reachable Endpoints
*   **Customer Order Service API**: `http://localhost:8080/customer-orders`
*   **Product Catalog Service API**: `http://localhost:8081/product-offerings` 
```

### Configuration

**customer-order-service**

| Property                   | env var                           |                  default value                  | mandatory   | description                 |
|----------------------------|-----------------------------------|:-----------------------------------------------:|:-----------:|-----------------------------|
| server.port                | SERVER_PORT                       |                      8080                       |             | local server port to listen |
| spring.datasource.url      | SPRING_DATASOURCE_URL             | jdbc:mariadb://customer-order-db:3306/order_db  |      x      | mariadb url                 |
| spring.datasource.username | SPRING_DATASOURCE_USERNAME        |                      pyur                       |      x      | mariadb username            |
| spring.datasource.password | SPRING_DATASOURCE_PASSWORD        |                      admin                      |      x      | mariadb password            |


**product-catalog-service**

| Property                   | env var                          |                   default value                   | mandatory   | description                 |
|----------------------------|----------------------------------|:-------------------------------------------------:|:-----------:|------------------------------|
| server.port                | SERVER_PORT                      |                       8081                        |             | local server port to listen  |
| spring.datasource.url      | SPRING_DATASOURCE_URL            | jdbc:mariadb://product-catalog-db:3306/catalog_db |      x      | mariadb url                  |
| spring.datasource.username | SPRING_DATASOURCE_USERNAME       |                       pyur                        |      x      | mariadb username             |
| spring.datasource.password | SPRING_DATASOURCE_PASSWORD       |                       admin                       |      x      | mariadb password             |


## APIs

**customer-order-service**

**For enum values, please use the same string values(case-insensitive) as provided in the exercise document by you**

### List all orders- GET `http://localhost:8080/customer-orders`

**Response**

```json
{
  "orders": [
    {
      "id": "81def7c3-d58b-47c3-a794-2824ac428405",
      "category": "B2B",
      "state": "draft",
      "customer": {
        "id": "CUST-001"
      },
      "site": {
        "id": "SITE-001"
      },
      "paymentMethod": {
        "type": "DIRECT_DEBIT",
        "iban": "6773899308387"
      },
      "items": [
        {
          "productOfferingId": "po-1",
          "quantity": 1
        },
        {
          "productOfferingId": "po-2",
          "quantity": 2
        }
      ],
      "createdAt": "2026-05-14T11:07:54.669994",
      "updatedAt": "2026-05-14T11:07:54.669943"
    }
  ],
  "total": 1,
  "limit": 20,
  "offset": 0
}
```
### Get order with Id- GET `http://localhost:8080/customer-orders/{id}`

```json
{
  "id": "529008ad-18f8-4edd-b2a7-48c6e397f621",
  "category": "B2B",
  "state": "draft",
  "customer": {
    "id": "CUST-001"
  },
  "site": {
    "id": "SITE-001"
  },
  "paymentMethod": {
    "type": "INVOICE",
    "iban": null
  },
  "items": [
    {
      "productOfferingId": "po-2",
      "quantity": 1
    },
    {
      "productOfferingId": "po-1",
      "quantity": 2
    }
  ],
  "createdAt": "2026-05-14T18:15:19.4713",
  "updatedAt": "2026-05-14T18:15:19.47128"
}
```
### Create order with Idemoteny-key- POST `http://localhost:8080/customer-orders`
**Request-Header : Idempotency-Key** = "test-key-106" (example)

**Request-Body**

```json
{
  "category": "B2B",
  "customer": {
    "id": "CUST-001"
  },
  "site": {
    "id": "SITE-001"
  },
  "orderItems": [
    {
      "productOfferingId": "po-2",
      "quantity": 1
    },
    {
      "productOfferingId": "po-1",
      "quantity": 2
    }
  ],
  "paymentType": {
    "type":"INVOICE"
  }
}
```
Response with full order details similar to above request

### PATCH order with Idemoteny-key- PATCH `http://localhost:8080/customer-orders/{id}`


```json
{
"category": "B2B",
"state": "draft|preview|submitted",
"customer":{
"id": "CUST-001"
} ,
"site": {
"id":"sites-001"},
"orderItems": [
{
"productOfferingId": "po-4",
"quantity": 1
},
{
"productOfferingId": "po-1",
"quantity": 2
}
],
"paymentType": {
"type":"INVOICE"
}
}
```

For Submitted orders ready to be confirmed : only post the new status

```json
{
  "state": "confirmed"
}
```
**product-catalog-service**

###  Product catalog list GET: `http://localhost:8081/product-offerings`

```json
[{"id":"po-1","name":"Small Widget","price":29.99},{"id":"po-2","name":"Big Widget","price":49.99},{"id":"po-3","name":"Mega Widget","price":199.99}]
```
These offerings have been imported during startup of the catalog-db via initSQL

###  Product catalog item with ID GET: `http://localhost:8081/product-offerings/{id}`

```json
{"id":"po-1","name":"Small Widget","price":29.99}
```
###  Validate product ids POST: `http://localhost:8081/product-offerings/validate`

**Request Body**

```json
["po-1", "po-2"]
```

**Response**
204 No Content when all validated
404 Not Found when a product id not found

## Databases:
Both databases can be accessed by phpmyadmin running on `http://localhost:8088`
Database names are: customer-order-db & product-catalog-db