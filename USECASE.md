## Customer Order Service: Requirements

Two backend services that talk to each other:

1. **Customer Order Service** | manages customer orders. An order moves through a defined lifecycle, where some transitions are valid and some aren't, and where editing is allowed only in certain states.
2. **Product Catalog Service** | owns product offerings. The order service consults it to validate that referenced offerings actually exist.

When an order is created or modified, the order service must confirm with the catalog that each `productOfferingId` is real before accepting the change.

## Stack

- **Java 21**
- **Spring Boot 3.x**
- **Maven**
- Persistence and migrations are your choice (Postgres, H2, Flyway, JPA, MapStruct, Lombok - all welcome, none required).
- **Docker + docker-compose**: the submission must come up cleanly with `docker compose up`. Both services, their database(s), and any seed data must be ready by the time compose finishes.

The order service should bind to **port 8080**. The catalog service should bind to **port 8081**.

The catalog should be pre-seeded with a small set of product offerings (at minimum: `po-1`, `po-2`, `po-3`) so the system i*s usable immediately after `docker compose up`. Seed however you like (Flyway, app init, SQL dump, etc.).

---

## Contract

The two services expose REST APIs.

### Customer Order Service (port 8080)

#### Resource: `Order`

```json
{
  "id": "string (UUID, server-assigned)",
  "state": "draft | preview | submitted | confirmed",
  "category": "B2B | B2C",
  "customer": { "id": "string" },
  "site": { "id": "string" },
  "orderItems": [
    { "productOfferingId": "string", "quantity": "integer (>= 1)" }
  ],
  "paymentMethod": {
    "type": "DIRECT_DEBIT | INVOICE",
    "iban": "string (required if type = DIRECT_DEBIT, otherwise omit)"
  },
  "createdAt": "ISO 8601 timestamp",
  "updatedAt": "ISO 8601 timestamp"
}
```

#### Operations

The order service must support, on `/customer-orders`:

- **Create** an order. Server assigns the `id` and the initial state of `draft`. Required at creation: `category`, `customer.id`, `site.id`, `orderItems` (non-empty), `paymentMethod.type`. Honors an `Idempotency-Key` request header, see below.
- **Retrieve** a single order by id.
- **List** orders, with paging (`limit`, `offset`) and an optional `category` filter. Response shape: `{ "items": [...], "total": int, "limit": int, "offset": int }`. `limit` defaults to 20, `offset` defaults to 0.
- **Partially update** an order. Behaves as JSON Merge Patch: fields present in the body are updated; absent fields are left untouched. State rules below constrain what may change in which state.

### State transitions

- New orders start in `draft`.
- Allowed transitions (set by patching the `state` field):
  - `draft → preview`
  - `preview → draft` (revert)
  - `preview → submitted`
  - `submitted → confirmed`
- All other transitions are rejected.
- Once `submitted`, payload fields **other than `state`** cannot be modified.
- Once `confirmed`, no further changes are allowed.

### Idempotency

- Order create accepts an optional `Idempotency-Key` request header.
- Two creates with the **same key + identical payload** must return the **same order**. The replay should be clearly distinguishable from a fresh creation.
- Two creates with the **same key + different payload** must be rejected as a conflict.

### Catalog interaction

- On create and on patch, the order service must verify each `productOfferingId` against the catalog. References to unknown offerings must be rejected.
- Catalog unavailability must be handled.

### Errors

Validation failures and rule violations return a JSON body. Shape is your choice (RFC 7807, your own schema, etc.). Be **consistent across all error responses**.

### Product Catalog Service (port 8081)

A small service that owns product offerings.

#### Resource: `ProductOffering`

```json
{
  "id": "string",
  "name": "string",
  "price": "decimal"
}
```

The minimum the catalog must support is a way for the order service to look up an offering by `id` and confirm whether it exists.

---

