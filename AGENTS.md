# Java Code Style Requirements

These rules apply to all Java code written or modified in this repository.

## Nullability

- Annotate every class with JetBrains Annotations `@NotNullByDefault`.
- Any type, field, parameter, return value, local variable, or generic type argument that may be `null` must be explicitly annotated with `@Nullable`.
- Nullability must never be implicit.

## Immutability

- Immutable arrays and collections must be explicitly annotated with JetBrains Annotations `@Unmodifiable` or `@UnmodifiableView` as appropriate.
- For arrays, use type-use syntax such as `String @Unmodifiable []`.

## Documentation

- Every class, field, and method must have documentation.
- Documentation must use `///` Markdown-style Javadoc comments.
- Keep documentation accurate and specific to the actual behavior, constraints, and side effects.
- Add concise implementation comments inside complex logic whenever they materially improve readability or explain non-obvious behavior.

## Gradle

- When invoking Gradle in this repository, always set `GRADLE_USER_HOME` to the workspace-local `.gradle-user-home` directory.
- Prefer commands such as `./gradlew -g .gradle-user-home ...` or the equivalent environment-variable-based configuration.
- When running Gradle `test` tasks, use a higher timeout of ten minutes.
