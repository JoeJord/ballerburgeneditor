# Atari ST build

The Atari GEM application is built with the MiNT-ELF GCC cross-compiler and
GEMlib. From the workspace root, run:

```powershell
.\ballerburgeneditor\atari-st\build.bat
```

The resulting `ballered.prg` is written to
`ballerburgeneditor\out\artifacts\ballered.prg`, outside the source directory.
The build preserves debug information for Hatari (`-g`, without stripping).

The C sources are split by responsibility:

- `src\main.c` coordinates AES startup, event handling, and shutdown.
- `src\menu.c` and `src\menu.h` implement the GEM menu tree and translate menu
  selections into application actions.
- `src\about_dialog.c` and `src\about_dialog.h` display the About dialog.
