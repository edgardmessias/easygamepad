# BLE scan and connection — official app evidence and EasyGamepad changes

This note records what was extracted from the patched official EasySMX app
(`com.smx.platform` 1.06 under the canonical repo `C:/dev/edgard/easygamepad/.tmp/`)
and how EasyGamepad applies it. No TLS bypass from the official patch was copied.
Heuristics here are **independently written** (GPL EasyGamepad); we did not copy
proprietary smali/Java from the APK.

## Evidence sources (official)

| Topic | Source |
| --- | --- |
| GATT UUIDs | `jadx-…/com/smx/platform/util/Constants.java` — `CMD_SERVICE_ID` / `CMD_CHARACTERISTIC_ID` / `CMD_NOTIFICATION_CHARACTERISTIC_ID` → `0000ff00` / `ff01` / `ff02` (same as [Models.kt](../protocol/src/main/kotlin/br/dev/edgard/easygamepad/protocol/Models.kt)); catalog `Equipment.serviceId` is set but connect uses these constants, not per-row GATT from the API |
| LE scan, no ADV service filter | `jadx-…/com/inuker/bluetooth/library/search/le/BluetoothLESearcher.java` — `startLeScan(mLeScanCallback)` with **no UUID array** |
| Scan cadence | `jadx-…/com/smx/platform/ui/equipment/ConnectingViewModel.java` — `SearchRequest.Builder().searchBluetoothLeDevice(2000, 2).searchBluetoothClassicDevice(2000).searchBluetoothLeDevice(2000)`; first name match calls `connectGatt` then `stopSearch` (auto-connect; we do **not** copy that) |
| Patch vs stock JADX | Patched `apktool-…/smali/…/ConnectingViewModel$1.smali` widens device types 0–3; patched `TxtUtil.smali` `matchesEquipmentName` treats **qmacro** substring and X10 catalog ↔ **Xbox** name — risk of wrong radio; **not ported** |
| Immediate list seed | `BluetoothSearchRequest.notifyConnectedBluetoothLeDevices()` before passive scan (inuker library) — **connected GATT only**; we also list bonded non-classic for UX |
| Connect GATT | `BleConnectWorker.openGatt` — `connectGatt(context, false, callback, 2)` (LE transport); `BLEManager` 30s connect / 20s discover / retries — **not copied** (EasyGamepad keeps 18s ready timeout) |
| Field log | `.tmp/logcat-session-full.txt` — `EasySMX-BLE: scan hit name=QMacro` and Xbox ~8ms later; **no** end-to-end timing proof on EasyGamepad hardware |

## Grok 4.7 review — objections addressed

| Objection | Resolution |
| --- | --- |
| Do not filter scan on `ff00` | No `ScanFilter` on service UUID; `BleDiscovery.advertisesKrlyService` is debug logging only |
| Do not auto-connect or match Xbox like patch | No auto-`connectGatt`; `configurationChannelName` excludes Xbox; user still selects row |
| Do not broaden Classic scan like patch | Still seed bonded **non-classic** + GATT-connected only; no classic discovery pass |
| Do not copy proprietary matching code | [BleDiscovery.kt](../app/src/main/kotlin/br/dev/edgard/easygamepad/ble/BleDiscovery.kt) uses our own hint list / sort keys |
| `getRemoteDevice` vs scan latency | `resolveConnectDevice`: fresh scan cache (30s, **scan callbacks only**) → **6s** address-filtered discovery → bonded non-classic → GATT-connected → `getRemoteDevice` |
| Seed must not fake scan cache | UI seed ([seedScanDevices](../app/src/main/kotlin/br/dev/edgard/easygamepad/ble/BleClient.kt)) never writes `observed`; only `ScanResult` paths refresh connect identity |
| Location on API 31+ | `BLUETOOTH_SCAN` with `neverForLocation` still allows LE scan when location is off; `locationEnabled()` is always true on API 31+ and address discovery is not skipped for that reason. Pre-12 still needs location on for passive scan (unchanged). |
| No measured speed claims | None in app strings or this doc; no claim of fixed Xiaomi scanner start latency |

## EasyGamepad implementation

| Area | Change |
| --- | --- |
| [BleDiscovery.kt](../app/src/main/kotlin/br/dev/edgard/easygamepad/ble/BleDiscovery.kt) | Name heuristics and optional `ff00` ADV hint (non-filtering) |
| [BleConnectCache.kt](../app/src/main/kotlin/br/dev/edgard/easygamepad/ble/BleConnectCache.kt) | Fresh-scan window and address-discovery timeout constants; `mergeFoundDevice` for seed rows |
| [BleClient.kt](../app/src/main/kotlin/br/dev/edgard/easygamepad/ble/BleClient.kt) | GATT-connected + bonded UI seed; aggressive `ScanSettings` (API 23+); QMacro-first sort; connect resolution order above; `CONNECTION_PRIORITY_HIGH` after GATT connect |
| [ForegroundReconnectPolicy.kt](../app/src/main/kotlin/br/dev/edgard/easygamepad/data/ForegroundReconnectPolicy.kt) | Idle ready link stays up on background; calibration/macro upload disconnect; foreground retry only when `DISCONNECTED` |
| Tests | [BleDiscoveryTest.kt](../app/src/test/kotlin/br/dev/edgard/easygamepad/ble/BleDiscoveryTest.kt), [BleConnectCacheTest.kt](../app/src/test/kotlin/br/dev/edgard/easygamepad/ble/BleConnectCacheTest.kt), [ForegroundReconnectPolicyTest.kt](../app/src/test/kotlin/br/dev/edgard/easygamepad/data/ForegroundReconnectPolicyTest.kt) |

## Verification

```powershell
.\gradlew.bat :protocol:test :app:lintDebug :app:testDebugUnitTest
```

## Residual limits

- QMacro may be absent from the list until the first ADV callback; seeding helps when the link is already GATT-connected (e.g. after official app or prior session).
- Advertisement service UUID presence on X10 firmware variants unknown.
- `requestConnectionPriority(HIGH)` effect varies by OEM stack.
- Parallel `adb logcat` (`EasySMX-BLE` vs `EasyGamepad`) still required for real latency comparison.
