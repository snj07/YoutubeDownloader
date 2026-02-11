# Contributing

Thanks for wanting to contribute! Please follow these steps and guidelines.

## Code of conduct
Follow the project's `CODE_OF_CONDUCT.md`.

## Developing locally

- Build:

```bash
./gradlew clean build
```

- Run desktop:

```bash
./gradlew :desktop-app:run
```

- Run CLI:

```bash
./gradlew :cli:run --args="--url https://www.youtube.com/watch?v=... --format mp3"
```

## Tests & static checks

- Run unit tests (if present):

```bash
./gradlew test
```

- Linting: add and run `ktlint` or `detekt` if configured.

## Code style

- Keep Kotlin idiomatic; avoid large, unrelated changes in a single PR.

## Submitting PRs

- Fork -> feature branch -> open PR against `main`.
- Include a clear description, related issue (if any), and a short testing checklist.
- Ensure CI passes.

## Secrets

- Do not commit API keys, tokens, or private credentials.
- Use environment variables or CI secrets for private values. See `.env.example` if present.
