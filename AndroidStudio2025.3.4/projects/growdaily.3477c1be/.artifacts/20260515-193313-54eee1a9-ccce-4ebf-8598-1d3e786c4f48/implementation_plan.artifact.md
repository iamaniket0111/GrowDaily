# Completion Rate UI Enhancement

Redesign the Completion Rate section (`layout_total_completion.xml`) to be more visually engaging and data-centric. The new layout will feature a more prominent semi-circle progress view and a grouped row of sub-stats with icons.

## User Review Required

> [!NOTE]
> I am proposing to group the "Completed", "Incomplete", and "Scheduled" stats into a single horizontal container with icons to make the information quicker to digest.

## Proposed Changes

### UI Layouts

#### [layout_total_completion.xml](file:///C:/Users/anike/AndroidStudioProjects/GrowDaily/app/src/main/res/layout/layout_total_completion.xml)
- Increase the size of the `SemiCircleProgressView` container for better impact.
- Refine the percentage typography (larger number, smaller percentage sign).
- Replace the simple columns with a unified horizontal "Insights Row".
- Add icons for each sub-stat:
    - **Completed**: `ic_check`
    - **Incomplete**: `ic_minus` or `ic_close`
    - **Scheduled**: `ic_calendar`
- Apply soft, theme-aware background tints to this row.

---

### Logic & Adapters

#### [AnalysisRepeatTaskFragment.kt](file:///C:/Users/anike/AndroidStudioProjects/GrowDaily/app/src/main/java/com/anitech/growdaily/fragment/AnalysisRepeatTaskFragment.kt)
- Update the binding logic to tint the new sub-stat icons and their background containers.
- Ensure the semi-circle track color is also theme-aware (soft tint).

## Verification Plan

### Manual Verification
- **Visual Inspection**: Open the analysis screen and verify the new Completion Rate section looks balanced and professional.
- **Theme Testing**: Verify the soft tints look correct in both Light and Dark modes.
- **Dynamic Color**: Change task/accent color and ensure all icons and backgrounds update correctly.
