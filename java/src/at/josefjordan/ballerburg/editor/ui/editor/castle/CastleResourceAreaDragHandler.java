package at.josefjordan.ballerburg.editor.ui.editor.castle;

import at.josefjordan.ballerburg.editor.model.CastleDocument.Castle;
import at.josefjordan.ballerburg.editor.model.CastleDocument.Point;
import at.josefjordan.ballerburg.editor.model.CastleDocument.Size;

import java.awt.geom.Point2D;

/**
 * Owns selection-independent drag state and movement or resizing of resource areas.
 */
final class CastleResourceAreaDragHandler {
    /**
     * Castle view containing the selected and dragged resource area.
     */
    private final CastleViewPanel panel;
    /**
     * Resolves resource-area geometry and hit targets from the current castle.
     */
    private final CastleHitTester hitTester;
    /**
     * Computes slot-aligned dimensions for resize gestures.
     */
    private final ResourceAreaResizer resizer;
    /**
     * Resource area being moved or resized, or {@code null} when idle.
     */
    private ResourceArea resourceArea;
    /**
     * Corner index used for the active resize, or {@code -1} otherwise.
     */
    private int cornerIndex = -1;
    /**
     * Whether the active gesture moves the area instead of resizing it.
     */
    private boolean moving;
    /**
     * Screen-space offset between the area's lower-left position and the pointer.
     */
    private Point2D pointerOffset;
    /**
     * Area dimensions captured at the start of a resize gesture.
     */
    private Size originalSize;
    /**
     * Area position captured at the start of a resize gesture.
     */
    private Point originalPosition;
    /**
     * Pointer location captured at the start of a resize gesture.
     */
    private Point2D dragStartPoint;

    /**
     * Creates a resource-area drag handler for the specified view and geometry helpers.
     *
     * @param panel     view whose resource areas can be moved or resized
     * @param hitTester helper used to identify resource areas and their geometry
     * @param resizer   helper that computes slot-aware resize results
     */
    CastleResourceAreaDragHandler(CastleViewPanel panel, CastleHitTester hitTester, ResourceAreaResizer resizer) {
        this.panel = panel;
        this.hitTester = hitTester;
        this.resizer = resizer;
    }

    /**
     * Starts resizing a selected resource area's corner when the pointer hits it.
     *
     * @param castle     castle currently displayed
     * @param left       castle origin's screen x-coordinate
     * @param bottom     castle baseline's screen y-coordinate
     * @param scale      screen pixels per castle coordinate
     * @param clickPoint initial pointer position in panel coordinates
     * @return {@code true} if a corner resize gesture starts
     */
    boolean startCornerDrag(Castle castle, double left, double bottom, double scale, Point2D clickPoint) {
        ResourceArea selectedArea = panel.getSelectedResourceArea();
        if (selectedArea == null) {
            return false;
        }
        int clickedCorner = hitTester.findClickedResourceCorner(
                castle, selectedArea, left, bottom, scale, clickPoint);
        if (clickedCorner < 0) {
            return false;
        }
        resourceArea = selectedArea;
        cornerIndex = clickedCorner;
        dragStartPoint = clickPoint;
        originalSize = hitTester.resourceSize(castle, selectedArea);
        originalPosition = hitTester.resourcePosition(castle, selectedArea);
        panel.setDraggingResourceCorner(clickedCorner);
        return true;
    }

    /**
     * Selects and starts moving the resource area under the pointer.
     *
     * @param castle     castle currently displayed
     * @param left       castle origin's screen x-coordinate
     * @param bottom     castle baseline's screen y-coordinate
     * @param scale      screen pixels per castle coordinate
     * @param clickPoint initial pointer position in panel coordinates
     * @return {@code true} if a resource-area move gesture starts
     */
    boolean startMove(Castle castle, double left, double bottom, double scale, Point2D clickPoint) {
        ResourceArea clickedArea = hitTester.findClickedResourceArea(castle, left, bottom, scale, clickPoint);
        if (clickedArea == null) {
            return false;
        }
        panel.setSelectedResourceArea(clickedArea);
        panel.setSelectedPolygon(null);
        panel.setSelectedPointIndex(-1);
        panel.setSelectedCannonIndex(-1);
        panel.setSelectedThrone(false);
        panel.getEditorPanel().onPointSelectionChanged();
        panel.getEditorPanel().onCannonSelectionChanged();
        resourceArea = clickedArea;
        moving = true;
        Point position = hitTester.resourcePosition(castle, clickedArea);
        double areaScreenX = left + position.x() * scale;
        double areaScreenY = bottom - position.y() * scale;
        pointerOffset = new Point2D.Double(
                clickPoint.getX() - areaScreenX, clickPoint.getY() - areaScreenY);
        return true;
    }

    /**
     * Reports whether a resource area is being moved or resized.
     *
     * @return {@code true} while a resource-area drag is active
     */
    boolean isDragging() {
        return resourceArea != null;
    }

    /**
     * Updates the active move or resize gesture to the current pointer position.
     *
     * @param dragPoint current pointer position in panel coordinates
     * @param castle    current castle snapshot
     * @param left      castle origin's screen x-coordinate
     * @param bottom    castle baseline's screen y-coordinate
     * @param scale     screen pixels per castle coordinate
     */
    void dragTo(Point2D dragPoint, Castle castle, double left, double bottom, double scale) {
        if (!isDragging()) {
            return;
        }
        if (moving) {
            updatePosition(dragPoint, left, bottom, scale);
        } else {
            resize(dragPoint, castle, scale);
        }
    }

    /**
     * Adjusts the move offset and resource-area position after viewport scrolling.
     *
     * @param dragPoint current pointer position in panel coordinates
     * @param deltaX    horizontal scroll displacement in pixels
     * @param deltaY    vertical scroll displacement in pixels
     * @param left      castle origin's current screen x-coordinate
     * @param bottom    castle baseline's current screen y-coordinate
     * @param scale     current screen pixels per castle coordinate
     */
    void adjustForScroll(Point2D dragPoint, int deltaX, int deltaY, double left, double bottom, double scale) {
        if (pointerOffset == null) {
            return;
        }
        pointerOffset = new Point2D.Double(
                pointerOffset.getX() - deltaX, pointerOffset.getY() - deltaY);
        updatePosition(dragPoint, left, bottom, scale);
    }

    /**
     * Ends any active gesture, clears captured geometry, and removes the resize-corner indicator.
     */
    void clear() {
        resourceArea = null;
        cornerIndex = -1;
        moving = false;
        pointerOffset = null;
        originalSize = null;
        originalPosition = null;
        dragStartPoint = null;
        panel.setDraggingResourceCorner(-1);
    }

    /**
     * Moves the active resource area so its lower-left position tracks the pointer.
     *
     * @param dragPoint current pointer position in panel coordinates
     * @param left      castle origin's screen x-coordinate
     * @param bottom    castle baseline's screen y-coordinate
     * @param scale     screen pixels per castle coordinate
     */
    private void updatePosition(Point2D dragPoint, double left, double bottom, double scale) {
        int x = (int) Math.round((dragPoint.getX() - pointerOffset.getX() - left) / scale);
        int y = (int) Math.round((bottom - (dragPoint.getY() - pointerOffset.getY())) / scale);
        panel.getEditorPanel().updateResourceAreaPosition(resourceArea, new Point(x, y));
    }

    /**
     * Resizes the active area using displacement from the original pointer location.
     *
     * @param dragPoint current pointer position in panel coordinates
     * @param castle    current castle snapshot used to calculate valid dimensions
     * @param scale     screen pixels per castle coordinate
     */
    private void resize(Point2D dragPoint, Castle castle, double scale) {
        double deltaX = (dragPoint.getX() - dragStartPoint.getX()) / scale;
        double deltaY = (dragPoint.getY() - dragStartPoint.getY()) / scale;
        ResourceAreaResizer.ResizeResult result = resizer.resize(
                castle, resourceArea, originalPosition, originalSize, cornerIndex, deltaX, deltaY);
        panel.getEditorPanel().updateResourceAreaSize(resourceArea, result.position(), result.size());
    }
}
