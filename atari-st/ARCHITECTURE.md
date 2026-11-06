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
| `src/io/castle_data.c` | Parses and validates BALLER.DAT numeric castle records and drawing commands. |
| `src/io/castle_data.h` | Defines the owned castle model and parser interface. |
| `src/ui/menu.c` | Gets and installs the `MAIN` menu tree, translates AES selections into application actions, updates the Close item state, and releases the resource. |
| `src/ui/menu.h` | Declares the menu action contract used by the application event loop. |
| `src/ui/toolbar.c` | Displays the fixed toolbar below the menu using its RSC free images, and maps clicks to application actions. |
| `src/ui/toolbar.h` | Declares toolbar lifecycle, input, and layout functions. |
| `src/ui/about_dialog.c` | Retrieves and displays the `DLG_ABOUT` dialog tree. |
| `src/ui/about_dialog.h` | Declares the About dialog entry point. |
| `src/ui/document_window.c` | Owns parsed castle documents, creates document windows, and redraws their work areas. |
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
   the same actions, logging each toolbar button click separately. Edit-menu
   object IDs and edit toolbar icons are conditionally included when present in
   the generated `BALLERED.h`, allowing C-side preparation while the GEM
   resource is being edited. The document-window module processes redraw,
   close, and top messages in the same event loop.
5. `main()` dispatches New to `document_window_create()`, Open through the GEM
   file selector and `document_window_open_file()`, Close to
   `document_window_close_top()`, About to `about_dialog_show()`, and castle
   navigation to `document_window_navigate_castles()`. Edit commands are
   routed as application actions and logged pending their editing
   implementations; Quit exits the event loop. After each event it reports
   whether document windows are open to `menu_set_close_enabled()`; `menu.c`
   owns the enabled-state flag and updates the item only when that state
   changes.
6. On shutdown, `document_window_shutdown()` closes all remaining document
   windows and its VDI virtual workstation. Then `toolbar_hide()` closes the
   toolbar and its VDI workstation, `menu_hide()` removes the menu bar and
   releases the resource through `app_resources_release()`, and `main()` calls
   `appl_exit()`.

Selecting File > New creates a blank, white document window titled using the
`MSG_DOC_UNTITLED` string from the loaded resource.
Multiple windows can be opened (up to the module's fixed capacity of eight),
and each can be moved, resized, maximized/restored with the Fuller box, and
closed independently. Open DAT documents retain their parsed castle models for
the lifetime of their windows; their work areas show the number of loaded
castles. The toolbar is a fixed, borderless GEM window immediately
below the menu bar, with buttons for Quit, New, Open, and Info. File > Close is
available only from the menu. The toolbar gets its icons from the `IC_QUIT`,
`IC_FILE_NEW`, `IC_FILE_OPEN`, and `IC_ABOUT` free-image resources. Document
windows are confined to the desktop below the toolbar, including their maximize
bounds. Toolbar icons have no individual frames; each 16x16 image is centered
within a 20x20 button area. The toolbar provides Quit, New, Open, Previous
Castle, Next Castle, any resource-defined editing actions, and Info actions, in
that order. Castle navigation applies to the most recently activated document
window and stops at the first and last castle; blank documents and documents
without additional castles are unchanged.
The toolbar window is 22 pixels high, leaving a one-pixel margin above and
below each button area.

Selecting File > Open invokes `fsel_exinput()` with a `*.DAT` filter, starting
in the current drive and directory. Its title comes from resource string
`MSG_FILE_OPEN`. `castle_data_read()` parses the file's signed decimal values,
asterisk-delimited comments, fixed castle fields, and ordered drawing commands.
It requires the `-999` file terminator, `-1` castle terminators, and `-9`
polygon terminators; `-2` fill-style changes and `-4` filled/outline toggles
are retained in the model. A malformed file is rejected before a document
window is opened. The selected castle's polygon geometry is drawn in the
document work area at one-to-one scale, horizontally centered with its
baseline near the bottom of the work area. The renderer also draws the castle's
king, cannon positions, and starting money, powder, and cannonball inventories
using the original game's sprite command streams and resource-grid spacing.
The king and all three resource storage areas are cleared to solid white before
their sprites are drawn, matching the original game's object-background
clearing behavior. These clear rectangles preserve the original inclusive VDI
coordinates: the king area begins 25 pixels above its baseline and is 30x25;
each resource area starts one pixel left of its DAT position and extends through
the storage area's far edge. In the editor, these white backgrounds are offset
one pixel right and one pixel up from that original clear rectangle.
Inventory amounts are converted to sprite counts as in the game and limited by
each store's configured dimensions; empty money sacks use a simple solid outer
contour, empty powder barrels use a sparse pixel raster, and empty cannonballs
are shown as closed circles. The renderer also draws the castle's wind
vane at its DAT position, including the original white clearing area and mast;
its flag uses a fixed positive wind strength of 15. The toolbar's Previous
Castle and Next Castle buttons select among all castles in the active DAT
document. Geometry and sprite patterns use the original game's VDI writing
modes, and rendering restores the work-area background fill state. DAT window
titles come from the
`MSG_CASTLE_NO` resource string with its `%1$d` current-castle and `%2$d`
total-castle tokens expanded by the editor and prefixed by the DAT filename
and ` - `. Each open window owns a stable title buffer, avoiding transient
title-string lifetimes across AES calls. Other game-state values not represented
by castle geometry or starting inventory are retained in the model but are not
rendered.

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
The script compiles all ten C implementation files, links against GEMlib,
and copies `BALLERED.RSC` beside the executable. Both it and the Makefile create
`out\artifacts` under the Atari editor project and place both
`ballered.prg` and `BALLERED.RSC` there. TOS must be able to find the resource
file in the application's current directory at startup.
