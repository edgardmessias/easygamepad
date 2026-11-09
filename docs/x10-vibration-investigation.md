# X10 vibration testing

## Android input-device route

The maintainer reported the unsupported-vibration notice on 2026-11-09. The
connected Android 15 phone exposes the X10 gameplay interface as an Xbox Wireless
Controller. Its input mapper and kernel event capabilities provide buttons and
axes but no vibrator/force feedback. This finding applies to this connection
mode and phone; the advertised Xbox name does not establish vibration support.

EasyGamepad queries only the selected gameplay device. Android 12 and later use
that device's `VibratorManager`; earlier releases use its legacy vibrator. The
test stays disabled with an explanation when Android exposes no vibrator or no
gameplay device is selected. It does not redirect a test to the phone or another
controller. See the [Android InputDevice API](https://developer.android.com/reference/android/view/InputDevice#getVibratorManager()).

## KRLY candidate

The local interoperability reference for companion package `com.smx.platform`
version 1.05 identifies command **69 / 0x45**, named `ForceVibrationTestReq` /
`CMD_TEST_VIBRATION_FORCE`. It accepts three payload bytes. Its KRLY frame shape
is therefore:

```text
06 45 p0 p1 p2 sequence
```

The available reference does not identify the three parameters' meanings or
units, and no calling screen or physical X10 trace establishes suitable values.
In particular, left intensity, right intensity, duration, motor selection and a
stop action must not be inferred from the request name. An ACK alone would not
prove which motor moved, for how long, or whether settings stayed unchanged.

No command 69 is currently sent by EasyGamepad. Before enabling this route,
establish the payload semantics and a bounded start/stop test, tell the
maintainer the exact physical test required and wait for them, then record the
controller/firmware, observed motor behavior and unchanged settings. The existing
command 53 changes configured motor intensities; it is not a proven substitute
for a temporary pulse test.
