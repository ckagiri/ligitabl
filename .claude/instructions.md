# Claude Instructions for Ligitabl Project

## General Workflow Preferences

- **Proactive Execution**: Continue implementing multi-step tasks without asking for permission to proceed at each step
- **Ask Only for Critical Decisions**: Only pause for user input when:
  - Making destructive/irreversible changes (deleting files, dropping database tables, force-pushing to git)
  - Choosing between multiple equally valid architectural approaches
  - Clarifying ambiguous requirements that affect implementation strategy
- **Complete Task Chains**: When given a task like "implement feature X", complete all necessary steps (code, tests, documentation) unless explicitly told otherwise

## Project-Specific Context

### Architecture
- Clean architecture with use case pattern
- Domain-driven design with value objects (Email, Password, etc.)
- Functional error handling using `Either<UseCaseError, T>`
- Controllers should call use cases, not duplicate business logic

### Code Style
- Use existing use cases rather than creating new ones
- Follow the established error handling pattern (Either fold)
- Maintain separation: controllers → use cases → repositories
- Value objects validate themselves in constructors
- Use Lombok annotations for boilerplate reduction

### Technology Stack
- Spring Boot 3.5.3 + Java 21
- jOOQ for database access (not JPA)
- Liquibase for schema migrations
- Thymeleaf + HTMX + Alpine.js for UI
- JWT for API auth, sessions for web UI

### Common Patterns

**Use Case Execution**:
```java
Either<UseCaseError, ResultType> result = useCase.execute(command);
return result.fold(
    error -> handleError(error),
    success -> handleSuccess(success)
);
```

**Domain Type Creation**:
```java
Email email = Email.create(rawEmail);  // Validates in constructor
Password.Plaintext password = Password.Plaintext.create(rawPassword);
```

**Controller Pattern**:
- Web controllers return view names (String)
- API controllers return ResponseEntity
- Check `HX-Request` header for HTMX partial vs full page

### Testing
- Unit tests for use cases (mock repos)
- Integration tests use Testcontainers + real Postgres
- WebMvc tests for controller mapping

### Database
- Never write SQL directly - use jOOQ generated code
- Schema changes require Liquibase migration + jOOQ codegen
- Use value objects in domain models, not primitives
