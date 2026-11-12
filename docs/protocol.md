# KRLY controller protocol

This reference describes the **KRLY / QMacro configuration channel implemented
for the EasySMX X10**. It documents the current codec, transport and command
sequences for interoperability. It is not a specification for every EasySMX
controller or firmware revision.

The implementation derives from reverse engineering of the vendor companion
protocol. This guide describes the resulting behavior, not the process used to
obtain it. See [third-party credits](../THIRD_PARTY.md) for provenance.

**KeyLinker is a different protocol.** Its UUIDs, CRC and scrambling do not apply
to these frames. The X10 artwork shared with x20ctl does not imply shared
configuration support.

Jump to [transport](#ble-transport), [frames and sessions](#frame-format-and-command-sessions),
[commands and key codes](#implemented-commands), [motor vibration](#motor-vibration),
[battery](#battery-responses), [paged readback](#paged-readback), [versions](#firmware-information),
[profile writes](#write-examples-and-profile-application), [calibration](#calibration-and-async-events)
or [validation limits](#evidence-and-validation-limits).

## Implementation map

| Component | Responsibility |
| --- | --- |
| [KrlyCodec.kt](../protocol/src/main/kotlin/br/dev/edgard/easygamepad/protocol/KrlyCodec.kt) | Encoding, stream decoding, battery, firmware, calibration and mapping pages |
| [Models.kt](../protocol/src/main/kotlin/br/dev/edgard/easygamepad/protocol/Models.kt) | GATT defaults, capabilities, key codes and value validation |
| [ControllerSettings.kt](../protocol/src/main/kotlin/br/dev/edgard/easygamepad/protocol/ControllerSettings.kt) | Settings pages and length-delimited sections |
| [CommandSession.kt](../protocol/src/main/kotlin/br/dev/edgard/easygamepad/protocol/CommandSession.kt) | Serialized requests, matching, ACKs and events |
| [BleClient.kt](../app/src/main/kotlin/br/dev/edgard/easygamepad/ble/BleClient.kt) | Android discovery, subscriptions, chunk writes and connection lifecycle |
| [X10PaddleMacros.kt](../protocol/src/main/kotlin/br/dev/edgard/easygamepad/protocol/X10PaddleMacros.kt) | Rear paddle slot decode, merge, editor transitions and clear encoding |
| [X10UploadPlan.kt](../protocol/src/main/kotlin/br/dev/edgard/easygamepad/protocol/X10UploadPlan.kt) | Ordered X10 upload: rear slots on command 58, face mappings on 61 |
| [X10ProfileClient.kt](../protocol/src/main/kotlin/br/dev/edgard/easygamepad/protocol/X10ProfileClient.kt) | Upload, read-back over queries 119/80/121 and apply comparison |
| [ControllerRepository.kt](../app/src/main/kotlin/br/dev/edgard/easygamepad/data/ControllerRepository.kt) | Feature operations and profile application order |

The examples below use unsigned hexadecimal bytes. Array offsets are zero-based;
slice end offsets are exclusive. Command tables also show decimal identifiers.
Illustrative requests assume a fresh session, with sequence byte `00`.

## BLE transport

| Role | UUID |
| --- | --- |
| Configuration service | `0000ff00-0000-1000-8000-00805f9b34fb` |
| Command write | `0000ff01-0000-1000-8000-00805f9b34fb` |
| Response / event notification | `0000ff02-0000-1000-8000-00805f9b34fb` |
| Client characteristic configuration descriptor (CCCD) | `00002902-0000-1000-8000-00805f9b34fb` |

The app discovers the selected service and characteristics and verifies write and
notify/indicate properties. It enables notifications or indications through the
CCCD. Only successful descriptor-write completion moves the connection to `READY`.
The gameplay HID connection is independent of this GATT session.

Writes are serialized and split into chunks of **at most 20 bytes**, regardless
of a larger negotiated MTU. The client prefers writes with response when the
characteristic supports them, otherwise uses writes without response. It awaits
the Android write callback for each chunk. GATT completion and a KRLY ACK are
separate confirmations.

Notifications form a byte stream: one callback may contain a partial frame,
one complete frame or multiple frames. Reassemble using the leading length byte,
not callback boundaries. `FrameDecoder` retains incomplete frames, extracts
complete frames and skips leading length values below 3. It clears buffered data
on session close; it does not supply a checksum or general corruption recovery.

| Timer | Current implementation |
| --- | --- |
| User scan window | 12 seconds |
| Connection setup | 18 seconds |
| Individual GATT chunk write | 4 seconds |
| Command request, including writing and awaiting a response | 5 seconds |
| Battery polling | Immediately on ready, then every 180 seconds; skipped during calibration |
| Calibration session | 90 seconds |

Connection errors and notification-buffer overflow fail the link. See the
[development guide](development.md#connections-and-device-identity) for foreground
reconnection and device selection.

## Frame format and command sessions

```text
[totalLength, commandId, payload..., sequence]
```

| Offset | Field | Meaning |
| --- | --- | --- |
| 0 | Total length | Includes header, payload and final sequence byte |
| 1 | Command ID | Unsigned byte |
| 2 through length − 2 | Payload | Command-specific bytes, possibly empty |
| length − 1 | Sequence | Incrementing unsigned byte, wrapping after 255 |

`FrameEncoder` permits IDs 0–255 and payloads up to 252 bytes: a frame is at most
255 bytes. A new `CommandSession` has a new encoder beginning at sequence 0.
The implemented KRLY framing adds no CRC or scrambling.

`CommandSession` permits one request at a time. It matches replies by **command
ID**, not by an echoed sequence byte. Its acceptance predicate can keep waiting
for more pages or reject an invalid response. Every decoded frame is also emitted
on the session event flow, including replies consumed by a pending request.

For commands using `ack`, response byte 2 must be `00`; a nonzero result fails
the operation. A typical successful mapping ACK is `04 3D 00 SN`.
Query responses instead use their own parsers. `send` writes without awaiting
a command response and is used for calibration controlled through async events.

A timeout, cancellation or exception within `request` closes the session and
aborts the transport. Reconnect before retrying: a late ACK for an earlier command
must not confirm a subsequent request with the same ID. A controller rejection
checked by `ack` stops the operation but does not itself prove a transport failure.

## Implemented commands

Payload bytes in this table exclude length, command ID and sequence.

| Decimal / hex | Operation | Request payload | Completion |
| --- | --- | --- | --- |
| 1 / `01` | Set mode | `10` editing, `00` normal | ACK |
| 5 / `05` | Factory reset | `00` | ACK |
| 49 / `31` | Query battery | Empty | Battery response; also unsolicited events |
| 52 / `34` | End / commit profile upload | Empty | ACK |
| 53 / `35` | Motor intensities | Left, right | ACK |
| 54 / `36` | Initialize profile upload | `00 00` | ACK |
| 55 / `37` | Turbo entry | Key, automatic flag, rate | ACK |
| 57 / `39` | Trigger ranges | Left min, left max, right min, right max | ACK |
| 58 / `3A` | Macro step | Trigger, repeat flag, index, hold hi/lo, wait hi/lo, key | ACK |
| 61 / `3D` | Button mapping | Source key, target key | ACK |
| 62 / `3E` | Stick ranges / inversion | Left min/max/invert X/invert Y, then right equivalents | ACK |
| 65 / `41` | Start self-calibration | Empty | ACK; result later through event 67 or 73 |
| 66 / `42` | Stop self-calibration | Empty | ACK |
| 69 / `45` | X10 motor test | Selected side and intensity; see below | ACK; physical effect checked separately |
| 80 / `50` | Query mappings | `01` | Mapping pages (face buttons; rear may appear) |
| 119 / `77` | Query settings | `02 01` | Settings pages with selector `02` |
| 121 / `79` | Query macro sub-pages | `01 01` | Paddle macro slot blob (M1–M4 in wire format) |
| 120 / `78` | Stick / trigger calibration | Action, type | Async progress and result |
| 132 / `84` | Query version information | `01 00` | Firmware response |

The codec also parses legacy version response **9 / `09`**. The application uses
132 for its version query. IDs 67 / `43` and 73 / `49` are received self-calibration
events, not requests sent by this implementation. Lighting, sleep configuration,
firmware uploads and other vendor commands are outside the implemented command set.

## Motor vibration

Command **53 / `35`** stores the left and right motor intensities, each 0–100,
during profile application. It is separate from the temporary motor test,
command **69 / `45`**, which has three payload bytes:

```text
06 45 P0 P1 P2 SN
```

Owner-supervised checks on one X10 established these payload patterns:

| Requested side | Preview / hold payload (decimal) | Stop follow-up |
| --- | --- | --- |
| Left (LT/LB) | `value, 0, 1` | `0, 0, 0` on slider release |
| Right (RT/RB) | `0, value, 2` | `0, 0, 0` on slider release |

In the profile editor, **`X10VibrationPreview`** sends command 69 with the draft
value (0–100) through **`send`** while the user drags a motor slider and sends
the all-zero follow-up when the slider is released. Preview does not write the
profile; command 53 during apply stores intensities.

**`X10VibrationTest`** is the diagnostic path: ACK on the start command, 500 ms
wait, all-zero follow-up, and settings read-back (commands 119 and 80) before and
after. It attempts the all-zero follow-up in cancellation cleanup if the session
is still open; link loss can prevent it. The physical force scale, other
motor-selector values and cause of stopping remain unconfirmed. See
[X10 vibration test findings](x10-vibration-investigation.md) for the physical
observations and validation limits.

## Key codes

These are vendor key codes, not Android `KeyEvent` codes or USB HID usages.

| Keys | Decimal codes | Hex codes |
| --- | --- | --- |
| A, B, X, Y | 160, 161, 162, 163 | `A0 A1 A2 A3` |
| LB, LT, L3 | 164, 165, 166 | `A4 A5 A6` |
| RB, RT, R3 | 167, 168, 169 | `A7 A8 A9` |
| View / Select, Menu / Start | 170, 171 | `AA AB` |
| Left, Right, Up, Down | 209, 210, 211, 212 | `D1 D2 D3 D4` |
| M1, M2, M3, M4 | 1, 2, 3, 4 | `01 02 03 04` |

The X10 UI offers its physical M1/M2 paddles. The model retains M3/M4 values for
existing data, without claiming the X10 has four paddles. Mapping targets and
macro step outputs use regular output keys, not M1–M4.

### Settings values

Ranges use percentages: minimum 0–99, maximum 1–100, and minimum strictly less
than maximum. Inversion and automatic-turbo flags use 0 or 1. Motor intensities
are 0–100. Turbo rate is 1–20 in protocol units.

Each macro has 1–32 steps and a unique trigger. Step hold duration is 1–65,535 ms;
wait duration is 0–65,535 ms. Both durations use **big-endian unsigned 16-bit**
values. Step indices start at 0. The repeat byte is inverted: **0 means repeat**,
**1 means play once**. Turbo entries must have distinct keys.

## Battery responses

Send `03 31 00` for the first battery query in a fresh session. Both solicited
and unsolicited responses with command `31` use these raw-frame offsets:

| Offset | Interpretation |
| --- | --- |
| 2 | Additional display field, not used as the app's battery percentage |
| 3 | Charging: nonzero is true |
| 4 | Additional charging-display field, not used as the main percentage |
| 5 | Actual battery percentage, 0–100 |
| 6 | Low-battery flag: nonzero is true |

The parser requires at least 7 raw bytes, ID 49 and a valid percentage at offset 5.
Do not substitute the values at offsets 2 or 4 for offset 5.

Real response covered by the codec tests:

```text
08 31 01 00 01 3C 00 00
               ^^ 60 percent
```

Charging and low-battery flags are both false. The app timestamps receipt locally.

## Paged readback

Paging is an application-protocol layer above BLE frame reassembly. Assemble
complete frames first, then collect page content, excluding each frame's sequence.
The parsers accept an initial page of 0 or 1, require consecutive pages and a
stable final-page field, and finish when current page equals final page. Treat
that field as the final index when using zero-based pages.

### Mapping query: 80 / `50`

First request: `04 50 01 00`.

```text
[length, 50, finalPage, currentPage, pageContent..., sequence]
```

Concatenate `raw[4 .. length−1)` from each page. The content starts with a **byte
count**, followed by triples:

```text
[byteCount, kind, source, target, kind, source, target, ...]
```

`byteCount` must be divisible by 3. Only kind `00` and recognized source/target
codes are included; other kinds or unknown codes are skipped. At least
`byteCount + 1` content bytes must be present. The parser limits the final-page
field to 0–64 and accumulated content to 1,024 bytes.

Real empty-mapping reply: `06 50 01 01 00 00`.
A synthetic two-page example from the tests:

```text
08 50 02 01 06 00 01 09
09 50 02 02 A0 00 A1 A2 0A
```

The content is `06 00 01 A0 00 A1 A2`: M1 → A and B → X. The final `09` and `0A`
in their respective frames are sequence bytes, excluded from content.

Query 80 can still list M1/M2 remaps, but **`X10ProfileClient` treats query 121
as authoritative for rear paddles**. When a slot in the 121 blob is cleared,
disabled, a passthrough remap or a multi-step macro, it overrides any matching
M1/M2 entry from query 80 during merge.

### Macro sub-pages: 121 / `79`

First request: `05 79 01 01 00` (payload `01 01`).

```text
[length, 79, finalPage, currentPage, pageContent..., sequence]
```

`MacroSubPages` uses the same consecutive-page rules as mapping query 80, with
command byte `121` (`0x79`). Concatenate `raw[4 .. length−1)` from each page.

The combined blob is a sequence of **paddle slots**. Each slot begins with a
7-byte header:

| Header offset | Field |
| --- | --- |
| 0 | Trigger code (M1–M4: `01`–`04`) |
| 1 | Repeat flag (inverted: `00` repeat, `01` play once) |
| 2–5 | Not interpreted by the current decoder |
| 6 | Step count (`00` cleared/disabled, `01` passthrough or macro) |

For a positive step count, step records follow: step index, hold (16-bit BE),
delay (16-bit BE), key count, then key bytes. The compact fixture below stores
the output key directly in the key-count position instead.
**`X10PaddleMacroCodec`** classifies
each slot:

- **Empty / cleared** (`step count 0`, or one step with zero keys): adds the
  trigger to `disabledRearPaddles`.
- **Passthrough** (one step, hold `0`, delay `0`–`1`, one output key): rear remap
  `trigger → key` (the compact factory fixture decodes with delay `1`).
- **Macro** (any other non-empty pattern): converted to a `Macro` for that trigger.

The codec walks the blob sequentially; if a header cannot be parsed it advances
one byte. Truncated blobs stop without hanging (see tests). For malformed step
headers it accepts **compact** key lists (`keyCount > 8`) and a **legacy**
single-byte fallback when the key list would run past the buffer.

Factory passthrough examples from
[`X10PaddleMacroTest`](../protocol/src/test/kotlin/br/dev/edgard/easygamepad/protocol/X10PaddleMacroTest.kt):

```text
01 01 00 00 00 00 01 00 00 00 00 01 A0   # M1 → A (play once)
02 01 00 00 00 00 01 00 00 00 00 01 A1   # M2 → B
```

Cleared slot (disabled paddle), header only:

```text
01 01 00 00 00 00 00                     # M1 disabled (7 bytes total)
```

### Settings query: 119 / `77`

First request: `05 77 02 01 00`.

```text
[length, 77, 02, finalPage, currentPage, pageContent..., sequence]
```

Concatenate `raw[5 .. length−1)`. `SettingsPages` verifies the frame length,
selector `02`, page order and final-page field (0–64), with a 4,096-byte content
limit. The combined content contains exactly four length-delimited sections:

| Section | Data after its one-byte length prefix |
| --- | --- |
| Turbo | Zero or more triples: key, automatic flag, rate |
| Triggers | Exactly 4 bytes: LT min/max, RT min/max |
| Sticks | Exactly 9 bytes: left min/max/invert X/invert Y, right equivalents, swap mode |
| Motors | Exactly 2 bytes: left, right intensity |

Swap mode must be 0; complete-stick/D-pad swapping is not supported by the current
editor. Invalid ranges, nonboolean flags, duplicate turbo keys, truncated sections
or trailing content fail decoding rather than silently substituting defaults.

The existing hardware fixture consists of these two complete frames:

```text
14 77 02 02 01 00 04 03 61 03 61 09 05 64 00 00 05 64 00 01
0B 77 02 02 02 00 00 02 64 64 01
```

After excluding headers and sequence bytes, the sections are:

```text
00                             # no turbo entries
04 03 61 03 61                 # LT and RT: 3..97
09 05 64 00 00 05 64 00 00 00  # both sticks: 5..100, no inversion or swap
02 64 64                       # both motors: 100
```

The app reads command **119**, then **80**, then **121**, before merging the
result into the draft. Query 119 supplies sticks, triggers, vibration and turbo.
Query 80 supplies face-button mappings. Query 121 supplies **M1/M2** remaps,
rear-paddle macros and disabled-paddle state; `mergeFromSlots` merges face plus
rear mappings and drops stale M1/M2 rows from query 80 when 121 disagrees.

`ControllerSettings.mergeInto` replaces mappings, ranges, vibration, turbo, rear
macros and `disabledRearPaddles`, while keeping the local profile name and any
**M3/M4** macro sequences still present only in the draft (the X10 UI edits
physical M1/M2 only; the model allows M1–M4 for import/validation). This is still
not a guaranteed full backup of every onboard rule across power cycles.

## Firmware information

First request: `05 84 01 00 00`. Values are concatenated uppercase hexadecimal
byte strings, not interpreted as semantic version numbers.

| Field | Command 132 slice | Legacy command 9 slice |
| --- | --- | --- |
| Project | `[4, 7)` | `[2, 5)` |
| Protocol | `[7, 10)` | `[5, 8)` |
| Firmware | `[10, 14)` | `[8, 11)` |
| Hardware | `[14, 17)` | `[11, 14)` when the optional bytes are present |

Real response fixture:

```text
12 84 01 00 56 02 87 03 00 40 02 40 71 9A 01 00 A0 02
```

Decoded values: project `560287`, protocol `030040`, firmware `0240719A`,
hardware `0100A0`. The firmware field here has four bytes, unlike legacy command 9.

## Write examples and profile application

Each line below is an independent encoding example with sequence `00`:

```text
0B 3A 01 01 00 00 00 00 00 A0 00         # M1 play-once passthrough to A (cmd 58)
0B 3A 02 00 00 01 2C 03 E8 A2 00         # M2 repeat, step 0: X held 300 ms, wait 1000 ms
0B 3E 05 5A 01 00 03 5F 00 01 00         # sticks: left 5..90/invert X; right 3..95/invert Y
05 3D A1 A0 00                           # map B to A (cmd 61; face buttons only)
0A 3A 01 01 00 00 00 00 00 00            # disable / clear M1 slot (7-byte payload)
```

Rear paddles **do not** use command 61. Sending `61` with M1/M2 as the source
duplicated face-button output in early testing
([issue #3](https://github.com/edgardmessias/easygamepad/issues/3)); the
implemented route overwrites each rear **macro slot** with command **58**
instead. Simple M1/M2 remaps use `KrlyCodec.rearPaddlePassthrough` (8-byte
payload matching the factory passthrough frame). Explicit disable / cleared slots
use `X10PaddleMacroCodec.clearStep` (**7 bytes**: `[trigger, 1, 0, 0, 0, 0, 0]`).
Multi-step rear macros use the usual command 58 macro-step encoding. Do not assume
an 8-byte clear variant; only the 7-byte clear encoding is implemented and tested.

The app validates the complete `ControllerConfig` before uploading and sends
commands in this order, awaiting each ACK:

1. Command 1 with `10`: enter editing mode.
2. Command 54 with `00 00`: initialize the upload.
3. Command 58 for macro steps in profile list order, followed by M1/M2
   passthrough remaps or clear/disable writes as applicable.
4. Command 55 for each turbo entry.
5. Command 57 for the trigger ranges, then 62 for the sticks.
6. Command 61 for remaining mappings, with physical M1/M2 sources excluded.
   M3/M4 model/import support does not establish hardware support on the X10.
7. Command 53 for both motor intensities. This stores the two levels; it does not pulse the motors. See [Motor vibration](#motor-vibration).
8. Command 52 with no payload: end/commit the upload.
9. Command 1 with `00`: restore normal mode.
10. Queries **119**, **80** and **121**: read back and merge before reporting.

`X10UploadPlan` omits face and rear assignments that are absent from the draft;
those omissions **leave onboard state untouched**. **`disabledRearPaddles`** and
explicit rear remaps/macros **do** generate command 58 writes, including the
7-byte clear pattern, which disables the paddle on the controller.

`X10ProfileClient` reports `Matched`, `Mismatch` (with the differing field groups),
or `Unconfirmed` when the upload was accepted but read-back failed. Comparison
covers face and rear button mappings, **M1/M2 rear macros**, **`disabledRearPaddles`**,
sticks, triggers, motor intensity and turbo. It normalizes omitted identity
mappings and ignores turbo ordering. A match compares **readable configuration**,
not physical macro playback, repeat timing, or persistence after power-off. Only
a match records a successful read-back timestamp. See
[X10 read-back validation](x10-readback-validation.md) for automated and limited
hardware evidence.

Upload stops on the first failure. Cleanup attempts to restore normal mode when
the link remains ready, including after rejection or cancellation. There is no
transaction or rollback; earlier writes may already have taken effect. Omitting
a face mapping, turbo or unconfigured rear paddle does not erase an existing onboard
rule. Factory reset uses its separate command 5 with payload `00`.

## Calibration and async events

For command 120, action 1 starts and action 2 stops; type 1 selects sticks and
type 2 selects triggers:

```text
05 78 01 01 00  # start stick calibration
05 78 02 01 00  # stop stick calibration
05 78 01 02 00  # start trigger calibration
05 78 02 02 00  # stop trigger calibration
```

These use `send`, not an awaited ACK. While a matching calibration session is
active, event 120 interprets raw offset 2 as 1 = active, 2 = needs retry,
3 = succeeded; offset 3 is retained as the result value. Other positions leave
the phase unchanged.

Self-calibration starts with command 65 and stops with 66, both without payload
and both awaiting ACK. Its result arrives through event 67 or 73: offset 2 equal
to 0 means success, otherwise failure. Self-calibration events do not complete
stick/trigger sessions, or vice versa. Events are ignored when no session needs
stopping.

Success does not clear `stopRequired`: finishing still sends the stop operation.
A stop alone does not establish success. Timeout attempts a stop after 90 seconds;
disconnect marks an unfinished calibration failed. A lost radio link can prevent
cleanup, so reconnecting is not proof that firmware left its special mode.

## Evidence and validation limits

[Codec tests](../protocol/src/test/kotlin/br/dev/edgard/easygamepad/protocol/KrlyCodecTest.kt)
include real battery, empty-mapping and firmware fixtures, plus synthetic edge
cases. [Settings tests](../protocol/src/test/kotlin/br/dev/edgard/easygamepad/protocol/ControllerSettingsTest.kt)
include a real two-frame settings fixture.
[Session tests](../protocol/src/test/kotlin/br/dev/edgard/easygamepad/protocol/CommandSessionTest.kt)
exercise matching, delayed responses, cancellation and timeouts with fake transport.

Physical read and input evidence does not validate every write, erase operation,
calibration path or firmware revision. Record **ACK**, **readback**, **observed
behavior** and **persistence after a controller restart** separately. Follow the
[hardware checklist](development.md#hardware-validation) before expanding support
or claiming an operation has been verified on hardware.
