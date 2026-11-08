# Third-party credits

EasyGamepad is distributed under **GPL-3.0-or-later**; see [LICENSE](LICENSE) and
[NOTICE](NOTICE). Third-party components keep their own authors and license terms.

## Reverse engineering (scope of this document)

The **KRLY framing and command set** implemented in the `protocol` module and used
for EasySMX X10–style configuration over BLE is **derived from reverse engineering**
of EasySMX’s official Android companion software (package names **`com.smx.platform`**
and **`com.krly.platform`**, as distributed on [Google Play](https://play.google.com/)).
This file and the rest of the published documentation **name those products and
community references** but **do not** explain tools, workflows, or procedural steps
used to obtain that knowledge.

Separately, **x20ctl** (linked below) is an independent open-source project whose
assets are reused under its license; it is named here for attribution, not as the
sole source of the protocol.

## x20ctl — EasySMX X10 images and geometry

- Project: https://github.com/AmjadAAYD/x20ctl  
- Copyright © 2026 Amjad AAYD  
- Imported revision: `02a4244bd1758d8527fc58e25259903a2840f833` (2026-11-05)  
- License for imported materials: **GPL-3.0-or-later**  
- Full license text: [licenses/x20ctl-GPL-3.0.txt](licenses/x20ctl-GPL-3.0.txt)  
- Original notice: [licenses/x20ctl-NOTICE.txt](licenses/x20ctl-NOTICE.txt)

The following PNGs are unmodified copies from `src/assets/controllers/x10/native/`
in the upstream project:

| Upstream file | Path in EasyGamepad |
| --- | --- |
| `front-body.png` | `app/src/main/res/drawable-nodpi/x10_front_body.png` |
| `rear-full.png` | `app/src/main/res/drawable-nodpi/x10_rear.png` |
| `stick-l.png` | `app/src/main/res/drawable-nodpi/x10_stick_left.png` |
| `stick-r.png` | `app/src/main/res/drawable-nodpi/x10_stick_right.png` |

`app/src/main/assets/controllers/x10-layout.json` combines regions from upstream
`control-shapes.json` and `native-layers.json` in the same 1536×1024 coordinate
space, together with the native PNGs. Compose integration lives in
`app/.../ui/ControllerArt.kt` under the same GPL-3.0-or-later header.

## x20ctl — EasySMX X20 and X20 Pro images, geometry and KeyLinker protocol

- Imported revision: `aac546147aed3a8fb11abe3ff1c67e3e173dcaf3` (2026-11-07)
- Same project, copyright and license as above (GPL-3.0-or-later).

Unmodified PNG copies (byte-identical, 1536×1024):

| Upstream file | Path in EasyGamepad |
| --- | --- |
| `src/assets/controllers/x20/native/front-body.png` | `app/src/main/res/drawable-nodpi/x20_front_body.png` |
| `src/assets/controllers/x20/native/rear-full.png` | `app/src/main/res/drawable-nodpi/x20_rear.png` |
| `src/assets/controllers/x20/native/stick-l.png` | `app/src/main/res/drawable-nodpi/x20_stick_left.png` |
| `src/assets/controllers/x20/native/stick-r.png` | `app/src/main/res/drawable-nodpi/x20_stick_right.png` |
| `src/assets/controllers/x20_pro/native/front-body.png` | `app/src/main/res/drawable-nodpi/x20pro_front_body.png` |
| `src/assets/controllers/x20_pro/native/rear-full.png` | `app/src/main/res/drawable-nodpi/x20pro_rear.png` |
| `src/assets/controllers/x20_pro/native/stick-l.png` | `app/src/main/res/drawable-nodpi/x20pro_stick_left.png` |
| `src/assets/controllers/x20_pro/native/stick-r.png` | `app/src/main/res/drawable-nodpi/x20pro_stick_right.png` |

`app/src/main/assets/controllers/x20-layout.json` and `x20-pro-layout.json` are built
exactly like the X10 file: `{ sourceSize, shapes: control-shapes.json models.<model>,
layers: native-layers.json models.<model> }`, with no edits to the values.

The KeyLinker protocol code in `protocol/src/main/kotlin/.../protocol/keylinker/`
(framing, CRC-8, scrambling, record decoders, request classification and the
hardware-evidence table) is a Kotlin port of x20ctl's `x20ctl/protocol.py`,
`x20ctl/client.py`, `x20ctl/validation.py` and the `desktop-dotnet` KeyLinker
codec at the revision above. Byte-level test vectors in
`protocol/src/test/kotlin/.../keylinker/` were produced by running that upstream
code. See [docs/keylinker.md](docs/keylinker.md).

When redistributing these materials or an app that embeds them, preserve notices,
license text, and provide corresponding source as required by the GPL.

Credits, original notices, and full license text are also available offline under
**Settings → About EasyGamepad** in the APK. EasyGamepad is **not** affiliated with EasySMX or x20ctl;
product names are trademarks of their respective owners.

The APK includes the x20ctl NOTICE, legacy MIT notice referenced upstream, and the
full GPLv3 text as bundled assets, alongside this project’s NOTICE.

## Build-time dependencies

AndroidX / Jetpack Compose, Kotlin, and kotlinx.coroutines ship under their own
licenses (mostly Apache-2.0). Gradle and the wrapper are separate third-party tools.
Pinned versions are declared in `gradle/libs.versions.toml` and Gradle scripts.
The wrapper JAR license is kept at `licenses/gradle-wrapper-LICENSE.txt`.

## What we do not ship

Source packages and this repository **do not** include vendor APKs or other
third-party binaries obtained for local comparison. Ephemeral or personal
working copies must not be committed; follow `.gitignore` and keep the tree
limited to what belongs in version control.
