# TODO

## Editor
 - Scrolling
 - Zoom in status bar adjustable
 - Show coordinates in status bar
 - Undo/Redo

## File Handling
 - Coordinates lower 0 are not allowed ⇒ rebase before saving
 - Calculate the width of the castle (is saved as a separate value)

## Refactoring
 - Replace magic numbers with constants:
   - UI margins: 24.0 (view margin)
   - UI sizes: 90x24 (position label size)
   - SplitPane: 1.0 (resize weight)
   - Stroke widths: 1.0f (default), 2.0f (selected)
 - Remove code duplication
 - Reduce code complexity
 - Add java documentation
