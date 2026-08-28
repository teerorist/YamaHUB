# Implementation Plan - Scale Gauge to 110% Screen Width

Proportionally scale the `SmithsGauge` component to exactly 110% of the screen width.

## Proposed Changes

### UI Components

#### [MODIFY] [DashboardScreen.kt](file:///D:/###Users/teerorist/Desktop/YamaHUB/Andro/app/src/main/java/com/yamahub/app/ui/DashboardScreen.kt)
- Use `LocalConfiguration.current.screenWidthDp.dp` to get the screen width.
- Update the `Box` containing `SmithsGauge` to use `Modifier.requiredWidth(screenWidth * 1.1f)`.
- Keep `aspectRatio(1f)` to ensure the scaling is proportional (height will also be 110% of screen width).
- Ensure the `Box` is centered despite being wider than its parent `Column` (which it will be by default with `Alignment.CenterHorizontally` in the `Column`).

## Verification Plan

### Manual Verification
- Deploy the app.
- Confirm the gauge overflows the screen edges by 5% on each side.
- Verify the gauge remains square and all internal elements are scaled correctly.
