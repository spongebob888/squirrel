---
target: app/src/main/java/dev/squirrel/vpn/SettingsActivity.java
mode: Operate
---

# Settings

Set the app-wide default HTTP latency-probe URL and clear private databases and
dashboard HTML from the top-right More menu.
This standalone page inherits the Squirrelboard native dashboard in `DESIGN.md`;
it leaves primary navigation intact.

## Storage cleanup

An outlined destructive action under Storage confirms removal of private routing
databases for all saved profiles, orphan database files and dashboard HTML.
Configurations, logs and preferences remain. External paths and symbolic links
are skipped. The action is disabled while the VPN is connecting, connected or
stopping; the native session lock also prevents deletion while the core runs.
One background operation spans activity recreation without repeating removal,
retaining the draft probe URL and reporting progress, removed file count, empty
results or retryable errors through a live status. The confirmation has secure
window protection and Cancel preserves all files.

## Final behavior

Save trims and validates an absolute HTTP URL with a valid host and port and no
embedded credentials. The core currently supports HTTP probes only. The initial
default is `http://example.com/`; private app-wide preferences store changes
separately from VPN profiles. Back discards unsaved changes, while draft text
survives activity recreation. Save reports “Saved. New probes use this URL.”;
editing clears that feedback and the prior field error. Invalid input uses
native field error feedback. Keyboard Done invokes the same save operation.
Production secure-window protection remains enabled.

Probe latency starts immediately with the latest saved default. Pending state
guards duplicate submission for the same outbound and engine session; the probe
interval is 10 seconds and the REST read timeout is 13 seconds. API tools prefills
the saved default and preserves its existing per-request override and draft.

## Local visual application

The panel-filled Back/title header remains above one scrolling form. It reuses
the existing 23sp bold standalone title, 48dp Back target, native vector icon and
day/night resources. Body padding is 22dp horizontally and 24dp vertically. The
form uses the existing 16sp bold section, 14sp body/input, 13sp label/status and
12sp help roles. The URL field has a 48dp minimum height, 14dp horizontal / 10dp
vertical padding, 12dp corners and a fine 1dp outline. Save uses the canonical
flat periwinkle action, 13sp label, 48dp minimum height, 10dp vertical / 12dp
horizontal padding, 8dp corners and native ripple feedback.

The single column continues across phone and tablet widths. Native resizing and
scrolling keep the form reachable above the real software keyboard. Field error,
cursor, selection and keyboard behavior remain native; the YAML editor's custom
focus stroke does not apply here. No new palette, font, component family, motion
scale or shipping raster is introduced. The Storage section uses the same
recorded tokens; `DESIGN.md` documents its action and state behavior.

## Evidence and limits

The [Settings evidence packet](../review/settings/packet.json) records 17 native
captures: default, error and saved states in phone light/dark, enlarged text,
landscape and tablet layouts, plus real-keyboard phone and tablet captures.
Synthetic URLs are fixtures rather than remote-connectivity claims. The native
surface does not use a browser detector.

The independent [finish review](../review/settings/finish-review.md) inspected all
17 captures, confirmed their claimed states and native dimensions, found no
material fix, and assigned disposition **ship**. It confirms the inherited
type/material/ground, navigation, validation, feedback and keyboard behavior.

Documentation comparison covered SettingsActivity, AppSettings, MainActivity's
overflow and immediate probe, API tools' prefill/validation, the manifest and
day/night resources against `DESIGN.md` and its sidecar. Phone default, dark error
and tablet keyboard captures were reopened for this pass. The packet reports
both-ABI build/lint and 19 UI instrumentation tests passing; this documentation
pass did not rerun them. Emulator and JNI loopback results do not establish
physical-device or deployed-server behavior.

## Storage review

The cleanup extension passes 24 Rust tests, Rust formatting, both-ABI Android
build/lint and eight focused device tests on the API 36 x86_64 emulator. Coverage
includes all-profile/custom-path cleanup, orphan files, external and symlink
exclusions, protected workspace files, a running native session, Cancel, busy
states, duplicate work, recreation, empty results and retryable failures.
Destructive device tests use isolated fixture directories. Native captures in
`.build/storage-review/` were inspected in light/dark appearance, at font scale
1.3, in a short landscape viewport and on tablet portrait/landscape viewports.
Disposition: ship. Actual VPN lifecycle and downloaded server content are not
replaced by these fixture-based cleanup checks.
