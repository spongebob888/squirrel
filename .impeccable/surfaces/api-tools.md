---
target: app/src/main/java/dev/squirrel/vpn/ApiToolsActivity.java
mode: Operate
---

# API tools

The user requests all ShadowQUIC APIs, specifically list-users, add-user,
remove-user, kill-conn, get-stats and clear-stats, on a standalone, infrequently
used page outside the main tabs. It inherits the user-pinned squirrelboard dashboard, replacing the earlier Technical atlas materials.

## Direction contract

THESIS: Infrequent administration belongs in an explicit target, operation and
result workspace reached through the overflow menu, preserving everyday tabs.

OWN-WORLD: Reuse native system sans, cool gray canvas, white/charcoal outlined
fields, periwinkle actions and day/night resource roles. Actions have 8dp corners;
fields/results have 12dp corners. Protocol/JSON output remains selectable monospace.

STORY: Choose remote server or local runtime. Remote operations use a saved
profile's supported outbound and admin credentials. Select scope, inspect the
effect, confirm mutations, read a labeled snapshot; never replay a command.

FIRST VIEWPORT: Back and API tools title, two native scope choices, target
controls, operation and conditional fields, one primary action, status, then
result. Wide phones put the result beside the form; the entire body scrolls.

FORM: Precise native adaptation of user-pinned ../squirrelboard. The earlier atlas
seed is superseded. Exact palette/type/component tokens live in DESIGN.md and its
schemaVersion 2 sidecar; no shipping raster asset is required.

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance

## Verification

Remote operations are protocol UserManager calls, not invented REST routes.
Keep runtime/descriptor ownership untouched. Local REST calls target the running
app-private authenticated endpoint. No password in saved state, logs, results or
fixtures outside explicit synthetic tests. Validate native light/dark, enlarged
text, portrait/landscape, error/empty/busy and confirmation states on an emulator.

## Final implementation

The header's native overflow opens the non-exported API tools Activity; the five
main tabs remain unchanged, and Back returns to the previous tab and draft.
Remote server and Local runtime are explicit, separate scopes. Remote commands
read a saved-profile snapshot, never the unsaved editor; Reload refreshes that
snapshot. Compatible targets are ShadowQUIC/SunnyQUIC outbounds whose configured
login starts with `admin`. These protocol UserManager calls do not start a TUN or
use the local REST API. The configured address-resolver backend uses a temporary
loopback listener and direct requests; fake-IP address resolution is rejected.

All six remote commands ship: `list-users`; `add-user` (add or replace password);
`remove-user` (delete and close connections); `kill-conn` (close connections while
preserving the account); `get-stats`; and `clear-stats` (reset byte counters while
preserving live counts). Statistics and resets support one user or explicitly
checked **All users**. An empty single-user field fails validation rather than
implicitly selecting everyone. Mutation dialogs state the saved profile,
outbound, user scope and effect. Stats identify sent as server-to-client and
received as client-to-server.

Usernames and outbound/member tags remain exact keys through confirmation,
dispatch and results; surrounding spaces are not trimmed. Whitespace-bearing
fields expose an exact-identifier preview such as `"\u0020alice\u0020"`.
Confirmations, outbound labels, user lists and statistics quote identities with
JSON spelling and visible whitespace escapes. This preserves the distinction
between `alice` and ` alice `, including local selector keys and members.

Local runtime requires the connected engine and uses its private authenticated
`/api/v1` endpoint. Every bundled REST operation is exposed:

| Method | Path | Scope |
| --- | --- | --- |
| GET | `/outbounds` | Configured runtime outbounds |
| GET | `/connections` | Active flows |
| GET | `/traffic` | Traffic snapshot |
| GET | `/memory` | Memory snapshot |
| GET | `/outbounds/{exact-tag}/selection` | Current selector state |
| PUT | `/outbounds/{exact-tag}/selection` | Confirmed exact member for new sessions |
| POST | `/outbounds/{exact-tag}/probe` | HTTP-only probe, timeout 1–60000 ms |

Tag path segments are URI-encoded without normalizing the key. Probe URLs reject
embedded credentials; read timeout includes the requested probe interval plus
three seconds. Local results are discarded if the engine session changes.

An application-context worker retains in-flight work across rotation without
replaying commands; pending controls disable duplicate submission. Results retain
request identity when another operation is selected. After process recreation,
an interrupted request is reported instead of rerun; a timeout warns that a
remote mutation may already have applied. Passwords are masked, excluded from
saved form/view state and autofill, and request no personalized IME learning.
They clear on submission, backgrounding and confirmation cancellation/dismissal;
pending credential confirmation closes on pause. Production secure-window
protection remains enabled, native errors avoid exposing configuration values,
and submitted passwords are redacted from surfaced errors.

## Local visual application

The existing day/night palette, system sans hierarchy, flat ripple actions,
48dp minimum controls, vector navigation/chevrons and 12dp field / 8dp action
radii are reused. The selectable protocol/JSON result uses the existing native
12sp monospace role with 3dp added line spacing, 14dp padding and a 1dp outline.
This extends the source-data use of monospace locally; it does not establish a
new display face or global token.

Page-specific differences remain local: the standalone Back/title header uses
panel fill, with canvas navigation bars, matching the main dashboard materials;
title is 23sp, result heading 18sp, form text 14sp and support text 13sp. Actions
use 14dp horizontal padding rather than the canonical 12dp action padding;
fields use 10dp vertical padding rather than filters' 8dp. Operation choices are
native Spinners, with native radio/checkbox scope controls. Inputs retain native
cursor/error/IME behavior without borrowing the YAML editor's focus stroke.

At `screenWidthDp >= 600`, form and result share a 1:1.2 row with a 24dp form-end
gap, inside one scrolling body. Below it they stack. The header remains outside
the ScrollView; the wide columns are not independently scrolling or sticky.
Native resize/scroll keeps the action reachable above the real keyboard.
No new shipping raster asset, palette, identity or motion scale is introduced.
`DESIGN.md` and `.impeccable/design.json` now record the complete dashboard system.
API-local padding and column rules remain local rather than becoming global defaults.

## Prior API implementation evidence

The following evidence belongs to the earlier API functionality pass. It remains
a record of that pass; current dashboard finish review lives under
`.impeccable/review/squirrelboard/` and must be assessed independently.

The independent [full finish review](../review/api/finish-review.md) identified
one exact-target defect. The [fixes verdict](../review/api/verdict.md) scores that
finding resolved with disposition **ship** and no remaining finding; its scope
is the scored correction, while the original full review remains preserved.
The [evidence packet](../review/api/packet.json) records all 41 captures, including
39 recaptures and the two exact-identifier originals. Review evidence covers API
36 portrait light/dark, font scale 1.3, landscape and the actual software IME,
plus populated, empty, busy, error, confirmation and disconnected states.

Recorded validation: both Android ABIs build and lint pass; all **31 emulator
instrumentation tests** and **15 native host tests** pass after the correction,
including exact whitespace identities, real JNI selector keys, configured DNS,
loopback ShadowQUIC/SunnyQUIC protocol commands, local REST coverage and retained
worker/no-replay behavior. Summaries were checked in
`/tmp/squirrel-api-all-device-tests.log` and
`/tmp/squirrel-api-native-tests.log`; this documentation pass did not rerun them.
Source comparison covered ApiToolsActivity, ApiToolsModel, MainActivity overflow,
ShadowQuicApi, native admin dispatch and the manifest against the incumbent
system. Original identifier target/confirmation, keyboard, landscape stats-result
and dark local-probe captures were reopened for this documentation pass.

All fixtures are synthetic. Emulator, loopback protocol/REST and certificate
checks establish neither deployed-server compatibility/connectivity nor
physical-device, tablet or hardware gesture/performance verification. Preserve
those limits when reporting this extension.
