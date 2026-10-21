#!/usr/bin/env bash
set -euo pipefail

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
apk_path="${APK_PATH:-$root_dir/app/build/outputs/apk/debug/app-debug.apk}"
boot_timeout="${EMULATOR_BOOT_TIMEOUT:-180}"
avd_name=""
headless=false
rebuild=false
restart=false
gpu="${SQUIRREL_EMULATOR_GPU:-swiftshader}"

usage() {
    cat <<'EOF'
Usage: ./scripts/run-emulator.sh [AVD_NAME] [--headless] [--build] [--restart]

Enter the pinned Nix environment, start or reuse an emulator, install the
debug APK, and open Squirrel. Creates squirrel_api36 if no AVD is specified.

Options:
  --headless  Run without an emulator window or audio.
  --build     Rebuild the APK before installation (also built if missing).
  --restart   Cold-restart the selected emulator, preserving its saved data.
  -h, --help  Show this help.

Environment: SQUIRREL_AVD_NAME, SQUIRREL_SYSTEM_IMAGE, APK_PATH,
             EMULATOR_BOOT_TIMEOUT (seconds; default: 180),
             SQUIRREL_EMULATOR_GPU (default: swiftshader; use host for host GPU).
SDK, NDK, Java, Rust, and emulator tools are supplied by nix/flake.nix.
EOF
}

die() { printf 'Error: %s\n' "$*" >&2; exit 1; }

for argument in "$@"; do
    case "$argument" in
        --headless) headless=true ;;
        --build) rebuild=true ;;
        --restart) restart=true ;;
        -h|--help) usage; exit 0 ;;
        -*) die "Unknown option: $argument. See --help." ;;
        *)
            [[ -z "$avd_name" ]] || die 'Specify only one AVD name.'
            avd_name="$argument"
            ;;
    esac
done

[[ "$boot_timeout" =~ ^[1-9][0-9]*$ ]] || die 'EMULATOR_BOOT_TIMEOUT must be a positive integer.'
if [[ "${SQUIRREL_NIX_ENV:-}" != 1 ]]; then
    command -v nix >/dev/null 2>&1 || die "Nix is required. Install Nix, then rerun this script."
    exec nix develop "$root_dir/nix" --command bash "$root_dir/scripts/run-emulator.sh" "$@"
fi

sdk_dir="${ANDROID_HOME:?Enter nix develop ./nix to configure the Android SDK.}"
adb="$sdk_dir/platform-tools/adb"
emulator="$sdk_dir/emulator/emulator"
[[ -x "$adb" ]] || die "adb not found: $adb. Set ANDROID_HOME."
[[ -x "$emulator" ]] || die "Emulator not found: $emulator. Set ANDROID_HOME."
emulator_command=("$emulator")
case "$gpu" in
    auto|host|software|swiftshader|lavapipe|swangle) ;;
    *) die "Unsupported SQUIRREL_EMULATOR_GPU: $gpu" ;;
esac
avds="$("${emulator_command[@]}" -list-avds | tr -d '\r')"
avd_name="${avd_name:-${SQUIRREL_AVD_NAME:-squirrel_api36}}"
if ! printf '%s\n' "$avds" | grep -Fxq -- "$avd_name"; then
    command -v avdmanager >/dev/null 2>&1 || die "avdmanager is missing from the Nix environment."
    system_image="${SQUIRREL_SYSTEM_IMAGE:-system-images;android-36;google_apis;x86_64}"
    mkdir -p "${ANDROID_AVD_HOME:-${ANDROID_USER_HOME:-$HOME/.android}/avd}"
    printf 'Creating %s using %s…\n' "$avd_name" "$system_image"
    printf 'no\n' | avdmanager create avd --name "$avd_name" --package "$system_image" --device pixel_6
fi

# Resolve the actual AVD directory, including imported profiles with a custom
# path. An emulator with the same name in another AVD home is a different VM.
avd_home="${ANDROID_AVD_HOME:-${ANDROID_USER_HOME:-$HOME/.android}/avd}"
avd_path="$(sed -n 's/^path=//p' "$avd_home/$avd_name.ini")"
[[ -n "$avd_path" ]] || die "Cannot resolve the AVD directory for $avd_name."
avd_path="$(realpath -m "$avd_path")"

if "$rebuild" || [[ ! -f "$apk_path" ]]; then
    printf 'Building the debug APK…\n'
    (cd "$root_dir" && ./gradlew assembleDebug)
fi
[[ -f "$apk_path" ]] || die "APK not found after building: $apk_path. Check APK_PATH."

"$adb" start-server

console() {
    local target="$1"
    shift
    timeout 5 "$adb" -s "$target" emu "$@" 2>/dev/null | tr -d '\r' | sed -n '1p'
}

device_state() {
    timeout 5 "$adb" -s "$1" get-state 2>/dev/null | tr -d '\r'
}

# Explicit serials keep installation scoped to the selected AVD, even when
# another emulator or a physical phone is connected.
find_emulator() {
    local candidate name path
    while IFS= read -r candidate; do
        name="$(console "$candidate" avd name)" || continue
        if [[ "$name" == "$avd_name" ]]; then
            path="$(console "$candidate" avd path)" || continue
            [[ "$(realpath -m "$path")" == "$avd_path" ]] || continue
            printf '%s\n' "$candidate"
            return 0
        fi
    done < <("$adb" devices | awk '$1 ~ /^emulator-/ && ($2 == "device" || $2 == "offline") {print $1}')
    return 1
}

stop_emulator() {
    local target="$1" discovery pid="" executable="" stop_deadline
    discovery="$(console "$target" avd discoverypath)" || discovery=""
    if [[ "$discovery" =~ /pid_([0-9]+)\.ini$ ]]; then
        pid="${BASH_REMATCH[1]}"
        executable="$(readlink "/proc/$pid/exe")" || executable=""
        [[ "$executable" == */qemu-system-* ]] || pid=""
    fi
    printf 'Stopping %s (%s); saved data will be kept…\n' "$avd_name" "$target"
    # Flush recent imports/settings before the console terminates the VM.
    # An offline guest cannot respond, so recovery still uses its console.
    if [[ "$(device_state "$target" || true)" == device ]]; then
        timeout 10 "$adb" -s "$target" shell sync >/dev/null 2>&1 || true
    fi
    timeout 5 "$adb" -s "$target" emu kill >/dev/null 2>&1 || true
    stop_deadline=$((SECONDS + 20))
    while (( SECONDS < stop_deadline )); do
        if [[ -n "$pid" ]]; then
            [[ -e "/proc/$pid/exe" ]] || return 0
        elif ! console "$target" ping >/dev/null; then
            return 0
        fi
        sleep 1
    done
    # Only terminate the QEMU process identified by this AVD's own console.
    # A hung renderer can prevent the normal console shutdown from completing.
    if [[ -n "$pid" && "$(readlink "/proc/$pid/exe" 2>/dev/null)" == "$executable" ]]; then
        printf 'Emulator did not exit normally; terminating its stalled process.\n'
        kill -TERM "$pid" 2>/dev/null || true
        for _ in {1..10}; do
            [[ -e "/proc/$pid/exe" ]] || return 0
            sleep 1
        done
        if [[ "$(readlink "/proc/$pid/exe" 2>/dev/null)" == "$executable" ]]; then
            kill -KILL "$pid" 2>/dev/null || true
        fi
        sleep 1
        [[ ! -e "/proc/$pid/exe" ]] && return 0
    fi
    die "Cannot stop $avd_name. Close its emulator window and rerun with --restart."
}

serial="$(find_emulator)" || serial=""
if [[ -n "$serial" && "$restart" == false && "$(device_state "$serial" || true)" == offline ]]; then
    printf '%s is offline. Reconnecting its ADB transport…\n' "$avd_name"
    timeout 5 "$adb" -s "$serial" reconnect >/dev/null 2>&1 || true
    reconnect_deadline=$((SECONDS + 20))
    while (( SECONDS < reconnect_deadline )); do
        [[ "$(device_state "$serial" || true)" == device ]] && break
        sleep 2
    done
    if [[ "$(device_state "$serial" || true)" != device ]]; then
        printf 'ADB did not recover; cold-restarting the selected emulator.\n'
        restart=true
    fi
fi
if [[ -n "$serial" && "$headless" == false && "$(console "$serial" avd windowtype || true)" == windowtype=headless ]]; then
    printf 'Reopening %s with a visible window…\n' "$avd_name"
    restart=true
fi
if [[ -n "$serial" && "$restart" == true ]]; then
    stop_emulator "$serial"
    serial=""
fi
emulator_pid=""
mkdir -p "$root_dir/.build/emulator-logs"
log_path="$root_dir/.build/run-emulator.log"
if [[ -z "$serial" ]]; then
    safe_name="${avd_name//[^a-zA-Z0-9_.-]/_}"
    run_log="emulator-logs/$safe_name-$(date +%Y%m%d-%H%M%S)-$$.log"
    if [[ -f "$log_path" && ! -L "$log_path" ]]; then
        mv "$log_path" "$root_dir/.build/emulator-logs/previous-$$.log"
    fi
    ln -sfn "$run_log" "$log_path"
    log_path="$root_dir/.build/$run_log"
    emulator_options=(-avd "$avd_name" -no-snapshot -show-kernel -gpu "$gpu")
    if "$headless"; then
        emulator_options+=(-no-window -no-audio)
    elif [[ -n "${DISPLAY:-}" ]]; then
        # The SDK bundles the xcb plugin; it has no native Wayland Qt plugin.
        emulator_command=(env QT_QPA_PLATFORM=xcb "${emulator_command[@]}")
    fi
    printf 'Starting %s (log: %s)\n' "$avd_name" "$log_path"
    # Detach from the caller's terminal/process group and retain a supervisor
    # for runtime wrappers that exit when their parent disappears.
    nohup setsid --wait bash -c '"$@" & wait "$!"' squirrel-emulator \
        "${emulator_command[@]}" "${emulator_options[@]}" >"$log_path" 2>&1 </dev/null &
    emulator_pid=$!
else
    printf 'Reusing %s (%s).\n' "$avd_name" "$serial"
fi

printf 'Waiting for Android to finish booting (up to %s seconds)…\n' "$boot_timeout"
deadline=$((SECONDS + boot_timeout))
while (( SECONDS < deadline )); do
    if [[ -n "$emulator_pid" ]] && grep -Fq 'previously interrupted during encryption step' "$log_path"; then
        serial="$(find_emulator)" || serial=""
        [[ -z "$serial" ]] || stop_emulator "$serial"
        die "Android cannot mount $avd_path/userdata-qemu.img after interrupted encryption. Back up this AVD before creating a fresh one; --restart cannot repair its data. See $log_path."
    fi
    if [[ -z "$serial" ]]; then
        serial="$(find_emulator)" || serial=""
    fi
    if [[ -n "$serial" ]]; then
        booted="$(timeout 5 "$adb" -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" || booted=""
        if [[ "$booted" == 1 ]] && timeout 5 "$adb" -s "$serial" shell pm path android >/dev/null 2>&1; then
            printf 'Installing Squirrel on %s…\n' "$serial"
            "$adb" -s "$serial" install -r "$apk_path"
            "$adb" -s "$serial" shell am start -W -n dev.squirrel.vpn/.MainActivity
            printf '\nSquirrel is open. Import your config or choose Direct test, then tap Connect.\n'
            exit 0
        fi
    fi
    if [[ -n "$emulator_pid" ]] && ! kill -0 "$emulator_pid" 2>/dev/null; then
        tail -n 30 "$log_path" >&2
        die "Emulator exited before booting. See $log_path."
    fi
    sleep 2
done
if [[ -n "$emulator_pid" && -n "$serial" ]]; then
    stop_emulator "$serial"
fi
die "Android did not boot within $boot_timeout seconds. Retry with --restart. Startup log: $log_path."
