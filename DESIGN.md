---
name: Squirrel — Squirrelboard native dashboard
description: Cool gray workspaces, outlined panels and periwinkle/teal diagnostics adapted from squirrelboard to
  native Android.
colors:
  surface-light: '#F7F8FA'
  ink-light: '#292E3D'
  secondary-light: '#666E80'
  primary-light: '#5366CA'
  on-primary-light: '#FFFFFF'
  container-light: '#EDF0FD'
  outline-light: '#E3E7EF'
  success-light: '#258161'
  error-light: '#B83E4C'
  download-light: '#237F77'
  panel-light: '#FFFFFF'
  sidebar-light: '#FCFCFD'
  log-warning-light: '#936000'
  log-magenta-light: '#8A4BA8'
  surface-dark: '#14171E'
  ink-dark: '#E3E7F1'
  secondary-dark: '#A2A9B9'
  primary-dark: '#98A8FF'
  on-primary-dark: '#171D32'
  container-dark: '#2C3350'
  outline-dark: '#2C3240'
  success-dark: '#66C9A5'
  error-dark: '#ED949A'
  download-dark: '#65CBBD'
  panel-dark: '#1C2029'
  sidebar-dark: '#191C24'
  log-warning-dark: '#E7BD65'
  log-magenta-dark: '#CE9BE7'
  brand-mark: '#5570E9'
typography:
  headline:
    fontFamily: sans-serif
    fontSize: 26sp
    fontWeight: 700
  headline-compact:
    fontFamily: sans-serif
    fontSize: 22sp
    fontWeight: 700
  api-title:
    fontFamily: sans-serif
    fontSize: 23sp
    fontWeight: 700
  brand:
    fontFamily: sans-serif
    fontSize: 20sp
    fontWeight: 700
  state:
    fontFamily: sans-serif
    fontSize: 20sp
    fontWeight: 700
  result-title:
    fontFamily: sans-serif
    fontSize: 18sp
    fontWeight: 700
  section:
    fontFamily: sans-serif
    fontSize: 16sp
    fontWeight: 700
  panel-title:
    fontFamily: sans-serif
    fontSize: 15sp
    fontWeight: 700
  body:
    fontFamily: sans-serif
    fontSize: 14sp
    fontWeight: 400
  support:
    fontFamily: sans-serif
    fontSize: 13sp
    fontWeight: 400
  action:
    fontFamily: sans-serif
    fontSize: 13sp
  route:
    fontFamily: sans-serif
    fontSize: 13sp
    fontWeight: 700
  caption:
    fontFamily: sans-serif
    fontSize: 12sp
    fontWeight: 400
  micro:
    fontFamily: sans-serif
    fontSize: 11sp
    fontWeight: 400
  total:
    fontFamily: sans-serif
    fontSize: 24sp
    fontWeight: 700
    fontFeature: tnum
  measurement:
    fontFamily: sans-serif
    fontSize: 14sp
    fontWeight: 700
    fontFeature: tnum
  core-mark:
    fontFamily: sans-serif
    fontSize: 9sp
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
  action: 8dp
  panel: 12dp
spacing:
  '4': 4dp
  '6': 6dp
  '8': 8dp
  '10': 10dp
  '12': 12dp
  '14': 14dp
  '16': 16dp
  '18': 18dp
  '20': 20dp
  '22': 22dp
  '24': 24dp
components:
  button-primary:
    backgroundColor: '{colors.primary-light}'
    textColor: '{colors.on-primary-light}'
    typography: '{typography.action}'
    rounded: '{rounded.action}'
    padding: 10dp 12dp
  button-connection:
    backgroundColor: '{colors.primary-light}'
    textColor: '{colors.on-primary-light}'
    typography: '{typography.action}'
    rounded: '{rounded.action}'
    padding: 10dp 12dp
    width: 112dp
  button-outline:
    backgroundColor: '{colors.panel-light}'
    textColor: '{colors.ink-light}'
    typography: '{typography.action}'
    rounded: '{rounded.action}'
    padding: 10dp 12dp
  button-disabled:
    backgroundColor: '{colors.surface-light}'
    textColor: '{colors.secondary-light}'
    typography: '{typography.action}'
    rounded: '{rounded.action}'
    padding: 10dp 12dp
  filter:
    backgroundColor: '{colors.panel-light}'
    textColor: '{colors.ink-light}'
    typography: '{typography.body}'
    rounded: '{rounded.panel}'
    padding: 8dp 14dp
  panel:
    backgroundColor: '{colors.panel-light}'
    textColor: '{colors.ink-light}'
    typography: '{typography.body}'
    rounded: '{rounded.panel}'
    padding: 16dp
  route-chip:
    backgroundColor: '{colors.container-light}'
    textColor: '{colors.primary-light}'
    typography: '{typography.route}'
    rounded: '{rounded.action}'
    padding: 7dp 10dp
  navigation-selected:
    backgroundColor: '{colors.container-light}'
    textColor: '{colors.primary-light}'
    typography: '{typography.route}'
    rounded: '{rounded.action}'
    padding: 8dp 12dp
  yaml-editor:
    backgroundColor: '{colors.panel-light}'
    textColor: '{colors.ink-light}'
    typography: '{typography.yaml}'
    rounded: '{rounded.panel}'
    padding: 14dp
  yaml-panel:
    backgroundColor: '{colors.panel-light}'
    textColor: '{colors.ink-light}'
    rounded: '{rounded.panel}'
    padding: 12dp 16dp 16dp
  log-viewer:
    backgroundColor: '{colors.panel-light}'
    textColor: '{colors.ink-light}'
    typography: '{typography.log}'
    rounded: '{rounded.panel}'
    padding: 14dp
  button-primary-dark:
    backgroundColor: '{colors.primary-dark}'
    textColor: '{colors.on-primary-dark}'
    typography: '{typography.action}'
    rounded: '{rounded.action}'
    padding: 10dp 12dp
  button-connection-dark:
    backgroundColor: '{colors.primary-dark}'
    textColor: '{colors.on-primary-dark}'
    typography: '{typography.action}'
    rounded: '{rounded.action}'
    padding: 10dp 12dp
    width: 112dp
  button-outline-dark:
    backgroundColor: '{colors.panel-dark}'
    textColor: '{colors.ink-dark}'
    typography: '{typography.action}'
    rounded: '{rounded.action}'
    padding: 10dp 12dp
  button-disabled-dark:
    backgroundColor: '{colors.surface-dark}'
    textColor: '{colors.secondary-dark}'
    typography: '{typography.action}'
    rounded: '{rounded.action}'
    padding: 10dp 12dp
  filter-dark:
    backgroundColor: '{colors.panel-dark}'
    textColor: '{colors.ink-dark}'
    typography: '{typography.body}'
    rounded: '{rounded.panel}'
    padding: 8dp 14dp
  panel-dark:
    backgroundColor: '{colors.panel-dark}'
    textColor: '{colors.ink-dark}'
    typography: '{typography.body}'
    rounded: '{rounded.panel}'
    padding: 16dp
  route-chip-dark:
    backgroundColor: '{colors.container-dark}'
    textColor: '{colors.primary-dark}'
    typography: '{typography.route}'
    rounded: '{rounded.action}'
    padding: 7dp 10dp
  navigation-selected-dark:
    backgroundColor: '{colors.container-dark}'
    textColor: '{colors.primary-dark}'
    typography: '{typography.route}'
    rounded: '{rounded.action}'
    padding: 8dp 12dp
  yaml-editor-dark:
    backgroundColor: '{colors.panel-dark}'
    textColor: '{colors.ink-dark}'
    typography: '{typography.yaml}'
    rounded: '{rounded.panel}'
    padding: 14dp
  yaml-panel-dark:
    backgroundColor: '{colors.panel-dark}'
    textColor: '{colors.ink-dark}'
    rounded: '{rounded.panel}'
    padding: 12dp 16dp 16dp
  log-viewer-dark:
    backgroundColor: '{colors.panel-dark}'
    textColor: '{colors.ink-dark}'
    typography: '{typography.log}'
    rounded: '{rounded.panel}'
    padding: 14dp
---

# Design System: Squirrel

## Overview

**Creative North Star: "Squirrelboard native dashboard"**

Squirrelboard’s precise dashboard language expressed through native Android Views. A cool gray canvas supports white outlined panels, slate text, periwinkle selection and teal transfer accents. Charcoal day/night counterparts preserve the same hierarchy. The local reference is `../squirrelboard`; the native implementation owns the values recorded here, including stronger light-mode text accents for contrast.

The same materials cover Connection, Proxies, Configuration, Logs, Sessions, API tools and app-owned dialogs. Technical detail remains visible through labeled measurements, native text scaling and large source panes. The header displays the Squirrel wordmark without an adjacent icon; the reference vector asset remains available. Android system sans replaces the reference’s web font, while YAML, logs and protocol/JSON results use native monospace.

**Key Characteristics:**
- Cool gray canvas, white or charcoal panels and thin outlines.
- Periwinkle selection and download roles; teal upload and green local-engine status.
- Small rounded actions, larger panel corners and flat native ripple feedback.
- System sans, tabular measurements and monospace source data.
- Adaptive bottom navigation, horizontal tabs and an expanded navigation rail.

## Colors

The palette is cool and restrained, with accents reserved for actions, selected destinations, transfer direction and explicit status. Frontmatter is normative. Light/dark pairs map to Android day/night resources; the fixed brand-mark color belongs only to the squirrel identity.

### Primary

- **Periwinkle** (`primary-light` / `primary-dark`): filled primary actions, selected navigation, route labels, section icons and download measurements.
- **Action Contrast** (`on-primary-light` / `on-primary-dark`): text and icons on filled primary actions.
- **Selection Wash** (`container-light` / `container-dark`): selected navigation backgrounds and outbound selection chips.
- **Squirrel Blue** (`brand-mark`): the fixed vector mark; it is not another action color.

### Secondary

- **Transfer Teal** (`download-light` / `download-dark`): upload icons and row measurements. The resource name is inherited; the rendered semantic role is upload.
- **Engine Green** (`success-light` / `success-dark`): the labeled local “VPN active” state.
- **Failure Red** (`error-light` / `error-dark`): failed requests, unavailable notices and connection errors.
- **Log Amber** (`log-warning-light` / `log-warning-dark`): warning-level labels and ANSI yellow text.
- **Log Magenta** (`log-magenta-light` / `log-magenta-dark`): ANSI magenta text in retained log content.

### Neutral

- **Cool Canvas** (`surface-light` / `surface-dark`): page grounds, system navigation bars and disabled actions.
- **Panel Paper / Charcoal** (`panel-light` / `panel-dark`): outlined panels, fields, source panes and header/status-bar grounds.
- **Quiet Sidebar** (`sidebar-light` / `sidebar-dark`): adaptive navigation background.
- **Slate Ink** (`ink-light` / `ink-dark`): headings, totals, state and ordinary text.
- **Secondary Slate** (`secondary-light` / `secondary-dark`): explanatory copy, metadata, hints and disabled text.
- **Fine Outline** (`outline-light` / `outline-dark`): panel/field/button strokes, engine dividers and native ripple tint.

**The Labeled Direction Rule.** Use periwinkle for download and teal for upload. Connection columns show Download then Upload; session pairs put upload before download. Session and proxy measurements use upload/download vector arrows with full accessible labels and tooltips. Direction remains identifiable independently of color.

## Typography

**Display and Body Font:** Android system sans (`sans-serif`; theme family `sans`). System sans is a deliberate native adaptation of the reference. There is no custom font asset. **Source Font:** native `monospace`, reserved for YAML, log content and API protocol/JSON results.

**Character:** Compact, readable and technical. Bold weight separates headings and measurements; source text retains a normal weight. Android default font metrics remain in force. Frontmatter records observed sp roles, not a fixed line-height system. YAML adds (2dp) and logs/API results add (3dp) to each native line at multiplier 1. Action text inherits the native Button weight; no explicit custom weight is established.

### Hierarchy

- **Headline / headline-compact:** main destination titles, with a smaller role for configuration heights below (480dp).
- **API title / result-title:** the standalone administration title and its result heading.
- **Brand / core-mark:** the header’s Squirrel identity and small SHADOWQUIC identifier; the core mark alone uses explicit tracking.
- **State / section / panel-title:** connection state, Runtime/session headings and proxy row names. The inherited panel-title token now serves the proxy row title.
- **Body / support / action / route:** ordinary copy and inputs, explanatory metadata, native action labels and bold selection/route labels. Regular navigation uses support-sized text; selected navigation is bold.
- **Caption / micro:** operational caveats, measurement labels, tile captions and bottom-navigation labels. Selected bottom labels are bold.
- **Total / measurement:** Connection’s transfer totals and smaller row/engine measurements. Probe latency also enables tabular figures at support size and normal weight.
- **YAML / log:** multiline editor and selectable output. API results reuse the log role.

**The Figures Stay Put Rule.** Use tabular figures for changing measurements and preserve identity and alignment during refresh.

## Layout

The native shell has a panel-colored brand header, adaptive navigation and a ViewPager2 body with five destinations. System insets preserve status, side and navigation-bar space. Header horizontal padding is (22dp), vertical padding (6dp) plus the status inset; overflow and Back targets are (48dp). The Squirrel wordmark is text only, with no adjacent icon or icon gap. A horizontal separator is not required between header and canvas.

Navigation follows available Android configuration size: below **600dp width**, five equal bottom destinations use icons above (11sp) labels and “Config” as the short visible label, with a full Configuration accessibility name. At **840dp width or more and 480dp height or more**, a (184dp) left rail displays icon-and-label rows. Remaining sizes use horizontal scrolling tabs under the header. Regular tabs/rail labels are (13sp). All retain swipe navigation and native selected states. A short, wide landscape viewport uses horizontal tabs rather than the rail.

Page horizontal padding is (22dp). Normal diagnostic pages begin at (24dp), short pages at (12dp). Panel padding is normally (16dp); panel gaps are (12dp) or (16dp). The shared spacing scale is in frontmatter; values are Android dp rather than browser pixels. Engine label/value rows allocate widths 1:1.4, use (13dp) vertical padding and align values to the end. Lists use outlined, content-wrapping records with (12dp) gaps, preserving long names and bracketed IPv6 destinations. RecyclerView stable diff updates keep reading position without item animations.

Diagnostics compact mode is **screenHeightDp < 480**. Titles shrink, list descriptions disappear, and Connection puts controls beside measurements at widths 1:1.5 with a (16dp) gap. The compact Connection control panel uses (12dp) padding. State and the power action share its first row; pending/error detail and the separated profile row follow. The power action retains its documented minimum height while wrapping copy expands the panel. Portrait and taller tablet layouts stack the control panel above measurements. Session rows separate identity from measurements at 1:1.3. Compact Sessions places text filtering and protocol choices side by side. Compact Proxies places its title and icon count beside the filter to preserve list height. These are height adaptations, independent of navigation width thresholds.

Configuration is a vertically scrolling controls page: title, profile row, import/download/save actions, status and a YAML action panel with More and Edit YAML. At **600dp width or more**, its centered controls are at most **720dp** wide; narrower pages fill the available width. YAML editing opens in a separate native Dialog. Below **600dp width** or **480dp height**, it fills the window with square outer corners. Otherwise, a centered panel uses **min(900dp, screenWidthDp − 48dp)** width, (12dp) corners and (24dp) top/bottom margins inside a full-height resizing window. Panel padding is (12dp 16dp 16dp), plus system insets. The editor fills remaining height; when the measured inner panel height is below **300dp**, profile/status context hides while the title, Save, Close and source remain. Logs hides its heading below **450dp measured page height**, retaining filter/live/export controls and a flexible selectable viewer.

API tools keeps its Back/title header outside a vertically scrolling body. Below **600dp width**, form and result stack; at or above it, columns use 1:1.2 widths and (24dp) form-end spacing. The entire body scrolls together. Scope choices, native Spinners and conditional fields retain their own Android behavior.

## Elevation & Depth

Ordinary panels, buttons and navigation are flat. Fill contrast and fine (1dp) outlines create grouping; buttons disable Android’s elevation animator and set elevation to zero. Focus in the YAML editor changes its outline to (2dp) periwinkle. Android owns dialog, popup, cursor, switch and error feedback; this system does not establish a custom shadow scale or motion duration. The YAML editing window dims the underlying page at 32%; Android owns its window elevation. Native ripples use the outline role. Navigation uses ViewPager2 scrolling; live rows suppress decorative item animation.

**The Outlined Panel Rule.** Separate work areas with panel fill and a thin outline; keep ordinary panels and actions flat.

## Shapes

Actions, selected navigation backgrounds and route chips share small (8dp) corners. Panels, filters, source panes and API fields share larger (12dp) corners. Outlined actions use a (1dp) border; filled primary and disabled actions omit it. Panel strokes and keyed engine dividers use the fine outline role.

The reference squirrel is copied as vector geometry in `ic_squirrelboard.xml`, including its blue rounded-square ground and white squirrel stroke; native line icons use vector XML, normally an (18dp) rendered size and an (8dp) label gap. Connection’s power action uses a larger top icon as documented below. Bottom navigation moves the icon above the label with a (4dp) gap. Do not substitute text glyphs for these icons. No shipping raster is introduced by this visual system.

## Components

### Buttons

Compact rectangular actions with modest rounding. Primary actions use periwinkle and action-contrast text; secondary actions use panel fill, slate text and a thin outline. Both have (8dp) corners, (10dp 12dp) padding and a minimum (48dp) height. API actions locally use (14dp) horizontal padding. Native outline-tinted ripples provide pressed feedback; disabled actions use canvas fill and secondary text, with no elevation. Preserve pending labels and disabled controls during work.

### Connection control and disclosure

Connection pairs its labeled local-engine state with a power action, then places the selected profile beneath a divider. The primary action uses the `button-connection` width, the shared action corners and padding, a minimum (96dp) height and a top vector icon (28dp) with an (8dp) label gap. Power changes to an hourglass during preparation or transition; the visible action and accessible name follow the engine state. Detail appears inline for pending and error states. Ordinary disconnected/connected states omit repeated explanatory copy.

The Runtime header has a (48dp) information action. Its help starts collapsed and expands inline before the TCP/UDP measurements; the icon changes from information to close, with matching action names, tooltips and an expanded/collapsed accessibility state on supported Android versions. Disclosure state survives supported recreation. Help explains local-engine scope, measurement semantics and packet support without covering or hiding metric widgets. All metrics remain in the scrollable page in both states.

### Chips

Proxy members use circular selection controls. A checked circle and periwinkle outline identify the active member; the accessible name includes “Selected” and its selector. Selector headers show the current member and member count. Sessions pair a small tonal protocol badge with the destination and retain plain route text below it.

### Cards / Containers

Connection controls, the shared transfer panel, Runtime detail and proxy/session rows use panel fill with (12dp) corners and a (1dp) outline. Normal internal padding is (16dp). Session records use (16dp 16dp 4dp) outer padding and manage bottom spacing inside. Proxy records use (12dp 8dp) padding and (8dp) gaps. Compact Connection uses its documented smaller padding and control-first order.

### Proxy selectors and outbound quality

Following `../squirrelboard`, selectors appear first, each immediately followed by its configured members in configuration order. Members are directly selectable; a selector's radio icon also opens the native chooser. Ordinary outbounds follow under All outbounds. Shared members have independent row identities per selector and retain their own selected state. Live refreshes preserve scroll position, and searching a member retains its selector context. Searching a selector shows its members.

Each proxy row shows its name, a lightning icon with the cached HTTP probe result, and a (48dp) refresh-icon action to probe again. Selector headers retain the checked selected-member name and show their member count beside an outbound icon. Pending probes show an hourglass and disable duplicate requests across all copies of the outbound. A pending selection uses an hourglass and disables that selector's controls until the request finishes; the selected outline and accessible state retain the last confirmed selection. The checkmark changes only after a successful refreshed snapshot. Failures remain visible in the page's action notice.

When a QUIC sample exists, a compact measurement line shows upload and download arrows, integer RTT in milliseconds, and packet-loss percentage. RTT and probe milliseconds round to the nearest integer; zero stays zero and invalid or missing RTT stays an em dash. Full measurement meanings are available through accessibility labels and tooltips. Rows without transport samples omit that line. Probe states remain explicit: Untested, Timed out, or Failed. Names wrap, and controls keep (48dp) targets at larger text sizes. No explanatory subtitle or repeated Outbound/Upload RTT/Download RTT/Packet loss labels appear on this page.

### Inputs / Fields

Filters use panel fill, slate text, secondary hints, a fine outline, (12dp) corners, (8dp 14dp) padding and a minimum (48dp) height. API text fields locally use (10dp) vertical padding. Native cursor, error, selection and IME behavior remain. Only the YAML editor establishes an explicit (2dp) focused periwinkle stroke; other fields keep their native behavior.

Sessions and Proxies filters include a leading search vector and concise hints. Their page headings pair the title with an icon/count; a filtered list shows matched / total, while pending data shows a dash. Full live-data and count semantics remain accessible and available as tooltips. Short viewports place the title/count block beside the filter area; Sessions also retains its native All/TCP/UDP choices beside the text filter. Taller layouts put filters below the title row. Repeated descriptive subtitles and a separate live-summary line are omitted.

### Navigation

All three layouts share quiet-sidebar fill, outlined-ripple feedback, selected periwinkle text/icons, selection-wash background and (8dp) corners. Selected labels are bold. Bottom destinations are equal width; horizontal tabs scroll to the selected destination; expanded rail rows are start-aligned. API tools remains in the header overflow with native Back navigation.

### Transfer and engine measurements

Connection shows one outlined transfer panel with equal Download and Upload columns, in that order. Each column has (12dp) inner-edge padding. Totals use slate tabular figures, with periwinkle/teal direction icons and concise visible labels. Runtime repeats equal TCP/UDP columns with session counts and arrow-led upload/download totals, followed by Tasks, Resident and Allocated icon/value rows. Every measurement remains visible in the scrollable layout; full meanings remain available through accessibility labels and tooltips. The healthy refresh explanation is omitted; loading, partial and failed metric notices remain inline. Proxy/session row pairs put upload left in teal and download right in periwinkle, using arrows and accessible labels. Engine rows use plain key/value alignment and thin dividers. Missing values use an em dash; zero remains an actual measurement.

Session records keep the selectable destination and full protocol/inbound/outbound information. A compact protocol badge uses the selection wash, primary text, (8dp) corners and (8dp 4dp) padding beside the destination; the route vector and inbound/outbound names follow below. Arrow-led transfer totals share a row, with teal upload values and primary download values. Lightning-led first-response values use primary color and stronger weight, while clock-led duration remains secondary on the next row. These concise measurements retain their full names in accessibility labels and tooltips. A half-circle icon and the explicit Half closed label appear only for that state. All measurements remain in each record; no expansion is required. Existing compact identity/measurement columns and stable RecyclerView refresh behavior remain.

### Source workspaces

YAML is a multiline, horizontally scrolling native EditText inside the secure editing panel, with source-role typography, (14dp) padding, native selection, no suggestions, no personalized keyboard learning and no fullscreen/extract editor. Its (1dp) outline changes to (2dp) periwinkle on focus. Profile selection uses a native chevron action; Manage and More retain native dialogs/menus and all profile/template/export operations.

The Edit YAML primary action opens the panel. Its header uses a bold (20sp) title, a two-line ellipsized (12sp) profile identity, Save and a (48dp) vector Close action. Save retains the shared minimum (48dp) height, validates asynchronously, reports success and leaves the panel open. Validation failure preserves the draft and saved profile. Close and native Back retain the draft without saving; tapping outside does not dismiss. Successful import, download and template loading open the panel for review. Both activity and editor windows use FLAG_SECURE; the editor window resizes for the keyboard. Supported activity recreation restores panel visibility, source selection, scroll, focus and keyboard visibility alongside retained per-profile drafts. This does not establish autosave or new process-death draft persistence.

Logs and API results use selectable monospace on outlined panel fill with (14dp) padding. Live logs retain a snapshot while paused or on read failure; refresh follows the tail only when already at the bottom. Filter changes return to the top. Export still includes all retained logs independently of filter/pause. API results keep explicit remote/local scope, target, operation and status; mutations use native confirmation and never replay after recreation. Credential masking and secure capture remain product requirements.

The log panel renders native rich text while retaining its monospace size, wrapping, selection and scrolling. File headings are bold periwinkle, timestamps use secondary slate, and severity labels remain textual and bold: INFO uses green, WARN/WARNING amber, ERROR/FATAL red, and DEBUG/TRACE secondary slate. Service lines without level labels still receive timestamp styling.

ANSI SGR supports foreground/background colors, indexed and RGB colors, bold, italic, underline, strikethrough, dim, inverse and selective/full resets. Basic terminal colors map to the app's theme roles; explicit colors are adjusted where necessary to meet 4.5:1 text contrast against their background. Explicit ANSI colors take precedence over inferred level colors. Terminal control sequences are omitted, while message text and literal markup remain visible. Filters match visible text and preserve styles across retained lines, including multiline ANSI ranges. A change to ANSI styling refreshes the view even if its visible text is unchanged. Copy uses visible text without escape codes; full-log export continues to read the original retained files.

## Do's and Don'ts

### Do:

- **Do** use the matched day/night resource roles across all five destinations, API tools and app-owned dialogs.
- **Do** retain direction labels, tabular numbers and explicit unavailable, empty, error and busy states.
- **Do** use native sp scaling, dp geometry and at least 48dp for action and filter touch targets.
- **Do** keep Connection’s primary action before wrapping detail/profile text in short viewports.
- **Do** preserve long identifiers, filter state, editor drafts, secure-window behavior and live reading position.
- **Do** distinguish local-engine state from remote reachability, saved configuration from drafts and visible log filtering from full retained-log export.

### Don't:

- **Don’t** restore violet chrome, lavender-filled workspaces or capsule actions from the superseded Technical atlas.
- **Don’t** shrink native controls to the reference website’s compact desktop dimensions.
- **Don’t** invent chart data, traffic history, remote connectivity claims or production fixture labels.
- **Don’t** apply the YAML focus border to every native field or replace native errors, selection and keyboard behavior.
- **Don’t** add shadows or web hover motion to ordinary native panels and buttons.
