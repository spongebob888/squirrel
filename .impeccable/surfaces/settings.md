---
target: app/src/main/java/dev/squirrel/vpn/SettingsActivity.java
mode: Operate
---

# Settings

Set the app-wide default HTTP latency-probe URL from the top-right More menu.
This standalone page inherits the Squirrelboard native dashboard in `DESIGN.md`;
it leaves the five main destinations intact.

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
scale or shipping raster is introduced. `DESIGN.md` and `.impeccable/design.json`
are preserved unchanged; this is an ordinary extension of that system.

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
