---
version: 1
slug: "java-dev-squirrel-vpn-diagnosticsui-java-826269fb"
primary_target: "app/src/main/java/dev/squirrel/vpn/DiagnosticsUi.java"
related_targets: []
---

# Icon-led diagnostics

Mode: Operate. Scope: Connection redesign and subsequent Sessions/Proxies polish,
within the existing Squirrelboard Android shell.
Audience: technical ShadowQUIC users; task: connect, identify the selected profile and inspect actual local runtime measurements.
Preserve all metrics, pending and failure states, secure capture and VPN lifecycle.

## Direction contract

THESIS: Make connection control and measured activity readable through icons and concise labels, removing repeated explanations from the working screen.
OWN-WORLD: Retain the pinned cool canvas, outlined native panels, periwinkle download/action and teal upload, system sans and tabular numbers. Change this screen's composition, not the app's identity.
STORY: Identify state and profile, tap power, then scan traffic, TCP/UDP sessions, tasks and memory. An initially collapsed information action reveals inline help about local-engine scope and measurement semantics before the TCP/UDP measurements; it toggles to a close action and preserves disclosure state across recreation.
FIRST VIEWPORT: A Connection heading leads to a horizontal state/power composition and a compact profile row. One paired transfer panel precedes Runtime: TCP and UDP columns with arrow-led totals, then three concise icon/value rows. Short/wide screens place controls beside metrics; normal and large type scroll without clipping. Signature interaction: the power control changes its icon and accessible action with the engine state, with errors kept inline.
FORM: Code-led, precisely scoped icon-first redesign within the existing user-pinned visual world; seed: none (user-pinned reference).
FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance

The user explicitly requested more icons and less text, then confirmed: “Keep all metrics visible.” All metric widgets remain in the scrollable layout with the inline help collapsed or expanded; only explanatory copy is disclosed. Full measurement meanings and action names remain available to accessibility and tooltips. No remote-connectivity claims or invented measurements.

## Implemented Connection behavior

The state and power action share the control panel’s first row. The action is
112dp wide, at least 96dp tall and uses the inherited 8dp action corners, a
28dp top vector icon and an 8dp label gap. Pending/error detail and a separated
profile row follow. Transfer totals share one outlined panel with equal
Download/Upload columns. Runtime keeps TCP/UDP session counts, both protocols’
upload/download totals, Tasks, Resident and Allocated visible. Missing values
remain dashes and metric failures remain inline.

At `screenHeightDp < 480`, controls and measurements use 1:1.5 columns with a
16dp gap and 12dp control padding. Taller portrait/tablet layouts stack the
control and measurement panels. Native scrolling and text scaling preserve
access to all content. The Runtime information action uses a 48dp target and
inline disclosure, with accessible action names and expanded/collapsed state;
it does not open a help dialog or hide metrics.

## Sessions and Proxies polish

Mode: Operate. The user extended the same icon-first, less-text rule to Sessions
and Proxies, retaining the explicit requirement to keep every metric visible.
Preserve the established palette, panel geometry, native filters, selector order,
selection/probe behavior, full identifiers, and pending/empty/error states.
Use compact icon/count headings and search icons. Session records retain
destination and protocol/route, with arrow-led transfer values and icon-led
first-response/duration values; Half closed stays explicit. Proxy group headers
show the selected member and an icon/count; a refresh icon runs the existing
HTTP probe while cached latency retains its lightning icon. Full measurement
meanings remain in accessibility labels and tooltips. No data is disclosed away.

## Adaptive proxy grid

The user requested two or more proxies in one row. Proxy members and ordinary
outbounds share an adaptive 1–4 column grid, based on the actual list width with
168dp minimum card width scaled by max(1, font scale). Keep 8dp gutters and row
gaps. Selector headers and All outbounds span every column; retain configuration
order, selector context while filtering, shared-member identities and refresh
position. Names wrap on their own line, latency remains visible, and upload/
download quality values stack. Selection and probe controls sit in the card footer
with 48dp targets; selector headers keep their inline chooser and probe controls.
Larger type and narrow windows reduce columns. No measurements are hidden.
