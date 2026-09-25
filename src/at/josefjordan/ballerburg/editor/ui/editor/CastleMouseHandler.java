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
 * Handles mouse interactions for the castle view (selection, editing, etc.).
 */
final class CastleMouseHandler extends MouseAdapter {
    private final CastleViewPanel panel;
    private int draggingPolygonIndex = -1;
    private int draggingPointIndex = -1;
    private boolean draggingEntirePolygon = false;
    private int draggingCannonIndex = -1;
    private Point2D cannonDragOffset;
    private boolean draggingThrone = false;
    private Point2D throneDragOffset;
    private boolean draggingWindVane = false;
    private Point2D windVaneDragOffset;
    private ResourceArea draggingResourceArea;
    private int draggingCornerIndex = -1;
    private boolean draggingResourceAreaMove = false;
    private Point2D resourceAreaDragOffset;
    private Size originalResourceSize;
    private Point originalResourcePosition;
    private Point2D dragStartPoint;
    private Point2D polygonDragOffset;
    private final Timer edgeScrollTimer;
    private Point2D lastDragPoint;
    private Point2D accumulatedScrollDelta;

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

    private void finishPolygonCreation() {
        List<Point> points = panel.getCreationPoints();
        if (points.size() >= 3) {
            panel.getEditorPanel().finishPolygonCreation(points);
        } else {
            panel.getEditorPanel().cancelPolygonCreation();
        }
    }

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

    private void startPolygonPointDrag(int polygonIndex, int pointIndex) {
        draggingPolygonIndex = polygonIndex;
        draggingPointIndex = pointIndex;
        draggingEntirePolygon = false;
        panel.setSelectedPointIndex(pointIndex);
        clearNonPolygonSelections();
    }

    private void startPolygonMove(int polygonIndex, Polygon polygon, double left, double bottom, double scale,
                                  Point2D clickPoint) {
        draggingPolygonIndex = polygonIndex;
        draggingPointIndex = -1;
        draggingEntirePolygon = true;
        polygonDragOffset = createPolygonDragOffset(polygon, left, bottom, scale, clickPoint);
        panel.setSelectedPointIndex(-1);
        clearNonPolygonSelections();
    }

    private void clearNonPolygonSelections() {
        panel.setSelectedCannonIndex(-1);
        panel.setSelectedThrone(false);
        panel.setSelectedWindVane(false);
        panel.setSelectedResourceArea(null);
        panel.getEditorPanel().onPointSelectionChanged();
        panel.getEditorPanel().onCannonSelectionChanged();
    }

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

    private void selectPolygon(Polygon polygon) {
        panel.setSelectedPolygon(polygon);
        panel.setSelectedPointIndex(-1);
        clearNonPolygonSelections();
    }

    private Point toCastlePoint(Point2D point, double left, double bottom, double scale) {
        int x = (int) Math.round((point.getX() - left) / scale);
        int y = (int) Math.round((bottom - point.getY()) / scale);
        return new Point(x, y);
    }

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

    private boolean hasActiveDrag() {
        return draggingPolygonIndex >= 0 || draggingCannonIndex >= 0 || draggingThrone || draggingWindVane
                || draggingResourceArea != null;
    }

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

    private void handleThroneDrag(Point2D dragPoint, double left, double bottom, double scale) {
        Point newPoint = toCastlePoint(dragPoint, throneDragOffset, left, bottom, scale);
        panel.getEditorPanel().updateThronePosition(newPoint);
        autoScrollToKeepVisible(dragPoint);
    }

    private void handleWindVaneDrag(Point2D dragPoint, double left, double bottom, double scale) {
        Point newPoint = toCastlePoint(dragPoint, windVaneDragOffset, left, bottom, scale);
        panel.getEditorPanel().updateWindVanePosition(newPoint);
        autoScrollToKeepVisible(dragPoint);
    }

    private void handleCannonDrag(Point2D dragPoint, double left, double bottom, double scale) {
        Point newPoint = toCastlePoint(dragPoint, cannonDragOffset, left, bottom, scale);
        panel.getEditorPanel().updateCannonPosition(draggingCannonIndex, newPoint);
        autoScrollToKeepVisible(dragPoint);
    }

    private Point toCastlePoint(Point2D point, Point2D offset, double left, double bottom, double scale) {
        int x = (int) Math.round((point.getX() - offset.getX() - left) / scale);
        int y = (int) Math.round((bottom - (point.getY() - offset.getY())) / scale);
        return new Point(x, y);
    }

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

    private javax.swing.JViewport getScrollViewport() {
        if (panel.getParent() instanceof javax.swing.JViewport viewport
                && viewport.getParent() instanceof javax.swing.JScrollPane) {
            return viewport;
        }
        return null;
    }

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

    private int effectiveScrollDelta(int actualDelta, boolean nearStart, boolean nearEnd) {
        if (actualDelta != 0) {
            return actualDelta;
        }
        if (nearStart) {
            return -10;
        }
        return nearEnd ? 10 : 0;
    }

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

    private void adjustPolygonDragOffset(int deltaX, int deltaY) {
        if (polygonDragOffset != null) {
            polygonDragOffset = new Point2D.Double(
                    polygonDragOffset.getX() - deltaX,
                    polygonDragOffset.getY() - deltaY);
        }
    }

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

    private void shiftDragStartPoint(int deltaX, int deltaY) {
        if (dragStartPoint != null) {
            dragStartPoint = new Point2D.Double(
                    dragStartPoint.getX() + deltaX,
                    dragStartPoint.getY() + deltaY);
        }
    }

    private void adjustObjectDragOffsets(int deltaX, int deltaY) {
        cannonDragOffset = adjustDragOffset(cannonDragOffset, deltaX, deltaY);
        throneDragOffset = adjustDragOffset(throneDragOffset, deltaX, deltaY);
        windVaneDragOffset = adjustDragOffset(windVaneDragOffset, deltaX, deltaY);
        resourceAreaDragOffset = adjustDragOffset(resourceAreaDragOffset, deltaX, deltaY);
    }

    private Point2D adjustDragOffset(Point2D offset, int deltaX, int deltaY) {
        if (offset == null) {
            return null;
        }
        return new Point2D.Double(offset.getX() - deltaX, offset.getY() - deltaY);
    }

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

    private Point2D createPolygonDragOffset(Polygon polygon, double left, double bottom,
                                            double scale, Point2D clickPoint) {
        Point anchor = polygon.points().getFirst();
        double anchorScreenX = left + anchor.x() * scale;
        double anchorScreenY = bottom - anchor.y() * scale;
        return new Point2D.Double(clickPoint.getX() - anchorScreenX,
                clickPoint.getY() - anchorScreenY);
    }

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

    private boolean isWindVaneClicked(Castle castle, double left, double bottom, double scale, Point2D clickPoint) {
        Point windVane = castle.windVane();
        if (windVane.x() == Integer.MIN_VALUE || windVane.y() == Integer.MIN_VALUE) {
            return false;
        }
        Rectangle2D windVaneRect = CastleViewPanel.calculateWindVaneRectangle(windVane, left, bottom, scale);
        return windVaneRect.contains(clickPoint);
    }

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

    private Point getResourcePosition(Castle castle, ResourceArea resourceArea) {
        return switch (resourceArea) {
            case MONEY -> castle.money();
            case POWDER -> castle.powder();
            case BALLS -> castle.balls();
        };
    }

    private Size getResourceSize(Castle castle, ResourceArea resourceArea) {
        return switch (resourceArea) {
            case MONEY -> castle.moneyRoom();
            case POWDER -> castle.powderRoom();
            case BALLS -> castle.ballRoom();
        };
    }

    private int getResourceStepX(ResourceArea resourceArea) {
        return switch (resourceArea) {
            case MONEY -> CastleFileFormat.MONEY_WIDTH;
            case POWDER -> CastleFileFormat.POWDER_WIDTH;
            case BALLS -> CastleFileFormat.CANNONBALL_WIDTH;
        };
    }

    private int getResourceStepY(ResourceArea resourceArea) {
        return switch (resourceArea) {
            case MONEY -> CastleFileFormat.MONEY_HEIGHT;
            case POWDER -> CastleFileFormat.POWDER_HEIGHT;
            case BALLS -> CastleFileFormat.CANNONBALL_HEIGHT;
        };
    }

    private int getResourceCount(Castle castle, ResourceArea resourceArea) {
        return switch (resourceArea) {
            case MONEY -> divideRoundedUp(castle.resources().money(), CastleFileFormat.MONEY_PER_UNIT);
            case POWDER -> divideRoundedUp(castle.resources().powder(), CastleFileFormat.POWDER_PER_UNIT);
            case BALLS -> castle.resources().balls();
        };
    }

    private int divideRoundedUp(int value, int divisor) {
        return value <= 0 ? 0 : (value + divisor - 1) / divisor;
    }

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
