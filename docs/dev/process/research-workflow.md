# Research Before Adding a Dependency (MANDATORY)

Before adding a library or adopting an unfamiliar API:

1. **Official docs.** Read the current documentation and release notes.
2. **Pros, cons, alternatives.** Check known issues, maintenance status, size and licence.
3. **OSS practice.** See how comparable open-source projects (Element X, Wanda) do it.
4. **Decide and justify.** Record a one-line rationale as a comment in `gradle/libs.versions.toml`.

## Project policy

- Prefer sovereign French solutions, then European ones, over others. Ask for approval before adding either.
- No Google services, no telemetry, no analytics, no crash-reporting SDKs.
- The licence must be compatible with EUPL-1.2.

Does not apply to bug fixes or code using libraries already in the project.
