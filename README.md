# Instructions for candidates

This is the Java version of the Payment Gateway challenge. If you haven't already read this [README.md](https://github.com/cko-recruitment/) on the details of this exercise, please do so now.

## Requirements
- JDK 17
- Docker

## Template structure

src/ - A skeleton SpringBoot Application

test/ - Some simple JUnit tests

imposters/ - contains the bank simulator configuration. Don't change this

.editorconfig - don't change this. It ensures a consistent set of rules for submissions when reformatting code

docker-compose.yml - configures the bank simulator


## API Documentation
For documentation openAPI is included, and it can be found under the following url: **http://localhost:8090/swagger-ui/index.html**

**Feel free to change the structure of the solution, use a different library etc.**

## Solution Overview
### Endpoints
- `POST /payment` - validates and processes a card payment via the acquiring bank (200 OK with Authorized/Declined, 400 Bad Request with Rejected on invalid/malformed input, 502 Bad Gateway if the bank is unavailable)
- `GET /payment/{id}` - retrieves a previously processed payment by UUID (200 OK with a masked card_number_last_four or 404 Not Found)

### Design Decisions & Assumptions

Key architectural decisions are documented as ADRs in docs/adr/:

- [ADR-001](docs/adr/ADR-001.md): API Contract, JSON Naming, and DTO Separation
- [ADR-002](docs/adr/ADR-002.md): Request Validation Strategy and Rejected Payment Handling
- [ADR-003](docs/adr/ADR-003.md): Acquiring Bank Integration and Error Handling
- [ADR-004](docs/adr/ADR-004.md): Thread-Safe In-Memory Storage & Immutability

### Assumptions:
- Supported currencies: Validated against USD, GBP, and EUR (3 ISO 4217 codes).
- Card expiry: A card is valid through the end of its expiry month (YearMonth). 
- Amount: Integer in minor currency units and must be > 0. 
- PCI & data types: card_number_last_four and cvv are modeled as String to preserve leading zeros; full card numbers and CVVs are
never stored, returned, or logged. 
- Persistence: Authorized and Declined payments are stored in-memory (ConcurrentHashMap); Rejected (400) and upstream failures (502)
are not persisted.