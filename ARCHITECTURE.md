# Architecture of the Ballerburg Editor

## Overview

The Ballerburg Editor is a Java Swing desktop application for editing castle collections in the original text-based `BALLER.DAT` format. It uses HGBase for the main frame, menus, file dialogs, status bar, configuration, and localization. The editor itself is divided into the `io`, `model`, and `ui` packages; the UI and editing logic are more closely coupled than the model and file format.

The application entry point is `at.josefjordan.ballerburg.editor.ui.MainFrame`. The JAR manifest at `src/META-INF/MANIFEST.MF` points to this class. The source uses records, sealed interfaces, record patterns, and `Math.clamp()`, and therefore requires Java 21 or later. The IntelliJ module `ballerburgeneditor.iml` configures `ballerburgeneditor/src` as its source root and depends on the separate `HGBase` module. There is no Maven, Gradle, or Ant build file and no separate test source tree in the editor subdirectory.

## Package and File Responsibilities

| Area | Key classes/files | Responsibility |
|---|---|---|
| Entry point and main frame | `ui/MainFrame.java` | Initializes the frame, editor, menu, and status bar; coordinates the file lifecycle, undo/redo, navigation, zoom display, and UI callbacks |
| Menus and status | `ui/MainMenu.java`, `ui/StatusBar.java`, `src/xml/menu.xml` | Connects menus/toolbars to editor actions, controls enabled states, and displays status and zoom |
| Document model | `model/CastleDocument.java`, `model/CastleFileFormat.java` | Immutable document, castle, geometry, resource, and drawing-command data, plus `BALLER.DAT` format constants |
| File I/O | `io/CastleReader.java`, `io/CastleWriter.java` | Reads and writes `BALLER.DAT` independently of Swing |
| Document and selection coordination | `ui/editor/EditorPanel.java`, `EditorDocumentController.java`, `EditorSelectionController.java`, `CastleCollectionEditor.java` | Holds the current document and selection, applies changes, and notifies dependent views |
| Editing actions | `ui/editor/EditorCastleActions.java` | Creates, moves, and modifies castle elements and drawing polygons |
| Canvas and interaction | `ui/editor/castle/` | Renders castle data and implements selection, hit testing, polygon editing, moving, resizing, zoom, and scrolling |
| Validation | `ui/editor/castle/CastleValidator.java`, `ui/editor/problems/ProblemsPanel.java` | Checks castle data and displays localized errors and warnings |
| Properties | `ui/editor/properties/PropertyPalettePanel.java` | Edits starting values for money, powder, cannonballs, and population |
| History | `ui/DocumentHistory.java` | Stores immutable document snapshots for undo/redo and groups related gestures |
| Configuration and messages | `src/xml/settings.xml`, `src/xml/messages.xml`, `src/xml/menu.xml` | HGBase settings, German/English labels, menus, and toolbars |
| Graphics and credits | `src/gfx/`, `src/html/de/`, `src/html/en/` | Application, status, and menu graphics, plus localized credits pages |

## Startup and Document Lifecycle

1. `MainFrame.main()` creates the main frame using `/xml/settings.xml`, sets `MainMenu` and `StatusBar`, initializes `EditorPanel`, and then passes command-line arguments to HGBase.
2. `MainFrame.initializeEditor()` connects document, selection, history, zoom, and status notifications between the frame and editor. The grid setting is loaded from HGBase configuration.
3. “New” creates an empty `CastleDocument` and adds an empty castle. “Open” reads a file through `CastleReader`, installs the document, and resets undo/redo history to a new baseline.
4. `EditorDocumentController` holds the current document reference and selected-castle index. Changes replace a castle with a new `CastleDocument` instance; callbacks update the canvas, properties, problems list, selection status, and history.
5. “Save” validates the document for the confirmation prompt, writes it through `CastleWriter`, and explicitly asks the user to confirm saving despite unresolved problems.
6. Closing clears the document and resets history. `DocumentHistory` retains previous and undone snapshots together with the selected castle index.

The file-I/O layer throws `IOException` for invalid or unreadable data; `MainFrame` displays read/write errors using HGBase error dialogs. Invalid input data encountered while opening is also shown as an error. The writer throws `IOException` if polygon coordinates remain negative after normalization.

## Data Model and Editing Flow

`CastleDocument` and its nested records (`Castle`, `Point`, `Size`, `Resources`, `FillStyle`, `Polygon`) form an immutable snapshot. Lists are copied with `List.copyOf()` when constructed. A `Castle` contains its nominal width, up to ten cannon positions, optional positions for the throne, wind vane, and three storage areas, storage-area dimensions, starting resources, four reserved format values, and an ordered list of drawing commands.

An unset model position is represented by `Point.unset()` with `Integer.MIN_VALUE` on both axes. The file format has its own sentinels, notably `-1,-1` for unused positions. The model enforces at most ten cannon positions, exactly four reserved values, and at least one point per polygon.

`EditorPanel` is the UI composition point. It contains the scrollable `CastleViewPanel`, a property palette, and the problems list. It delegates model/selection changes to `EditorDocumentController` and `EditorSelectionController`; `EditorCastleActions` encapsulates concrete editing operations.

The canvas renders the current castle snapshot through `CastleRenderer` and owns transient view state: selection, selected polygon vertex, zoom, scroll position, grid, and in-progress polygon creation. `CastleViewTransformController` converts between castle and screen coordinates. `CastleMouseHandler` dispatches mouse and keyboard gestures to specialized helpers:

- `CastleHitTester` detects hit objects, areas, and polygon edges.
- `CastlePolygonCreationHandler` and `CastlePolygonCreation` manage wall and roof polygon creation.
- `CastlePolygonDragHandler` moves polygons and vertices; `CastlePolygonEditor` transforms their immutable drawing commands.
- `CastleObjectDragHandler` edits cannons, the throne, and the wind vane.
- `CastleResourceAreaDragHandler`, `ResourceAreaEditor`, and `ResourceAreaResizer` select, move, and resize storage areas.
- `CastleDragAutoScroller` scrolls the viewport when a drag reaches its edge.

Changes are returned to the document controller as new `Castle` and `CastleDocument` snapshots. `DocumentHistory` compares document values, stores the state preceding an edit, and clears redo history when new edits are made. `beginGroup()`/`endGroup()` can group a continuous gesture into one history step. Restoring a snapshot restores the document and castle index together.

## Rendering and Validation

`CastleRenderer` draws the grid, castle polygons, objects, and selection/preview markers in a fixed order using Swing `Graphics2D`. Wall and roof fills are interpreted from the format's fill commands; `Sprites` provides the game-object graphics. `CastleBoundsCalculator` determines the canvas bounds. A transform controller manages zoom, scrolling, and coordinate conversion.

`CastleValidator` checks the components and relationships expected by the game: required castle wall, throne, wind vane, and storage areas; width; placement inside the wall; overlaps among cannons, objects, and polygons; and empty starting resources. Missing cannons and zero values are shown as warnings; other conditions may be errors. `ProblemsPanel` displays localized findings. Existing problems do not automatically block saving; `MainFrame.onFileSave()` asks for explicit confirmation instead.

## `BALLER.DAT` Format and I/O

`CastleReader` decodes files as IBM437/CP437, removes comments enclosed by `*`, and parses signed decimal integers. Commas and whitespace are separators. The parser expects the original format's fixed field order and maps sentinels to model representations. `-999` ends the file, `-1` ends a castle's drawing commands, `-2` introduces a fill style and two arguments, and `-9` ends a polygon.

A castle record contains its width, ten fixed cannon pairs, positions for the other game elements, three storage-area dimensions, starting resources, four reserved values, and drawing commands. Drawing commands are ordered: a `FillStyle` changes the fill used for subsequent polygons. `CastleFileFormat` centralizes markers and game-coordinate dimensions.

`CastleWriter` writes a canonical CP437 file with CRLF line endings and comments for each castle. Before writing, it translates all set coordinates so their minimum origin is `(0,0)`. Up to ten cannon slots are serialized; unused slots are padded with `-1,-1`. The nominal castle width is calculated from the greatest polygon X coordinate. Source comments and original formatting are not preserved as document data.

The model covers the `BALLER.DAT` content relevant to the editor, not GEM resources or the game's separate `BALLER.TAB` results table.

## Menus and Configuration

`src/xml/menu.xml` declares the File, Edit, View, Settings, and Help menus, as well as toolbar contents. `MainMenu` registers the concrete Swing actions and updates menu/toolbar states depending on whether a file, castle, or editable selection exists. Individual actions in `ui/menu/` delegate to `MainFrame`.

`src/xml/messages.xml` contains German and English message keys, including validation findings and interaction hints. `settings.xml` includes the standard HGBase settings and configures the application name/version, menu and message files, `.dat` file type, window size, and recent-file list. Text and menu structure are therefore mostly declarative; editor actions and dynamic enablement rules remain in Java code.

## Maintenance Notes and Limitations

- `CastleDocument` is immutable, whereas selection, tool modes, zoom, and scroll values are stateful UI data. Model changes should flow as new snapshots through controllers/editor helpers, not be made directly in the renderer or mouse listeners.
- UI actions pass through a callback chain from `MainMenu`/`MainFrame` to `EditorPanel`, actions/controllers, and back to history and views. New actions must also account for menu/toolbar enablement and selection notifications.
- The original format is a positional numeric stream without a version field. The parser and writer must preserve the same field counts, markers, CP437 encoding, and coordinate conventions.
- The writer produces canonical output and normalizes coordinates. Opening and saving therefore does not necessarily preserve the original formatting or comment arrangement.
- `MainFrame` and `CastleValidator` use HGBase messages/dialogs; the editor cannot readily be compiled or launched as a standalone Swing module unless HGBase is on the classpath.
- The UI is Swing-based and must be updated on the Swing event dispatch thread. The class structure separates rendering from gesture logic, but is not a complete MVC or plugin architecture.
- `TODO.md` identifies more precise scrolling while dragging near the canvas edges as an outstanding usability issue.
