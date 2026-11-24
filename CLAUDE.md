Backend service: Java 25, Spring Boot, Maven. Check `pom.xml` for exact versions before writing imports.

## Task files
- Each part of the task lives in its own file: `instruction_part1.md`, `instruction_part2.md`, and so on.
- The highest-numbered part is the current task. Read every part file in order before starting.
- Earlier parts still apply unless the current part changes them. Keep all earlier tests passing.
- Build only the current part. Don't build ahead, and don't change earlier behavior the current part doesn't ask for.
- If one is unclear or contradicts another, say so in your summary.And once approve add back to the part file.
- Only implement the `instructions-part{n}.md` file I name in my prompt. Don't read ahead or act on later parts just because they exist in the repo.

## How to work
- Follow the design in the part file: package layout, class names, method signatures. If you must deviate, say why.
- If the part file is silent on something, pick the simplest safe option and list it under "Decisions". Stop and ask only if the choice changes the public API or the output format.
- Write the tests the part file lists and add more testcases if required and add to part file, then the code. Run `mvn -q test` and fix until green. Never report done with failing or skipped tests.
- Keep changes small. No unrelated refactors. Don't add a dependency without saying why.
- Copy imports and patterns from existing passing code before writing new ones.

## End every task with this summary
```
Files changed:
Decisions not in the part file:
Not done / known gaps:
Tests: <pass/fail counts from mvn -q test>
```

## Commands
- `mvn -q test` runs all tests. `mvn -q -Dtest=SomeClassTest test` runs one class.
- Don't start the app unless asked.

## Java style
- Records for DTOs and immutable values. Classes for anything with behavior or mutable state.
- Constructor injection, `final` fields. No field injection, no Lombok.
- Prefer plain loops over long stream chains. A short lambda is fine if it reads in one line.
- Small classes with one job. Clear names.
- No preview features (anything that needs `--enable-preview`).
- SLF4J with placeholders for logging. No `System.out`.

## Spring
- Controllers only map HTTP. Business logic lives in services, which don't use HTTP types.
- One `@RestControllerAdvice` maps exceptions to status codes. Client errors are 4xx, never 500. No stack traces in responses.
- Settings come from `@ConfigurationProperties`, not hardcoded values.


## Tests
- JUnit 5 and AssertJ. MockMvc for endpoints.
- No test touches the real network. Stub external HTTP calls.
- Cover failure cases, not just the happy path.
- Tests are deterministic: no sleeps to wait for timing.