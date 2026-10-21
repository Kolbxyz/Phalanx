# Coding Style & Architecture Rules

Formatting is enforced by ktlint (`.editorconfig`, `android_studio` style, 120 columns) and detekt. Comments explain **why**, not **what**.

## 1. File Structure & Boundaries

- **Hard cap 300 lines per file.** Split when a file passes 250 lines.
- One concept per file, named for it. No `Components.kt`, `Entities.kt` or `Utils.kt` grab-bags.
- Default to `internal`. Use `public` only for genuine cross-package APIs.

## 2. Robustness & Error Handling

- No speculative fallbacks. If something fails, return a typed failure and surface it.
- Never invent placeholder data.
- Never swallow errors with blanket `catch (e: Exception)`.
- No dead code. A helper with no caller does not get written.

## 3. UI & Jetpack Compose

- Stateless composables driven by `StateFlow<UiState>`; no side effects inside composition.
- Every `LazyColumn`/`LazyRow` item has a stable, unique `key`. A timeline item's key is its event or transaction id.
- Immutable `data class` state, annotated `@Immutable`/`@Stable` where it helps recomposition.
- All user-visible text is a `@StringRes Int` in `strings.xml`, with `<plurals>` and positional format args.

## 4. Coroutines & Concurrency

- Coroutines and Flow only.
- `Dispatchers.IO` is applied at the repository / `core/matrix` boundary, never in a composable or ViewModel body.
