# Atari ST Editor Architecture

## Overview

The Atari ST version is a native GEM application written in C and built with
MiNT-ELF GCC and GEMlib. AES owns menu rendering and interaction; the program
provides the menu's `OBJECT` tree and handles the resulting AES messages.

## Source Responsibilities

| File | Responsibility |
|---|---|
| `src/main.c` | Initializes AES, runs the message loop, routes menu actions, and shuts down the application. |
| `src/menu.c` | Owns the GEM menu object tree and labels; creates/shows/hides the menu and translates AES selection messages into application actions. |
| `src/menu.h` | Declares the menu action contract used by the application event loop. |
| `src/about_dialog.c` | Owns the About alert text and displays the application information dialog. |
| `src/about_dialog.h` | Declares the About dialog entry point. |

`main.c` does not know menu object indices or dialog text. The menu module does
not implement application behavior: it reports an action to `main.c`, which
then invokes the corresponding feature module.

## Startup and Event Flow

1. `main()` initializes AES with `appl_init()`.
2. `menu_show()` constructs the menu tree and installs it with `menu_bar()`.
   `main()` then restores the standard arrow cursor with `graf_mouse()`, since
   the application cursor is startup state rather than menu behavior.
3. `evnt_mesag()` waits for AES messages. `menu_handle_message()` ignores
   non-selection messages; for `MN_SELECTED`, it normalizes the selected title
   and returns an application-level `MenuAction`.
4. `main()` dispatches the About action to `about_dialog_show()` or exits its
   event loop for the quit action.
5. `menu_hide()` removes the menu bar, then `main()` calls `appl_exit()`.

The About dialog is deliberately a separate feature module so its alert content
and presentation can evolve without changing menu-tree construction or event
routing.

## Build

From the workspace root, run `.\ballerburgeneditor\atari-st\build.bat`.
The script compiles all three C implementation files and links against GEMlib.
Both it and the Makefile create `out\artifacts` under the Atari editor project
and write the resulting `ballered.prg` there, keeping generated binaries out of
the source directory.
