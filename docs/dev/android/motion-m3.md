# Material 3 Expressive & Motion

## 1. Theming and Motion Scheme

- Use `MaterialExpressiveTheme` and `MotionScheme.expressive()` (see `ui/theme/Theme.kt`).
- Take spring specs from `MaterialTheme.motionScheme`; never hand-roll raw `spring()` parameters in screens.
- Reduced motion swaps in `NoMotionScheme` (1 ms tweens, never `snap()`, so predictive back can still seek). Any animation that hand-rolls a spec must check `LocalReducedMotion`.

## 2. Screens and Navigation

- Predictive back on every screen.
- Shared-axis transitions between destinations; shared-element transitions for list → detail.
- Adaptive layout via `NavigationSuiteScaffold` / `ShortNavigationBar` and list-detail scaffolds; every screen must work at compact, medium and expanded widths.

## 3. Expressive Components

Prefer the Expressive component over the static equivalent:
`ShortNavigationBar`, `FloatingToolbar`, `ButtonGroup`/`ToggleButton`, `SplitButton`, `LoadingIndicator`, wavy progress, `MaterialShapes` morphs, `LargeFlexibleTopAppBar`.
