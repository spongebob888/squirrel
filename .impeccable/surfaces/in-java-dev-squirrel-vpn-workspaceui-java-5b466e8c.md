---
version: 1
slug: "in-java-dev-squirrel-vpn-workspaceui-java-5b466e8c"
primary_target: "app/src/main/java/dev/squirrel/vpn/WorkspaceUi.java"
related_targets: ["app/src/main/java/dev/squirrel/vpn/MainActivity.java"]
---

# Configuration and Logs dashboard workspaces

Scope: apply the user-pinned ../squirrelboard dashboard to Configuration and Logs, including shared chrome and native dialogs. This replaces the earlier Technical atlas direction while preserving every existing operation.
Visitor mode: Operate. Audience, diagnostics priority, code-first and visual system are already confirmed. No new identity or open surface direction is needed.
Task: select/manage a profile, import/download/edit/save/export YAML, and inspect/export retained logs.
Constraints: preserve private storage, per-profile drafts, validation and download behavior, all profile operations, full-log exports, background I/O, secure-window protection, VPN bypass and lifecycle.

## Direction contract

THESIS: Carry squirrelboard’s precise outlined dashboard materials into the native YAML and log workspaces.

OWN-WORLD: Cool gray canvas, white/charcoal outlined panes, slate ink, periwinkle primary actions and selection. System sans controls, native monospace source text, 8dp actions and 12dp panes follow DESIGN.md. The reference squirrel remains a native vector; no shipping raster is added.

STORY: Choose a profile, bring in configuration, edit and save it without losing the draft. Inspect a readable log tail, filter or pause its display, and export all retained logs independently of the view filter.

FIRST VIEWPORT: Configuration places its title above a profile row, then a single Edit YAML/Save/More row, status and paired Import file/Download URL actions. The primary editing entry is directly below the profile, without a surrounding panel or repeated YAML format label. Edit YAML opens a secure full-height editor window on phones and short landscape displays, or a centered editing panel on tablets. The editor has Save and Close actions; closing retains the draft. Logs places title, scoped filter/export controls, live switch and status above a large selectable monospace viewer. All destinations share the existing header and adaptive navigation.

FORM: Native adaptation of user-pinned ../squirrelboard, superseding the earlier atlas seed. Exact tokens and responsive thresholds are recorded in DESIGN.md and its schemaVersion 2 sidecar.

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance

Verification: full instrumentation suite, native light/dark/enlarged-text/landscape captures, profile/download regression checks and keyboard editing. Review data is synthetic; production FLAG_SECURE remains enabled.

## YAML editing panel

The user explicitly requested a popup panel for YAML editing. This is protected
editing, with the existing native visual world and every configuration action
preserved. There is one editor instance per workspace; its draft remains when
the panel closes. Save validates asynchronously and keeps the panel open;
validation failure preserves both the entered draft and the saved profile.
Activity recreation restores the open panel, draft, selection, scroll, focus
and keyboard visibility. Drafts remain temporary; there is no new autosave or
process-death persistence.
Successful import, download and template loading open the panel for review.
The panel fills the window below 600dp width or 480dp height. Taller tablets
use min(900dp, screenWidthDp − 48dp) width and 24dp vertical margins inside a
full-height resizing window; the controls page centers at max720dp width.
Save and Close retain at least 48dp touch targets. The panel has its own
secure-window flag, resizes for the IME and leaves system
Back behavior native. Short editing viewports omit profile/status context to
preserve space for the source, Save and Close. Profile and source operations
remain on the scrollable Configuration page, without an embedded editor.

## Rich log text

Log content uses native text spans: bold periwinkle file headings, quiet timestamps,
and bold severity labels colored green/amber/red or secondary slate. ANSI SGR color,
background, indexed/RGB color, bold, italic, underline, strikethrough, dim, inverse
and resets render in the same selectable monospace pane. Theme roles and contrast
adjustment keep colored text readable in light/dark appearance. Filtering matches
visible text and preserves multiline styles; style-only updates also refresh.
Pause, read-error snapshots, tail-following, filter focus and original-file exports
retain their existing behavior. Verification for this refinement covers focused
formatter/workspace tests and native phone/tablet appearance captures.

## Distill refinement

Keep all operations directly available. Group Edit YAML, Save and More immediately
after profile selection, with Edit YAML as the sole filled action. Remove the
extra YAML action container and format caption; keep the status live region and
place the equal-width source actions beneath it. No action moves into a new menu,
and the secure editor, drafts, busy-state disabling and diagnostics are unchanged.
