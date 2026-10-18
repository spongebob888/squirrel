# Squirrel for Android

An Android VPN powered by the local ShadowQUIC Rust core. Android 8.0+; ARM64 phones and x86_64 emulators. No root required.

- Connection, Proxies, Configuration, Logs, and Sessions tabs with swipe navigation.
- Live memory use, TCP/UDP session counts and traffic totals; outbound probe latency, QUIC RTT and packet loss; selector controls and an active session list.
- All five tabs share a light/dark atlas interface, with compact landscape layouts, clear actions, and readable configuration and diagnostic workspaces.
- Import local files or download remote YAML configs, then edit, validate, save, and export them.
- Keep multiple named configurations, switch profiles, or create, duplicate, rename, and delete them in the Configuration tab.
- TCP/UDP forwarding over IPv4 and IPv6, with ShadowQUIC's outbound protocols and Lua routing.
- Persistent service and native core logs, a live log viewer, and export. Each log retains a current file and one rotated file at approximately 1 MiB each.
- The VPN **always excludes its own package** (`dev.squirrel.vpn`) with `VpnService.Builder.addDisallowedApplication()` before establishing the interface. This bypass whitelist covers Rust upstream sockets and the app's DNS/network requests, preventing a routing loop. Failure to add the exclusion fails startup.
- Foreground notification with a disconnect action; cleanup on disconnect, permission revocation, and core exit.

## Build

Use the pinned Nix development environment on **x86_64 Linux**, following the Android SDK composition used by Homesick. `nix/flake.nix` and `nix/flake.lock` supply JDK 21, Rust with both Android targets, Python, native build tools, Android SDK/build tools 36, NDK **28.2.13676358**, and the emulator with a Google APIs x86_64 system image. A separate Android Studio installation, mutable SDK, or `steam-run` is not required. The composition accepts the Android SDK licenses through Nixpkgs configuration.

By default, each native build fetches the latest `main` branch from
[ShadowQUIC on GitHub](https://github.com/spongebob888/shadowquic) into the ignored
`.build/shadowquic-upstream/` cache. Git and network access are required; fetch
failures stop the build instead of silently using an older revision. Preparation
prints the resolved commit.

```sh
nix develop ./nix
./gradlew assembleDebug
```

For a single command without entering an interactive shell:

```sh
nix develop ./nix --command ./gradlew assembleDebug
```

The flake lives in `nix/` so Nix excludes changing build artifacts even in a source archive without Git metadata. Nix pins the development tools; Gradle and Cargo still fetch their locked project dependencies on first use. The shell sets the SDK/NDK/Java paths and overrides Gradle's AAPT2 with the Nix-packaged executable. Update tool inputs deliberately with `(cd nix && nix flake update)`; use `nixfmt nix/flake.nix`, `nix flake check ./nix`, and `shellcheck scripts/*.sh` to check the environment and shell scripts. The Android SDK setup follows the [Nixpkgs Android documentation](https://nixos.org/manual/nixpkgs/unstable/#android).

The installable APK is `app/build/outputs/apk/debug/app-debug.apk`. Gradle builds and packages the actual Rust library for both supported ABIs. To build only one architecture, use `ANDROID_ABIS=arm64-v8a ./gradlew assembleDebug` or `ANDROID_ABIS=x86_64 ./gradlew assembleDebug`. Native ELF segments are aligned for 16 KiB Android pages.

To use a local checkout instead, set `SHADOWQUIC_DIR=/path/to/shadowquic` (for example,
`SHADOWQUIC_DIR=/home/liu/ws/shadowquic nix develop ./nix --command ./gradlew assembleDebug`).
This skips GitHub fetching and leaves that checkout untouched. Unset the variable
to return to GitHub `main`.

Native builds explicitly load `$SHADOWQUIC_DIR/.cargo/config.toml` (or `.build/shadowquic-upstream/.cargo/config.toml` by default). This preserves upstream target settings, including Android ARM64's `-C default-linker-libraries`. The build appends its 16 KiB page-alignment flag through Cargo configuration array merging. Changes to the upstream config take effect on the next build without copying it into Squirrel. See [Cargo configuration](https://doc.rust-lang.org/cargo/reference/config.html).

The Android TUN changes enable Android TUN and fake-IP restoration, and accept an owned `VpnService` descriptor instead of creating a desktop adapter. `scripts/prepare-core.py` copies the checkout into the ignored `.build/shadowquic/` directory for configurable Cargo path dependencies. It recognizes the applied changes and copies them without applying the patch again. For an unpatched checkout based on ShadowQUIC `8ba1f5c07eca6c46c280e8e9a97a60c5c30f73f9` (0.4.4), it can still apply `patches/android-tun.patch` to the build copy; the preparation script never edits its source checkout. The Rust dependency lockfile is checked in. Incompatible core changes fail patching/building explicitly.

## Continuous integration and alpha releases

Every push to `main` runs `.github/workflows/alpha.yml` on GitHub Actions using
`nix develop ./nix`. It fetches ShadowQUIC `main` once, checks Rust formatting,
runs Rust unit tests, and builds/lints the universal ARM64/x86_64 release APK, then signs and verifies it.
Successful runs publish an `alpha-<run-number>-<attempt>` GitHub prerelease with
an APK, SHA-256 checksums, and both source commit IDs. Each push runs independently;
failed checks prevent publication. Device tests remain a local validation step.

The workflow uses the automatic `GITHUB_TOKEN` with `contents: write` for publishing.
Configure these repository Actions secrets before pushing:

- `ANDROID_KEYSTORE_BASE64`: base64-encoded release keystore.
- `ANDROID_KEYSTORE_PASSWORD`: keystore password.
- `ANDROID_KEY_ALIAS`: signing key alias.
- `ANDROID_KEY_PASSWORD`: private key password.

Use an existing app signing key if one has already been published. To generate a
new key, enter `nix develop ./nix` and run the following interactive command
(passwords are prompted, not embedded in the command):

```sh
keytool -genkeypair -keystore "$HOME/squirrel-release.jks" -storetype JKS \
  -alias squirrel -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 "$HOME/squirrel-release.jks" | gh secret set ANDROID_KEYSTORE_BASE64
gh secret set ANDROID_KEYSTORE_PASSWORD
gh secret set ANDROID_KEY_ALIAS --body squirrel
gh secret set ANDROID_KEY_PASSWORD
```

Run the `gh` commands from this repository with GitHub authentication configured.
Back up the keystore and passwords privately; keep the same key for future updates.
Missing secrets stop CI before building. Signing uses a temporary keystore that is
removed on exit, and `apksigner verify` must pass before publication.
The workflow sets version code to its run number and version name to
`0.1.0-alpha.<run-number>.<attempt>`; reruns retain the same version code.

For local signing, export the same four variables, build with
`nix develop ./nix --command ./gradlew assembleRelease lintRelease`, then run
`nix develop ./nix --command bash scripts/sign-release.sh`. The signed APK is
`app/build/outputs/apk/release/app-release.apk`.

Release-signed alphas can update previous alphas signed with the same key.
Switching from a debug build requires uninstalling it once; export configurations
first because uninstalling removes app data.

## Use

Start an emulator, install the existing debug APK, and open the app with:

```sh
./scripts/run-emulator.sh
# Rebuild after source changes, or run without a window:
./scripts/run-emulator.sh --build
./scripts/run-emulator.sh --headless
# Cold-restart the selected emulator without clearing its saved data:
./scripts/run-emulator.sh --restart
# Use a particular AVD:
./scripts/run-emulator.sh squirrel_api36
```

The launcher automatically enters `nix develop ./nix`, creates `squirrel_api36` with the packaged API 36 system image if missing, and builds the debug APK if missing. It reuses the selected AVD, reconnects or restarts an offline instance, waits for Android to boot, installs the app, and opens it. A normal invocation reopens a headless instance with a window. VPN consent remains in Android's normal connection dialog.

Software graphics (`-gpu swiftshader`) are the default; use `SQUIRREL_EMULATOR_GPU=host ./scripts/run-emulator.sh --restart` to try host graphics. GUI launches use Qt's X11 backend when `DISPLAY` is available, including XWayland sessions. Emulator data stays under `~/.android/avd` (or `ANDROID_AVD_HOME`). Separate startup and kernel logs are retained in `.build/emulator-logs/`; `.build/run-emulator.log` links to the latest launch. Use `--help` for further overrides.

If the launcher reports interrupted encryption, Android cannot mount the AVD's data partition. Restarting cannot repair that condition. Stop the emulator, move both its `.ini` file and `.avd` directory into a backup directory outside `~/.android/avd`, then rerun the launcher to create a fresh profile. Keep the backup for recovery; the new profile starts with empty app data. The launcher never automatically wipes an AVD.

1. Install the APK and open Squirrel. Tap a tab or swipe horizontally between **Connection**, **Proxies**, **Configuration**, **Logs**, and **Sessions**.
2. In **Configuration**, choose **Import file**, **Download URL**, or **Proxy template**. Replace the template's server, credentials, and TLS server name as needed.
3. For a remote config, paste its HTTP/HTTPS URL and tap **Download**. The app downloads and validates it before replacing the editor contents. A failed download preserves the editor. Tap **Save**, or go to **Connection** and tap **Connect**, to apply it.
4. **Connect** validates and saves the editor contents, then requests Android VPN consent. Allow notifications to see the disconnect control.
5. Open **Logs** to view live service/core output or export logs. Set `log-level: debug` or `trace` in the configuration for more detail, then reconnect.

**VPN active** means the local packet engine is running. ShadowQUIC opens remote connections when traffic arrives; authentication or remote availability failures appear in the core log.

The **Connection** tab displays jemalloc memory statistics, current TCP/UDP session counts, and cumulative upload/download bytes. **Proxies** lists configured outbounds with cached QUIC RTT and packet-loss samples, plus the latest probe latency. Add a `type: selector` outbound to switch among its configured members; the bundled Proxy template includes a selector for the proxy and direct outbounds. Probe buttons accept an HTTP URL. **Sessions** lists currently alive TCP and UDP flows with their destination and byte totals. These screens update while connected.

Filter **Proxies** by outbound tag. In **Sessions**, filter by destination, address, port, or route, and combine that text filter with **All**, **TCP**, or **UDP**. Filters survive activity recreation, and live list updates preserve scroll position. Missing measurements use a dash; disconnected and unavailable states clear live values rather than retaining figures from an earlier connection.

**Configuration** keeps the profile picker, management, import, download, and Save controls above the YAML editor. **More** contains configuration export, Proxy template, and Direct test. Short keyboard viewports collapse the redundant heading; landscape places the controls beside the editor. **Logs** supports a text filter and a **Live updates** switch: turn it off to read a fixed snapshot. Filtering and pausing affect only the displayed tail; **Export logs** still exports all retained service and core log files. New lines follow the tail only when the viewer was already at the bottom.

### API tools

Open **More options** in the app header, then **API tools**. This standalone page keeps infrequent administration outside the five tabs. Back returns to the previous tab and its draft.

**Remote server** uses a snapshot of a saved profile; choose its ShadowQUIC or SunnyQUIC outbound. The configured login must begin with `admin`. Unsaved editor changes are not used. **Reload** reads saved changes. These are the real remote control-plane commands, independent of the VPN and the local REST API:

| Operation | Scope and effect |
| --- | --- |
| `list-users` | List configured remote usernames. |
| `add-user` | Add a username/password, or replace an existing user's password. |
| `remove-user` | Delete a user and close their active connections. |
| `kill-conn` | Close a user's QUIC connections; they can reconnect. |
| `get-stats` | Traffic bytes and active QUIC/TCP/UDP counts for one user or **All users**. |
| `clear-stats` | Reset traffic byte counters for one user or **All users**, keeping live connection counts. Requires a compatible server version. |

Mutations ask for confirmation with the saved profile, outbound and user scope. A timed-out request may already have changed the server; inspect its state before retrying. Passwords are masked, cleared after submission/backgrounding, and excluded from saved form state and result text. Running requests survive rotation without replay. The page retains Android's secure-window protection.

Usernames and outbound/member tags are exact keys; surrounding spaces are preserved. Fields preview whitespace as JSON escapes, such as `"\u0020alice\u0020"`, and confirmations/results use quoted identifiers. An empty username never implicitly selects all users in the form: use **All users** explicitly.

Server connections use the outbound's transport, authentication, TLS and socket settings. A configured `addr-resolver` uses its DNS backend directly through a temporary loopback listener; it does not start the profile's listeners, router or VPN. Fake-IP address resolvers are rejected. Referenced certificates must already be readable on the Android device, as with the VPN profile.

**Local runtime** requires the VPN engine to be running. It exposes every bundled REST operation: outbounds, connections, traffic, memory, selector get/set and outbound HTTP probing. Results are JSON snapshots. Selector changes require confirmation and apply to new sessions; restarting restores the configured selection. Probe timeouts accept 1–60000 ms, and the client waits for that interval. The existing Proxies tab remains the everyday selection/probe interface.

The built-in **Direct test** profile exercises the packet engine without a remote server. It forwards directly and does not provide an encrypted remote tunnel. ICMP/ping is not supported by the core's TCP/UDP stack.

## Configuration

See [`examples/shadowquic.yaml`](examples/shadowquic.yaml) and [`examples/direct.yaml`](examples/direct.yaml). Standard ShadowQUIC configs are accepted. If a config has no TUN inbound, Squirrel adds one tagged `squirrel-tun` and preserves the other listeners. If you provide a TUN inbound, its addresses, MTU, session limits, and routing preference are used. Exactly one TUN is supported. The desktop TUN `name` is ignored on Android.

Use the profile picker at the top of **Configuration** to switch saved configs. **Manage** offers **New configuration** (proxy template), **Duplicate current** (including editor changes), **Rename**, and **Delete**. Imports, downloads, Save, and Export apply to the selected profile. The selection persists across app restarts and supplies the next VPN connection; switching while connected takes effect after reconnecting. Unsaved drafts and URL fields stay separate for each profile while the activity is open and survive rotation. Save drafts before closing the app. At least one profile is kept.

On upgrade, the previous `config.yaml` is copied unchanged into the **Default** profile and retained as a migration backup. Profiles live in private `configs/<id>.yaml` files with an atomic `profiles.json` index; profile names never become file paths.

The optional app-specific section controls the DNS addresses advertised to Android:

```yaml
android:
  dns-servers: [1.1.1.1, 1.0.0.1]
```

Squirrel replaces any `restful-api` entry in the runtime-only config with a private listener at `127.0.0.1:19090` for the live screens. It generates and stores a random bearer token in private app preferences. Exported and saved YAML is unchanged.

Both default routes (`0.0.0.0/0`, `::/0`) enter the VPN. DNS packets to these addresses follow the configured outbound/router, just like other UDP/TCP traffic. Defaults are the two addresses above. For local fake-IP DNS, use the core's inline Lua routing to send port 53 traffic to the configured DNS service. Imported `protect-path` values are cleared because UID bypass handles socket protection here.

Local and remote imports must be UTF-8 YAML, at most 1 MiB. URL downloads support up to five redirects, with 10-second connection and 15-second read timeouts. HTTPS uses normal certificate verification; redirects from HTTPS to HTTP are rejected. Downloaded configs enter the editor for review and are saved only by Save or Connect. Unsaved editor text and the selected tab survive activity recreation. Configs are copied into private app storage; future edits to the original file require reimporting. Saving while connected applies on the next connection. Use inline `router.src` for portable imports: the file picker imports only the YAML, not referenced Lua scripts, certificates, or databases. Lua scripts and certificate references must point to readable absolute paths on the Android device. App data backup is disabled. Exported configs contain the credentials you entered; verbose logs can include destination addresses.

When loading the VPN configuration, Squirrel resolves relative `router.database[].path` values under its private files directory: `data/geosite.redb` becomes `<app files>/data/geosite.redb`. Downloaded databases persist there across reconnects. Explicit absolute paths are preserved; relative paths that escape the private directory are rejected. The editor, saved YAML, and exports keep the original portable paths and comments.

Always-on/lockdown VPN is disabled for this version. Android's “Block connections without VPN” policy prevents bypassed apps from using the underlying network and is incompatible with this UID exclusion strategy. Wi-Fi/mobile handovers rely on the core's reconnection behavior; disconnect/reconnect if a session does not recover.

## Verification

```sh
nix develop ./nix
python3 scripts/prepare-core.py
cargo test --manifest-path native/Cargo.toml --locked
./gradlew assembleDebug assembleDebugAndroidTest lintDebug
# With an emulator or phone connected:
./gradlew connectedDebugAndroidTest
nix flake check ./nix
shellcheck scripts/*.sh
python3 scripts/test-run-emulator.py -v
```

Host tests cover config normalization, private database paths, legacy imports, routing validation, DNS validation, bundled examples, log rotation, and logger filtering. Instrumentation tests load the real JNI library, send a raw UDP packet through a simulated TUN to a local echo server, verify its reply, stop/restart the runtime, check Java descriptor ownership, and reject invalid imports. Database tests load a saved config with relative paths, download Geosite data from a local HTTP server into private storage, reopen it after restart with the source offline, and verify that saved YAML and DNS settings are preserved. UI and download tests cover swipe/tab navigation, draft retention across recreation, redirects, HTTP failures, UTF-8 and size validation, and replacing the editor only after config validation. Profile tests cover legacy migration, separate YAML files, durable selection, management dialogs, per-profile drafts, and rejected operations. The tests do not require VPN consent.

Diagnostic tests cover metric aggregation and clearing, unknown measurements, combined session filters, filter restoration, stable outbound rows and scroll position, and rejection of results from another tab or a previous connection. Review captures use explicitly labeled synthetic data in the real native views. Only the instrumentation harness temporarily permits screenshots of those fixture screens; the shipped activity retains `FLAG_SECURE`.

Workspace tests cover log filtering, paused snapshots, read-error recovery, filter focus during updates, editor space with the software keyboard open, and secondary configuration actions preserving the draft. Their configuration, log, menu, and dialog captures contain synthetic fixtures; no review operation saves that fixture over a stored profile.

API tools tests cover standalone navigation, exact command/scope dispatch, confirmation cancellation, password redaction, invalid-profile recovery, and a pending mutation surviving recreation without replay. Real JNI/loopback REST checks exercise every local endpoint, including a probe lasting longer than the ordinary three-second read timeout. Native host tests run all six remote commands against loopback ShadowQUIC and certificate-verified SunnyQUIC servers; these are local protocol checks, not verification of a deployed server.

Validated locally: both Android ABIs built; Android lint passed; all 11 host tests, 15 API 36 emulator instrumentation tests, and 8 core TUN tests passed (the desktop kernel-adapter test was skipped). A real Android VPN service session also forwarded a TCP HTTP request from the shell UID through the TUN; Android reported the app UID excluded from the VPN ranges. A remote ShadowQUIC server was not supplied, so remote authentication and transport were not tested.

For the proxy, memory, traffic, and session screens, the current revision builds both Android ABIs and passes Android lint. Live API behavior against a remote ShadowQUIC server has not been exercised in this environment.

The Nix environment was also used to rebuild both ABIs, run lint and all 15 API 36 instrumentation tests, and verify fresh AVD creation, visible app launch, emulator persistence after the launcher exits, reuse, and a cold restart retaining a file in app-private storage. Launcher regression tests use an isolated fake SDK to check AVD directory matching, offline reconnection/restart, reopening a headless instance, and interrupted-encryption diagnostics without touching real AVDs. Flake formatting and ShellCheck pass.

Android VPN integration follows the [Android VPN guide](https://developer.android.com/develop/connectivity/vpn).
