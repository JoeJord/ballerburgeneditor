# Atari ST build

The Atari GEM application is built with the MiNT-ELF GCC cross-compiler and
GEMlib. From the workspace root, run:

```powershell
.\ballerburgeneditor\atari-st\build.bat
```

The resulting `ballered.prg` is written to
`ballerburgeneditor\out\artifacts\ballered.prg`, outside the source directory.
The build also copies the required `BALLERED.RSC` beside the PRG. Keep both
files together when running the application in Hatari. Debug information is
preserved for Hatari (`-g`, without stripping).

The C sources are grouped by responsibility:

- `src\main.c` coordinates AES startup, event handling, and shutdown.
- `src\io\file_open.c` and `src\io\file_open.h` provide the GEM DAT-file
  selector used by File > Open.
- `src\ui\` contains the menu, toolbar, About dialog, document window,
  application alerts, and GEM resource-loading modules.
- `src\util\app_logger.c` and `src\util\app_logger.h` append application
  lifecycle, action, file, window, and alert events to `BALLERED.LOG` in the
  current working directory.
- `src\BALLERED.RSC` defines the menu and About dialog trees and is loaded by
  GEM/AES at startup. `src\BALLERED.h` provides the resource IDs used by C;
  `src\BALLERED.HRD` is resource-editor metadata.
