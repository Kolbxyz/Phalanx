# Window Insets & Safe Zones

The app is edge-to-edge (`enableEdgeToEdge()` in `MainActivity`). System bars and cutouts are never inset automatically.

- **Account for insets at container edges.** Any composable aligned to a container edge handles system insets itself unless an ancestor demonstrably already has.
- **Encapsulate insets in shared components**, not in each caller.
- **Selective edges.** Prefer `windowInsetsPadding(WindowInsets.safeDrawing.only(...))` over a blanket `safeDrawingPadding()` when only some edges matter.
- **Never clear siblings with hard-coded heights.** Measure with `onGloballyPositioned` and pass the value.
- **Keyboard.** The composer must stay above the IME (`WindowInsets.ime`).
- **Test both navigation modes.** Gesture and three-button navigation differ by about 30 dp at the bottom.
