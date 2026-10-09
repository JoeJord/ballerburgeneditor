package at.josefjordan.ballerburg.editor.ui.editor.castle;

import at.josefjordan.ballerburg.editor.model.CastleDocument.Point;

import java.awt.geom.Point2D;

/**
 * Owns drag state and coordinate updates for movable non-polygon castle objects.
 */
final class CastleObjectDragHandler {
    /**
     * View containing the objects being moved.
     */
    private final CastleViewPanel panel;
    /**
     * Kind of object currently being dragged.
     */
    private DraggedObject draggedObject = DraggedObject.NONE;
    /**
     * Index of the active cannon drag, when applicable.
     */
    private int cannonIndex = -1;
    /**
     * Screen-space offset between the pointer and the dragged object's origin.
     */
    private Point2D pointerOffset;

    /**
     * Creates a drag handler for the specified castle view.
     *
     * @param panel view whose objects may be moved
     */
    CastleObjectDragHandler(CastleViewPanel panel) {
        this.panel = panel;
    }

    /**
     * Starts dragging a cannon.
     *
     * @param cannonIndex   index of the cannon being moved
     * @param pointerOffset screen-space offset from its origin to the pointer
     */
    void startCannonDrag(int cannonIndex, Point2D pointerOffset) {
        this.cannonIndex = cannonIndex;
        this.pointerOffset = pointerOffset;
        draggedObject = DraggedObject.CANNON;
    }

    /**
     * Starts dragging the throne.
     *
     * @param pointerOffset screen-space offset from its origin to the pointer
     */
    void startThroneDrag(Point2D pointerOffset) {
        this.pointerOffset = pointerOffset;
        draggedObject = DraggedObject.THRONE;
    }

    /**
     * Starts dragging the wind vane.
     *
     * @param pointerOffset screen-space offset from its origin to the pointer
     */
    void startWindVaneDrag(Point2D pointerOffset) {
        this.pointerOffset = pointerOffset;
        draggedObject = DraggedObject.WIND_VANE;
    }

    /**
     * Reports whether a non-polygon object drag is active.
     *
     * @return {@code true} if an object is being dragged
     */
    boolean isDragging() {
        return draggedObject != DraggedObject.NONE;
    }

    /**
     * Moves the active object to the pointer's castle coordinate.
     *
     * @param dragPoint current pointer position in panel coordinates
     * @param left      castle origin's screen x-coordinate
     * @param bottom    castle baseline's screen y-coordinate
     * @param scale     screen pixels per castle coordinate
     */
    void dragTo(Point2D dragPoint, double left, double bottom, double scale) {
        if (!isDragging()) {
            return;
        }
        int x = (int) Math.round((dragPoint.getX() - pointerOffset.getX() - left) / scale);
        int y = (int) Math.round((bottom - (dragPoint.getY() - pointerOffset.getY())) / scale);
        Point newPosition = new Point(x, y);
        switch (draggedObject) {
            case CANNON -> panel.getEditorPanel().updateCannonPosition(cannonIndex, newPosition);
            case THRONE -> panel.getEditorPanel().updateThronePosition(newPosition);
            case WIND_VANE -> panel.getEditorPanel().updateWindVanePosition(newPosition);
            case NONE -> {
                // Nothing to do.
            }
        }
    }

    /**
     * Adjusts the pointer offset and object position after viewport scrolling.
     *
     * @param dragPoint current pointer position in panel coordinates
     * @param deltaX    horizontal scroll displacement in pixels
     * @param deltaY    vertical scroll displacement in pixels
     * @param left      castle origin's current screen x-coordinate
     * @param bottom    castle baseline's current screen y-coordinate
     * @param scale     current screen pixels per castle coordinate
     */
    void adjustForScroll(Point2D dragPoint, int deltaX, int deltaY,
                         double left, double bottom, double scale) {
        if (pointerOffset == null) {
            return;
        }
        pointerOffset = new Point2D.Double(
                pointerOffset.getX() - deltaX, pointerOffset.getY() - deltaY);
        dragTo(dragPoint, left, bottom, scale);
    }

    /**
     * Ends the active drag and clears its associated state.
     */
    void clear() {
        draggedObject = DraggedObject.NONE;
        cannonIndex = -1;
        pointerOffset = null;
    }

    /**
     * Types of non-polygon elements this handler can move.
     */
    private enum DraggedObject {
        NONE,
        CANNON,
        THRONE,
        WIND_VANE
    }
}
