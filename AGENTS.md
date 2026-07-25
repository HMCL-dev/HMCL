# Java Code Style Requirements

These rules apply to all Java code written or modified in this repository.

## Nullability

- Every class declared in a newly added Java source file must be annotated with JetBrains Annotations `@NotNullByDefault`.
- When writing or modifying Java code, any type, field, parameter, return value, local variable, or generic type argument that may be `null` must be explicitly annotated with `@Nullable`.
- Nullability in code being written or modified must never be implicit.

## Immutability

- Immutable arrays and collections must be explicitly annotated with JetBrains Annotations `@Unmodifiable` or `@UnmodifiableView` as appropriate.
- For arrays, use type-use syntax such as `String @Unmodifiable []`.

## Documentation

Apply the following requirements when writing or modifying code. Do not use them as code-review criteria.

- Every class, field, and method must have documentation.
- Documentation must use `///` Markdown-style Javadoc comments.
- Keep documentation accurate and specific to the actual behavior, constraints, and side effects.
- Add concise implementation comments inside complex logic whenever they materially improve readability or explain non-obvious behavior.
