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
FIRST VIEWPORT: White compact brand bar with the text-only Squirrel wordmark, five native bottom destinations on phones, horizontal tabs in landscape and vertical navigation on expanded screens, Connection heading, a state/power control panel, one paired transfer panel, then an outlined Runtime panel with all metrics. The Connection-specific contract below supersedes the original tile composition. Native scrolling and swipe navigation remain. Signature: paired transfer columns repeat the reference's periwinkle/teal measurement grammar without invented chart data.
FORM: User-pinned squirrelboard dashboard; code-led native adaptation. The concept roll is superseded by the explicit reference. seed: none (user-pinned reference).
FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance

## Connection contract (supersedes the original composition)

The scoped [Icon-led Connection contract](java-dev-squirrel-vpn-diagnosticsui-java-826269fb.md)
owns Connection composition. The user requested more icons and less text, then
confirmed “Keep all metrics visible.” State and the power action share a row;
pending/error detail and the separated profile follow. The power action is
112dp wide with a 96dp minimum height, inherited 8dp corners and a 28dp top icon.
Totals share one Download/Upload panel. Runtime retains all TCP/UDP, task and
memory measurements. Explanatory help starts collapsed and expands inline
before TCP/UDP; the info action becomes close. No metric widget is hidden by
the disclosure, and its state survives supported recreation.

At `screenHeightDp < 480`, controls and measurements retain 1:1.5 widths and a
16dp gap, with 12dp control padding. Taller layouts stack the panels. This
supersedes the old sequential state/action order and two-tile guidance only;
the global Squirrelboard world and other destinations remain authoritative.

## Alive tasks

Runtime includes a Tasks measurement row (accessible label “Alive tasks”) using the existing label/value
geometry and tabular number role. It reads `num_alive_tasks` from `/api/v1/tokio_metric`
on the Connection refresh. Zero is a valid count; missing or invalid values show a
dash. Disconnect, a new engine run, and a failed metrics refresh clear the value.
An unavailable Tokio endpoint leaves traffic and memory visible and retries normally.

## Compact Proxies

Selectors precede ordinary outbounds and show their members inline, matching
`../squirrelboard`. Members have 48dp circular selection actions and an explicit
checked state; all copies of an outbound share probe pending state. Proxy records
use 12dp horizontal and 8dp vertical padding, 8dp gaps, a refresh probe action,
and upload/download arrows alongside rounded integer RTT and packet loss.
Probe actions use refresh icons; cached HTTP probe results retain lightning
icons. Selector member counts use an outbound icon and a number, with full
accessible names and tooltips. Pending selector actions show hourglasses.
Accessible names and tooltips carry full action and measurement labels.
The page omits its explanatory subtitle and repeated measurement labels.
At heights below 480dp, title/icon count sit beside the filter to preserve list
space. This compact title/count treatment now also applies to Sessions, with
its native protocol choices beside the text filter. Ordinary outbounds remain
available below the selector groups. Both list headings use icon counts and
search-icon filters rather than a repeated live-summary line.

## Sessions polish

The user extended the Connection icon-first rule to both lists, retaining all
metrics. Sessions omits its descriptive subtitle and repeated measurement
labels: upload/download arrows, a lightning first-response icon and a clock
duration icon accompany the values. Full meanings remain accessible and in
tooltips. The complete destination, protocol and route remain visible; Half
closed retains its explicit label beside a half-circle icon. Lists keep native
filters, long-name wrapping, unknown dashes, state notices and stable refreshes.

## YAML editing panel

The scoped [Configuration and Logs contract](in-java-dev-squirrel-vpn-workspaceui-java-5b466e8c.md)
owns the YAML editing flow. Configuration is a scrollable controls page with
an Edit YAML entry, rather than an embedded source pane or control sidebar.
The secure native Dialog fills phones and short landscapes; taller tablets
use a centered max900dp panel with 24dp vertical margins. Save validates
asynchronously and remains open; Close/Back retain the draft. Successful
import, download and template loading open the panel for review. Supported
activity recreation restores its open state, source selection, scroll, focus
and IME visibility with retained drafts. This locally supersedes the inline
editor composition while preserving the Squirrelboard materials.
