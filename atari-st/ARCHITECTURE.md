# Atari ST Editor Architecture

## Overview

The Atari ST version is a native GEM application written in C and built with
MiNT-ELF GCC and GEMlib. AES owns menu rendering and interaction; the program
provides the menu's `OBJECT` tree and handles the resulting AES messages.

## Source Responsibilities

| File | Responsibility |
|---|---|
| `src/main.c` | Initializes AES, runs the message loop, routes menu actions, and shuts down the application. |
| `src/menu.c` | Gets and installs the `MAIN` menu tree, translates AES selection messages into application actions, and releases the resource. |
| `src/menu.h` | Declares the menu action contract used by the application event loop. |
| `src/about_dialog.c` | Retrieves and displays the `DLG_ABOUT` dialog tree. |
| `src/about_dialog.h` | Declares the About dialog entry point. |
| `src/app_resources.c` | Owns the resource filename and load lifecycle, and provides access to resource trees. |
| `src/app_resources.h` | Declares resource loading, release, and tree-access functions. |
| `src/BALLERED.RSC` | GEM resource containing the menu and About dialog trees, object layouts, flags, and labels. |
| `src/BALLERED.h` | Generated resource IDs included by C code (`MAIN`, `DLG_ABOUT`, and dialog object IDs). |
| `src/BALLERED.HRD` | Resource-editor hierarchy metadata; not loaded by the application at runtime. |

`main.c` does not know menu object indices or dialog text. The menu module does
not implement application behavior: it reports an action to `main.c`, which
then invokes the corresponding feature module.

## Startup and Event Flow

1. `main()` initializes AES with `appl_init()`.
2. `main()` checks `Getrez()` and requires the Atari ST high-resolution
   monochrome mode (resolution code 2, 640x400). Unsupported modes receive an
   AES alert and the application exits cleanly.
3. `menu_show()` loads the resource through `app_resources_load()`, gets its
   `MAIN` tree through `app_resources_get_main_menu()`, ensures the Info and
   Quit objects are selectable, and installs the tree with `menu_bar()`.
   `main()` then restores the standard arrow cursor with `graf_mouse()`, since
   the application cursor is startup state rather than menu behavior.
4. `evnt_mesag()` waits for AES messages. `menu_handle_message()` ignores
   non-selection messages; for `MN_SELECTED`, it normalizes the selected title
   and returns an application-level `MenuAction`.
5. `main()` dispatches the About action to `about_dialog_show()` or exits its
   event loop for the quit action.
6. `menu_hide()` removes the menu bar and releases the loaded resource through
   `app_resources_release()`, then `main()` calls `appl_exit()`.

The About dialog is deliberately a separate feature module so its presentation
can evolve without changing menu-tree construction or event routing. It gets
the already-loaded resource tree through `app_resources_get_about_dialog()`
(OK button `BTN_ABOUT_OK`) and does not load or release the resource itself.

The resource currently marks the Info and Quit entries as non-selectable (the
Quit entry only carries `OF_LASTOB`). `menu.c` adds `OF_SELECTABLE` to those two
resource objects after loading the tree. It uses the generated IDs from
`BALLERED.h` instead of duplicating object indices, so menu item reordering
must be reflected by regenerating that header.

## Build

From the workspace root, run `.\ballerburgeneditor\atari-st\build.bat`.
The script compiles all four C implementation files, links against GEMlib,
and copies `BALLERED.RSC` beside the executable. Both it and the Makefile create
`out\artifacts` under the Atari editor project and place both
`ballered.prg` and `BALLERED.RSC` there. TOS must be able to find the resource
file in the application's current directory at startup.
