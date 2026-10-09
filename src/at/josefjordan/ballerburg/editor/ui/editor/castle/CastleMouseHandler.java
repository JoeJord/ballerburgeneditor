package at.josefjordan.ballerburg.editor.ui.editor.castle;

import at.josefjordan.ballerburg.editor.model.CastleDocument.*;

import java.awt.event.*;
import java.awt.geom.Point2D;
import java.util.List;

/**
 * Selects, creates, moves, and resizes castle elements in response to pointer and keyboard input.
 */
final class CastleMouseHandler extends MouseAdapter {
    /**
     * Castle view receiving input and providing the current drawing transform.
     */
    private final CastleViewPanel panel;
    /**
     * Performs geometric hit tests independently of gesture state.
     */
    private final CastleHitTester hitTester = new CastleHitTester();
    /**
     * Owns polygon vertex and polygon movement state during drag gestures.
     */
    private final CastlePolygonDragHandler polygonDragHandler;
    /**
     * Calculates slot-aware storage geometry during resize gestures.
     */
    private final ResourceAreaResizer resourceAreaResizer = new ResourceAreaResizer();
    /**
     * Index of the cannon being dragged, or {@code -1} when none is active.
     */
    private int draggingCannonIndex = -1;
    /**
     * Screen-space offset from the cannon origin to the pointer.
     */
    private Point2D cannonDragOffset;
    /**
     * Whether the throne is the active dragged element.
     */
    private boolean draggingThrone = false;
    /**
     * Screen-space offset from the throne origin to the pointer.
     */
    private Point2D throneDragOffset;
    /**
     * Whether the wind vane is the active dragged element.
     */
    private boolean draggingWindVane = false;
    /**
     * Screen-space offset from the wind-vane origin to the pointer.
     */
    private Point2D windVaneDragOffset;
    /**
     * Storage area being moved or resized, or {@code null} when none is active.
     */
    private ResourceArea draggingResourceArea;
    /**
     * Index of the resource-area resize handle being dragged, or {@code -1}.
     */
    private int draggingCornerIndex = -1;
    /**
     * Whether the active storage-area drag moves it rather than resizing it.
     */
    private boolean draggingResourceAreaMove = false;
    /**
     * Screen-space offset from the storage area's origin to the pointer while moving it.
     */
    private Point2D resourceAreaDragOffset;
    /**
     * Storage-area dimensions captured when a corner resize begins.
     */
    private Size originalResourceSize;
    /**
     * Storage-area lower-left coordinate captured when a corner resize begins.
     */
    private Point originalResourcePosition;
    /**
     * Pointer position in panel coordinates when the current drag began.
     */
    private Point2D dragStartPoint;
    /**
     * Scrolls the castle view while a drag pointer remains near a viewport edge.
     */
    private final CastleDragAutoScroller autoScroller;

    /**
     * Installs mouse, motion, and keyboard listeners on the castle view.
     *
     * @param panel view whose objects respond to input
     */
    CastleMouseHandler(CastleViewPanel panel) {
        this.panel = panel;
        polygonDragHandler = new CastlePolygonDragHandler(panel, hitTester);
        autoScroller = new CastleDragAutoScroller(panel, this::applyEdgeScrollDelta);

        panel.addMouseMotionListener(createMouseMotionListener());
        panel.addKeyListener(createKeyListener());
        panel.setFocusable(true);
        panel.requestFocusInWindow();
    }

    /**
     * Creates a listener that forwards mouse movement and drag events to the handler.
     *
     * @return the created mouse motion listener
     */
    private MouseMotionAdapter createMouseMotionListener() {
        return new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                handleDrag(e);
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                if (panel.isInPolygonCreationMode()) {
                    panel.setMousePosition(e.getPoint());
                } else {
                    panel.setMousePosition(null);
                    // Update edge hover for selected polygon
                    panel.setEdgeHoverPosition(e.getPoint());
                }
            }
        };
    }

    /**
     * Creates a listener for keyboard events used during polygon creation.
     *
     * @return the created key listener
     */
    private KeyListener createKeyListener() {
        return new KeyListener() {
            @Override
            public void keyTyped(KeyEvent e) {
                // Not used
            }

            @Override
            public void keyPressed(KeyEvent e) {
                handleKeyPressed(e);
            }

            @Override
            public void keyReleased(KeyEvent e) {
                // Not used
            }
        };
    }

    /**
     * Cancels polygon creation on Escape or completes it on Enter.
     *
     * @param e key event to process while polygon creation is active
     */
    private void handleKeyPressed(KeyEvent e) {
        if (!panel.isInPolygonCreationMode()) {
            return;
        }
        if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
            panel.getEditorPanel().cancelPolygonCreation();
        } else if (e.getKeyCode() == KeyEvent.VK_ENTER) {
            List<Point> points = panel.getCreationPoints();
            if (points.size() >= 3) {
                panel.getEditorPanel().finishPolygonCreation(points);
            } else {
                panel.getEditorPanel().cancelPolygonCreation();
            }
        }
    }

    @Override
    public void mousePressed(MouseEvent e) {
        if (panel.isInPolygonCreationMode()) {
            handlePolygonCreationClick(e);
            return;
        }

        Castle castle = panel.getCastle();
        if (castle == null) {
            return;
        }
        panel.getEditorPanel().beginHistoryGroup();

        double[] params = panel.getRenderingParameters();
        double left = params[0];
        double bottom = params[1];
        double scale = params[2];
        if (scale <= 0) {
            return;
        }

        Point2D clickPoint = e.getPoint();
        if (tryAddPolygonPoint(e, left, bottom, scale, clickPoint)
                || tryStartResourceCornerDrag(castle, left, bottom, scale, clickPoint)
                || tryStartResourceAreaDrag(castle, left, bottom, scale, clickPoint)
                || tryStartWindVaneDrag(castle, left, bottom, scale, clickPoint)
                || tryStartThroneDrag(castle, left, bottom, scale, clickPoint)
                || tryStartCannonDrag(castle, left, bottom, scale, clickPoint)
                || tryStartSelectedPolygonDrag(castle, left, bottom, scale, clickPoint)) {
            return;
        }
        selectClickedPolygon(castle, left, bottom, scale, clickPoint);
    }

    /**
     * Adds a vertex on left click or finishes the polygon on right click.
     *
     * @param e mouse click received while polygon creation is active
     */
    private void handlePolygonCreationClick(MouseEvent e) {
        if (e.getButton() == MouseEvent.BUTTON1) {
            Castle castle = panel.getCastle();
            if (castle == null) {
                return;
            }
            double[] params = panel.getRenderingParameters();
            double left = params[0];
            double bottom = params[1];
            double scale = params[2];
            if (scale <= 0) {
                return;
            }

            panel.addCreationPoint(toCastlePoint(e.getPoint(), left, bottom, scale));
        } else if (e.getButton() == MouseEvent.BUTTON3) {
            finishPolygonCreation();
        }
    }

    /**
     * Finishes polygon creation when at least three vertices exist; otherwise cancels it.
     */
    private void finishPolygonCreation() {
        List<Point> points = panel.getCreationPoints();
        if (points.size() >= 3) {
            panel.getEditorPanel().finishPolygonCreation(points);
        } else {
            panel.getEditorPanel().cancelPolygonCreation();
        }
    }

    /**
     * Adds a vertex on the hovered edge when a selected polygon receives a left click.
     *
     * @param e          left-click event received by the castle view
     * @param left       the castle origin x-coordinate
     * @param bottom     the castle baseline y-coordinate
     * @param scale      the rendering scale
     * @param clickPoint mouse click position in panel coordinates
     * @return true if a vertex was added on the hovered edge; false otherwise
     */
    private boolean tryAddPolygonPoint(MouseEvent e, double left, double bottom, double scale, Point2D clickPoint) {
        if (e.getButton() != MouseEvent.BUTTON1 || panel.getSelectedPolygon() == null) {
            return false;
        }
        int edgeIndex = panel.findEdgeIndexForHoverPosition(left, bottom, scale);
        if (edgeIndex < 0) {
            return false;
        }
        panel.getEditorPanel().addPolygonPoint(edgeIndex, toCastlePoint(clickPoint, left, bottom, scale));
        panel.setEdgeHoverPosition(null);
        return true;
    }

    /**
     * Starts resizing a selected resource-area corner when the pointer hits it.
     *
     * @param castle     castle currently shown in the view
     * @param left       the castle origin x-coordinate
     * @param bottom     the castle baseline y-coordinate
     * @param scale      the rendering scale
     * @param clickPoint mouse click position in panel coordinates
     * @return true if a selected resource-area corner drag begins; false otherwise
     */
    private boolean tryStartResourceCornerDrag(Castle castle, double left, double bottom, double scale,
                                               Point2D clickPoint) {
        ResourceArea selectedResourceArea = panel.getSelectedResourceArea();
        if (selectedResourceArea == null) {
            return false;
        }
        int clickedCorner = hitTester.findClickedResourceCorner(
                castle, selectedResourceArea, left, bottom, scale, clickPoint);
        if (clickedCorner < 0) {
            return false;
        }
        draggingResourceArea = selectedResourceArea;
        draggingCornerIndex = clickedCorner;
        dragStartPoint = clickPoint;
        originalResourceSize = hitTester.resourceSize(castle, selectedResourceArea);
        originalResourcePosition = hitTester.resourcePosition(castle, selectedResourceArea);
        panel.setDraggingResourceCorner(clickedCorner);
        return true;
    }

    /**
     * Selects and starts moving the resource area under the pointer.
     *
     * @param castle     castle currently shown in the view
     * @param left       the castle origin x-coordinate
     * @param bottom     the castle baseline y-coordinate
     * @param scale      the rendering scale
     * @param clickPoint mouse click position in panel coordinates
     * @return true if a resource-area move begins; false otherwise
     */
    private boolean tryStartResourceAreaDrag(Castle castle, double left, double bottom, double scale,
                                             Point2D clickPoint) {
        ResourceArea clickedResourceArea =
                hitTester.findClickedResourceArea(castle, left, bottom, scale, clickPoint);
        if (clickedResourceArea == null) {
            return false;
        }
        panel.setSelectedResourceArea(clickedResourceArea);
        panel.setSelectedPolygon(null);
        panel.setSelectedPointIndex(-1);
        panel.setSelectedCannonIndex(-1);
        panel.setSelectedThrone(false);
        panel.getEditorPanel().onPointSelectionChanged();
        panel.getEditorPanel().onCannonSelectionChanged();
        draggingResourceArea = clickedResourceArea;
        draggingResourceAreaMove = true;
        Point resourcePosition = hitTester.resourcePosition(castle, clickedResourceArea);
        double resourceScreenX = left + resourcePosition.x() * scale;
        double resourceScreenY = bottom - resourcePosition.y() * scale;
        resourceAreaDragOffset = new Point2D.Double(clickPoint.getX() - resourceScreenX,
                clickPoint.getY() - resourceScreenY);
        return true;
    }

    /**
     * Selects and starts moving the wind vane when it is hit.
     *
     * @param castle     castle currently shown in the view
     * @param left       the castle origin x-coordinate
     * @param bottom     the castle baseline y-coordinate
     * @param scale      the rendering scale
     * @param clickPoint mouse click position in panel coordinates
     * @return true if a wind-vane drag begins; false otherwise
     */
    private boolean tryStartWindVaneDrag(Castle castle, double left, double bottom, double scale,
                                         Point2D clickPoint) {
        if (!hitTester.isWindVaneClicked(castle, left, bottom, scale, clickPoint)) {
            return false;
        }
        panel.setSelectedWindVane(true);
        panel.setSelectedPolygon(null);
        panel.setSelectedPointIndex(-1);
        panel.setSelectedCannonIndex(-1);
        panel.setSelectedThrone(false);
        panel.setSelectedResourceArea(null);
        panel.getEditorPanel().onPointSelectionChanged();
        panel.getEditorPanel().onCannonSelectionChanged();
        draggingWindVane = true;
        Point windVane = castle.windVane();
        double windVaneScreenX = left + windVane.x() * scale;
        double windVaneScreenY = bottom - windVane.y() * scale;
        windVaneDragOffset = new Point2D.Double(clickPoint.getX() - windVaneScreenX,
                clickPoint.getY() - windVaneScreenY);
        return true;
    }

    /**
     * Selects and starts moving the throne when it is hit.
     *
     * @param castle     castle currently shown in the view
     * @param left       the castle origin x-coordinate
     * @param bottom     the castle baseline y-coordinate
     * @param scale      the rendering scale
     * @param clickPoint mouse click position in panel coordinates
     * @return true if a throne drag begins; false otherwise
     */
    private boolean tryStartThroneDrag(Castle castle, double left, double bottom, double scale, Point2D clickPoint) {
        if (!hitTester.isThroneClicked(castle, left, bottom, scale, clickPoint)) {
            return false;
        }
        panel.setSelectedThrone(true);
        panel.setSelectedPolygon(null);
        panel.setSelectedPointIndex(-1);
        panel.setSelectedCannonIndex(-1);
        panel.setSelectedWindVane(false);
        panel.setSelectedResourceArea(null);
        panel.getEditorPanel().onPointSelectionChanged();
        panel.getEditorPanel().onCannonSelectionChanged();
        draggingThrone = true;
        Point throne = castle.throne();
        double throneScreenX = left + throne.x() * scale;
        double throneScreenY = bottom - throne.y() * scale;
        throneDragOffset = new Point2D.Double(clickPoint.getX() - throneScreenX, clickPoint.getY() - throneScreenY);
        return true;
    }

    /**
     * Selects and starts moving the cannon under the pointer.
     *
     * @param castle     castle currently shown in the view
     * @param left       the castle origin x-coordinate
     * @param bottom     the castle baseline y-coordinate
     * @param scale      the rendering scale
     * @param clickPoint mouse click position in panel coordinates
     * @return true if a cannon drag begins; false otherwise
     */
    private boolean tryStartCannonDrag(Castle castle, double left, double bottom, double scale, Point2D clickPoint) {
        int clickedCannonIndex = hitTester.findClickedCannonIndex(castle, left, bottom, scale, clickPoint);
        if (clickedCannonIndex < 0) {
            return false;
        }
        panel.setSelectedCannonIndex(clickedCannonIndex);
        panel.setSelectedPolygon(null);
        panel.setSelectedPointIndex(-1);
        panel.setSelectedThrone(false);
        panel.setSelectedWindVane(false);
        panel.setSelectedResourceArea(null);
        panel.getEditorPanel().onCannonSelectionChanged();
        draggingCannonIndex = clickedCannonIndex;
        dragStartPoint = clickPoint;
        Point cannon = castle.cannons().get(clickedCannonIndex);
        double cannonScreenX = left + cannon.x() * scale;
        double cannonScreenY = bottom - cannon.y() * scale;
        cannonDragOffset = new Point2D.Double(clickPoint.getX() - cannonScreenX, clickPoint.getY() - cannonScreenY);
        return true;
    }

    /**
     * Starts moving the selected polygon or one of its vertices when hit.
     *
     * @param castle     castle currently shown in the view
     * @param left       the castle origin x-coordinate
     * @param bottom     the castle baseline y-coordinate
     * @param scale      the rendering scale
     * @param clickPoint mouse click position in panel coordinates
     * @return true if a selected polygon or vertex drag begins; false otherwise
     */
    private boolean tryStartSelectedPolygonDrag(Castle castle, double left, double bottom, double scale,
                                                Point2D clickPoint) {
        Polygon selectedPolygon = panel.getSelectedPolygon();
        if (selectedPolygon == null) {
            return false;
        }
        int polygonIndex = hitTester.polygonIndexOf(castle, selectedPolygon);
        if (polygonIndex < 0) {
            return false;
        }
        int pointIndex = hitTester.findClickedPointIndex(selectedPolygon, left, bottom, scale, clickPoint);
        if (pointIndex >= 0) {
            startPolygonPointDrag(polygonIndex, pointIndex);
            return true;
        }
        if (!hitTester.polygonPath(selectedPolygon, left, bottom, scale).contains(clickPoint)) {
            return false;
        }
        startPolygonMove(polygonIndex, selectedPolygon, left, bottom, scale, clickPoint);
        return true;
    }

    /**
     * Starts the polygon point drag.
     *
     * @param polygonIndex zero-based index among polygon drawing commands
     * @param pointIndex   zero-based vertex index within that polygon
     */
    private void startPolygonPointDrag(int polygonIndex, int pointIndex) {
        polygonDragHandler.startPointDrag(polygonIndex, pointIndex);
        panel.setSelectedPointIndex(pointIndex);
        clearNonPolygonSelections();
    }

    /**
     * Starts the polygon move.
     *
     * @param polygonIndex zero-based index among polygon drawing commands
     * @param polygon      selected polygon whose drag is starting
     * @param left         the castle origin x-coordinate
     * @param bottom       the castle baseline y-coordinate
     * @param scale        the rendering scale
     * @param clickPoint   mouse click position in panel coordinates
     */
    private void startPolygonMove(int polygonIndex, Polygon polygon, double left, double bottom, double scale,
                                  Point2D clickPoint) {
        polygonDragHandler.startPolygonMove(polygonIndex, polygon, left, bottom, scale, clickPoint);
        panel.setSelectedPointIndex(-1);
        clearNonPolygonSelections();
    }

    /**
     * Clears selections for objects other than the polygon being dragged.
     */
    private void clearNonPolygonSelections() {
        panel.setSelectedCannonIndex(-1);
        panel.setSelectedThrone(false);
        panel.setSelectedWindVane(false);
        panel.setSelectedResourceArea(null);
        panel.getEditorPanel().onPointSelectionChanged();
        panel.getEditorPanel().onCannonSelectionChanged();
    }

    /**
     * Selects the polygon under the pointer, or clears polygon selection when none is hit.
     *
     * @param castle     castle currently shown in the view
     * @param left       the castle origin x-coordinate
     * @param bottom     the castle baseline y-coordinate
     * @param scale      the rendering scale
     * @param clickPoint mouse click position in panel coordinates
     */
    private void selectClickedPolygon(Castle castle, double left, double bottom, double scale, Point2D clickPoint) {
        Polygon clickedPolygon = hitTester.findClickedPolygon(castle, left, bottom, scale, clickPoint);
        if (clickedPolygon != null) {
            selectPolygon(clickedPolygon);
            int polygonIndex = hitTester.polygonIndexOf(castle, clickedPolygon);
            if (polygonIndex >= 0) {
                startPolygonMove(polygonIndex, clickedPolygon, left, bottom, scale, clickPoint);
            }
        } else {
            selectPolygon(null);
        }
    }

    /**
     * Selects a polygon and clears selections for non-polygon elements.
     *
     * @param polygon polygon to select, or {@code null} to clear polygon selection
     */
    private void selectPolygon(Polygon polygon) {
        panel.setSelectedPolygon(polygon);
        panel.setSelectedPointIndex(-1);
        clearNonPolygonSelections();
    }

    /**
     * Converts a screen-space point to the nearest integer castle coordinate.
     *
     * @param point  screen-space point to convert to castle coordinates
     * @param left   the castle origin x-coordinate
     * @param bottom the castle baseline y-coordinate
     * @param scale  the rendering scale
     * @return the point converted to integer castle coordinates
     */
    private Point toCastlePoint(Point2D point, double left, double bottom, double scale) {
        int x = (int) Math.round((point.getX() - left) / scale);
        int y = (int) Math.round((bottom - point.getY()) / scale);
        return new Point(x, y);
    }

    /**
     * Ends the edit group and clears the current drag state.
     *
     * @param e mouse event that ended the current edit gesture
     */
    @Override
    public void mouseReleased(MouseEvent e) {
        panel.getEditorPanel().endHistoryGroup();
        polygonDragHandler.clear();
        draggingCannonIndex = -1;
        cannonDragOffset = null;
        draggingThrone = false;
        throneDragOffset = null;
        draggingWindVane = false;
        windVaneDragOffset = null;
        draggingResourceArea = null;
        draggingCornerIndex = -1;
        draggingResourceAreaMove = false;
        resourceAreaDragOffset = null;
        originalResourceSize = null;
        originalResourcePosition = null;
        dragStartPoint = null;
        panel.setDraggingResourceCorner(-1);
        autoScroller.stop();
    }

    /**
     * Updates the active dragged element and records the latest pointer location.
     *
     * @param e mouse-drag event to process
     */
    private void handleDrag(MouseEvent e) {
        if (!hasActiveDrag()) {
            return;
        }

        Castle castle = panel.getCastle();
        if (castle == null) {
            return;
        }

        double[] params = panel.getRenderingParameters();
        double left = params[0];
        double bottom = params[1];
        double scale = params[2];
        autoScroller.setDragPoint(e.getPoint());

        if (draggingResourceArea != null) {
            handleResourceAreaDrag(e.getPoint(), castle, left, bottom, scale);
            return;
        }

        if (draggingThrone) {
            handleThroneDrag(e.getPoint(), left, bottom, scale);
            return;
        }

        if (draggingWindVane) {
            handleWindVaneDrag(e.getPoint(), left, bottom, scale);
            return;
        }

        if (draggingCannonIndex >= 0) {
            handleCannonDrag(e.getPoint(), left, bottom, scale);
            return;
        }

        if (polygonDragHandler.isDragging()) {
            Point2D dragPoint = e.getPoint();
            polygonDragHandler.dragTo(dragPoint, left, bottom, scale);
            autoScroller.keepPointerVisible(dragPoint);
        }
    }

    /**
     * Returns whether any castle object or polygon is currently being dragged.
     *
     * @return {@code true} if a polygon, vertex, cannon, throne, wind vane, or resource area is being dragged
     */
    private boolean hasActiveDrag() {
        return polygonDragHandler.isDragging() || draggingCannonIndex >= 0 || draggingThrone || draggingWindVane
                || draggingResourceArea != null;
    }

    /**
     * Moves or resizes the active storage area and keeps the pointer visible while dragging.
     *
     * @param dragPoint the current drag position
     * @param castle    castle containing the storage area being resized
     * @param left      the castle origin x-coordinate
     * @param bottom    the castle baseline y-coordinate
     * @param scale     the rendering scale
     */
    private void handleResourceAreaDrag(Point2D dragPoint, Castle castle, double left, double bottom, double scale) {
        if (draggingResourceAreaMove) {
            int newX = (int) Math.round((dragPoint.getX() - resourceAreaDragOffset.getX() - left) / scale);
            int newY = (int) Math.round((bottom - (dragPoint.getY() - resourceAreaDragOffset.getY())) / scale);
            panel.getEditorPanel().updateResourceAreaPosition(draggingResourceArea, new Point(newX, newY));
        } else {
            resizeResourceArea(dragPoint, castle, scale);
        }
        autoScroller.keepPointerVisible(dragPoint);
    }

    /**
     * Resizes the active resource area in whole storage-slot increments.
     *
     * @param dragPoint the current drag position
     * @param castle    castle currently shown in the view
     * @param scale     the rendering scale
     */
    private void resizeResourceArea(Point2D dragPoint, Castle castle, double scale) {
        double deltaX = (dragPoint.getX() - dragStartPoint.getX()) / scale;
        double deltaY = (dragPoint.getY() - dragStartPoint.getY()) / scale;
        ResourceAreaResizer.ResizeResult result = resourceAreaResizer.resize(
                castle, draggingResourceArea, originalResourcePosition, originalResourceSize,
                draggingCornerIndex, deltaX, deltaY);
        panel.getEditorPanel().updateResourceAreaSize(draggingResourceArea, result.position(), result.size());
    }

    /**
     * Moves the throne to the pointer's castle coordinate and scrolls as needed.
     *
     * @param dragPoint the current drag position
     * @param left      the castle origin x-coordinate
     * @param bottom    the castle baseline y-coordinate
     * @param scale     the rendering scale
     */
    private void handleThroneDrag(Point2D dragPoint, double left, double bottom, double scale) {
        Point newPoint = toCastlePoint(dragPoint, throneDragOffset, left, bottom, scale);
        panel.getEditorPanel().updateThronePosition(newPoint);
        autoScroller.keepPointerVisible(dragPoint);
    }

    /**
     * Moves the wind vane to the pointer's castle coordinate and scrolls as needed.
     *
     * @param dragPoint the current drag position
     * @param left      the castle origin x-coordinate
     * @param bottom    the castle baseline y-coordinate
     * @param scale     the rendering scale
     */
    private void handleWindVaneDrag(Point2D dragPoint, double left, double bottom, double scale) {
        Point newPoint = toCastlePoint(dragPoint, windVaneDragOffset, left, bottom, scale);
        panel.getEditorPanel().updateWindVanePosition(newPoint);
        autoScroller.keepPointerVisible(dragPoint);
    }

    /**
     * Moves the active cannon to the pointer's castle coordinate and scrolls as needed.
     *
     * @param dragPoint the current drag position
     * @param left      the castle origin x-coordinate
     * @param bottom    the castle baseline y-coordinate
     * @param scale     the rendering scale
     */
    private void handleCannonDrag(Point2D dragPoint, double left, double bottom, double scale) {
        Point newPoint = toCastlePoint(dragPoint, cannonDragOffset, left, bottom, scale);
        panel.getEditorPanel().updateCannonPosition(draggingCannonIndex, newPoint);
        autoScroller.keepPointerVisible(dragPoint);
    }

    /**
     * Converts a screen-space point to the nearest integer castle coordinate.
     *
     * @param point  the point to convert
     * @param offset pointer-to-object offset in screen coordinates
     * @param left   the castle origin x-coordinate
     * @param bottom the castle baseline y-coordinate
     * @param scale  the rendering scale
     * @return the point converted to integer castle coordinates
     */
    private Point toCastlePoint(Point2D point, Point2D offset, double left, double bottom, double scale) {
        int x = (int) Math.round((point.getX() - offset.getX() - left) / scale);
        int y = (int) Math.round((bottom - (point.getY() - offset.getY())) / scale);
        return new Point(x, y);
    }

    /**
     * Applies viewport scrolling to the currently active drag operation.
     *
     * @param dragPoint pointer position in panel coordinates
     * @param deltaX    horizontal scroll displacement in pixels
     * @param deltaY    vertical scroll displacement in pixels
     */
    private void applyEdgeScrollDelta(Point2D dragPoint, int deltaX, int deltaY) {
        Castle castle = panel.getCastle();
        if (castle == null) {
            return;
        }
        double[] params = panel.getRenderingParameters();
        applyScrollDeltaToObject(dragPoint, deltaX, deltaY, params[0], params[1], params[2]);
    }

    /**
     * Applies edge-scroll movement to the active drag operation.
     *
     * @param deltaX        horizontal scroll displacement in pixels
     * @param deltaY        vertical scroll displacement in pixels
     * @param currentLeft   castle origin's current screen x-coordinate
     * @param currentBottom castle baseline's current screen y-coordinate
     * @param currentScale  current screen-pixel scale per castle coordinate
     */
    private void applyScrollDeltaToObject(Point2D dragPoint, int deltaX, int deltaY,
                                          double currentLeft, double currentBottom, double currentScale) {
        if (polygonDragHandler.isDragging()) {
            polygonDragHandler.adjustForScroll(
                    dragPoint, deltaX, deltaY, currentLeft, currentBottom, currentScale);
        } else {
            adjustObjectDragOffsets(deltaX, deltaY);
            updateDraggedObjectPosition(dragPoint, currentLeft, currentBottom, currentScale);
        }
    }

    /**
     * Adjusts active object-to-pointer offsets to compensate for viewport movement.
     *
     * @param deltaX horizontal scroll displacement in pixels
     * @param deltaY vertical scroll displacement in pixels
     */
    private void adjustObjectDragOffsets(int deltaX, int deltaY) {
        cannonDragOffset = adjustDragOffset(cannonDragOffset, deltaX, deltaY);
        throneDragOffset = adjustDragOffset(throneDragOffset, deltaX, deltaY);
        windVaneDragOffset = adjustDragOffset(windVaneDragOffset, deltaX, deltaY);
        resourceAreaDragOffset = adjustDragOffset(resourceAreaDragOffset, deltaX, deltaY);
    }

    /**
     * Adjusts an object-to-pointer offset to compensate for viewport movement.
     *
     * @param offset current offset, or {@code null} when that object is not being dragged
     * @param deltaX horizontal scroll displacement in pixels
     * @param deltaY vertical scroll displacement in pixels
     * @return adjusted offset, or {@code null} if the input offset is {@code null}
     */
    private Point2D adjustDragOffset(Point2D offset, int deltaX, int deltaY) {
        if (offset == null) {
            return null;
        }
        return new Point2D.Double(offset.getX() - deltaX, offset.getY() - deltaY);
    }

    /**
     * Recomputes the active element's castle coordinates from the current pointer and view transform.
     *
     * @param dragPoint     pointer position in panel coordinates
     * @param currentLeft   castle origin's current screen x-coordinate
     * @param currentBottom castle baseline's current screen y-coordinate
     * @param currentScale  current screen-pixel scale per castle coordinate
     */
    private void updateDraggedObjectPosition(Point2D dragPoint, double currentLeft, double currentBottom, double currentScale) {
        if (draggingResourceArea != null) {
            if (draggingResourceAreaMove) {
                int newX = (int) Math.round((dragPoint.getX() - resourceAreaDragOffset.getX() - currentLeft) / currentScale);
                int newY = (int) Math.round((currentBottom - (dragPoint.getY() - resourceAreaDragOffset.getY())) / currentScale);
                Point newPosition = new Point(newX, newY);
                panel.getEditorPanel().updateResourceAreaPosition(draggingResourceArea, newPosition);
            }
        } else if (draggingThrone) {
            int newX = (int) Math.round((dragPoint.getX() - throneDragOffset.getX() - currentLeft) / currentScale);
            int newY = (int) Math.round((currentBottom - (dragPoint.getY() - throneDragOffset.getY())) / currentScale);
            Point newPoint = new Point(newX, newY);
            panel.getEditorPanel().updateThronePosition(newPoint);
        } else if (draggingWindVane) {
            int newX = (int) Math.round((dragPoint.getX() - windVaneDragOffset.getX() - currentLeft) / currentScale);
            int newY = (int) Math.round((currentBottom - (dragPoint.getY() - windVaneDragOffset.getY())) / currentScale);
            Point newPoint = new Point(newX, newY);
            panel.getEditorPanel().updateWindVanePosition(newPoint);
        } else if (draggingCannonIndex >= 0) {
            int newX = (int) Math.round((dragPoint.getX() - cannonDragOffset.getX() - currentLeft) / currentScale);
            int newY = (int) Math.round((currentBottom - (dragPoint.getY() - cannonDragOffset.getY())) / currentScale);
            Point newPoint = new Point(newX, newY);
            panel.getEditorPanel().updateCannonPosition(draggingCannonIndex, newPoint);
        }
    }

}
