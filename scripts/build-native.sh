#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
python3 scripts/prepare-core.py
shadowquic_dir="${SHADOWQUIC_DIR:-../shadowquic}"
cargo_config="$shadowquic_dir/.cargo/config.toml"
if [[ ! -f "$cargo_config" ]]; then
  echo "ShadowQUIC Cargo config missing: $cargo_config. Check SHADOWQUIC_DIR." >&2
  exit 1
fi
sdk_dir="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
ndk_dir="${ANDROID_NDK_HOME:-$sdk_dir/ndk/28.2.13676358}"
case "$(uname -s)" in
  Linux) host_tag=linux-x86_64 ;;
  Darwin) host_tag=darwin-x86_64 ;;
  *) echo 'Build on Linux or macOS with Android NDK 28.' >&2; exit 1 ;;
esac
toolchain="$ndk_dir/toolchains/llvm/prebuilt/$host_tag/bin"
if [[ ! -x "$toolchain/clang" ]]; then
  echo "Android NDK missing: $ndk_dir. Set ANDROID_NDK_HOME." >&2; exit 1
fi
export AR="$toolchain/llvm-ar"
export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$toolchain/aarch64-linux-android26-clang"
export CARGO_TARGET_X86_64_LINUX_ANDROID_LINKER="$toolchain/x86_64-linux-android26-clang"
export CC_aarch64_linux_android="$CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER"
export CC_x86_64_linux_android="$CARGO_TARGET_X86_64_LINUX_ANDROID_LINKER"
export AR_aarch64_linux_android="$AR"
export AR_x86_64_linux_android="$AR"
IFS=',' read -ra abis <<< "${ANDROID_ABIS:-arm64-v8a,x86_64}"
for abi in "${abis[@]}"; do
  case "$abi" in
    arm64-v8a) target=aarch64-linux-android ;;
    x86_64) target=x86_64-linux-android ;;
    *) echo "Unsupported ABI: $abi" >&2; exit 1 ;;
  esac
  # The Nix shell already supplies both Android standard libraries. Keep
  # rustup as a fallback for developers using a manually installed toolchain.
  target_libdir="$(rustc --print target-libdir --target "$target")"
  if [[ ! -d "$target_libdir" ]]; then
    if [[ "${SQUIRREL_NIX_ENV:-}" == 1 ]] || ! command -v rustup >/dev/null 2>&1; then
      echo "Rust target $target is missing. Enter 'nix develop ./nix'." >&2; exit 1
    fi
    rustup target add "$target"
  fi
  # --manifest-path does not load a dependency's .cargo/config.toml. Load the
  # source checkout's settings explicitly, then append Android page alignment
  # through Cargo's array merging so upstream rustflags stay in effect.
  cargo --config "$cargo_config" \
    --config "target.$target.rustflags=[\"-C\",\"link-arg=-Wl,-z,max-page-size=16384\"]" \
    build --manifest-path native/Cargo.toml --locked --release --target "$target"
  mkdir -p "app/build/generated/squirrel-jni/$abi"
  cp "native/target/$target/release/libsquirrel.so" "app/build/generated/squirrel-jni/$abi/"
done
