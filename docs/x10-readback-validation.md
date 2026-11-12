# X10 profile read-back validation

Status: protocol and app unit tests in CI cover the read-back codec,
`X10ProfileClient`, rear-paddle slot parsing and upload planning; they do not
prove Android BLE timing or physical controller behavior.

On **2026-11-09**, the maintainer confirmed that writing and reading back core
settings worked on their X10 using a debug APK (version code 8) on Android 15.
Exact firmware revision and field-by-field before/after values were not recorded.

On **2026-11-11**, the maintainer exercised the updated **M1/M2** rear mapping,
macro and disable flow in the profile editor. An early attempt left **M1
emitting no key** after **Disable paddle**; a later run with the adjusted
upload and read-back path behaved as expected for the mapping/macro/disable
combinations they tried. That is limited evidence for the implemented
configuration workflow only—not for every rear combination, physical playback
timings, repeat behavior, power-cycle retention or firmware variants.

The maintainer separately reported that **Test Android gamepad vibration** did
not vibrate and displayed the unsupported-device notice. Android's input dump
and kernel input capabilities showed no vibrator/force-feedback capability for
the connected gameplay device. The UI now disables this test when Android
exposes no vibrator and explains that motor intensity can still be configured.
That Android limitation is separate from the later KRLY motor tests, which the
maintainer physically validated on the same X10. See [X10 vibration](x10-vibration-investigation.md)
for the checked sequences and their limits.

## What apply compares

The upload keeps its existing KRLY command sequence (rear M1/M2 via command 58
slots, face mappings via command 61). After returning to normal mode, it queries
**119** (sticks, triggers, vibration, turbo), **80** (face mappings) and **121**
(M1/M2 slots: remaps, macros and cleared/disabled paddles), then merges query 121
over any stale M1/M2 rows from query 80.

Comparison checks **readable configuration**, including:

- Face-button mappings (with identity-mapping normalization).
- Rear M1/M2 remaps and rear-paddle macros recovered from query 121.
- **`disabledRearPaddles`** when a slot read back as cleared.
- Sticks, triggers, vibration and turbo (turbo order ignored).

It does **not** verify physical macro playback, repeat timing, power-cycle
persistence or gameplay HID behavior. A **Matched** result does not substitute
for testing paddles in **Test** or after a restart when persistence matters.

Identity mappings omitted by the controller and turbo entry reordering do not
count as differences. Unexpected remaining turbo rules or face mappings do count.
Omitting a draft entry does not send an erase command; explicit
**Disable paddle** and rear remaps/macros do send command 58 writes, including
the 7-byte clear pattern (`[trigger, 1, 0, 0, 0, 0, 0]`).

The result is one of:

- **Matched:** the merged read-back matches the profile that was sent for the
  fields above.
- **Mismatch:** all three queries completed, but at least one field group differs.
  This does not revert writes or replace the editing draft.
- **Unconfirmed:** upload commands were accepted, but read-back did not finish.
  No successful-match timestamp is recorded.

Early testing mapped rear paddles through command 61 and duplicated face
output ([issue #3](https://github.com/edgardmessias/easygamepad/issues/3)). The
current implementation uses command 58 macro slots instead; read-back validation
follows query 121 for M1/M2.

## Automated checks

```powershell
.\gradlew.bat :protocol:test :app:lintDebug :app:testDebugUnitTest :app:assembleDebug
```

`X10ProfileClientTest` and `X10PaddleMacroTest` exercise ordered uploads,
paged queries 119/80/121, rear-slot merge, disabled-paddle clears and comparison
normalization with simulated controllers. These tests do not prove Android BLE
behavior or physical controller behavior.

## Physical test

1. Install the debug APK with `adb install -r`; keep the existing app data.
2. Connect the X10 configuration channel, open Profiles and **Read from X10**.
   Save/export that baseline after the read completes.
3. Change **one** readable field at a time when investigating mismatches (for
   example motor intensity, one face remap, one M1 passthrough, one M2 macro or
   one disabled paddle). Keep the app in the foreground, then apply once.
4. Check that the operation stays busy through read-back and finishes with a
   result under **Last apply result**, plus a matching timestamp on success.
5. **Read from X10** again and confirm the chosen change appears in the draft
   (including M1/M2 state when query 121 succeeds).
6. Restore the baseline, apply and check the result again.
7. Report the model/firmware if readable, Android version, what changed, the
   displayed result, and whether gameplay input remains functional. If a mismatch
   or unconfirmed result occurs, retain Connection diagnostics for investigation
   and avoid repeatedly applying the profile.

Testing power-cycle persistence is a separate validation step. It must not be
inferred from an immediate read-back match.
