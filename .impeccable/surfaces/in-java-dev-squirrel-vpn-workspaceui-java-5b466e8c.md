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

FIRST VIEWPORT: Configuration places its title above a profile row and a compact import/download/save toolbar, followed by the status and a large YAML pane with secondary tools. Landscape puts controls beside the editor; a short IME viewport hides the redundant heading. Logs places title, scoped filter/export controls, live switch and status above a large selectable monospace viewer. All destinations share the panel-colored brand header and adaptive bottom navigation, horizontal tabs or expanded rail.

FORM: Native adaptation of user-pinned ../squirrelboard, superseding the earlier atlas seed. Exact tokens and responsive thresholds are recorded in DESIGN.md and its schemaVersion 2 sidecar.

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance

Verification: full instrumentation suite, native light/dark/enlarged-text/landscape captures, profile/download regression checks and keyboard editing. Review data is synthetic; production FLAG_SECURE remains enabled.
