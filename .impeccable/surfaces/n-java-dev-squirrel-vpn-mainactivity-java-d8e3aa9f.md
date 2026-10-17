---
version: 1
slug: "n-java-dev-squirrel-vpn-mainactivity-java-d8e3aa9f"
primary_target: "app/src/main/java/dev/squirrel/vpn/MainActivity.java"
related_targets: ["app/src/main/java/dev/squirrel/vpn/DiagnosticsUi.java"]
---

# Diagnostics redesign

Scope: Connection, Proxies, Sessions; shared navigation adapts to the active screen.
Visitor mode: Operate.
Audience and priority: technical ShadowQUIC users; detailed controls and diagnostics.
Task: understand local connection state, inspect traffic and memory, choose/probe an outbound, and inspect live flows without losing place during refresh.
Constraints: preserve VPN lifecycle, app bypass, secure-window protection, profile/editor behavior, all existing metric fields, and system Back. API unavailable is distinct from zero, and disconnected views never show stale live figures.

## Direction contract

THESIS: Technical atlas. A readable Android diagnostic workspace replaces the oversized green connection card and unstructured session dump with keyed, aligned facts.

OWN-WORLD: Violet app chrome, pale lavender supporting surfaces, near-white work areas, dark violet text, teal download/success accents, standard Android controls and system sans with tabular measurement figures. Dark mode uses corresponding violet roles. Paired upload/download columns and their repeated legend are the signature.

STORY: Identify the engine state, connect or disconnect, read transfer totals and engine counts, then inspect or act on the corresponding outbound and session. Distinguish local runtime health from remote success.

FIRST VIEWPORT: Match the chosen atlas sketch: full-width violet app bar, familiar horizontal tabs, a left-aligned two-line connection heading, state/detail and primary action, followed by paired upload/download totals and aligned engine rows. Proxies and Sessions reuse this hierarchy with scoped filters and stable native lists. Purely ornamental numbering/eyebrows are omitted.

FORM: Scientific reference atlas, grounded candidate 1; user chose the pick over assigned candidate 5. seed:a6a4c002e29a9a84. Approved sketch: .impeccable/mocks/decision/atlas.html. Code-first; no approved raster comp.

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance

Unresolved: no additional product-level tablet, accessibility, or localization requirements were set. This pass verifies phone portrait/landscape, dark mode, and enlarged system text. No remote server is supplied.
