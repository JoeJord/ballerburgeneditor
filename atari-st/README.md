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

The C sources are split by responsibility:

- `src\main.c` coordinates AES startup, event handling, and shutdown.
- `src\menu.c` and `src\menu.h` implement the GEM menu tree and translate menu
  selections into application actions.
- `src\about_dialog.c` and `src\about_dialog.h` display the About dialog from
  the loaded GEM resource.
- `src\document_window.c` and `src\document_window.h` create and manage blank
  document windows opened through File > New.
- `src\file_open.c` and `src\file_open.h` provide the GEM DAT-file selector
  used by File > Open.
- `src\app_resources.c` and `src\app_resources.h` own resource loading and
  provide accessors for the menu and About dialog trees.
- `src\app_alerts.c` and `src\app_alerts.h` centralize the fixed English
  application alert strings.
- `src\app_logger.c` and `src\app_logger.h` append application lifecycle,
  action, file, window, and alert events to `BALLERED.LOG` in the current
  working directory.
- `src\BALLERED.RSC` defines the menu and About dialog trees and is loaded by
  GEM/AES at startup.
- `src\BALLERED.h` provides the resource IDs used by C; `BALLERED.HRD` is
  resource-editor metadata.
