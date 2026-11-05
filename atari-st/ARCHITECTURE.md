# Atari ST Editor Architecture

## Overview

The Atari ST version is a native GEM application written in C and built with
MiNT-ELF GCC and GEMlib. AES owns menu rendering and interaction; the program
provides the menu's `OBJECT` tree and handles the resulting AES messages.

## Source Responsibilities

| File | Responsibility |
|---|---|
| `src/main.c` | Initializes AES, runs the message loop, routes menu actions, and shuts down the application. |
| `src/io/file_open.c` | Shows the GEM file selector for DAT files and returns the selected path. |
| `src/io/file_open.h` | Declares the DAT file selection entry point. |
| `src/ui/menu.c` | Gets and installs the `MAIN` menu tree, translates AES selections into application actions, updates the Close item state, and releases the resource. |
| `src/ui/menu.h` | Declares the menu action contract used by the application event loop. |
| `src/ui/toolbar.c` | Displays the fixed toolbar below the menu using its RSC free images, and maps clicks to application actions. |
| `src/ui/toolbar.h` | Declares toolbar lifecycle, input, and layout functions. |
| `src/ui/about_dialog.c` | Retrieves and displays the `DLG_ABOUT` dialog tree. |
| `src/ui/about_dialog.h` | Declares the About dialog entry point. |
| `src/ui/document_window.c` | Creates blank document windows, redraws their work areas, and handles close/top messages. |
| `src/ui/document_window.h` | Declares document-window creation, message handling, and shutdown. |
| `src/ui/app_resources.c` | Owns the resource filename and load lifecycle, and provides access to resource trees and strings. |
| `src/ui/app_resources.h` | Declares resource loading, release, and resource-access functions. |
| `src/ui/app_alerts.c` | Defines shared fixed English alert strings and displays them through AES. |
| `src/ui/app_alerts.h` | Declares the alert-string constants and alert display function. |
| `src/util/app_logger.c` | Appends application events and alerts to `BALLERED.LOG`. |
| `src/util/app_logger.h` | Declares application log lifecycle and write functions. |
| `src/BALLERED.RSC` | GEM resource containing the menu and About dialog trees, object layouts, flags, and labels. |
| `src/BALLERED.h` | Generated resource IDs included by C code (`MAIN`, `DLG_ABOUT`, and dialog object IDs). |
| `src/BALLERED.HRD` | Resource-editor hierarchy metadata; not loaded by the application at runtime. |

`main.c` does not know menu object indices or dialog text. The menu module does
not implement application behavior: it reports an action to `main.c`, which
then invokes the corresponding feature module.

## Startup and Event Flow

1. `main()` opens `BALLERED.LOG` in the current directory, then initializes AES
   with `appl_init()`. The logger appends one event per line and flushes each
   event to disk. File logging can be disabled at compile time by setting
   `APP_LOGGING_ENABLED` to `0` in `src/util/app_logger.c`.
2. `main()` checks `Getrez()` and requires the Atari ST high-resolution
   monochrome mode (resolution code 2, 640x400). Unsupported modes receive an
   AES alert and the application exits cleanly.
3. `menu_show()` loads the resource through `app_resources_load()`, gets its
   `MAIN` tree through `app_resources_get_main_menu()`, ensures the Info and
   File actions and Quit objects are selectable, and installs the tree with
   `menu_bar()`.
   `toolbar_show()` then opens a fixed toolbar directly below the menu bar.
   `main()` restores the standard arrow cursor with `graf_mouse()`, since the
   application cursor is startup state rather than menu behavior.
4. `evnt_multi()` waits for AES messages and toolbar mouse clicks.
   `menu_handle_message()` ignores non-selection messages; for `MN_SELECTED`,
   it normalizes the selected title and returns an application-level
   `MenuAction`. The toolbar handles redraw messages and maps button clicks to
   the same actions, logging each toolbar button click separately. The
   document-window module processes redraw, close, and top messages in the same
   event loop.
5. `main()` dispatches New to `document_window_create()`, Open through the GEM
   file selector and `document_window_open_file()`, Close to
   `document_window_close_top()`, About to `about_dialog_show()`, or exits its
   event loop for Quit. After each event it reports whether document windows
   are open to `menu_set_close_enabled()`; `menu.c` owns the enabled-state flag
   and updates the item only when that state changes.
6. On shutdown, `document_window_shutdown()` closes all remaining document
   windows and its VDI virtual workstation. Then `toolbar_hide()` closes the
   toolbar and its VDI workstation, `menu_hide()` removes the menu bar and
   releases the resource through `app_resources_release()`, and `main()` calls
   `appl_exit()`.

Selecting File > New creates a blank, white document window titled using the
`MSG_DOC_UNTITLED` string from the loaded resource.
Multiple windows can be opened (up to the module's fixed capacity of eight),
and each can be moved, resized, maximized/restored with the Fuller box, and
closed independently. The toolbar is a fixed, borderless GEM window immediately
below the menu bar, with buttons for Quit, New, Open, and Info. File > Close is
available only from the menu. The toolbar gets its icons from the `IC_QUIT`,
`IC_FILE_NEW`, `IC_FILE_OPEN`, and `IC_ABOUT` free-image resources. Document
windows are confined to the desktop below the toolbar, including their maximize
bounds. Toolbar icons have no individual frames; each 16x16 image is centered
within a 20x20 button area. The toolbar window is 22 pixels high, leaving a
one-pixel margin above and below each button area.

Selecting File > Open invokes `fsel_exinput()` with a `*.DAT` filter, starting
in the current drive and directory. Its title comes from resource string
`MSG_FILE_OPEN`. A selected path is checked for readability and opened in a
document window titled with its filename. The Atari window currently provides
the blank editing surface; parsing and rendering the selected castle data is
not yet implemented.

The About dialog is deliberately a separate feature module so its presentation
can evolve without changing menu-tree construction or event routing. It gets
the already-loaded resource tree through `app_resources_get_about_dialog()`
(OK button `BTN_ABOUT_OK`) and does not load or release the resource itself.

The resource currently marks the Info, New, and Quit entries as non-selectable
(the Quit entry only carries `OF_LASTOB`). `menu.c` adds `OF_SELECTABLE` to
those resource objects after loading the tree. It uses the generated IDs from
`BALLERED.h` instead of duplicating object indices, so menu item reordering
must be reflected by regenerating that header.

## Build

From the workspace root, run `.\ballerburgeneditor\atari-st\build.bat`.
The script compiles all nine C implementation files, links against GEMlib,
and copies `BALLERED.RSC` beside the executable. Both it and the Makefile create
`out\artifacts` under the Atari editor project and place both
`ballered.prg` and `BALLERED.RSC` there. TOS must be able to find the resource
file in the application's current directory at startup.
