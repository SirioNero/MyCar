# License

myCar is licensed under the GNU Affero General Public License v3.0. The full text is in
`LICENSE`.

You may use, run, and modify myCar freely. If you distribute a modified version, or let others
use one over a network, you must publish your changes under the same license.

## Based on Gearslip

myCar is a modified version of [Gearslip](https://github.com/twinksoftware/gearslip),
Copyright (C) 2026 Twink Software LLC and contributors, licensed under the AGPL-3.0. myCar's
own changes are Copyright (C) 2026 sirionero and myCar contributors, under the same license.

Twink Software LLC sells a separate commercial license for Gearslip. That license covers
Gearslip only. myCar is available under the AGPL-3.0 alone.

## Third-party code

- `app/src/main/java/com/k2fsa/sherpa/onnx/` and `libsherpa-onnx-jni.so`:
  [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx), Apache License 2.0.
- `app/src/main/res/font/inter_*.ttf`: the [Inter](https://github.com/rsms/inter) typeface,
  Copyright 2016 The Inter Project Authors, SIL Open Font License 1.1
  ([licenses/Inter-OFL.txt](licenses/Inter-OFL.txt)). Static Regular, Medium, SemiBold and Bold
  instances made from the variable font in Google Fonts, under the same license.
- `app/src/main/res/drawable/sym_*.xml`: icons from
  [Material Symbols](https://github.com/google/material-design-icons) (Rounded), by Google,
  Apache License 2.0 ([licenses/Material-Symbols-Apache-2.0.txt](licenses/Material-Symbols-Apache-2.0.txt)).
- Plancia inside myCar (`app/src/main/java/io/github/sirionero/mycar/home/`, `car/Plancia*.kt`)
  is adapted from [Plancia](https://github.com/SirioNero/Home-Assistant-on-Gearslip), and its
  icon (`app/src/main/res/drawable/ic_plancia.xml`) comes from it. Plancia is by myCar's author,
  under the AGPL-3.0.
- Xonos inside myCar (`app/src/main/java/io/github/sirionero/mycar/xonos/`), its icons
  (`ic_xonos.xml`, `xonos_*.xml`) and its words (`values/xonos.xml`) are adapted from
  [Xonos](https://github.com/SirioNero/Music-Assistant-on-Gearslip), by myCar's author, under the
  AGPL-3.0 (or later).
- Voice models, downloaded on the phone when you choose them: Kroko ASR by Banafo
  (CC BY-SA 4.0), and Parakeet and Canary by NVIDIA (CC BY 4.0).
- Car certificates, downloaded on the phone only when you ask and never shipped with myCar: the
  one published by [aasdk](https://github.com/opencardev/aasdk) (GPL-3.0) and, as a spare, the
  one in [Headunit Revived](https://github.com/andreknieriem/headunit-revived) (AGPL-3.0). myCar
  checks each against its SHA-256 fingerprint before keeping it.
