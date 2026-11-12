# Demo X10 controller (UI testing)

EasyGamepad can run without physical hardware using the **Demo X10** entry in the
controller list (after saved/real devices). It exposes the same KRLY configuration
flows as a real EasySMX X10 through an in-process simulator. Bluetooth is not used
for this entry.

The demo uses a reserved locally administered address (`02:00:5E:00:53:01`). The app
never opens that address on the radio; [ConfigurationLinkHub](../app/src/main/kotlin/br/dev/edgard/easygamepad/demo/ConfigurationLinkHub.kt)
routes traffic only while the demo controller is selected.

## Limitations

- X20 / X20 Pro (KeyLinker) are not simulated.
- Android gameplay vibration (`rumble()` on the tester tab) is not available on the
  synthetic input device; X10 motor preview through KRLY still works.
- Power-off persistence and real calibration completion are not modeled.
- ADB commands are accepted only in **debug APK** builds, and input/simulator commands
  require the **Demo X10** selection with an active demo configuration link.

## ADB interface (debug APK only)

The debug build registers a broadcast receiver for action
`br.dev.edgard.easygamepad.demo.COMMAND`.

- `connect` / `disconnect` / `factory_reset` are routed through the same
  [HubViewModel](../app/src/main/kotlin/br/dev/edgard/easygamepad/HubViewModel.kt)
  lifecycle as the UI (manual disconnect is honoured; real BLE sessions are not
  torn down by demo disconnect).
- **ADB connect** is allowed only after persisted selection restore finished, when no
  physical controller is selected (welcome screen auto-selects demo) or when **Demo X10**
  is already selected, and while no real BLE configuration session is in progress
  (`DemoAdbLifecyclePolicy`, `HubSelectionStartupPolicy`).
- **Demo disconnect** does not call `disconnectAll()`; an open real BLE session stays
  untouched when the user works on a physical controller.
- Button, axis and battery commands are ignored unless the demo link is `READY`.

General form:

```text
adb shell am broadcast -a br.dev.edgard.easygamepad.demo.COMMAND --es cmd <command> [extras...]
```

### Link control

Select **Demo X10** in the app (or call `connect` after the app process is running
so the ViewModel can register the ADB coordinator):

```text
adb shell am broadcast -a br.dev.edgard.easygamepad.demo.COMMAND --es cmd connect
adb shell am broadcast -a br.dev.edgard.easygamepad.demo.COMMAND --es cmd disconnect
adb shell am broadcast -a br.dev.edgard.easygamepad.demo.COMMAND --es cmd factory_reset
```

`disconnect` keeps the demo offline until you connect again from the UI or ADB.
`factory_reset` clears the in-process simulator (same as power-on defaults in the
simulator) and triggers a KRLY battery read in the app. This is **not** the same as
the **Reset** tool in the X10 profile editor, which sends KRLY command 5 to a
physical controller and clears applied-at timestamps on the phone.

### Button tester (synthetic gameplay input)

Requires demo selected and link ready:

```text
adb shell am broadcast -a br.dev.edgard.easygamepad.demo.COMMAND --es cmd press --es button A
adb shell am broadcast -a br.dev.edgard.easygamepad.demo.COMMAND --es cmd release --es button A
adb shell am broadcast -a br.dev.edgard.easygamepad.demo.COMMAND --es cmd press --es button M1
```

Supported `button` labels include `A`, `B`, `X`, `Y`, `LB`, `RB`, `LT`, `RT`, `L3`, `R3`,
`VIEW`, `MENU`, D-pad directions, and `M1`–`M4`.

### Sticks (axis values −1.0 … 1.0)

```text
adb shell am broadcast -a br.dev.edgard.easygamepad.demo.COMMAND --es cmd axis --es stick left --ef x 0.75 --ef y -0.25
adb shell am broadcast -a br.dev.edgard.easygamepad.demo.COMMAND --es cmd axis --es stick right --ef x 0 --ef y 0
```

### Simulated BLE battery read

```text
adb shell am broadcast -a br.dev.edgard.easygamepad.demo.COMMAND --es cmd battery --ei percent 42 --es charging true
adb shell am broadcast -a br.dev.edgard.easygamepad.demo.COMMAND --es cmd battery --ei percent 15 --es charging false --es low true
```

Battery extras update the simulator and trigger an immediate KRLY command 49 refresh
when the demo link is active. You can also use **Refresh battery** in the app.

## Suggested manual test flow

1. Open the controller sheet and explicitly select **Demo X10**, then **Connect demo**
   (no Bluetooth sheet).
2. Open **Profiles**, change values, **Read** and **Apply** — expect matched read-back.
3. Open **Tester** and drive buttons/sticks via ADB `press` / `axis`.
4. `adb … disconnect`, confirm the demo stays offline and foreground reconnect does not
   auto-connect within 1.5s.
5. Switch to a saved real controller — demo commands must no longer affect the UI.
