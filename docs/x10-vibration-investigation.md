# X10 vibration test findings

These findings were recorded during owner-supervised tests on 2026-11-09 using
an EasySMX X10 and an Android 15 phone. The firmware revision was not recorded.
They apply to that controller and connection; other firmware remains unverified.

## Android and X10 tests use separate connections

The X10 gameplay interface appeared as **Xbox Wireless Controller**, but Android
exposed no vibrator for it. That name does not establish vibration support.
The Android test therefore stays disabled. It uses only the selected gamepad's
vibrator and does not redirect vibration to the phone or another controller.

The X10 motor tests use its separate KRLY configuration connection. Command
**53 / 0x35** stores the motor intensity settings during profile application;
command **69 / 0x45** is used for the temporary tests described below.

## Physical observations

Each diagnostic sequence sent command 69 with the selected three-byte payload,
waited 500 ms after its acknowledgment, then sent `0, 0, 0`. The owner initiated
each test manually and reported these results, looking at the controller's front:

| Candidate | Payload (decimal bytes) | Physical result |
| --- | --- | --- |
| 1 | `20, 0, 1` | Left grip, LT/LB side; stopped without powering off |
| 2 | `0, 20, 1` | No perceptible vibration |
| 3 | `20, 1, 1` | Apparently the same side as candidate 1; stopped normally |
| 4 | `20, 0, 2` | No perceptible vibration |
| 5 | `0, 40, 2` | Right side, RT/RB; stopped without powering off |

The app logs recorded accepted test and all-zero follow-up commands, with
readable settings unchanged. The owner then tested the initial **Test left motor
on X10** and **Test right motor on X10** buttons with fixed values and confirmed that each activated
its corresponding side and stopped normally without an error.

The first version used fixed test values. After the owner reported that moving
the profile sliders did not change the test strength, a separate diagnostic
allowed custom values. The owner confirmed different perceived intensities
on both sides and normal stopping. The shipped app holds command 69 at the
slider value while a motor slider is dragged and sends the all-zero follow-up
when the slider is released. Logs recorded these values, with accepted
commands and unchanged readable settings in every completed test:

| Side | Values sent in the custom-value diagnostic |
| --- | --- |
| Left | `6, 20, 62, 85, 100` |
| Right | `0, 6, 14, 20, 51, 100` |

## Current implementation and limits

Live preview uses **`X10VibrationPreview`**: command 69 with `value, 0, 1` for the
left motor or `0, value, 2` for the right while the slider is held, then `0, 0, 0`
on release. Preview commands use **`send`** (no ACK wait) via a serial queue so
cancelling a drag mid-flight does not tear down the BLE session. Preview commands
do not store the profile; **Apply to X10** (command 53) is still required.

**`X10VibrationTest`** remains for diagnostics: one motor pulse with ACK, 500 ms
wait, all-zero follow-up, and settings read-back before and after (commands 119
and 80). It is not used in the profile editor UI.

The app requires the selected X10 configuration connection to be ready and
blocks preview during other operations, controller switching or calibration.

The observations establish individual motor selection and different perceived
intensities. They do not establish a linear percentage of physical force,
duration units or a general motor bitmask. The stops do not distinguish an
effect of the all-zero follow-up from a firmware timer.
A lost connection can prevent the follow-up, so keep the app open and turn the
controller off if vibration continues.

Read-back covers only readable settings. Onboard macro sequences and
persistence after a power cycle were not checked. An acknowledgment alone is
not evidence of physical vibration.

## Verification

The implementation passed Kotlin unit tests, debug lint without errors, and
both debug and release builds. `X10VibrationTestTest` covers the exact payloads,
custom-value bounds, cleanup after rejection or cancellation, missing replies,
rejected follow-ups and readable-setting changes without an automatic profile restore.
`X10VibrationPreviewTest` covers hold and stop frames. These simulated checks
complement the physical observations; they cannot sense motors.
