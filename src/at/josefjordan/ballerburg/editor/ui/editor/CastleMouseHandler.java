package at.josefjordan.ballerburg.editor.ui.editor;

import at.josefjordan.ballerburg.editor.model.CastleDocument.*;
import at.josefjordan.ballerburg.editor.model.CastleFileFormat;

import javax.swing.*;
import java.awt.event.*;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
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
     * Index of the polygon being dragged, or {@code -1} when none is active.
     */
    private int draggingPolygonIndex = -1;
    /**
     * Index of the dragged polygon vertex, or {@code -1} when moving the whole polygon.
     */
    private int draggingPointIndex = -1;
    /**
     * Whether the active polygon drag moves all of its vertices.
     */
    private boolean draggingEntirePolygon = false;
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
     * Screen-space offset from the polygon's anchor vertex to the pointer.
     */
    private Point2D polygonDragOffset;
    /**
     * Timer that continues scrolling while a drag pointer remains near a viewport edge.
     */
    private final Timer edgeScrollTimer;
    /**
     * Most recent drag pointer position in panel coordinates.
     */
    private Point2D lastDragPoint;
    /**
     * Total screen-space displacement applied by edge scrolling during the active drag.
     */
    private Point2D accumulatedScrollDelta;

    /**
     * Installs mouse, motion, and keyboard listeners on the castle view.
     *
     * @param panel view whose objects respond to input
     */
    CastleMouseHandler(CastleViewPanel panel) {
        this.panel = panel;

        // Initialize edge scroll timer for continuous dragging at screen edge
        edgeScrollTimer = new Timer(50, e -> {
            if (lastDragPoint != null) {
                handleEdgeScrolling();
            }
        });

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
        int clickedCorner = findClickedResourceCorner(castle, selectedResourceArea, left, bottom, scale, clickPoint);
        if (clickedCorner < 0) {
            return false;
        }
        draggingResourceArea = selectedResourceArea;
        draggingCornerIndex = clickedCorner;
        dragStartPoint = clickPoint;
        originalResourceSize = getResourceSize(castle, selectedResourceArea);
        originalResourcePosition = getResourcePosition(castle, selectedResourceArea);
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
        ResourceArea clickedResourceArea = findClickedResourceArea(castle, left, bottom, scale, clickPoint);
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
        Point resourcePosition = getResourcePosition(castle, clickedResourceArea);
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
        if (!isWindVaneClicked(castle, left, bottom, scale, clickPoint)) {
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
        if (!isThroneClicked(castle, left, bottom, scale, clickPoint)) {
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
        int clickedCannonIndex = findClickedCannonIndex(castle, left, bottom, scale, clickPoint);
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
        int polygonIndex = findPolygonIndex(castle, selectedPolygon);
        if (polygonIndex < 0) {
            return false;
        }
        int pointIndex = findClickedPointIndex(selectedPolygon, left, bottom, scale, clickPoint);
        if (pointIndex >= 0) {
            startPolygonPointDrag(polygonIndex, pointIndex);
            return true;
        }
        if (!createPolygonPath(selectedPolygon, left, bottom, scale).contains(clickPoint)) {
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
        draggingPolygonIndex = polygonIndex;
        draggingPointIndex = pointIndex;
        draggingEntirePolygon = false;
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
        draggingPolygonIndex = polygonIndex;
        draggingPointIndex = -1;
        draggingEntirePolygon = true;
        polygonDragOffset = createPolygonDragOffset(polygon, left, bottom, scale, clickPoint);
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
        Polygon clickedPolygon = findClickedPolygon(castle, left, bottom, scale, clickPoint);
        if (clickedPolygon != null) {
            selectPolygon(clickedPolygon);
            int polygonIndex = findPolygonIndex(castle, clickedPolygon);
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
        draggingPolygonIndex = -1;
        draggingPointIndex = -1;
        draggingEntirePolygon = false;
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
        polygonDragOffset = null;
        panel.setDraggingResourceCorner(-1);
        lastDragPoint = null;
        accumulatedScrollDelta = null;
        edgeScrollTimer.stop();
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
        lastDragPoint = e.getPoint();
        accumulatedScrollDelta = null;

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

        if (draggingEntirePolygon) {
            Point2D dragPoint = e.getPoint();
            updateDraggedPolygonPosition(dragPoint, left, bottom, scale);
            autoScrollToKeepVisible(dragPoint);
        } else if (draggingPointIndex >= 0) {
            handlePolygonPointDrag(e.getPoint(), left, bottom, scale);
        }
    }

    /**
     * Returns whether any castle object or polygon is currently being dragged.
     *
     * @return {@code true} if a polygon, vertex, cannon, throne, wind vane, or resource area is being dragged
     */
    private boolean hasActiveDrag() {
        return draggingPolygonIndex >= 0 || draggingCannonIndex >= 0 || draggingThrone || draggingWindVane
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
        autoScrollToKeepVisible(dragPoint);
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
        int stepX = getResourceStepX(draggingResourceArea);
        int stepY = getResourceStepY(draggingResourceArea);
        int resourceCount = getResourceCount(castle, draggingResourceArea);
        Size minSize = calculateMinimumSize(resourceCount, stepX, stepY);
        int deltaXRounded = (int) Math.round(deltaX / stepX) * stepX;
        int deltaYRounded = (int) Math.round(deltaY / stepY) * stepY;
        Size newSize = calculateNewResourceSize(deltaXRounded, deltaYRounded, minSize, resourceCount, stepX, stepY);

        Point newPosition = calculateNewResourcePosition(newSize);
        panel.getEditorPanel().updateResourceAreaSize(draggingResourceArea, newPosition, newSize);
    }

    /**
     * Keeps the opposite corner fixed while resizing the selected corner.
     *
     * @param newSize resized storage-area dimensions
     * @return the calculated new resource position
     */
    private Point calculateNewResourcePosition(Size newSize) {
        int newWidth = newSize.width();
        int newHeight = newSize.height();
        return switch (draggingCornerIndex) {
            case 0 -> new Point(originalResourcePosition.x() + originalResourceSize.width() - newWidth,
                    originalResourcePosition.y() + originalResourceSize.height() - newHeight);
            case 1 -> new Point(originalResourcePosition.x(),
                    originalResourcePosition.y() + originalResourceSize.height() - newHeight);
            case 2 -> new Point(originalResourcePosition.x() + originalResourceSize.width() - newWidth,
                    originalResourcePosition.y());
            default -> originalResourcePosition;
        };
    }

    /**
     * Calculates a slot-aligned resource-area size that fits its resource count.
     *
     * @param deltaXRounded horizontal resize change rounded to a storage-slot increment
     * @param deltaYRounded vertical resize change rounded to a storage-slot increment
     * @param minSize       minimum dimensions needed to hold the current contents
     * @param resourceCount number of resource items that must fit
     * @param stepX         horizontal distance between storage slots
     * @param stepY         vertical distance between storage slots
     * @return the calculated new resource size
     */
    private Size calculateNewResourceSize(int deltaXRounded, int deltaYRounded, Size minSize,
                                          int resourceCount, int stepX, int stepY) {
        Size resizedSize = switch (draggingCornerIndex) {
            case 0 -> new Size(originalResourceSize.width() - deltaXRounded,
                    originalResourceSize.height() + deltaYRounded);
            case 1 -> new Size(originalResourceSize.width() + deltaXRounded,
                    originalResourceSize.height() + deltaYRounded);
            case 2 -> new Size(originalResourceSize.width() - deltaXRounded,
                    originalResourceSize.height() - deltaYRounded);
            case 3 -> new Size(originalResourceSize.width() + deltaXRounded,
                    originalResourceSize.height() - deltaYRounded);
            default -> originalResourceSize;
        };

        int width = Math.max(minSize.width(), (resizedSize.width() / stepX) * stepX);
        int height = Math.max(minSize.height(), (resizedSize.height() / stepY) * stepY);
        if (!canFitResources(resourceCount, width, height, stepX, stepY)) {
            Size validSize = findNearestValidSize(resourceCount, width, height, stepX, stepY);
            width = validSize.width();
            height = validSize.height();
        }
        return new Size(width, height);
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
        autoScrollToKeepVisible(dragPoint);
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
        autoScrollToKeepVisible(dragPoint);
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
        autoScrollToKeepVisible(dragPoint);
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
     * Moves the active polygon vertex to the pointer's castle coordinate.
     *
     * @param dragPoint the current drag position
     * @param left      the castle origin x-coordinate
     * @param bottom    the castle baseline y-coordinate
     * @param scale     the rendering scale
     */
    private void handlePolygonPointDrag(Point2D dragPoint, double left, double bottom, double scale) {
        Point newPoint = toCastlePoint(dragPoint, left, bottom, scale);
        panel.getEditorPanel().updatePolygonPoint(draggingPolygonIndex, draggingPointIndex, newPoint);
        autoScrollToKeepVisible(dragPoint);

        Castle updatedCastle = panel.getCastle();
        int currentPolygonIndex = 0;
        for (DrawingCommand command : updatedCastle.drawing()) {
            if (command instanceof Polygon polygon) {
                if (currentPolygonIndex == draggingPolygonIndex) {
                    panel.setSelectedPolygon(polygon);
                    break;
                }
                currentPolygonIndex++;
            }
        }
    }

    /**
     * Scrolls the viewport when the drag pointer approaches its edges.
     *
     * @param dragPoint the current drag position
     */
    private void autoScrollToKeepVisible(Point2D dragPoint) {
        // Get the scroll pane from the panel's viewport
        if (panel.getParent() instanceof javax.swing.JViewport viewport) {
            javax.swing.JScrollPane scrollPane = (javax.swing.JScrollPane) viewport.getParent();
            if (scrollPane == null) {
                return;
            }

            java.awt.Rectangle viewRect = viewport.getViewRect();
            int margin = 50; // Margin from edge before scrolling starts

            // The drag point from e.getPoint() is in panel coordinates
            int mouseX = (int) dragPoint.getX();
            int mouseY = (int) dragPoint.getY();

            // Calculate viewport-relative position
            int viewportX = mouseX - viewRect.x;
            int viewportY = mouseY - viewRect.y;

            // Calculate the desired scroll position to keep the point visible
            int scrollX = viewRect.x;
            int scrollY = viewRect.y;
            boolean needsScroll = false;

            // Check if mouse is near left edge of viewport
            if (viewportX < margin) {
                scrollX = Math.max(0, mouseX - margin);
                needsScroll = true;
            }
            // Check if mouse is near right edge of viewport
            else if (viewportX > viewRect.width - margin) {
                int maxScrollX = Math.max(0, panel.getPreferredSize().width - viewRect.width);
                scrollX = Math.clamp((long) mouseX - viewRect.width + margin, 0, maxScrollX);
                needsScroll = true;
            }

            // Check if mouse is near top edge of viewport
            if (viewportY < margin) {
                scrollY = Math.max(0, mouseY - margin);
                needsScroll = true;
            }
            // Check if mouse is near bottom edge of viewport
            else if (viewportY > viewRect.height - margin) {
                int maxScrollY = Math.max(0, panel.getPreferredSize().height - viewRect.height);
                scrollY = Math.clamp((long) mouseY - viewRect.height + margin, 0, maxScrollY);
                needsScroll = true;
            }

            if (needsScroll) {
                viewport.setViewPosition(new java.awt.Point(scrollX, scrollY));
                // Start edge scroll timer for continuous scrolling
                if (!edgeScrollTimer.isRunning()) {
                    edgeScrollTimer.start();
                }
            } else {
                // Stop timer if not near edge
                edgeScrollTimer.stop();
            }
        }
    }

    /**
     * Advances scrolling while the last drag pointer remains near a viewport edge.
     */
    private void handleEdgeScrolling() {
        if (lastDragPoint == null) {
            return;
        }

        javax.swing.JViewport viewport = getScrollViewport();
        if (viewport == null) {
            return;
        }
        java.awt.Rectangle viewRect = viewport.getViewRect();
        int mouseX = (int) lastDragPoint.getX();
        int mouseY = (int) lastDragPoint.getY();
        int viewportX = mouseX - viewRect.x;
        int viewportY = mouseY - viewRect.y;
        boolean nearLeft = viewportX < 50;
        boolean nearRight = viewportX > viewRect.width - 50;
        boolean nearTop = viewportY < 50;
        boolean nearBottom = viewportY > viewRect.height - 50;
        if (!(nearLeft || nearRight || nearTop || nearBottom)) {
            edgeScrollTimer.stop();
            return;
        }

        int scrollX = calculateEdgeScroll(viewRect.x, panel.getPreferredSize().width - viewRect.width, nearLeft, nearRight);
        int scrollY = calculateEdgeScroll(viewRect.y, panel.getPreferredSize().height - viewRect.height, nearTop, nearBottom);
        viewport.setViewPosition(new java.awt.Point(scrollX, scrollY));
        updateDraggedObjectForEdgeScroll(viewRect, scrollX, scrollY, nearLeft, nearRight, nearTop, nearBottom);
    }

    /**
     * Returns the scroll viewport.
     *
     * @return the containing scroll viewport, or null when unavailable
     */
    private javax.swing.JViewport getScrollViewport() {
        if (panel.getParent() instanceof javax.swing.JViewport viewport
                && viewport.getParent() instanceof javax.swing.JScrollPane) {
            return viewport;
        }
        return null;
    }

    /**
     * Calculates a bounded 10-pixel scroll step toward the nearby edge.
     *
     * @param currentPosition current viewport position along one axis
     * @param contentOverflow maximum scroll position along that axis
     * @param nearStart       whether the pointer is near the left or top edge
     * @param nearEnd         whether the pointer is near the right or bottom edge
     * @return next viewport position along the axis
     */
    private int calculateEdgeScroll(int currentPosition, int contentOverflow, boolean nearStart, boolean nearEnd) {
        if (nearStart) {
            return Math.max(0, currentPosition - 10);
        }
        if (nearEnd) {
            int maxScroll = Math.max(0, contentOverflow);
            return Math.clamp((long) currentPosition + 10, 0, maxScroll);
        }
        return currentPosition;
    }

    /**
     * Reapplies the drag after scrolling, including fallback displacement at a scroll limit.
     *
     * @param viewRect   viewport bounds before the scroll
     * @param scrollX    new horizontal viewport position
     * @param scrollY    new vertical viewport position
     * @param nearLeft   whether the pointer is near the viewport's left edge
     * @param nearRight  whether the pointer is near the viewport's right edge
     * @param nearTop    whether the pointer is near the viewport's top edge
     * @param nearBottom whether the pointer is near the viewport's bottom edge
     */
    private void updateDraggedObjectForEdgeScroll(java.awt.Rectangle viewRect, int scrollX, int scrollY,
                                                  boolean nearLeft, boolean nearRight,
                                                  boolean nearTop, boolean nearBottom) {
        Castle castle = panel.getCastle();
        if (castle == null) {
            return;
        }
        double[] params = panel.getRenderingParameters();
        int deltaX = effectiveScrollDelta(scrollX - viewRect.x, nearLeft, nearRight);
        int deltaY = effectiveScrollDelta(scrollY - viewRect.y, nearTop, nearBottom);
        accumulateScrollDelta(deltaX, deltaY);
        applyScrollDeltaToObject(deltaX, deltaY, params[0], params[1], params[2]);
    }

    /**
     * Returns the scroll delta, using a 10-pixel fallback when movement is constrained.
     *
     * @param actualDelta actual viewport displacement in pixels
     * @param nearStart   whether the pointer is near the left or top edge
     * @param nearEnd     whether the pointer is near the right or bottom edge
     * @return the actual or fallback scroll displacement in pixels
     */
    private int effectiveScrollDelta(int actualDelta, boolean nearStart, boolean nearEnd) {
        if (actualDelta != 0) {
            return actualDelta;
        }
        if (nearStart) {
            return -10;
        }
        return nearEnd ? 10 : 0;
    }

    /**
     * Adds the latest edge-scroll movement to the accumulated delta.
     *
     * @param deltaX horizontal scroll displacement in pixels
     * @param deltaY vertical scroll displacement in pixels
     */
    private void accumulateScrollDelta(int deltaX, int deltaY) {
        if (accumulatedScrollDelta == null) {
            accumulatedScrollDelta = new Point2D.Double(deltaX, deltaY);
        } else {
            accumulatedScrollDelta.setLocation(
                    accumulatedScrollDelta.getX() + deltaX,
                    accumulatedScrollDelta.getY() + deltaY
            );
        }
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
    private void applyScrollDeltaToObject(int deltaX, int deltaY, double currentLeft, double currentBottom, double currentScale) {
        if (draggingEntirePolygon) {
            adjustPolygonDragOffset(deltaX, deltaY);
            updateDraggedObjectPosition(lastDragPoint, currentLeft, currentBottom, currentScale);
        } else if (draggingPointIndex >= 0) {
            shiftDraggedPolygonPoint(deltaX, deltaY, currentScale);
        } else {
            adjustObjectDragOffsets(deltaX, deltaY);
            updateDraggedObjectPosition(lastDragPoint, currentLeft, currentBottom, currentScale);
        }
    }

    /**
     * Adjusts the polygon's pointer offset to compensate for viewport movement.
     *
     * @param deltaX horizontal scroll displacement in pixels
     * @param deltaY vertical scroll displacement in pixels
     */
    private void adjustPolygonDragOffset(int deltaX, int deltaY) {
        if (polygonDragOffset != null) {
            polygonDragOffset = new Point2D.Double(
                    polygonDragOffset.getX() - deltaX,
                    polygonDragOffset.getY() - deltaY);
        }
    }

    /**
     * Moves the dragged polygon vertex by the scroll delta.
     *
     * @param deltaX       horizontal scroll displacement in pixels
     * @param deltaY       vertical scroll displacement in pixels
     * @param currentScale current screen-pixel scale per castle coordinate
     */
    private void shiftDraggedPolygonPoint(int deltaX, int deltaY, double currentScale) {
        double castleDeltaX = deltaX / currentScale;
        double castleDeltaY = -deltaY / currentScale;
        Point currentPoint = getCurrentPolygonPoint(draggingPolygonIndex, draggingPointIndex);
        if (currentPoint != null) {
            int newX = (int) Math.round(currentPoint.x() + castleDeltaX);
            int newY = (int) Math.round(currentPoint.y() + castleDeltaY);
            panel.getEditorPanel().updatePolygonPoint(draggingPolygonIndex, draggingPointIndex,
                    new Point(newX, newY));
        }
        shiftDragStartPoint(deltaX, deltaY);
    }

    /**
     * Adjusts the drag origin to account for viewport scrolling.
     *
     * @param deltaX horizontal scroll displacement in pixels
     * @param deltaY vertical scroll displacement in pixels
     */
    private void shiftDragStartPoint(int deltaX, int deltaY) {
        if (dragStartPoint != null) {
            dragStartPoint = new Point2D.Double(
                    dragStartPoint.getX() + deltaX,
                    dragStartPoint.getY() + deltaY);
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
        } else if (draggingEntirePolygon) {
            updateDraggedPolygonPosition(dragPoint, currentLeft, currentBottom, currentScale);
        } else if (draggingPointIndex >= 0) {
            int newX = (int) Math.round((dragPoint.getX() - currentLeft) / currentScale);
            int newY = (int) Math.round((currentBottom - dragPoint.getY()) / currentScale);
            Point newPoint = new Point(newX, newY);
            panel.getEditorPanel().updatePolygonPoint(draggingPolygonIndex, draggingPointIndex, newPoint);
        }
    }

    /**
     * Translates every vertex so the polygon's first vertex follows the pointer.
     *
     * @param dragPoint     pointer position in panel coordinates
     * @param currentLeft   castle origin's current screen x-coordinate
     * @param currentBottom castle baseline's current screen y-coordinate
     * @param currentScale  current screen-pixel scale per castle coordinate
     */
    private void updateDraggedPolygonPosition(Point2D dragPoint, double currentLeft,
                                              double currentBottom, double currentScale) {
        Polygon polygon = getPolygon(draggingPolygonIndex);
        if (polygon == null || polygonDragOffset == null) {
            return;
        }

        Point anchor = polygon.points().getFirst();
        int targetX = (int) Math.round(
                (dragPoint.getX() - polygonDragOffset.getX() - currentLeft) / currentScale);
        int targetY = (int) Math.round(
                (currentBottom - (dragPoint.getY() - polygonDragOffset.getY())) / currentScale);
        int deltaX = targetX - anchor.x();
        int deltaY = targetY - anchor.y();

        List<Point> newPoints = new ArrayList<>();
        for (Point point : polygon.points()) {
            newPoints.add(new Point(point.x() + deltaX, point.y() + deltaY));
        }
        panel.getEditorPanel().updatePolygonPoints(draggingPolygonIndex, newPoints);

        Polygon updatedPolygon = getPolygon(draggingPolygonIndex);
        if (updatedPolygon != null) {
            panel.setSelectedPolygon(updatedPolygon);
        }
    }

    /**
     * Calculates the screen-space offset from the click to the polygon's first vertex.
     *
     * @param polygon    polygon being moved
     * @param left       the castle origin x-coordinate
     * @param bottom     the castle baseline y-coordinate
     * @param scale      the rendering scale
     * @param clickPoint click position in panel coordinates
     * @return screen-space difference between the click and first vertex
     */
    private Point2D createPolygonDragOffset(Polygon polygon, double left, double bottom,
                                            double scale, Point2D clickPoint) {
        Point anchor = polygon.points().getFirst();
        double anchorScreenX = left + anchor.x() * scale;
        double anchorScreenY = bottom - anchor.y() * scale;
        return new Point2D.Double(clickPoint.getX() - anchorScreenX,
                clickPoint.getY() - anchorScreenY);
    }

    /**
     * Returns the castle polygon at the specified polygon-only index.
     *
     * @param polygonIndex zero-based index among polygon drawing commands
     * @return the polygon, or {@code null} if the castle or index is unavailable
     */
    private Polygon getPolygon(int polygonIndex) {
        Castle castle = panel.getCastle();
        if (castle == null) {
            return null;
        }
        int currentPolygonIndex = 0;
        for (DrawingCommand command : castle.drawing()) {
            if (command instanceof Polygon polygon) {
                if (currentPolygonIndex == polygonIndex) {
                    return polygon;
                }
                currentPolygonIndex++;
            }
        }
        return null;
    }

    /**
     * Returns a vertex from the currently displayed castle.
     *
     * @param polygonIndex zero-based index among polygon drawing commands
     * @param pointIndex   zero-based vertex index within that polygon
     * @return the vertex, or {@code null} if the castle or either index is unavailable
     */
    private Point getCurrentPolygonPoint(int polygonIndex, int pointIndex) {
        Castle castle = panel.getCastle();
        if (castle == null) {
            return null;
        }
        int currentPolygonIndex = 0;
        for (DrawingCommand command : castle.drawing()) {
            if (command instanceof Polygon(List<Point> points)) {
                if (currentPolygonIndex == polygonIndex) {
                    if (pointIndex >= 0 && pointIndex < points.size()) {
                        return points.get(pointIndex);
                    }
                    break;
                }
                currentPolygonIndex++;
            }
        }
        return null;
    }

    /**
     * Finds the first polygon whose rendered interior contains the click.
     *
     * @param castle     castle whose polygons are hit-tested
     * @param left       the castle origin x-coordinate
     * @param bottom     the castle baseline y-coordinate
     * @param scale      the rendering scale
     * @param clickPoint mouse click position in panel coordinates
     * @return the hit polygon, or {@code null} when no polygon contains the click
     */
    private Polygon findClickedPolygon(Castle castle, double left, double bottom, double scale, Point2D clickPoint) {
        for (DrawingCommand command : castle.drawing()) {
            if (command instanceof Polygon polygon) {
                Path2D path = createPolygonPath(polygon, left, bottom, scale);
                if (path.contains(clickPoint)) {
                    return polygon;
                }
            }
        }
        return null;
    }

    /**
     * Transforms a castle polygon into a closed screen-space path.
     *
     * @param polygon polygon to transform
     * @param left    the castle origin x-coordinate
     * @param bottom  the castle baseline y-coordinate
     * @param scale   the rendering scale
     * @return closed path in panel coordinates
     */
    private Path2D createPolygonPath(Polygon polygon, double left, double bottom, double scale) {
        Path2D path = new Path2D.Double();
        Point first = polygon.points().getFirst();
        path.moveTo(left + first.x() * scale, bottom - first.y() * scale);
        for (int i = 1; i < polygon.points().size(); i++) {
            Point point = polygon.points().get(i);
            path.lineTo(left + point.x() * scale, bottom - point.y() * scale);
        }
        path.closePath();
        return path;
    }

    /**
     * Finds a polygon's position among the castle's polygon drawing commands.
     *
     * @param castle        castle whose drawing commands are searched
     * @param targetPolygon polygon instance to locate
     * @return zero-based polygon index, or {@code -1} if the instance is absent
     */
    private int findPolygonIndex(Castle castle, Polygon targetPolygon) {
        int index = 0;
        for (DrawingCommand command : castle.drawing()) {
            if (command instanceof Polygon polygon) {
                if (polygon == targetPolygon) {
                    return index;
                }
                index++;
            }
        }
        return -1;
    }

    /**
     * Finds a vertex within the click hit radius.
     *
     * @param polygon    polygon whose vertices are checked
     * @param left       the castle origin x-coordinate
     * @param bottom     the castle baseline y-coordinate
     * @param scale      the rendering scale
     * @param clickPoint mouse click position in panel coordinates
     * @return zero-based vertex index, or {@code -1} when no vertex is close enough
     */
    private int findClickedPointIndex(Polygon polygon, double left, double bottom, double scale, Point2D clickPoint) {
        final double hitRadius = 6.0;
        for (int i = 0; i < polygon.points().size(); i++) {
            Point point = polygon.points().get(i);
            double screenX = left + point.x() * scale;
            double screenY = bottom - point.y() * scale;
            double distance = clickPoint.distance(screenX, screenY);
            if (distance <= hitRadius) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Finds the cannon whose rendered bounds contain the click.
     *
     * @param castle     castle whose cannon positions are checked
     * @param left       the castle origin x-coordinate
     * @param bottom     the castle baseline y-coordinate
     * @param scale      the rendering scale
     * @param clickPoint mouse click position in panel coordinates
     * @return zero-based cannon index, or {@code -1} when no cannon is hit
     */
    private int findClickedCannonIndex(Castle castle, double left, double bottom, double scale, Point2D clickPoint) {
        for (int i = 0; i < castle.cannons().size(); i++) {
            Point cannon = castle.cannons().get(i);
            Rectangle2D cannonRect = CastleViewPanel.calculateCannonRectangle(cannon, left, bottom, scale);
            if (cannonRect.contains(clickPoint)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Returns whether the pointer is inside the wind-vane bounds.
     *
     * @param castle     castle currently shown in the view
     * @param left       the castle origin x-coordinate
     * @param bottom     the castle baseline y-coordinate
     * @param scale      the rendering scale
     * @param clickPoint mouse click position in panel coordinates
     * @return {@code true} if the wind vane is positioned and its rendered bounds contain the pointer
     */
    private boolean isWindVaneClicked(Castle castle, double left, double bottom, double scale, Point2D clickPoint) {
        Point windVane = castle.windVane();
        if (windVane.x() == Integer.MIN_VALUE || windVane.y() == Integer.MIN_VALUE) {
            return false;
        }
        Rectangle2D windVaneRect = CastleViewPanel.calculateWindVaneRectangle(windVane, left, bottom, scale);
        return windVaneRect.contains(clickPoint);
    }

    /**
     * Returns whether the pointer is inside the throne bounds.
     *
     * @param castle     castle currently shown in the view
     * @param left       the castle origin x-coordinate
     * @param bottom     the castle baseline y-coordinate
     * @param scale      the rendering scale
     * @param clickPoint mouse click position in panel coordinates
     * @return {@code true} if the throne is positioned and its rendered bounds contain the pointer
     */
    private boolean isThroneClicked(Castle castle, double left, double bottom, double scale, Point2D clickPoint) {
        Point throne = castle.throne();
        if (throne.x() == Integer.MIN_VALUE || throne.y() == Integer.MIN_VALUE) {
            return false;
        }
        Rectangle2D throneRect = new Rectangle2D.Double(left + throne.x() * scale,
                bottom - (throne.y() + CastleFileFormat.THRONE_HEIGHT) * scale,
                CastleFileFormat.THRONE_WIDTH * scale, CastleFileFormat.THRONE_HEIGHT * scale);
        return throneRect.contains(clickPoint);
    }

    /**
     * Finds the storage area whose rendered rectangle contains the click.
     *
     * @param castle     castle whose storage areas are checked
     * @param left       the castle origin x-coordinate
     * @param bottom     the castle baseline y-coordinate
     * @param scale      the rendering scale
     * @param clickPoint mouse click position in panel coordinates
     * @return the hit storage area, or {@code null} when no area contains the click
     */
    private ResourceArea findClickedResourceArea(Castle castle, double left, double bottom, double scale, Point2D clickPoint) {
        // Check money area
        Rectangle2D moneyRect = new Rectangle2D.Double(left + castle.money().x() * scale,
                bottom - (castle.money().y() + castle.moneyRoom().height()) * scale,
                castle.moneyRoom().width() * scale, castle.moneyRoom().height() * scale);
        if (moneyRect.contains(clickPoint)) {
            return ResourceArea.MONEY;
        }

        // Check powder area
        Rectangle2D powderRect = new Rectangle2D.Double(left + castle.powder().x() * scale,
                bottom - (castle.powder().y() + castle.powderRoom().height()) * scale,
                castle.powderRoom().width() * scale, castle.powderRoom().height() * scale);
        if (powderRect.contains(clickPoint)) {
            return ResourceArea.POWDER;
        }

        // Check balls area
        Rectangle2D ballsRect = new Rectangle2D.Double(left + castle.balls().x() * scale,
                bottom - (castle.balls().y() + castle.ballRoom().height()) * scale,
                castle.ballRoom().width() * scale, castle.ballRoom().height() * scale);
        if (ballsRect.contains(clickPoint)) {
            return ResourceArea.BALLS;
        }

        return null;
    }

    /**
     * Finds the resize handle within the pointer hit radius.
     *
     * @param castle       castle containing the selected storage area
     * @param resourceArea storage area whose corners are checked
     * @param left         the castle origin x-coordinate
     * @param bottom       the castle baseline y-coordinate
     * @param scale        the rendering scale
     * @param clickPoint   mouse click position in panel coordinates
     * @return corner index (bottom-left 0, bottom-right 1, top-left 2, top-right 3), or {@code -1}
     */
    private int findClickedResourceCorner(Castle castle, ResourceArea resourceArea, double left, double bottom, double scale, Point2D clickPoint) {
        final double hitRadius = 6.0;
        Point position = getResourcePosition(castle, resourceArea);
        Size size = getResourceSize(castle, resourceArea);

        double x = left + position.x() * scale;
        double y = bottom - (position.y() + size.height()) * scale;
        double width = size.width() * scale;
        double height = size.height() * scale;

        // Check bottom-left corner (index 0)
        double blY = y + height;
        if (clickPoint.distance(x, blY) <= hitRadius) {
            return 0;
        }

        // Check bottom-right corner (index 1)
        double brX = x + width;
        double brY = y + height;
        if (clickPoint.distance(brX, brY) <= hitRadius) {
            return 1;
        }

        // Check top-left corner (index 2)
        if (clickPoint.distance(x, y) <= hitRadius) {
            return 2;
        }

        // Check top-right corner (index 3)
        double trX = x + width;
        if (clickPoint.distance(trX, y) <= hitRadius) {
            return 3;
        }

        return -1;
    }

    /**
     * Returns the lower-left coordinate of the selected storage area.
     *
     * @param castle       castle containing the storage area
     * @param resourceArea storage area to query
     * @return its lower-left castle coordinate
     */
    private Point getResourcePosition(Castle castle, ResourceArea resourceArea) {
        return switch (resourceArea) {
            case MONEY -> castle.money();
            case POWDER -> castle.powder();
            case BALLS -> castle.balls();
        };
    }

    /**
     * Returns the dimensions of the selected storage area.
     *
     * @param castle       castle containing the storage area
     * @param resourceArea storage area to query
     * @return its width and height in castle coordinates
     */
    private Size getResourceSize(Castle castle, ResourceArea resourceArea) {
        return switch (resourceArea) {
            case MONEY -> castle.moneyRoom();
            case POWDER -> castle.powderRoom();
            case BALLS -> castle.ballRoom();
        };
    }

    /**
     * Returns the horizontal resource-slot spacing.
     *
     * @param resourceArea storage area whose slot spacing is requested
     * @return width of one resource slot in castle coordinates
     */
    private int getResourceStepX(ResourceArea resourceArea) {
        return switch (resourceArea) {
            case MONEY -> CastleFileFormat.MONEY_WIDTH;
            case POWDER -> CastleFileFormat.POWDER_WIDTH;
            case BALLS -> CastleFileFormat.CANNONBALL_WIDTH;
        };
    }

    /**
     * Returns the vertical resource-slot spacing.
     *
     * @param resourceArea storage area whose slot spacing is requested
     * @return height of one resource slot in castle coordinates
     */
    private int getResourceStepY(ResourceArea resourceArea) {
        return switch (resourceArea) {
            case MONEY -> CastleFileFormat.MONEY_HEIGHT;
            case POWDER -> CastleFileFormat.POWDER_HEIGHT;
            case BALLS -> CastleFileFormat.CANNONBALL_HEIGHT;
        };
    }

    /**
     * Returns the number of occupied sprite slots required by the starting quantity.
     *
     * @param castle       castle containing the starting resource values
     * @param resourceArea resource type whose count is requested
     * @return occupied slot count for that resource type
     */
    private int getResourceCount(Castle castle, ResourceArea resourceArea) {
        return switch (resourceArea) {
            case MONEY -> divideRoundedUp(castle.resources().money(), CastleFileFormat.MONEY_PER_UNIT);
            case POWDER -> divideRoundedUp(castle.resources().powder(), CastleFileFormat.POWDER_PER_UNIT);
            case BALLS -> castle.resources().balls();
        };
    }

    /**
     * Returns zero for non-positive values; otherwise rounds the quotient up to the next integer.
     *
     * @param value   starting quantity to convert to slots
     * @param divisor quantity represented by each slot
     * @return zero for non-positive values, otherwise the quotient rounded upward
     */
    private int divideRoundedUp(int value, int divisor) {
        return value <= 0 ? 0 : (value + divisor - 1) / divisor;
    }

    /**
     * Finds the smallest width and height that can hold the requested number of slots.
     *
     * @param resourceCount number of items that must fit
     * @param stepX         slot width in castle coordinates
     * @param stepY         slot height in castle coordinates
     * @return minimum slot-aligned storage dimensions
     */
    private Size calculateMinimumSize(int resourceCount, int stepX, int stepY) {
        if (resourceCount <= 0) {
            return new Size(stepX, stepY);
        }
        // Find the minimum dimensions that can fit the resources in any arrangement
        // Try different column counts from 1 to resourceCount
        int minWidth = Integer.MAX_VALUE;
        int minHeight = Integer.MAX_VALUE;

        for (int columns = 1; columns <= resourceCount; columns++) {
            int rows = (int) Math.ceil((double) resourceCount / columns);
            int width = columns * stepX;
            int height = rows * stepY;

            // Track the minimum for each dimension across all valid arrangements
            minWidth = Math.min(minWidth, width);
            minHeight = Math.min(minHeight, height);
        }

        return new Size(minWidth, minHeight);
    }

    /**
     * Returns whether the storage dimensions can hold the requested resource slots.
     *
     * @param resourceCount number of items that must fit
     * @param width         proposed storage width in castle coordinates
     * @param height        proposed storage height in castle coordinates
     * @param stepX         slot width in castle coordinates
     * @param stepY         slot height in castle coordinates
     * @return {@code true} if the complete slots in the area can hold every item
     */
    private boolean canFitResources(int resourceCount, int width, int height, int stepX, int stepY) {
        if (resourceCount <= 0) {
            return true;
        }
        int columns = width / stepX;
        int rows = height / stepY;
        if (columns <= 0 || rows <= 0) {
            return false;
        }
        return columns * rows >= resourceCount;
    }

    /**
     * Finds the nearest slot-aligned size that can contain all resources.
     *
     * @param resourceCount number of items that must fit
     * @param targetWidth   preferred storage width in castle coordinates
     * @param targetHeight  preferred storage height in castle coordinates
     * @param stepX         slot width in castle coordinates
     * @param stepY         slot height in castle coordinates
     * @return the slot-aligned dimensions closest to the target that fit every item
     */
    private Size findNearestValidSize(int resourceCount, int targetWidth, int targetHeight, int stepX, int stepY) {
        if (resourceCount <= 0) {
            // For zero resources, just ensure minimum size of one unit
            return new Size(Math.max(stepX, targetWidth), Math.max(stepY, targetHeight));
        }

        // Ensure target dimensions are at least one unit
        targetWidth = Math.max(stepX, targetWidth);
        targetHeight = Math.max(stepY, targetHeight);

        // Try to find a valid size close to the target
        // First try keeping the width and adjusting height to fit resources
        int columns = Math.max(1, targetWidth / stepX);
        int rows = (int) Math.ceil((double) resourceCount / columns);
        int validHeight = rows * stepY;
        Size sizeWithFixedWidth = new Size(columns * stepX, validHeight);

        // Then try keeping the height and adjusting width to fit resources
        rows = Math.max(1, targetHeight / stepY);
        columns = (int) Math.ceil((double) resourceCount / rows);
        int validWidth = columns * stepX;
        Size sizeWithFixedHeight = new Size(validWidth, rows * stepY);

        // Choose the option closest to the target size
        double diffWithFixedWidth = (double) Math.abs(sizeWithFixedWidth.width() - targetWidth) + Math.abs(sizeWithFixedWidth.height() - targetHeight);
        double diffWithFixedHeight = (double) Math.abs(sizeWithFixedHeight.width() - targetWidth) + Math.abs(sizeWithFixedHeight.height() - targetHeight);

        if (diffWithFixedWidth <= diffWithFixedHeight) {
            return sizeWithFixedWidth;
        } else {
            return sizeWithFixedHeight;
        }
    }
}
