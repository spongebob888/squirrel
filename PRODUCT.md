# Squirrel

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users

Technical users who manage their own ShadowQUIC configurations, confirmed by the
user during initialization. They work with YAML profiles and need direct access
to connection controls and network diagnostics on Android.

## Product Purpose

Squirrel runs the ShadowQUIC Rust core as an Android VPN. Users import or edit a
configuration, connect with Android's VPN consent, and inspect traffic, proxies,
sessions, and logs. Successful use means applying the intended configuration and
understanding the connection's actual state and any failures.

The user-confirmed priority is detailed control and diagnostics for ShadowQUIC
users. Future product decisions should favor visibility and precise control for
this audience.

## Positioning

An Android client for users who bring their own ShadowQUIC configuration and
server details. Its existing mechanism combines a native VPN service, editable
YAML profiles, and visibility into the running core. No comparative performance,
security, anonymity, or reliability claims have been established.

## Operating Context

- Native Android app, Android 8.0 and later; ARM64 phones and x86_64 emulators.
  No root is required.
- Five existing destinations: Connection, Proxies, Configuration, Logs, and
  Sessions, with tab and swipe navigation.
- Infrequent API administration lives on a standalone API tools page reached
  through the header overflow menu, rather than another main tab.
- The same menu opens Settings for the app-wide default HTTP latency-probe URL.
  Probe latency starts immediately with the latest saved URL; API tools prefills
  it while retaining a per-request override. Settings validates and saves the URL
  independently of VPN profiles; its unsaved draft survives activity recreation.
- Users import a local file, download an HTTP/HTTPS YAML configuration, or start
  from a bundled template. Server details and credentials come from the user.
- Users validate and save a profile, connect, inspect runtime information, and
  disconnect or reconnect when configuration or network conditions change.
- A foreground notification exposes a disconnect action while the VPN runs.

## Capabilities and Constraints

- Multiple named profiles support creation, duplication, renaming, deletion,
  selection, import, editing, validation, saving, and export. At least one profile
  remains. Saved selection persists across restarts; unsaved per-profile drafts
  survive activity recreation but are not durable storage.
- Save or Connect applies editor contents to the saved profile. Changes and
  profile switches while connected take effect on the next connection.
- Configuration imports are UTF-8 YAML up to 1 MiB. A file import does not bundle
  referenced scripts, certificates, or databases. Failed remote downloads retain
  the editor contents; successful downloads enter the editor for review.
- IPv4 and IPv6 TCP/UDP forwarding uses ShadowQUIC outbounds and Lua routing.
  Exactly one TUN interface is supported. ICMP/ping is unsupported.
- Connection information includes memory, traffic totals, and TCP/UDP session
  counts. Proxy tools expose outbound selection, HTTP probe latency, QUIC RTT,
  and packet loss. Sessions and persistent, exportable logs aid diagnosis. The
  log viewer supports text filtering and pausing its live display; exports
  include all retained logs independently of those display controls.
- "VPN active" indicates that the local packet engine is running. It does not
  establish successful remote authentication or server reachability.
- API tools uses a saved profile's ShadowQUIC/SunnyQUIC admin outbound for remote
  user listing, addition/password updates, removal, connection termination,
  statistics and counter resets. Single-user and all-user statistics scopes are
  explicit. These operations do not require starting the VPN. Mutations confirm
  the target and are not automatically replayed after activity recreation.
- The tools page also exposes all bundled local REST operations while connected.
  Remote control-plane operations and local engine snapshots have separate scope.
  Submitted passwords are not persisted in form state or shown in results.
- The Direct test profile forwards traffic directly; it does not provide an
  encrypted remote tunnel.
- The app package must bypass the VPN before the interface is established.
  Descriptor ownership and cleanup on disconnect, permission revocation, and core
  exit must remain correct. Blocking I/O and native lifecycle work stay off the
  UI thread.
- Always-on and lockdown VPN are disabled because they conflict with the app's
  package-bypass strategy. Network handovers may require reconnecting.
- Configurations live in private app storage and app backup is disabled. The
  current activity prevents screenshots and recents captures because profiles
  may contain credentials. Exports include entered credentials, and verbose
  logs may reveal destinations; future copy must describe these facts accurately.

## Brand Commitments

The existing product name is Squirrel, with ShadowQUIC identified as its core.
The repository contains a squirrel icon at
`app/src/main/res/drawable/ic_squirrel.xml`. No additional binding voice,
aesthetic, or branding requirements were confirmed during initialization.

## Evidence on Hand

- `README.md`: current workflows, supported features, limitations, and previously
  reported verification. Those reports are not new test results from this init.
- `app/src/main/java/dev/squirrel/vpn/`: the implemented Android interface,
  configuration storage, downloads, VPN service, and native bridge.
- `app/src/main/AndroidManifest.xml` and `app/build.gradle.kts`: platform,
  permissions, supported API levels, and packaged architectures.
- `examples/` and `app/src/main/assets/`: configuration examples and bundled
  templates, which must remain synchronized.
- `app/src/androidTest/` and tests beside `native/src/` implementations:
  configuration, packet, storage, and lifecycle regression coverage.
- Remote-server authentication and transport success are not established by
  local packet tests or by initialization. Credentials, server claims,
  benchmarks, testimonials, and security assurances must not be invented.

## Product Principles

- Preserve direct configuration control for the confirmed technical audience.
- Prioritize detailed ShadowQUIC controls and diagnostics in product tradeoffs.
- Make connection state and diagnostic information precise enough to act on.
- Preserve users' configuration and drafts through supported operations, and
  distinguish editing, saving, and applying changes.
- Preserve VPN lifecycle correctness and credential-handling safeguards as the
  interface evolves.

## Open Decisions

- No additional accessibility, localization, or tablet-specific product
  requirements have been confirmed.
