# Repository Guidelines

## Project Structure & Module Organization

- `app/src/main/java/dev/squirrel/vpn/`: Java UI, VPN service, configuration storage, logging, and JNI declarations.
- `app/src/main/res/` and `app/src/main/assets/`: Android resources and bundled YAML templates.
- `app/src/androidTest/`: device instrumentation tests for JNI, packet forwarding, restart, and import validation.
- `native/src/`: Rust configuration normalization, rotating logs, and runtime/JNI lifecycle; unit tests live beside implementations.
- `examples/`: user configuration examples. Keep corresponding bundled assets synchronized.
- `scripts/`: resolve and build the pinned ShadowQUIC Git dependency from unchanged upstream sources. `.build/`, `native/target/`, and Gradle build directories are generated.

## Build, Test, and Development Commands

Use JDK 17/21, Rust stable with edition 2024 support, Python 3, Android SDK 36, and NDK `28.2.13676358`. Set `JAVA_HOME` and `ANDROID_HOME`. The native crate imports ShadowQUIC directly from GitHub tag `v0.4.6`; `native/Cargo.lock` pins its exact commit. Cargo manages the dependency checkout.

Run from the repository root:

```sh
python3 scripts/prepare-core.py
cargo test --manifest-path native/Cargo.toml --locked
cargo fmt --manifest-path native/Cargo.toml -- --check
./gradlew assembleDebug lintDebug
./gradlew connectedDebugAndroidTest
./gradlew installDebug
```

These prepare the upstream core, test Rust, check formatting, build/lint Android, run device tests, and install locally. Device commands require an emulator or phone. The APK is `app/build/outputs/apk/debug/app-debug.apk`. Set `ANDROID_ABIS=arm64-v8a` or `x86_64` to limit builds.

## Coding Style & Naming Conventions

Use four-space indentation in Java and Rust. Java classes use `PascalCase`; methods and fields use `camelCase`; constants use `UPPER_SNAKE_CASE`. Rust uses `snake_case` functions/modules and `PascalCase` types. Apply `cargo fmt` and Android lint. Keep blocking I/O and native lifecycle work off the UI thread.

## Testing Guidelines

Rust uses `#[test]`; Android uses `InstrumentationTestCase` and `InstrumentationTestRunner`. Give Rust tests descriptive behavior names and Java test methods a `test` prefix. Add regression coverage for configuration, packet handling, or lifecycle changes. No numeric coverage threshold is configured. Distinguish local packet tests from remote-server verification.

## Commit & Pull Request Guidelines

This snapshot has no Git history to establish conventions. Use concise imperative subjects, optionally prefixed `fix:`, `feat:`, or `docs:`. PRs should explain behavior changes, link relevant issues, list validation, and include screenshots for UI changes when practical. Identify untested device or remote-server behavior.

## Configuration & VPN Invariants

Preserve the app-package bypass before establishing the VPN. Maintain descriptor ownership and disconnect cleanup. Never edit a ShadowQUIC checkout or Cargo's cached dependency as the lasting fix; update the build workflow or make the change upstream. Keep credentials out of committed configs and test artifacts.
