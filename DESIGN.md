---
name: Squirrel — Technical atlas
description: Native Android workspaces with violet hierarchy, paired measurements and readable YAML and logs.
colors:
  primary-light: "#35316E"
  primary-dark: "#C4BCFF"
  on-primary-light: "#FFFFFF"
  on-primary-dark: "#282153"
  surface-light: "#FAFAFC"
  surface-dark: "#181624"
  ink-light: "#26223E"
  ink-dark: "#E9E5F5"
  secondary-light: "#59556F"
  secondary-dark: "#C0BAD1"
  container-light: "#EEEDF8"
  container-dark: "#302B46"
  outline-light: "#DAD8E6"
  outline-dark: "#4A445D"
  success-light: "#176B53"
  success-dark: "#8DDBBD"
  error-light: "#A32735"
  error-dark: "#FFB1BA"
  download-light: "#176B62"
  download-dark: "#8DD6CA"
  chrome-mark: "#E2DEF9"
typography:
  headline:
    fontFamily: sans-serif
    fontSize: 28sp
    fontWeight: 700
  headline-compact:
    fontFamily: sans-serif
    fontSize: 22sp
    fontWeight: 700
  brand:
    fontFamily: sans-serif
    fontSize: 23sp
    fontWeight: 700
  section:
    fontFamily: sans-serif
    fontSize: 19sp
    fontWeight: 700
  title:
    fontFamily: sans-serif
    fontSize: 18sp
    fontWeight: 700
  body:
    fontFamily: sans-serif
    fontSize: 14sp
    fontWeight: 400
  support:
    fontFamily: sans-serif
    fontSize: 13sp
    fontWeight: 400
  route:
    fontFamily: sans-serif
    fontSize: 13sp
    fontWeight: 700
  caption:
    fontFamily: sans-serif
    fontSize: 12sp
    fontWeight: 400
  total:
    fontFamily: sans-serif
    fontSize: 28sp
    fontWeight: 700
    fontFeature: tnum
  measurement:
    fontFamily: sans-serif
    fontSize: 14sp
    fontWeight: 700
    fontFeature: tnum
  core-mark:
    fontFamily: sans-serif
    fontSize: 10sp
    fontWeight: 400
    letterSpacing: 0.08em
  yaml:
    fontFamily: monospace
    fontSize: 13sp
    fontWeight: 400
  log:
    fontFamily: monospace
    fontSize: 12sp
    fontWeight: 400
rounded:
  strip: 8dp
  field: 12dp
  action: 24dp
spacing:
  tight: 4dp
  small: 6dp
  pair: 8dp
  control-vertical: 10dp
  control-horizontal: 12dp
  field-horizontal: 14dp
  group: 16dp
  row-top: 18dp
  section: 20dp
  page-horizontal: 22dp
  page-top: 24dp
  compact-columns: 28dp
components:
  button-primary:
    backgroundColor: "{colors.primary-light}"
    textColor: "{colors.on-primary-light}"
    typography: "{typography.body}"
    rounded: "{rounded.action}"
    padding: 10dp 12dp
  button-primary-dark:
    backgroundColor: "{colors.primary-dark}"
    textColor: "{colors.on-primary-dark}"
    typography: "{typography.body}"
    rounded: "{rounded.action}"
    padding: 10dp 12dp
  button-tonal:
    backgroundColor: "{colors.container-light}"
    textColor: "{colors.primary-light}"
    typography: "{typography.body}"
    rounded: "{rounded.action}"
    padding: 10dp 12dp
  button-tonal-dark:
    backgroundColor: "{colors.container-dark}"
    textColor: "{colors.primary-dark}"
    typography: "{typography.body}"
    rounded: "{rounded.action}"
    padding: 10dp 12dp
  button-disabled:
    backgroundColor: "{colors.container-light}"
    textColor: "{colors.secondary-light}"
    rounded: "{rounded.action}"
  button-disabled-dark:
    backgroundColor: "{colors.container-dark}"
    textColor: "{colors.secondary-dark}"
    rounded: "{rounded.action}"
  filter:
    backgroundColor: "{colors.container-light}"
    textColor: "{colors.ink-light}"
    typography: "{typography.body}"
    rounded: "{rounded.field}"
    padding: 8dp 14dp
  filter-dark:
    backgroundColor: "{colors.container-dark}"
    textColor: "{colors.ink-dark}"
    typography: "{typography.body}"
    rounded: "{rounded.field}"
    padding: 8dp 14dp
  legend:
    backgroundColor: "{colors.container-light}"
    textColor: "{colors.primary-light}"
    typography: "{typography.route}"
    rounded: "{rounded.strip}"
    padding: 12dp 14dp
  legend-dark:
    backgroundColor: "{colors.container-dark}"
    textColor: "{colors.primary-dark}"
    typography: "{typography.route}"
    rounded: "{rounded.strip}"
    padding: 12dp 14dp
  yaml-editor:
    backgroundColor: "{colors.container-light}"
    textColor: "{colors.ink-light}"
    typography: "{typography.yaml}"
    rounded: "{rounded.field}"
    padding: 14dp
  yaml-editor-dark:
    backgroundColor: "{colors.container-dark}"
    textColor: "{colors.ink-dark}"
    typography: "{typography.yaml}"
    rounded: "{rounded.field}"
    padding: 14dp
  log-viewer:
    backgroundColor: "{colors.container-light}"
    textColor: "{colors.ink-light}"
    typography: "{typography.log}"
    rounded: "{rounded.field}"
    padding: 14dp
  log-viewer-dark:
    backgroundColor: "{colors.container-dark}"
    textColor: "{colors.ink-dark}"
    typography: "{typography.log}"
    rounded: "{rounded.field}"
    padding: 14dp
---

# Design System: Squirrel

## Overview

**Creative North Star: "Technical atlas"**

A scientific reference atlas expressed through native Android Views. Violet chrome, quiet work areas and aligned facts give technical ShadowQUIC users a readable view of the local engine. Familiar controls and repeated measurement labels carry the character; there is no illustration or ornamental material layer.

This system describes all five shipped destinations: Connection, Proxies, Configuration, Logs and Sessions, together with their shared navigation chrome and app-owned native dialogs. Configuration and Logs extend the same atlas with large YAML and log workspaces; all destinations retain the violet header and lavender tab treatment. The confirmed atlas selection supplies the metaphor; installed native Views supply the tokens and behavior.

**Key Characteristics:**
- Violet hierarchy with teal download and success roles.
- Paired upload/download columns with explicit labels.
- Flat native controls, thin separators and stable live lists.
- Android system typography, tabular measurements and monospace source text.
- Distinct local-engine, unavailable, empty and failure states.

## Colors

The palette pairs violet ink and lavender support with a near-white work surface; night resources preserve these roles on a deep violet ground. Frontmatter owns exact values. Each light/dark suffix corresponds to Android day/night resource selection, not simultaneous accents.

### Primary

- **Atlas Violet** (`primary-light` / `primary-dark`): primary actions, selected tabs, routes, upload figures, focused editor stroke and native control accent.
- **Action Contrast** (`on-primary-light` / `on-primary-dark`): filled action text. White is also the shared header's brand text.
- **Chrome Violet**: the app header and status bar use `primary-light` in both modes; dark mode does not recolor this fixed header constant.
- **Core Lavender** (`chrome-mark`): the small SHADOWQUIC identity in the production header. “SYNTHETIC DATA” appears only in review fixtures.

### Secondary

- **Download Teal** (`download-light` / `download-dark`): the right-hand download figure in the repeated measurement pair.
- **Engine Teal** (`success-light` / `success-dark`): the explicit “VPN active” state. A green state reports local engine operation, not remote verification.
- **Failure Red** (`error-light` / `error-dark`): failed state and failed/unavailable notices.

### Neutral

- **Work Paper** (`surface-light` / `surface-dark`): all page and system navigation-bar grounds.
- **Violet Ink** (`ink-light` / `ink-dark`): headings, outbound/destination names, ordinary state and engine values.
- **Quiet Ink** (`secondary-light` / `secondary-dark`): body explanation, hints, metadata and disabled action text.
- **Lavender Support** (`container-light` / `container-dark`): tab strip, filter fields, tonal actions, metric/selection strips and YAML/log panes.
- **Fine Rule** (`outline-light` / `outline-dark`): dividers, resting workspace pane strokes and native action/tab ripple tint.

**The Repeated Legend Rule.** Keep upload on the left in the primary violet role and download on the right in the download teal role. Repeat the words at each measurement group so color is never the sole key.

## Typography

**Display and Body Font:** Android system sans (`sans-serif`; theme family `sans`). The user-confirmed native system face is intentional. There is no custom display font or shipping font asset. Native `monospace` is reserved for editable YAML and actual log content; controls, notices and runtime diagnostics retain system sans.

**Character:** Direct, readable and compact. Weight and alignment establish hierarchy; measurement figures use the OpenType tabular-number feature (`tnum`). Sizes are Android scale-independent pixels (`sp`), spacing and shapes are density-independent pixels (`dp`). Default Android font metrics remain in force. YAML adds (2dp) and log text adds (3dp) to each native line with a multiplier of 1; no fixed line-height scale is established.

### Hierarchy

- **Headline / headline-compact:** screen titles; the Connection heading contains an intentional line break. Short viewports use the compact role.
- **Brand / core-mark:** app name and the small core identity. The core mark alone has explicit tracking.
- **Section / title:** “Live engine,” connection state, outbound names and session destinations.
- **Body:** explanatory copy, filter text, radio options, action text and engine labels.
- **Support / route / caption:** summaries, notices, route labels, secondary timing, packet loss and operational caveats. Tabs use the support size.
- **Total / measurement:** prominent transfer totals and smaller paired or right-aligned engine measurements. Probe latency also enables tabular figures while retaining the support size and normal weight.
- **YAML / log:** native normal-weight monospace for source editing and selectable log output. Pane padding is shared, while line-spacing additions remain specific to each role.

**The Figures Stay Put Rule.** Use tabular figures for changing measurements and preserve identity and alignment during refresh; do not add a decorative number font.

## Layout

The native layout is a full-width app header, a horizontally scrollable tab strip and a ViewPager2 body. Insets add the status-bar height to the header and preserve side/bottom system insets. Tabs can scroll to reveal the active destination at enlarged text sizes; pages also support horizontal swiping.

Diagnostic content shares the `page-horizontal` inset. Normal pages use `page-top` at the top; Connection ends with a control-horizontal bottom inset. Most relationships use the small/pair/group/section spacing steps rather than a web grid. Transfer pairs are equal-width columns separated by opposing pair insets. Engine rows allocate label:value widths of 1:1.4, align values to the end and use fine horizontal rules. Row label/value vertical padding is (13dp).

Connection scrolls vertically. Proxies and Sessions place their title, optional description, filters and summary above a RecyclerView that takes the remaining height. Rows wrap content and preserve long destination names; IPv6 destinations keep brackets. Native stable diff updates preserve reading position and avoid animated list churn.

Compact adaptation is based on available configuration height (**screenHeightDp < 480**), not an orientation name or web width breakpoint. It reduces page top padding to control-horizontal, switches titles to headline-compact, omits list descriptions and reduces header vertical padding to pair. Connection puts controls and measurements side by side (weights 1:1.3) with a compact-columns gap. List rows separate identity and measurements with section gaps; proxy actions form a third vertical column (weights 1:1.3:0.8). Sessions places text filtering and protocol radios side by side (equal weights, group gap). Normal proxy actions share a horizontal row with a pair gap. Text scaling still uses native `sp`, wrapping and scrolling.

Configuration uses the same page-horizontal inset and reserves remaining height for its editor. Below (600dp) configuration width, profile/actions/notice stack above editor tools and the YAML pane. At **screenWidthDp >= 600**, controls move into a vertically scrollable sidebar, with width **min(360dp, screenWidthDp / 2 − 36dp)**, a page-top gap to the editor and a flexible editor column. This bounded native layout is observed in phone landscape; it does not establish tablet verification. Profile and Manage have weights 1.8:1; Import file, Download URL and Save have weights 1.05:1.3:0.75, with pair gaps. The More control is (88dp) wide.

Workspace short-height behavior uses measured page height, independently of the diagnostic configuration-height rule. A non-wide Configuration page below **500dp measured height** hides its heading and Selected profile label and uses control-horizontal top padding, preserving editing space above the real IME. Wide Configuration always uses this reduced top padding and retains its sidebar heading. Logs hides its heading below **450dp measured height**. Its top padding is control-horizontal on wide configurations and page-top otherwise. At the same 600dp width threshold, Live updates joins filter and Export logs on one row; below it, the switch sits on its own row. The viewer takes remaining height above an export-scope caption. Source panes preserve native scrolling and selection rather than shrinking type to fit.

## Elevation & Depth

App work surfaces are flat. Tonal support, whitespace, text hierarchy and fine rules create grouping. Action buttons explicitly set elevation to zero and remove their state-list elevation animator. Native ripples supply interaction feedback; no card shadow, gradient, blur or custom motion scale is established. Android system dialogs and the More popup retain their platform presentation and elevation; they are not redefined as flat custom panels.

**The Flat Workbench Rule.** Keep workspaces flat on the shared work surface; use lavender fill for controls, keyed strips and source panes, and separators between diagnostic records.

## Shapes

Three observed rounded forms recur: compact supporting strips (`strip`), filter fields and source panes (`field`), and pill-like actions (`action`). Main content and list rows have no enclosing rounded card. Separators are (1dp); the selected tab has a bottom indicator (3dp). The radius is native dp, not inferred from screenshot pixel dimensions.

## Components

### Buttons

Quiet native actions with clear hierarchy. Filled actions identify Connect/Disconnect and Save; Choose proxy, Probe latency, profile controls, Import file, Download URL, More and exports use tonal variants. Both use the action radius, body typography, sentence case and explicit control padding. Minimum height is (48dp); this is a minimum, not a fixed height. There is no explicit minimum width. Disabled buttons use the container background and secondary text. Probing changes its label to “Probing…” and disables the action; stopping disables the Connection toggle.

Action and tab backgrounds are native RippleDrawables tinted with Fine Rule. These actions and tabs have no custom hover treatment or drawn keyboard focus ring; the YAML editor has its own explicit focus stroke. Preserve native focus, accessibility and activation behavior; do not infer a shipped focus-ring token from documentation previews.

### Inputs / Fields

Filters are single-line native EditTexts on lavender support, with the field radius and body typography. Hints use Quiet Ink; text uses Violet Ink; minimum height is (48dp). Editing filters updates lists immediately. Native cursor, text selection and keyboard behavior remain; focusing does not select all text. Their custom background has no explicit focus or error border. Navigation away clears filter focus and dismisses the keyboard. Filter text and protocol choice survive activity recreation.

### Navigation

Horizontal sentence-case tabs use support typography, a (48dp) minimum height and (10dp vertical, 12dp horizontal) padding. Selected tabs use primary text and a bottom primary indicator; unselected tabs use Quiet Ink. The tab-strip ground is Lavender Support across all five destinations. Header, status bar and tab treatment remain shared as the active destination changes. Back and swipe remain native.

### Supporting Strips

The Connection metric legend uses the strip radius and bold route typography. Outbound selection strips reuse this form with (7dp vertical, 10dp horizontal) padding. These are contextual labels, not interactive chips or cards. Session protocol and route remain plain text.

### Protocol Choices

Sessions uses a horizontal native RadioGroup for All, TCP and UDP. Each option has equal weight, body typography, ink text, primary button tint and a (48dp) minimum height. Selection is conveyed by the native checked radio state as well as its text.

### Measurement Pairs and Engine Rows

Connection totals, proxy upload/download RTT and session byte counts repeat the same left/right semantic grammar. Proxy records retain probe latency and paired packet-loss values; sessions retain protocol/route, first response, duration and half-close state. Engine rows keep labels left and measurements right. Text labels and units remain visible alongside color. Byte counts use B, KiB, MiB or GiB; latency uses ms, duration uses s and loss uses percent. Missing data uses an em dash rather than invented zero.

### Lists, Notices and Dialogs

Records sit directly on the work ground with top spacing and a bottom rule. Titles can be selected. Runtime lists distinguish loading, disconnected, unavailable, truly empty and filtered-empty states, with a visible summary of matching and total records. Errors use Failure Red. State and action/empty notices use polite accessibility live regions; headings expose native accessibility heading semantics. Lifecycle changes clear old measurements. Failed requests explain automatic retries. The one-second refresh cadence is data behavior, not a visual animation token.

Outbound selection, probes, profile selection/management/naming/deletion, configuration downloads, template confirmation and app error dialogs use the shared atlas light/dark context. Retain native dialog focus and IME behavior. Android document pickers remain platform-owned. No new raster assets were introduced by this system.

### Profiles and Configuration Tools

The current profile is a single-line tonal button with end ellipsis and a native vector chevron at the end (24dp drawable, primary tint, pair drawable gap). It opens the native single-choice profile dialog; Manage opens the profile-operation dialog. This is a real vector affordance, not a Unicode arrow. The small YAML · UTF-8 label identifies content format. More opens a native PopupMenu anchored to the tonal control, with Export configuration, Proxy template and Direct test. Configuration export snapshots the editor contents. Save validates and saves; its confirmation distinguishes saved changes from their application on the next connection. Imports and successful downloads enter the editor for review; notices expose outcomes without replacing the draft on failure.

### YAML Editor

A large native multiline EditText uses the YAML type role, field radius, lavender container and ink text, with field-horizontal padding on every side. The resting outline is (1dp) in Fine Rule; focus changes it to (2dp) in the active primary role. Selection highlight uses the primary RGB at alpha (0x33). Text starts at the top/start, preserves long source lines with horizontal scrolling and adds the YAML line-spacing extension. EditorScrollHost gives scrollable lines their horizontal drag before handing a drag at the text edge to page navigation; vertical editing gestures remain with the editor.

Use the native IME and cursor/selection tools. The source disables suggestions, fullscreen/extract editing and personalized learning requests, and keeps editing inside the visible app pane. The measured-height adaptation above removes redundant labels when the keyboard shortens the pane. Draft restoration remains the app's existing per-profile mechanism rather than default View text saving; this visual system does not imply durable unsaved-draft storage.

### Log Workspace

Logs combines a single-line native filter, tonal Export logs action, native Live updates Switch and support-size status above a large selectable monospace viewer. The switch starts checked, uses ink text and a (48dp) minimum height with a control-horizontal switch gap; its native checked thumb/track follow the shared platform theme rather than a newly defined custom shape. The viewer has the field radius, lavender container, ink text, field-horizontal padding, a resting (1dp) outline and primary selection highlight at alpha (0x33). Its clipped ScrollView wraps long log lines and supplies vertical scrolling.

Filtering matches displayed log lines case-insensitively and retains matching source-file headers. Status reports Live or Paused, matching/total content-line counts and service + core scope. A no-match view explains clearing the filter; an empty snapshot remains distinct from loading. Pausing retains the displayed snapshot; if none exists yet, the initial snapshot is still read. Read failure displays an error status while keeping the last successful snapshot visible, with retry when live. Returning to successful reads clears the error. Filter and live preference survive activity recreation.

Live refresh does not rebuild the filter or steal its focus. Unchanged snapshots skip text replacement; a changed snapshot follows the tail only when already at the bottom, otherwise it restores the reading scroll offset. Changing the filter resets the viewer to the top. Export always reads all retained logs independently of the filter, paused state or displayed tail, and the caption says so. Preserve this display/export distinction when extending controls.

## Do's and Don'ts

### Do:

- **Do** use matched light/dark semantic roles from Android resources and keep the header's fixed violet treatment.
- **Do** repeat upload/download labels, left/right order and measurement colors at every pair.
- **Do** retain tabular figures, native sp text scaling, dp spacing and a minimum 48dp control height.
- **Do** distinguish local engine state, remote evidence, missing metrics, zero, empty results and failed requests.
- **Do** preserve long destinations, IPv6 notation, filter state and reading position during live updates.
- **Do** keep all five destinations and app-owned dialogs on the shared atlas, with monospace reserved for YAML and actual logs.
- **Do** preserve editor focus/IME space and log snapshot, filter focus and reading offset during refresh.
- **Do** distinguish configuration saving/application and displayed-log filtering/full retained-log export.

### Don't:

- **Don't** reintroduce the former green/dark Configuration and Logs treatment or change chrome by destination.
- **Don't** substitute color alone for text labels or report unavailable measurements as zero.
- **Don't** add decorative cards, illustration or elevation to the flat diagnostic record system.
- **Don't** treat synthetic review figures or fixture labels as production content or proof of remote connectivity.
- **Don't** spread the editor's focus stroke to filters or invent hover animation and fixed line-height rules absent from the native implementation.
- **Don't** discard a retained log snapshot on read failure or let filter/pause alter the full-export scope.
