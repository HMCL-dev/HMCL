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

## Cursor Cloud specific instructions

HMCL is a single JavaFX desktop application built with a Gradle multi-module setup (no backend/database services). Standard build/test/run commands live in `docs/Contributing.md` and the module `build.gradle.kts` files; the notes below only cover non-obvious cloud caveats.

- JDK: `docs/Contributing.md` asks for JDK 17+. The VM ships JDK 21 (no `JAVA_HOME` set), which works fine — do not downgrade or install a separate JDK.
- JavaFX is fetched automatically by the Gradle build (`buildSrc` `JavaFXUtils`), so no manual OpenJFX install is needed; the build just needs network access to Maven Central / JitPack / `libraries.minecraft.net`.
- Lint (matches `.github/workflows/check-codes.yml`): `./gradlew checkstyle checkTranslations`. Note `checkstyle` here is a custom aggregate task, not the default `check`.
- Run the GUI: a VNC X server is available at `DISPLAY=:1`. Launch with `DISPLAY=:1 ./gradlew run --no-daemon` (blocks while the app runs). The app writes its data folder to `./.hmcl` under the repo root by default.
- Expected harmless warnings when running an unofficial local build: `IntegrityChecker ... Signature is missing` / `Self verification failed` (the jar isn't signed), and a `getDefaultBrightness` stack trace when `fastfetch` is absent. Neither blocks startup.
- `MICROSOFT_AUTH_ID` and `CURSEFORGE_API_KEY` are only needed to bake real API keys into a build; blank defaults are fine for building, testing, and offline-account usage.
