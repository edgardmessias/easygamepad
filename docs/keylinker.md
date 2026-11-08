# KeyLinker protocol (EasySMX X20 and X20 Pro)

This reference describes the **KeyLinker configuration channel** EasyGamepad uses
for the EasySMX X20 and X20 Pro. It is a separate protocol from the X10's
[KRLY / QMacro channel](protocol.md): different GATT service, framing, checksum and
commands. The two are never mixed on one link.

The implementation is a Kotlin port of [x20ctl](https://github.com/AmjadAAYD/x20ctl)
(`x20ctl/protocol.py`, `client.py`, `validation.py` and the `desktop-dotnet`
codec) at revision `aac5461`. Byte-level test vectors were produced by running that
upstream code. See [third-party credits](../THIRD_PARTY.md) for provenance.

**Status on Android: identification, reads, and the writes x20ctl proved.** Editors show
the exact frames first; after the user confirms, writes that x20ctl recorded with a
read-back on the same product (remaps, stick and trigger curves, vibration, idle timeout,
macros) are sent the x20ctl way: re-read the record and stop if it changed, send each frame
(250 ms between chunks), then read it back and report Confirmed, Mismatch or Unconfirmed.
These writes have **not** yet been run from Android on a real controller. Calibration,
factory reset, lighting, turbo and firmware stay unavailable.

Jump to [implementation map](#implementation-map), [connection rules](#connection-rules),
[framing](#frame-format), [requests](#requests), [records](#records),
[evidence and write gate](#evidence-and-write-gate) or [support matrix](#support-matrix).

## Implementation map

| Component | Responsibility |
| --- | --- |
| [KeyLinkerCodec.kt](../protocol/src/main/kotlin/br/dev/edgard/easygamepad/protocol/keylinker/KeyLinkerCodec.kt) | UUIDs, CRC-8, scrambling, frame build/parse, length and serial counters |
| [KeyLinkerRecords.kt](../protocol/src/main/kotlin/br/dev/edgard/easygamepad/protocol/keylinker/KeyLinkerRecords.kt) | Identity, updater id, capabilities, battery, curves, motor, key lists, remaps, macros, lighting |
| [KeyLinkerEvidence.kt](../protocol/src/main/kotlin/br/dev/edgard/easygamepad/protocol/keylinker/KeyLinkerEvidence.kt) | Known models, classification, request classification, write decisions |
| [KeyLinkerSession.kt](../protocol/src/main/kotlin/br/dev/edgard/easygamepad/protocol/keylinker/KeyLinkerSession.kt) | One exchange at a time, reply matching, authorization gate, client, write plans and read-back apply |
| [KeyLinkerRepository.kt](../app/src/main/kotlin/br/dev/edgard/easygamepad/data/KeyLinkerRepository.kt) | Android lifecycle: identify on link ready, battery polling, reads, previews |
| [KeyLinkerScreen.kt](../app/src/main/kotlin/br/dev/edgard/easygamepad/ui/KeyLinkerScreen.kt) | Identification, settings, curve graphs, write review and send |

## Connection rules

- The person picks the model (**X10**, **X20** or **X20 Pro**) when adding a
  controller. The model decides the protocol; an advertised name never does.
- The KeyLinker link uses fixed UUIDs. The advanced GATT UUIDs in Settings apply to
  the X10 channel only.
- `BleClient` subscribes to notifications and sends nothing itself. Only after the
  link is `READY` with protocol `KEYLINKER` does `KeyLinkerRepository` start.
- `ControllerRepository` (KRLY) acts only on links opened for `KRLY`, so a KeyLinker
  controller never receives KRLY battery polls, and vice versa.
- Saved X20 entries keep `model` in the existing `controllers` JSON. The legacy
  `service` / `write` / `notify` keys keep describing the X10 channel and are not
  overwritten by a KeyLinker controller. No preference key was renamed.

| Role | UUID |
| --- | --- |
| Service | `d7f010e0-660d-46e9-96c3-19c4148bdab5` |
| Write | `d7f010e1-660d-46e9-96c3-19c4148bdab5` |
| Notify | `d7f010e2-660d-46e9-96c3-19c4148bdab5` |

## Frame format

Plain frame, at most 20 bytes:

| Offset | Field |
| --- | --- |
| 0 | Opcode |
| 1 | Length field: low 5 bits = payload length + 5; top 3 bits carry a counter or macro slot |
| 2 | Serial |
| 3 | Nonce |
| 4…n−2 | Payload |
| n−1 | CRC-8 (reflected polynomial `0xEB`, initial value 0) over bytes 0…n−2 |

On the wire the frame is scrambled with the vendor's two XOR tables (the serial key
offset depends on bit 1 of the nonce). `KeyLinkerCodec.scramble` / `unscramble`
implement it; tests check it round-trips and matches x20ctl byte for byte.

A reply is accepted only when **all** hold: opcode `0x19` (RESPONSE), CRC valid,
declared length equals the notification size, and serial equals the request's.
Anything else, including the `0x1F` input stream, is ignored.

Read requests use a serial that alternates its high bit (1, 130, 3, 132, …). Writes
use `saveButtonSerial(slot, counter) = parity << 7 | slot << 3 | counter % 8` and a
host length `(counter % 8) << 5 | (length + 5)` with a counter that counts down from 8.

Example, identity request with serial 1 and nonce 0, as sent:
`57 CB 81 C7 5E`.

## Requests

| Opcode | Name | Payload | Kind | Sent by EasyGamepad |
| --- | --- | --- | --- | --- |
| `0x91` | Identity | — | Identify | Yes, after the person chose the controller |
| `0x93` | Updater id | `[position]` | Identify | Yes |
| `0xB0` | Menu | `[position, kind]` (1 capabilities, 3 remap sources, 5 macro keys) | Identify / read | Yes |
| `0x1D` | Set mode (power) | `02 DF AB` | Read (battery) | Yes, followed by `0x1E` |
| `0x1E` | Normal mode | `02 DF AB` | Read | Yes, always after a power query |
| `0xB1` | Sticks | `[position]` | Read | Proven identities only |
| `0xB2` | Triggers | `[position]` | Read | Proven identities only |
| `0xB3` | Motor / idle timeout | `[position]` | Read | Proven identities only |
| `0xB4` | Turbo | `[position]` | Read | Only if exposed (raw bytes shown) |
| `0xB5` | Macro | `[position, slot]` | Read | Proven identities only |
| `0xB6` | Remap | `[position]` | Read | Proven identities only |
| `0xB7` | Lighting | `[position]` | Read | Only if exposed |
| `0x31`–`0x37` | Writes | record | Write | **Never** (preview only) |
| `0x16` | Recover (factory reset) | — | Write | **Never** |
| `0x1D` other payloads | Calibration | — | Write | **Never** |

The power query starts the `0x1F` input stream; `0x1E` is sent afterwards in a
`finally` block, even if the reply was lost.

Every outgoing frame passes `KeyLinkerGate.authorize` before it is written:
identification is allowed, reads need an identity that `mayRead`, writes need
`writeDecision == Allowed`. A refused frame throws `KeyLinkerNotAuthorized` and
nothing reaches the radio.

A read that times out returns "no usable reply" and the session stays open. A write
that times out would be indeterminate, so the session would close and the link be
aborted (unreachable today, because no write is authorized).

## Records

Records start with a byte count; longer records continue at position 1, 2, … (at
most 8 follow-ups). Decoders reject short or malformed data instead of guessing.

| Record | Decoded as |
| --- | --- |
| Identity | VID, PID, firmware `x.yy`; operating mode, sensor and generation only from the 7-byte form |
| Updater id | 14-byte vendor prefix + model code (`2091` X20, `2395` X20 Pro) and version |
| Capabilities | sticks, triggers, motors, turbo, macro slots, remap, lighting, engine bits |
| Battery | `DF AA` reply; level 1–4 from bits 5/6/7, charging bit 4. **No percentage exists** |
| Sticks / triggers | per channel: inner dead zone, outer (stored as max − outer), two curve points, flags |
| Motor | left/right strength (0–255 → %), idle shutdown in 12 000 ticks per minute, `FF FF FF FF` = never |
| Remap | one target per source, `0` = unchanged; View and Menu are targets only |
| Macro | `(entries × 5) + 2`, loop interval, then 5-byte steps (24-bit mask + 5 ms ticks) |
| Lighting | groups of six bytes; only the colour is shown |

Macro reads follow x20ctl's Python client, whose M1–M6 round trips are recorded:
the header counts bytes, and the macro-limits query (menu kind 6) is never sent
first, because doing so switches the header to an entry count.

## Evidence and write gate

`KeyLinkerEvidence.assess` classifies the identity reply:

| Classification | Meaning | Reads | Writes |
| --- | --- | --- | --- |
| Verified | Product, mode and firmware match a hardware record | Yes | Allowed when x20ctl proved write and read-back |
| Known model, unvalidated firmware | Product and mode match; firmware never tested | Yes | Allowed, as x20ctl's `known_product` |
| Known model, unvalidated mode | The mode can change the protocol | Identification only | Blocked |
| Unknown model | Speaks KeyLinker, identity unknown | Identification only | Blocked |

Hardware records (from x20ctl, Windows):

| Model | VID:PID | Mode | Firmware | Updater | Rear buttons |
| --- | --- | --- | --- | --- | --- |
| X20 | `1013:2009` | 2 | 1.23 | 2091 | M1–M4 |
| X20 Pro | `1020:2006` | 6 | 1.44 | 2395 | M1–M6 |

`writeDecision` mirrors x20ctl's `Assessment.may_write`, with two stricter rules: a feature
needs x20ctl's `readback` proof as well as `write` (EasyGamepad always confirms by reading
back), and calibration and factory reset are refused regardless. A write that gets no reply
no longer drops the link; like x20ctl's `_send`, the read-back decides.

## Support matrix

| Feature | X20 (Android) | X20 Pro (Android) | Evidence (x20ctl, Windows) |
| --- | --- | --- | --- |
| Identification | Implemented | Implemented | Recorded replies |
| Battery gauge | Read | Read | Power query recorded |
| Stick curves | Read · preview | Read · preview | Round trip recorded |
| Trigger curves | Read · preview | Read · preview | Round trip recorded |
| Vibration strength | Read · preview | Read · preview | Round trip recorded |
| Idle timeout | Read · preview | Read · preview | Round trip recorded |
| Button remapping | Read · preview | Read · preview | Round trip recorded |
| Macros | Read · preview | Read · preview | M4 (X20), M1–M6 (X20 Pro) recorded |
| Turbo | Not exposed | Raw bytes only | Layout not decoded |
| Lighting | Not exposed | Read colours | First colour only; writes unverified |
| X20 Pro display image | — | Not available | USB serial in update mode, not BLE |
| Calibration | Disabled | Disabled | Disruptive |
| Factory reset | Disabled | Disabled | Disruptive |
| Firmware update | Disabled | Disabled | Out of scope |

"Read" means implemented and unit-tested against recorded replies; it has **not**
been run on a real controller through Android yet. See
[development guide](development.md) for the hardware validation checklist.
