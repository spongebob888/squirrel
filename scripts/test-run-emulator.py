#!/usr/bin/env python3
"""Exercise launcher recovery with an isolated SDK; no real AVDs are touched."""

import json
import os
from pathlib import Path
import shutil
import signal
import subprocess
import tempfile
import unittest


FAKE_SDK = r'''#!/usr/bin/env python3
import json
import os
from pathlib import Path
import sys
import time

state_path = Path(os.environ["TEST_STATE"])
state = json.loads(state_path.read_text())
args = sys.argv[1:]
with Path(os.environ["TEST_EVENTS"]).open("a") as events:
    events.write(json.dumps([Path(sys.argv[0]).name, *args]) + "\n")

def save():
    temporary = state_path.with_suffix(".tmp")
    temporary.write_text(json.dumps(state))
    temporary.replace(state_path)

if Path(sys.argv[0]).name == "emulator":
    if args == ["-list-avds"]:
        print("squirrel_api36")
    else:
        Path(os.environ["TEST_PID"]).write_text(str(os.getpid()))
        if state.get("broken_data"):
            print("previously interrupted during encryption step", flush=True)
        state["devices"]["emulator-5558"] = {
            "name": "squirrel_api36", "path": os.environ["TEST_AVD"],
            "state": "offline" if state.get("broken_data") else "device",
            "window": "headless" if "-no-window" in args else "qtwindow"
        }
        save()
        time.sleep(60)
elif args == ["devices"]:
    print("List of devices attached")
    for serial, device in state["devices"].items():
        print(serial + "\t" + device["state"])
elif args == ["start-server"]:
    pass
elif len(args) > 2 and args[0] == "-s":
    serial, command = args[1], args[2:]
    device = state["devices"].get(serial)
    if device is None:
        sys.exit(1)
    if command == ["get-state"]:
        print(device["state"])
    elif command == ["reconnect"]:
        if state.get("reconnect_works"):
            device["state"] = "device"
            save()
    elif command[:1] == ["emu"]:
        query = command[1:]
        if query == ["avd", "name"]:
            print(device["name"] + "\r\nOK\r")
        elif query == ["avd", "path"]:
            print(device["path"] + "\r\nOK\r")
        elif query == ["avd", "windowtype"]:
            print("windowtype=" + device["window"])
        elif query == ["kill"]:
            del state["devices"][serial]
            save()
        elif query == ["avd", "discoverypath"]:
            print("/nonexistent/discovery.ini")
    elif command == ["shell", "getprop", "sys.boot_completed"]:
        if device["state"] == "device":
            print("1")
    elif command == ["shell", "sync"]:
        pass
    elif command[:2] == ["shell", "pm"]:
        print("package:/system/framework/framework-res.apk")
    elif command[:1] == ["install"]:
        print("Success")
    elif command[:3] == ["shell", "am", "start"]:
        print("Status: ok")
    else:
        sys.exit("Unexpected SDK call: " + repr(command))
else:
    sys.exit("Unexpected SDK call: " + repr(args))
'''


class LauncherTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="squirrel-launcher-test-")
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        (self.root / "scripts").mkdir()
        shutil.copyfile(Path(__file__).with_name("run-emulator.sh"),
                        self.root / "scripts/run-emulator.sh")
        self.avd = self.root / "avds/squirrel_api36.avd"
        self.avd.mkdir(parents=True)
        (self.avd.parent / "squirrel_api36.ini").write_text(f"path={self.avd}\n")
        sdk = self.root / "sdk"
        for tool in ("platform-tools/adb", "emulator/emulator"):
            path = sdk / tool
            path.parent.mkdir(parents=True)
            path.write_text(FAKE_SDK)
            path.chmod(0o755)
        self.apk = self.root / "app.apk"
        self.apk.touch()
        self.state = self.root / "state.json"
        self.events = self.root / "events.jsonl"
        self.pid = self.root / "emulator.pid"
        self.env = dict(os.environ, SQUIRREL_NIX_ENV="1", ANDROID_HOME=str(sdk),
                        ANDROID_AVD_HOME=str(self.avd.parent), APK_PATH=str(self.apk),
                        TEST_STATE=str(self.state), TEST_EVENTS=str(self.events),
                        TEST_PID=str(self.pid), TEST_AVD=str(self.avd),
                        SQUIRREL_AVD_NAME="squirrel_api36", SQUIRREL_EMULATOR_GPU="swiftshader",
                        EMULATOR_BOOT_TIMEOUT="15", DISPLAY=":0")
        self.addCleanup(self.stop_fake_emulator)

    def stop_fake_emulator(self):
        if self.pid.exists():
            try:
                os.kill(int(self.pid.read_text()), signal.SIGTERM)
            except ProcessLookupError:
                pass

    def run_launcher(self, devices=None, arguments=(), **state):
        self.state.write_text(json.dumps(dict(devices=devices or {}, **state)))
        result = subprocess.run(["bash", str(self.root / "scripts/run-emulator.sh"), *arguments],
                                env=self.env, capture_output=True, text=True, timeout=50)
        events = [json.loads(line) for line in self.events.read_text().splitlines()]
        return result, events

    def device(self):
        return dict(name="squirrel_api36", path=str(self.avd), state="device",
                    window="qtwindow")

    def test_reuses_only_the_selected_avd_path(self):
        other = self.device()
        other["path"] = str(self.root / "another-home/squirrel_api36.avd")
        result, events = self.run_launcher({"emulator-5554": other})
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn(["adb", "-s", "emulator-5558", "install", "-r", str(self.apk)], events)
        self.assertNotIn(["adb", "-s", "emulator-5554", "emu", "kill"], events)
        launch = next(call for call in events if call[:2] == ["emulator", "-avd"])
        self.assertEqual(launch[-2:], ["-gpu", "swiftshader"])

    def test_reconnects_offline_transport_without_restart(self):
        device = self.device()
        device["state"] = "offline"
        result, events = self.run_launcher({"emulator-5554": device}, reconnect_works=True)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn(["adb", "-s", "emulator-5554", "reconnect"], events)
        self.assertNotIn(["adb", "-s", "emulator-5554", "emu", "kill"], events)

    def test_restarts_unrecoverable_offline_transport(self):
        device = self.device()
        device["state"] = "offline"
        result, events = self.run_launcher({"emulator-5554": device})
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn(["adb", "-s", "emulator-5554", "emu", "kill"], events)
        self.assertIn("cold-restarting", result.stdout)

    def test_reopens_headless_instance_with_a_window(self):
        device = self.device()
        device["window"] = "headless"
        result, events = self.run_launcher({"emulator-5554": device})
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn(["adb", "-s", "emulator-5554", "emu", "kill"], events)
        launch = next(call for call in events if call[:2] == ["emulator", "-avd"])
        self.assertNotIn("-no-window", launch)
        self.assertLess(events.index(["adb", "-s", "emulator-5554", "shell", "sync"]),
                        events.index(["adb", "-s", "emulator-5554", "emu", "kill"]))

    def test_interrupted_encryption_fails_without_wiping_data(self):
        marker = self.avd / "userdata-qemu.img"
        marker.write_bytes(b"saved disk")
        result, events = self.run_launcher(broken_data=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Back up this AVD", result.stderr)
        self.assertEqual(marker.read_bytes(), b"saved disk")
        self.assertIn(["adb", "-s", "emulator-5558", "emu", "kill"], events)


if __name__ == "__main__":
    unittest.main()
