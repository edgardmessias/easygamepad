# X10 profile read-back validation

Status: automated checks passed (129 Kotlin tests and Debug lint with no errors).
On 2026-11-09, the maintainer confirmed that writing and reading back settings
worked as requested on their X10 using the debug APK with version code 8, installed
on the connected Android 15 phone. Exact firmware and before/after values were
not recorded. This is evidence for the requested write/read-back workflow, not
for every setting, macro playback or persistence after power-off.

The maintainer separately reported that **Test Android gamepad vibration** did
not vibrate. That action uses Android's input-device vibration API rather than
the KRLY profile write/read-back path and is being investigated separately.

The upload keeps its existing KRLY command sequence. After returning to normal
mode, it now queries settings (119) and mappings (80) and compares the complete
readable state with the profile that was sent. Identity mappings omitted by the
controller and turbo entry ordering do not count as differences. Unexpected
remaining turbo rules or mappings do count as differences; no erase command is
inferred from an omitted profile entry.

The result is one of:

- **Matched:** buttons, stick and trigger settings, vibration and turbo match
  the read-back. Macro sequences and persistence after power-off are not checked.
- **Mismatch:** both reads completed, but the listed fields differ. This does
  not revert the writes or replace the editing draft.
- **Unconfirmed:** upload commands were accepted, but read-back did not finish.
  No successful-match timestamp is recorded.

## Automated checks

```powershell
.\gradlew.bat :protocol:test :app:lintDebug :app:testDebugUnitTest :app:assembleDebug
```

`X10ProfileClientTest` exercises the real command session with simulated
controllers: accepted but ignored writes, remaining mappings/turbo, missing
settings or mapping replies, rejected uploads, cancellation, paged settings,
normal-mode cleanup and comparison normalization. These tests do not prove
Android BLE behavior or physical controller behavior.

## Physical test

1. Install the debug APK with `adb install -r`; keep the existing app data.
2. Connect the X10 configuration channel, open Profiles and read its current
   settings. Save/export that baseline. The read does not back up onboard macros.
3. Change only one motor intensity to a distinct value (for example, 40%). Keep
   the app in the foreground, then apply once.
4. Check that the operation stays busy through read-back and finishes with a
   result under **Last apply result**, plus a matching timestamp on success.
5. Read the controller settings again. Confirm that the chosen intensity was
   retained and that the other motor and other readable fields are unchanged.
6. Restore the baseline intensity, apply and check the result again.
7. Report the model/firmware, Android version, expected and reported intensity,
   the displayed result, and whether gameplay input remains functional. If a
   mismatch or unconfirmed result occurs, retain Connection diagnostics for
   investigation and avoid repeatedly applying the profile.

Testing power-cycle persistence is a separate validation step. It must not be
inferred from an immediate read-back match.
