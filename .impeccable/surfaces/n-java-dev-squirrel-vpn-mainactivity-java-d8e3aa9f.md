---
version: 1
slug: "n-java-dev-squirrel-vpn-mainactivity-java-d8e3aa9f"
primary_target: "app/src/main/java/dev/squirrel/vpn/MainActivity.java"
related_targets: ["app/src/main/java/dev/squirrel/vpn/DiagnosticsUi.java","app/src/main/java/dev/squirrel/vpn/WorkspaceUi.java","app/src/main/java/dev/squirrel/vpn/ApiToolsActivity.java"]
---

# Squirrelboard redesign

Mode: Operate. Scope: all five native destinations, navigation, dialogs and API tools.
The user pins the visual reference to ../squirrelboard and requires every existing feature.
Preserve profile/editor actions, filters, live diagnostics, secure capture, asynchronous work and VPN lifecycle.

## Direction contract

THESIS: Squirrelboard's precise dashboard language brought to the native VPN workspace.
OWN-WORLD: Cool gray canvas, white outlined panels, slate ink, periwinkle selection and teal transfer accents; 8dp actions and 12dp panels, system sans and tabular figures. Follow system dark appearance with the reference's charcoal palette.
STORY: Read local engine status, connect, inspect transfer totals and engine details, then select routes, inspect sessions, edit YAML or read logs.
FIRST VIEWPORT: White compact brand bar with the reference squirrel mark, five native bottom destinations on phones, horizontal tabs in landscape and vertical navigation on expanded screens, Connection heading, network status panel with its primary action, two transfer tiles, then an outlined live-engine panel. Native scrolling and swipe navigation remain. Signature: paired transfer tiles repeat the reference's periwinkle/teal measurement grammar without invented chart data.
FORM: User-pinned squirrelboard dashboard; code-led native adaptation. The concept roll is superseded by the explicit reference. seed: none (user-pinned reference).
FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance

## Compact Connection rule

At `screenHeightDp < 480`, the status panel uses 12dp padding and omits the
redundant Network status heading. Order its children as state, primary action,
wrapping detail, then profile. Keep the action at its full minimum 48dp height
and ahead of copy that can wrap. Controls and measurements use 1:1.5 widths
with a 16dp gap. The direction contract above remains unchanged.
