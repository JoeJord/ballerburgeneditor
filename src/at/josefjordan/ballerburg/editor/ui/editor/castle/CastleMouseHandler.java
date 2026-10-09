package at.josefjordan.ballerburg.editor.ui.editor.castle;

import at.josefjordan.ballerburg.editor.model.CastleDocument.*;

import java.awt.event.*;
import java.awt.geom.Point2D;

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
     * Handles click and keyboard input for polygon creation mode.
     */
    private final CastlePolygonCreationHandler polygonCreationHandler;
    /**
     * Owns polygon vertex and polygon movement state during drag gestures.
     */
    private final CastlePolygonDragHandler polygonDragHandler;
    /**
     * Owns the state and movement logic for cannons, the throne, and the wind vane.
     */
    private final CastleObjectDragHandler objectDragHandler;
    /**
     * Owns resource-area selection, movement, and resizing gestures.
     */
    private final CastleResourceAreaDragHandler resourceAreaDragHandler;
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
        polygonCreationHandler = new CastlePolygonCreationHandler(panel);
        polygonDragHandler = new CastlePolygonDragHandler(panel, hitTester);
        objectDragHandler = new CastleObjectDragHandler(panel);
        resourceAreaDragHandler = new CastleResourceAreaDragHandler(panel, hitTester, new ResourceAreaResizer());
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
                polygonCreationHandler.handleKeyPressed(e);
            }

            @Override
            public void keyReleased(KeyEvent e) {
                // Not used
            }
        };
    }

    @Override
    public void mousePressed(MouseEvent e) {
        if (panel.isInPolygonCreationMode()) {
            polygonCreationHandler.handleMousePressed(e);
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
                || resourceAreaDragHandler.startCornerDrag(castle, left, bottom, scale, clickPoint)
                || resourceAreaDragHandler.startMove(castle, left, bottom, scale, clickPoint)
                || tryStartWindVaneDrag(castle, left, bottom, scale, clickPoint)
                || tryStartThroneDrag(castle, left, bottom, scale, clickPoint)
                || tryStartCannonDrag(castle, left, bottom, scale, clickPoint)
                || tryStartSelectedPolygonDrag(castle, left, bottom, scale, clickPoint)) {
            return;
        }
        selectClickedPolygon(castle, left, bottom, scale, clickPoint);
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
        Point windVane = castle.windVane();
        double windVaneScreenX = left + windVane.x() * scale;
        double windVaneScreenY = bottom - windVane.y() * scale;
        objectDragHandler.startWindVaneDrag(new Point2D.Double(
                clickPoint.getX() - windVaneScreenX, clickPoint.getY() - windVaneScreenY));
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
        Point throne = castle.throne();
        double throneScreenX = left + throne.x() * scale;
        double throneScreenY = bottom - throne.y() * scale;
        objectDragHandler.startThroneDrag(new Point2D.Double(
                clickPoint.getX() - throneScreenX, clickPoint.getY() - throneScreenY));
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
        Point cannon = castle.cannons().get(clickedCannonIndex);
        double cannonScreenX = left + cannon.x() * scale;
        double cannonScreenY = bottom - cannon.y() * scale;
        objectDragHandler.startCannonDrag(clickedCannonIndex, new Point2D.Double(
                clickPoint.getX() - cannonScreenX, clickPoint.getY() - cannonScreenY));
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
        objectDragHandler.clear();
        resourceAreaDragHandler.clear();
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

        if (resourceAreaDragHandler.isDragging()) {
            resourceAreaDragHandler.dragTo(e.getPoint(), castle, left, bottom, scale);
            autoScroller.keepPointerVisible(e.getPoint());
            return;
        }

        if (objectDragHandler.isDragging()) {
            objectDragHandler.dragTo(e.getPoint(), left, bottom, scale);
            autoScroller.keepPointerVisible(e.getPoint());
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
        return polygonDragHandler.isDragging() || objectDragHandler.isDragging()
                || resourceAreaDragHandler.isDragging();
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
        } else if (resourceAreaDragHandler.isDragging()) {
            resourceAreaDragHandler.adjustForScroll(
                    dragPoint, deltaX, deltaY, currentLeft, currentBottom, currentScale);
        } else {
            objectDragHandler.adjustForScroll(
                    dragPoint, deltaX, deltaY, currentLeft, currentBottom, currentScale);
        }
    }

}
