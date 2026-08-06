# Deck Planner card-browser visual review

Run the standalone DP-04 review harness from the repository root:

```powershell
.\PreviewDeckPlannerCardBrowser.ps1
```

Review at narrow, normal, and wide window widths. Confirm:

- columns and card bounds recompute without overlap, clipping, or a jump to unrelated cards;
- fast scrolling keeps placeholders stable and does not visibly load far-off cards;
- delayed images replace only their own placeholders;
- mouse hover, click selection, keyboard arrows, Space selection, and focus are visible;
- resizing and result movement preserve logical selection and scroll position;
- no persistent flicker, long EDT stall, or corrupted shared image is visible.

Record any failed observation before DP-04 is marked complete. Production navigation and filter controls remain later roadmap work.

## Presentation checks added after first human pass
- Resize repeatedly and verify exposed background areas are immediately cleared with the current application theme.
- Confirm both visible scrollbars use the application's narrow custom scrollbar treatment.
- At normal and wide sizes, confirm cards remain at least 220 px wide; at narrow size, confirm one centered card remains readable rather than shrinking below that floor.
