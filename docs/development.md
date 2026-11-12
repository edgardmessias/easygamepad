# EasyGamepad development guide

EasyGamepad is a native Kotlin / Jetpack Compose Android app with a separate JVM
protocol module. Start with [Contributing](../CONTRIBUTING.md) for environment
setup, build commands and installation. The [KRLY reference](protocol.md) covers
wire-level behavior; the [user guide](user-guide.md) explains the app's workflows.

Jump to [architecture](#architecture), [device identity](#connections-and-device-identity),
[profile files](#profile-file-format), [diagnostics](#diagnostics),
[hardware validation](#hardware-validation) or [screenshots](#documentation-screenshots).

## Architecture

```mermaid
flowchart TD
    UI[Compose screens] --> VM[HubViewModel]
    VM --> STORE[ProfileStore / DataStore]
    VM --> INPUT[GamepadInput / InputSessions]
    VM -->     REPO[ControllerRepository / KeyLinkerRepository]
    REPO --> SESSION[CommandSession / protocol codecs]
    SESSION --> BLE[BleClient / GamepadTransport]
    BLE --> CONFIG[Configuration BLE (KRLY or KeyLinker)]
    HID[Android gamepad events] --> INPUT
```

| Area | Responsibilities |
| --- | --- |
| `app/.../ui` | Capability-dependent navigation, connection picker, profile editors, tester, tools and preferences |
| `HubViewModel` | Selected controller, foreground reconnect policy, drafts and operation dispatch |
| `app/.../data` | Device identity, status, controller operations and local persistence |
| `app/.../input` | Android input discovery, per-device snapshots, axes and paddle identification |
| `app/.../ble` | Permissions, scanning, GATT lifecycle, notification subscription, chunk writes and logs |
| `protocol` | Device profiles, value models, frame codecs, paged responses and command sessions; no Android dependency |

Paths abbreviated as `app/.../` refer to the Kotlin package under
[`app/src/main/kotlin/br/dev/edgard/easygamepad`](../app/src/main/kotlin/br/dev/edgard/easygamepad).

`HubApplication` owns the app services. `MainActivity` forwards Android key and
motion events and notifies the ViewModel of foreground transitions. Compose
screens collect state flows and request operations through the ViewModel.

`ControllerRepository` implements the **KRLY (X10) driver**; `KeyLinkerRepository`
implements the **KeyLinker (X20 / X20 Pro) driver**. Each acts only on links opened
for its protocol. The KRLY operation mutex serializes X10 feature work and exposes
busy/progress state. `CommandSession` and `KeyLinkerSession` separately serialize
requests; `BleClient` serializes GATT writes. Coroutine mutexes and suspended waits
do not block the UI thread while waiting for replies.

## Connections and device identity

Gameplay input and BLE configuration have distinct identities and lifecycles.
Do not infer configuration support from an Xbox display name or cloned VID/PID.

| Identity | Purpose |
| --- | --- |
| Saved configuration address | Identifies the selected BLE peripheral |
| `modelId` and `DeviceProfile` | Select the known configuration protocol, capabilities and artwork |
| Android runtime input ID | Distinguishes currently connected gameplay devices |
| Android input descriptor | Remembers an input association where the descriptor is unambiguous |
| Local display name | Helps users recognize a device; does not authorize commands |

Each saved controller contains its own address, name, model ID, optional input
descriptor and GATT UUIDs. Only one configuration BLE session is active at a time.
Switching controllers closes the old session, clears its hardware state and
restores the target's local editing draft. Switching is blocked during operations
or calibration.

With one saved X10 and one gamepad, the app can temporarily use that sole input.
With multiple choices or ambiguous descriptors, require an explicit choice.
Runtime IDs distinguish equal descriptors; removal of a selected input must not
silently select another controller with the same descriptor.

`InputSessions` retains independent pressed/tested buttons, axes, trails and
paddle bindings per input ID. Backgrounding clears held inputs. Paddle learning
is a tester aid: it observes the output a paddle emits and does not write firmware.

The configuration link moves through `DISCONNECTED`, `CONNECTING`, `DISCOVERING`,
`SUBSCRIBING` and `READY`. Readiness requires successful notification subscription.
Only a matching ready configuration address may receive feature operations.
Battery state is scoped to the selected device; an absent reading is not zero.

Foreground reconnection uses the saved, verified configuration address and
backoff. Leaving the foreground attempts cleanup and disconnects BLE. A manual
disconnect pauses retries until the next foreground entry. Scanning failures are
reported independently of an already-ready connection.

### Capabilities and additional controllers

`DeviceProfile` selects the configuration protocol per model: **X10** uses KRLY
([protocol reference](protocol.md)); **X20** and **X20 Pro** use KeyLinker
([KeyLinker reference](keylinker.md)). Capabilities govern configuration, battery,
calibration, firmware information, macros, turbo, ranges, vibration, rear buttons
and artwork. Unknown models receive input testing only.

To add support:

1. Establish the device's configuration identity, GATT layout, protocol and
   available operations with evidence for its firmware and connection mode.
2. Add a protocol-specific codec and tests. Reuse KRLY only when compatibility
   is established; a different name or edited UUIDs do not establish it.
3. For another protocol, introduce a driver interface and selection mechanism
   around the currently KRLY-specific repository. `GamepadTransport` abstracts
   bytes, but the app is not yet a complete interchangeable-driver framework.
4. Declare only supported capabilities. Keep artwork and physical paddle layout
   independent of protocol key codes and generic gameplay discovery.
5. Define any profile compatibility or migration explicitly, preserving existing
   DataStore keys and user files. Gate UI actions and ViewModel operations alike.
6. Follow the hardware checklist before claiming support.

**KeyLinker has a dedicated Android driver.** [`KeyLinkerRepository`](../app/src/main/kotlin/br/dev/edgard/easygamepad/data/KeyLinkerRepository.kt),
[`KeyLinkerScreen`](../app/src/main/kotlin/br/dev/edgard/easygamepad/ui/KeyLinkerScreen.kt)
and the `protocol/.../keylinker` codecs implement identification, reads and gated
writes on links opened for `KEYLINKER`. Its frame layout, UUIDs, checksum and
scrambling differ from KRLY. Artwork reused from x20ctl is documented in
[third-party credits](../THIRD_PARTY.md); upstream x20ctl does not by itself prove
support for unrelated controllers or firmware.

## Drafts, saved data and profiles

`ControllerDrafts` caches edits by controller identity during the app session.
`ProfileStore` persists theme, language, saved controllers, input names and saved
profiles in the Preferences DataStore named `easy_gamepad`. Draft edits are not
automatically durable saved profiles.

Readback uses `X10ProfileClient.readSettings`: command **119** (sticks, triggers,
vibration, turbo), **80** (face mappings) and **121** (M1/M2 paddle slots).
`ControllerSettings.mergeInto` replaces mappings, ranges, vibration, turbo,
rear M1/M2 macros and `disabledRearPaddles`, while keeping the local profile
name and any **M3/M4** macro entries that exist only in the draft model. A read
failure does not merge a partial result into the draft.

Applying an X10 profile also reads queries 119, 80 and 121 after normal mode.
`X10ProfileClient` compares face mappings, rear M1/M2 remaps and macros,
disabled paddles, sticks, triggers, vibration and turbo, then returns matched,
mismatch or unconfirmed. ACKs alone do not count as a read-back match. This check
leaves the local draft intact. Physical playback and power-cycle persistence
remain outside its coverage. See [X10 read-back validation](x10-readback-validation.md).

The current importer accepts only the exact format/version/device tuple below.
Do not infer compatibility from historical product names. Android backup is
disabled, and uninstalling removes app data; export before uninstalling.

### Profile file format

Exports are UTF-8 JSON with enum **names**, not wire codes, for keys. This complete
example is valid for the current importer:

```json
{
  "format": "easygamepad-profile",
  "version": 1,
  "device": "easysmx-x10",
  "config": {
    "name": "Default X10",
    "mappings": {
      "A": "A", "B": "B", "X": "X", "Y": "Y",
      "LB": "LB", "LT": "LT", "LS": "LS",
      "RB": "RB", "RT": "RT", "RS": "RS",
      "SELECT": "SELECT", "START": "START",
      "UP": "UP", "DOWN": "DOWN", "LEFT": "LEFT", "RIGHT": "RIGHT"
    },
    "leftStick": { "min": 0, "max": 100, "invertX": false, "invertY": false },
    "rightStick": { "min": 0, "max": 100, "invertX": false, "invertY": false },
    "leftTrigger": { "min": 0, "max": 100 },
    "rightTrigger": { "min": 0, "max": 100 },
    "leftVibration": 70,
    "rightVibration": 70,
    "macros": [],
    "turbos": [],
    "disabledRearPaddles": []
  }
}
```

`disabledRearPaddles` is optional on import: profiles without it decode with an
empty set. Exports always include the array (possibly empty) for M1/M2 paddles
marked **Disable paddle** in the editor.

Optional entries within the required `macros` and `turbos` arrays have these shapes:

```json
{
  "macroExample": {
    "trigger": "M1",
    "repeat": false,
    "steps": [
      { "key": "A", "holdMs": 100, "delayMs": 100 },
      { "key": "B", "holdMs": 100, "delayMs": 0 }
    ]
  },
  "turboExample": { "key": "A", "automatic": false, "rate": 15 }
}
```

`macroExample` and `turboExample` above are explanatory labels, not profile fields;
put their objects in the corresponding arrays. Empty arrays do not establish
that applying the profile clears old rules on the controller.

| Constraint | Accepted values |
| --- | --- |
| Input size | Up to 100,000 characters |
| Profile name | Nonblank, up to 40 characters |
| Stick / trigger ranges | Min 0–99, max 1–100, min < max |
| Motor intensity | 0–100 |
| Macro arrays | Up to 4 unique M1–M4 triggers in the model; X10 UI and read-back use M1/M2 |
| Disabled rear paddles | Optional JSON array of `M1`/`M2` names; import accepts absence |
| Macro steps | 1–32 per sequence; hold 1–65,535 ms, wait 0–65,535 ms |
| Turbo arrays | Up to 16 unique output keys, rate 1–20 |
| Output keys | Regular `PadKey` outputs; M1–M4 are not output targets |

All exported configuration fields are required by the current decoder. Range and
duplicate validation runs before imported settings are loaded. The profile
identifier does not include a BLE address: compatible X10 setups can be reused
across saved X10 devices, subject to firmware validation.

## Diagnostics

**Tools → Connection diagnostics** exposes a 200-entry in-memory ring buffer;
the expanded UI shows the most recent 35 entries. Sharing includes app/Android
versions and the retained history. It can contain device addresses and raw RX/TX
bytes; review before posting it publicly.

Debug builds also log summaries under `EasyGamepad`. RX/TX summaries contain
byte counts rather than payloads, and addresses in other summary messages are
masked. Release builds omit this debug mirror. To observe a debug installation:

```powershell
adb logcat -s EasyGamepad:D '*:S'
```

For a failed operation, inspect connection phase, characteristic subscription,
write callback, response ID, payload validation and any timeout. Never reuse a
timed-out session to retry a same-ID command: matching does not verify an echoed
sequence number.

## Hardware validation

JVM tests cover codecs, pagination, sessions, capability gates, identity, drafts,
input isolation and language selection. They do not exercise Android GATT or
prove on-device writes. Battery, firmware, mapping and settings fixtures capture
specific observed responses; coverage does not imply every operation has been
physically verified.

Record model, firmware, Android/app version, mode, connection type and expected
versus actual behavior. Before testing writes, save/export the draft and read
supported settings, noting that readback does not recover onboard macro sequences.

1. Confirm gameplay input and configuration independently, including battery
   refresh and a version read.
2. For a single reversible change, record the command response, supported
   readback and actual gameplay input separately. Restore and verify the baseline.
3. Test each mapping, range, inversion, motor, macro and turbo behavior separately.
   Verify removal of existing rules explicitly rather than inferring it from omission.
4. Power-cycle the controller and read/test again before claiming persistence.
5. Exercise calibration success, retry, finish, cancellation, timeout and link
   loss. Do not treat a start/stop write as calibration success.
6. Check manual disconnect, foreground/background transitions, competing phone
   connections and controller sleep. Check multiple-controller routing on hardware
   when claiming simultaneous input support.

Full profile-write, deletion/persistence and calibration validation remains
incomplete. Record new evidence per operation rather than marking the whole
controller verified from a battery read or a screenshot.

## Documentation screenshots

Capture the installed app using ADB, with **Settings → Language → English** and
**Appearance → Dark**. Use a real X10 connection for connected states. The current
images were captured on an M2007J20CG at native 1080 × 2400 resolution, using app
version 1.0.0 (code 7). These identify the capture session, not a version pin for
future documentation.

Navigate to the desired screen and wait for transient messages to disappear.
Keep system font/display settings consistent. For editors, label draft examples
as local edits. Do not apply settings, start calibration or reset the controller
just to obtain an image. Remove any temporary draft entries afterward.

From the repository root in PowerShell, this preserves the PNG's binary bytes:

```powershell
@'
from pathlib import Path
import subprocess

adb = ["adb"]  # With multiple devices, use ["adb", "-s", "SERIAL"].
image = subprocess.check_output(adb + ["exec-out", "screencap", "-p"])
target = Path("docs/images/dashboard.png")
target.parent.mkdir(parents=True, exist_ok=True)
target.write_bytes(image)
'@ | python -
```

Use the appropriate descriptive filename for each screen. Optimize PNG compression
losslessly, retain native resolution, and inspect every image for readability,
notifications, personal names and complete device addresses. Do not fabricate
status or repaint controls. Use English alt text and captions when embedding images.
The artwork shown in screenshots retains the attribution in [THIRD_PARTY.md](../THIRD_PARTY.md).

The README uses `dashboard.png`, `buttons.png` and `tester.png` at a compact
display width. The user guide also includes `tester-input.png`, `sticks.png`,
`triggers.png`, `macros.png`, `turbo.png`, `vibration.png`, `tools.png` and
`settings.png`. Keep filenames stable when renewing the images and inspect the
rendered Markdown after replacement.
