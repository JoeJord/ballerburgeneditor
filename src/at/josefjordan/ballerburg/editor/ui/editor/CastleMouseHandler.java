package at.josefjordan.ballerburg.editor.ui.editor;

import at.josefjordan.ballerburg.editor.model.CastleDocument.Castle;
import at.josefjordan.ballerburg.editor.model.CastleDocument.DrawingCommand;
import at.josefjordan.ballerburg.editor.model.CastleDocument.Point;
import at.josefjordan.ballerburg.editor.model.CastleDocument.Polygon;
import at.josefjordan.ballerburg.editor.model.CastleDocument.Size;
import at.josefjordan.ballerburg.editor.model.CastleFileFormat;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.event.MouseMotionAdapter;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Timer;

/** Handles mouse interactions for the castle view (selection, editing, etc.). */
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
    private String draggingResourceArea = null;
    private int draggingCornerIndex = -1;
    private boolean draggingResourceAreaMove = false;
    private Point2D resourceAreaDragOffset;
    private Size originalResourceSize;
    private Point originalResourcePosition;
    private double dragScale = 0;
    private double dragLeft = 0;
    private double dragBottom = 0;
    private Point2D dragStartPoint;
    private Point2D polygonDragOffset;
    private Timer edgeScrollTimer;
    private Point2D lastDragPoint;
    private Point2D accumulatedScrollDelta;
    private Point2D edgeScrollStartPoint;

    CastleMouseHandler(CastleViewPanel panel) {
        this.panel = panel;

        // Initialize edge scroll timer for continuous dragging at screen edge
        edgeScrollTimer = new Timer(50, e -> {
            if (lastDragPoint != null) {
                handleEdgeScrolling();
            }
        });

        panel.addMouseMotionListener(new MouseMotionAdapter() {
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
                    double[] params = panel.getRenderingParameters();
                    double left = params[0];
                    double bottom = params[1];
                    double scale = params[2];
                    panel.setEdgeHoverPosition(e.getPoint());
                }
            }
        });
        panel.addKeyListener(new KeyListener() {
            @Override
            public void keyTyped(KeyEvent e) {
                // Not used
            }

            @Override
            public void keyPressed(KeyEvent e) {
                if (panel.isInPolygonCreationMode()) {
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
            }

            @Override
            public void keyReleased(KeyEvent e) {
                // Not used
            }
        });
        panel.setFocusable(true);
        panel.requestFocusInWindow();
    }

    @Override
    public void mousePressed(MouseEvent e) {
        // Handle polygon creation mode
        if (panel.isInPolygonCreationMode()) {
            if (e.getButton() == MouseEvent.BUTTON1) {
                // Left click adds a point
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

                Point2D clickPoint = e.getPoint();
                int newX = (int) Math.round((clickPoint.getX() - left) / scale);
                int newY = (int) Math.round((bottom - clickPoint.getY()) / scale);

                panel.addCreationPoint(new Point(newX, newY));
            } else if (e.getButton() == MouseEvent.BUTTON3) {
                // Right click finishes the polygon
                List<Point> points = panel.getCreationPoints();
                if (points.size() >= 3) {
                    panel.getEditorPanel().finishPolygonCreation(points);
                } else {
                    panel.getEditorPanel().cancelPolygonCreation();
                }
            }
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
        if (scale <= 0) {
            return;
        }

        // Handle edge hover for adding points to selected polygon
        if (e.getButton() == MouseEvent.BUTTON1 && panel.getSelectedPolygon() != null) {
            int edgeIndex = panel.findEdgeIndexForHoverPosition(left, bottom, scale);
            if (edgeIndex >= 0) {
                Point2D clickPoint = e.getPoint();
                int newX = (int) Math.round((clickPoint.getX() - left) / scale);
                int newY = (int) Math.round((bottom - clickPoint.getY()) / scale);
                panel.getEditorPanel().addPolygonPoint(edgeIndex, new Point(newX, newY));
                panel.setEdgeHoverPosition(null);
                return;
            }
        }

        Point2D clickPoint = e.getPoint();

        // First check if clicking on a corner of a selected resource area
        String selectedResourceArea = panel.getSelectedResourceArea();
        if (selectedResourceArea != null) {
            int clickedCorner = findClickedResourceCorner(castle, selectedResourceArea, left, bottom, scale, clickPoint);
            if (clickedCorner >= 0) {
                draggingResourceArea = selectedResourceArea;
                draggingCornerIndex = clickedCorner;
                dragScale = scale;
                dragLeft = left;
                dragBottom = bottom;
                dragStartPoint = clickPoint;
                originalResourceSize = getResourceSize(castle, selectedResourceArea);
                originalResourcePosition = getResourcePosition(castle, selectedResourceArea);
                panel.setDraggingResourceCorner(clickedCorner);
                return;
            }
        }

        // First check if clicking on resource areas
        String clickedResourceArea = findClickedResourceArea(castle, left, bottom, scale, clickPoint);
        if (clickedResourceArea != null) {
            panel.setSelectedResourceArea(clickedResourceArea);
            panel.setSelectedPolygon(null);
            panel.setSelectedPointIndex(-1);
            panel.setSelectedCannonIndex(-1);
            panel.setSelectedThrone(false);
            panel.getEditorPanel().onPointSelectionChanged();
            panel.getEditorPanel().onCannonSelectionChanged();

            // Start dragging the resource area
            draggingResourceArea = clickedResourceArea;
            draggingResourceAreaMove = true;
            dragScale = scale;
            dragLeft = left;
            dragBottom = bottom;
            Point resourcePosition = getResourcePosition(castle, clickedResourceArea);
            double resourceScreenX = left + resourcePosition.x() * scale;
            double resourceScreenY = bottom - resourcePosition.y() * scale;
            resourceAreaDragOffset = new Point2D.Double(clickPoint.getX() - resourceScreenX, clickPoint.getY() - resourceScreenY);
            return;
        }

        // First check if clicking on the wind vane
        if (isWindVaneClicked(castle, left, bottom, scale, clickPoint)) {
            panel.setSelectedWindVane(true);
            panel.setSelectedPolygon(null);
            panel.setSelectedPointIndex(-1);
            panel.setSelectedCannonIndex(-1);
            panel.setSelectedThrone(false);
            panel.setSelectedResourceArea(null);
            panel.getEditorPanel().onPointSelectionChanged();
            panel.getEditorPanel().onCannonSelectionChanged();
            draggingWindVane = true;
            dragScale = scale;
            dragLeft = left;
            dragBottom = bottom;
            Point windVane = castle.windVane();
            double windVaneScreenX = left + windVane.x() * scale;
            double windVaneScreenY = bottom - windVane.y() * scale;
            windVaneDragOffset = new Point2D.Double(clickPoint.getX() - windVaneScreenX, clickPoint.getY() - windVaneScreenY);
            return;
        }

        // First check if clicking on the throne
        if (isThroneClicked(castle, left, bottom, scale, clickPoint)) {
            panel.setSelectedThrone(true);
            panel.setSelectedPolygon(null);
            panel.setSelectedPointIndex(-1);
            panel.setSelectedCannonIndex(-1);
            panel.setSelectedWindVane(false);
            panel.setSelectedResourceArea(null);
            panel.getEditorPanel().onPointSelectionChanged();
            panel.getEditorPanel().onCannonSelectionChanged();
            draggingThrone = true;
            dragScale = scale;
            dragLeft = left;
            dragBottom = bottom;
            Point throne = castle.throne();
            double throneScreenX = left + throne.x() * scale;
            double throneScreenY = bottom - throne.y() * scale;
            throneDragOffset = new Point2D.Double(clickPoint.getX() - throneScreenX, clickPoint.getY() - throneScreenY);
            return;
        }

        // First check if clicking on a cannon
        int clickedCannonIndex = findClickedCannonIndex(castle, left, bottom, scale, clickPoint);
        if (clickedCannonIndex >= 0) {
            panel.setSelectedCannonIndex(clickedCannonIndex);
            panel.setSelectedPolygon(null);
            panel.setSelectedPointIndex(-1);
            panel.setSelectedThrone(false);
            panel.setSelectedWindVane(false);
            panel.setSelectedResourceArea(null);
            panel.getEditorPanel().onCannonSelectionChanged();
            draggingCannonIndex = clickedCannonIndex;
            dragScale = scale;
            dragLeft = left;
            dragBottom = bottom;
            dragStartPoint = clickPoint;
            Point cannon = castle.cannons().get(clickedCannonIndex);
            double cannonScreenX = left + cannon.x() * scale;
            double cannonScreenY = bottom - cannon.y() * scale;
            cannonDragOffset = new Point2D.Double(clickPoint.getX() - cannonScreenX, clickPoint.getY() - cannonScreenY);
            return;
        }

        // First check if clicking on a point of the selected polygon
        Polygon selectedPolygon = panel.getSelectedPolygon();
        if (selectedPolygon != null) {
            int polygonIndex = findPolygonIndex(castle, selectedPolygon);
            if (polygonIndex >= 0) {
                int pointIndex = findClickedPointIndex(selectedPolygon, left, bottom, scale, clickPoint);
                if (pointIndex >= 0) {
                    draggingPolygonIndex = polygonIndex;
                    draggingPointIndex = pointIndex;
                    draggingEntirePolygon = false;
                    dragScale = scale;
                    dragLeft = left;
                    dragBottom = bottom;
                    panel.setSelectedPointIndex(pointIndex);
                    panel.setSelectedCannonIndex(-1);
                    panel.setSelectedThrone(false);
                    panel.setSelectedWindVane(false);
                    panel.setSelectedResourceArea(null);
                    panel.getEditorPanel().onPointSelectionChanged();
                    panel.getEditorPanel().onCannonSelectionChanged();
                    return;
                }
                // Check if clicking inside the selected polygon (but not on a point)
                Path2D path = createPolygonPath(selectedPolygon, left, bottom, scale);
                if (path.contains(clickPoint)) {
                    draggingPolygonIndex = polygonIndex;
                    draggingPointIndex = -1;
                    draggingEntirePolygon = true;
                    dragScale = scale;
                    dragLeft = left;
                    dragBottom = bottom;
                    polygonDragOffset = createPolygonDragOffset(selectedPolygon, left, bottom, scale, clickPoint);
                    panel.setSelectedPointIndex(-1);
                    panel.setSelectedCannonIndex(-1);
                    panel.setSelectedThrone(false);
                    panel.setSelectedWindVane(false);
                    panel.setSelectedResourceArea(null);
                    panel.getEditorPanel().onPointSelectionChanged();
                    panel.getEditorPanel().onCannonSelectionChanged();
                    return;
                }
            }
        }

        // If not clicking on selected polygon, check if clicking on any polygon
        Polygon clickedPolygon = findClickedPolygon(castle, left, bottom, scale, clickPoint);
        if (clickedPolygon != null) {
            panel.setSelectedPolygon(clickedPolygon);
            panel.setSelectedPointIndex(-1);
            panel.setSelectedCannonIndex(-1);
            panel.setSelectedThrone(false);
            panel.setSelectedWindVane(false);
            panel.setSelectedResourceArea(null);
            panel.getEditorPanel().onPointSelectionChanged();
            panel.getEditorPanel().onCannonSelectionChanged();
            int polygonIndex = findPolygonIndex(castle, clickedPolygon);
            if (polygonIndex >= 0) {
                draggingPolygonIndex = polygonIndex;
                draggingPointIndex = -1;
                draggingEntirePolygon = true;
                dragScale = scale;
                dragLeft = left;
                dragBottom = bottom;
                polygonDragOffset = createPolygonDragOffset(clickedPolygon, left, bottom, scale, clickPoint);
            }
        } else {
            panel.setSelectedPolygon(null);
            panel.setSelectedPointIndex(-1);
            panel.setSelectedCannonIndex(-1);
            panel.setSelectedThrone(false);
            panel.setSelectedWindVane(false);
            panel.setSelectedResourceArea(null);
            panel.getEditorPanel().onPointSelectionChanged();
            panel.getEditorPanel().onCannonSelectionChanged();
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
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
        edgeScrollStartPoint = null;
        edgeScrollTimer.stop();
    }

    private void handleDrag(MouseEvent e) {
        if (draggingPolygonIndex < 0 && draggingCannonIndex < 0 && !draggingThrone && !draggingWindVane && draggingResourceArea == null) {
            return;
        }

        Castle castle = panel.getCastle();
        if (castle == null) {
            return;
        }

        double[] params = panel.getRenderingParameters();
        double currentLeft = params[0];
        double currentBottom = params[1];
        double currentScale = params[2];

        // Store last drag point for edge scrolling
        lastDragPoint = e.getPoint();
        // Reset accumulated scroll delta when mouse actually moves
        accumulatedScrollDelta = null;
        edgeScrollStartPoint = null;

        if (draggingResourceArea != null) {
            if (draggingResourceAreaMove) {
                // Move the resource area
                Point2D dragPoint = e.getPoint();
                int newX = (int) Math.round((dragPoint.getX() - resourceAreaDragOffset.getX() - currentLeft) / currentScale);
                int newY = (int) Math.round((currentBottom - (dragPoint.getY() - resourceAreaDragOffset.getY())) / currentScale);

                Point newPosition = new Point(newX, newY);
                panel.getEditorPanel().updateResourceAreaPosition(draggingResourceArea, newPosition);
                autoScrollToKeepVisible(dragPoint);
                return;
            }

            // Resize the resource area by dragging a corner
            Point2D dragPoint = e.getPoint();
            double deltaX = (dragPoint.getX() - dragStartPoint.getX()) / currentScale;
            double deltaY = (dragPoint.getY() - dragStartPoint.getY()) / currentScale;

            // Get step sizes for the resource type
            int stepX = getResourceStepX(draggingResourceArea);
            int stepY = getResourceStepY(draggingResourceArea);

            // Calculate minimum size needed for current resources
            int resourceCount = getResourceCount(castle, draggingResourceArea);
            Size minSize = calculateMinimumSize(resourceCount, stepX, stepY);

            // Round delta to step sizes
            int deltaXRounded = (int) Math.round(deltaX / stepX) * stepX;
            int deltaXRoundedAbs = Math.abs(deltaXRounded);
            int deltaYRounded = (int) Math.round(deltaY / stepY) * stepY;
            int deltaYRoundedAbs = Math.abs(deltaYRounded);

            Point newPosition = originalResourcePosition;
            Size newSize = originalResourceSize;

            // Corner indices: 0 = bottom-left, 1 = bottom-right, 2 = top-left, 3 = top-right
            // Note: In castle coordinates, Y increases upward (opposite to Swing)
            switch (draggingCornerIndex) {
                case 0: // bottom-left
                    newPosition = new Point(originalResourcePosition.x() + deltaXRounded,
                            originalResourcePosition.y() - deltaYRounded);
                    newSize = new Size(originalResourceSize.width() - deltaXRounded,
                            originalResourceSize.height() + deltaYRounded);
                    break;
                case 1: // bottom-right
                    newPosition = new Point(originalResourcePosition.x(),
                            originalResourcePosition.y() - deltaYRounded);
                    newSize = new Size(originalResourceSize.width() + deltaXRounded,
                            originalResourceSize.height() + deltaYRounded);
                    break;
                case 2: // top-left
                    newPosition = new Point(originalResourcePosition.x() + deltaXRounded,
                            originalResourcePosition.y());
                    newSize = new Size(originalResourceSize.width() - deltaXRounded,
                            originalResourceSize.height() - deltaYRounded);
                    break;
                case 3: // top-right
                    newPosition = new Point(originalResourcePosition.x(),
                            originalResourcePosition.y());
                    newSize = new Size(originalResourceSize.width() + deltaXRounded,
                            originalResourceSize.height() - deltaYRounded);
                    break;
            }

            // Snap to multiples of step sizes
            int newWidth = (newSize.width() / stepX) * stepX;
            int newHeight = (newSize.height() / stepY) * stepY;

            // Ensure the size can fit the resources
            if (!canFitResources(resourceCount, newWidth, newHeight, stepX, stepY)) {
                // If the new size can't fit resources, try to find the nearest valid size
                Size validSize = findNearestValidSize(resourceCount, newWidth, newHeight, stepX, stepY);
                newWidth = validSize.width();
                newHeight = validSize.height();
            }

            newSize = new Size(newWidth, newHeight);

            panel.getEditorPanel().updateResourceAreaSize(draggingResourceArea, newPosition, newSize);
            autoScrollToKeepVisible(e.getPoint());
            return;
        }

        if (draggingThrone) {
            // Drag the throne
            Point2D dragPoint = e.getPoint();
            int newX = (int) Math.round((dragPoint.getX() - throneDragOffset.getX() - currentLeft) / currentScale);
            int newY = (int) Math.round((currentBottom - (dragPoint.getY() - throneDragOffset.getY())) / currentScale);

            Point newPoint = new Point(newX, newY);
            panel.getEditorPanel().updateThronePosition(newPoint);
            autoScrollToKeepVisible(dragPoint);
            return;
        }

        if (draggingWindVane) {
            // Drag the wind vane
            Point2D dragPoint = e.getPoint();
            int newX = (int) Math.round((dragPoint.getX() - windVaneDragOffset.getX() - currentLeft) / currentScale);
            int newY = (int) Math.round((currentBottom - (dragPoint.getY() - windVaneDragOffset.getY())) / currentScale);

            Point newPoint = new Point(newX, newY);
            panel.getEditorPanel().updateWindVanePosition(newPoint);
            autoScrollToKeepVisible(dragPoint);
            return;
        }

        if (draggingCannonIndex >= 0) {
            // Drag a cannon
            Point2D dragPoint = e.getPoint();
            int newX = (int) Math.round((dragPoint.getX() - cannonDragOffset.getX() - currentLeft) / currentScale);
            int newY = (int) Math.round((currentBottom - (dragPoint.getY() - cannonDragOffset.getY())) / currentScale);

            Point newPoint = new Point(newX, newY);
            panel.getEditorPanel().updateCannonPosition(draggingCannonIndex, newPoint);
            autoScrollToKeepVisible(dragPoint);
            return;
        }

        if (draggingEntirePolygon) {
            Point2D dragPoint = e.getPoint();
            updateDraggedPolygonPosition(dragPoint, currentLeft, currentBottom, currentScale);
            autoScrollToKeepVisible(dragPoint);
        } else if (draggingPointIndex >= 0) {
            // Drag a single point
            Point2D dragPoint = e.getPoint();
            int newX = (int) Math.round((dragPoint.getX() - currentLeft) / currentScale);
            int newY = (int) Math.round((currentBottom - dragPoint.getY()) / currentScale);

            Point newPoint = new Point(newX, newY);
            panel.getEditorPanel().updatePolygonPoint(draggingPolygonIndex, draggingPointIndex, newPoint);
            autoScrollToKeepVisible(dragPoint);

            // Update the selected polygon reference to the new polygon
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
                scrollX = Math.max(0, Math.min(panel.getPreferredSize().width - viewRect.width,
                                               mouseX - viewRect.width + margin));
                needsScroll = true;
            }

            // Check if mouse is near top edge of viewport
            if (viewportY < margin) {
                scrollY = Math.max(0, mouseY - margin);
                needsScroll = true;
            }
            // Check if mouse is near bottom edge of viewport
            else if (viewportY > viewRect.height - margin) {
                scrollY = Math.max(0, Math.min(panel.getPreferredSize().height - viewRect.height,
                                               mouseY - viewRect.height + margin));
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

        // Simulate drag at the edge by extending the drag point beyond the viewport
        if (panel.getParent() instanceof javax.swing.JViewport viewport) {
            javax.swing.JScrollPane scrollPane = (javax.swing.JScrollPane) viewport.getParent();
            if (scrollPane == null) {
                return;
            }

            java.awt.Rectangle viewRect = viewport.getViewRect();
            int margin = 50;
            int scrollSpeed = 10; // Pixels to scroll per timer tick

            int mouseX = (int) lastDragPoint.getX();
            int mouseY = (int) lastDragPoint.getY();

            // Calculate viewport-relative position
            int viewportX = mouseX - viewRect.x;
            int viewportY = mouseY - viewRect.y;

            int scrollX = viewRect.x;
            int scrollY = viewRect.y;
            boolean needsScroll = false;

            // Check if mouse is near left edge
            if (viewportX < margin) {
                scrollX = Math.max(0, viewRect.x - scrollSpeed);
                needsScroll = true;
            }
            // Check if mouse is near right edge
            else if (viewportX > viewRect.width - margin) {
                scrollX = Math.max(0, Math.min(panel.getPreferredSize().width - viewRect.width,
                                               viewRect.x + scrollSpeed));
                needsScroll = true;
            }

            // Check if mouse is near top edge
            if (viewportY < margin) {
                scrollY = Math.max(0, viewRect.y - scrollSpeed);
                needsScroll = true;
            }
            // Check if mouse is near bottom edge
            else if (viewportY > viewRect.height - margin) {
                scrollY = Math.max(0, Math.min(panel.getPreferredSize().height - viewRect.height,
                                               viewRect.y + scrollSpeed));
                needsScroll = true;
            }

            if (needsScroll) {
                viewport.setViewPosition(new java.awt.Point(scrollX, scrollY));

                // Also update the dragged object position based on scroll direction
                Castle castle = panel.getCastle();
                if (castle == null) {
                    return;
                }

                double[] params = panel.getRenderingParameters();
                double currentLeft = params[0];
                double currentBottom = params[1];
                double currentScale = params[2];

                // Calculate the scroll delta for this tick
                // If scroll was blocked at edge, use the intended scroll speed instead
                int deltaX = scrollX - viewRect.x;
                int deltaY = scrollY - viewRect.y;

                // If scroll was blocked (delta is 0 but mouse is at edge), use scroll speed
                if (deltaX == 0 && viewportX < margin) {
                    deltaX = -scrollSpeed;
                }
                if (deltaX == 0 && viewportX > viewRect.width - margin) {
                    deltaX = scrollSpeed;
                }
                if (deltaY == 0 && viewportY < margin) {
                    deltaY = -scrollSpeed;
                }
                if (deltaY == 0 && viewportY > viewRect.height - margin) {
                    deltaY = scrollSpeed;
                }

                // Accumulate scroll delta for this session
                if (accumulatedScrollDelta == null) {
                    accumulatedScrollDelta = new Point2D.Double(deltaX, deltaY);
                } else {
                    accumulatedScrollDelta.setLocation(
                        accumulatedScrollDelta.getX() + deltaX,
                        accumulatedScrollDelta.getY() + deltaY
                    );
                }

                // Apply scroll delta directly to objects without modifying drag state
                applyScrollDeltaToObject(deltaX, deltaY, currentLeft, currentBottom, currentScale);
            } else {
                edgeScrollTimer.stop();
            }
        }
    }

    private void applyScrollDeltaToObject(int deltaX, int deltaY, double currentLeft, double currentBottom, double currentScale) {
        if (draggingEntirePolygon) {
            if (polygonDragOffset != null) {
                polygonDragOffset = new Point2D.Double(
                        polygonDragOffset.getX() - deltaX,
                        polygonDragOffset.getY() - deltaY);
            }
            updateDraggedObjectPosition(lastDragPoint, currentLeft, currentBottom, currentScale, null);
        } else if (draggingPointIndex >= 0) {
            double castleDeltaX = deltaX / currentScale;
            double castleDeltaY = -deltaY / currentScale;
            Point currentPoint = getCurrentPolygonPoint(draggingPolygonIndex, draggingPointIndex);
            if (currentPoint != null) {
                int newX = (int) Math.round(currentPoint.x() + castleDeltaX);
                int newY = (int) Math.round(currentPoint.y() + castleDeltaY);
                Point newPoint = new Point(newX, newY);
                panel.getEditorPanel().updatePolygonPoint(draggingPolygonIndex, draggingPointIndex, newPoint);
            }
            // Update dragStartPoint to match the scroll so normal drag continues correctly
            if (dragStartPoint != null) {
                dragStartPoint = new Point2D.Double(
                    dragStartPoint.getX() + deltaX,
                    dragStartPoint.getY() + deltaY
                );
            }
        } else {
            // For offset-based objects (cannons, throne, etc.), update the offsets
            // Subtract scroll delta to compensate for viewport movement
            if (cannonDragOffset != null) {
                cannonDragOffset = new Point2D.Double(
                    cannonDragOffset.getX() - deltaX,
                    cannonDragOffset.getY() - deltaY
                );
            }
            if (throneDragOffset != null) {
                throneDragOffset = new Point2D.Double(
                    throneDragOffset.getX() - deltaX,
                    throneDragOffset.getY() - deltaY
                );
            }
            if (windVaneDragOffset != null) {
                windVaneDragOffset = new Point2D.Double(
                    windVaneDragOffset.getX() - deltaX,
                    windVaneDragOffset.getY() - deltaY
                );
            }
            if (resourceAreaDragOffset != null) {
                resourceAreaDragOffset = new Point2D.Double(
                    resourceAreaDragOffset.getX() - deltaX,
                    resourceAreaDragOffset.getY() - deltaY
                );
            }

            // Use the normal drag logic with the updated drag state
            updateDraggedObjectPosition(lastDragPoint, currentLeft, currentBottom, currentScale, null);
        }
    }

    private void updateDraggedObjectPosition(Point2D dragPoint, double currentLeft, double currentBottom, double currentScale, Point2D scrollDelta) {
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

        Point anchor = polygon.points().get(0);
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
        Point anchor = polygon.points().get(0);
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
            if (command instanceof Polygon polygon) {
                if (currentPolygonIndex == polygonIndex) {
                    List<Point> points = polygon.points();
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
        Point first = polygon.points().get(0);
        path.moveTo(left + first.x() * scale, bottom - first.y() * scale);
        for (int i = 1; i < polygon.points().size(); i++) {
            Point point = polygon.points().get(i);
            path.lineTo(left + point.x() * scale, bottom - point.y() * scale);
        }
        path.closePath();
        return path;
    }

    private int areaTop(Point position, Size size) {
        return position.y() + size.height();
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

    private String findClickedResourceArea(Castle castle, double left, double bottom, double scale, Point2D clickPoint) {
        // Check money area
        Rectangle2D moneyRect = new Rectangle2D.Double(left + castle.money().x() * scale,
                bottom - (castle.money().y() + castle.moneyRoom().height()) * scale,
                castle.moneyRoom().width() * scale, castle.moneyRoom().height() * scale);
        if (moneyRect.contains(clickPoint)) {
            return "money";
        }

        // Check powder area
        Rectangle2D powderRect = new Rectangle2D.Double(left + castle.powder().x() * scale,
                bottom - (castle.powder().y() + castle.powderRoom().height()) * scale,
                castle.powderRoom().width() * scale, castle.powderRoom().height() * scale);
        if (powderRect.contains(clickPoint)) {
            return "powder";
        }

        // Check balls area
        Rectangle2D ballsRect = new Rectangle2D.Double(left + castle.balls().x() * scale,
                bottom - (castle.balls().y() + castle.ballRoom().height()) * scale,
                castle.ballRoom().width() * scale, castle.ballRoom().height() * scale);
        if (ballsRect.contains(clickPoint)) {
            return "balls";
        }

        return null;
    }

    private int findClickedResourceCorner(Castle castle, String resourceArea, double left, double bottom, double scale, Point2D clickPoint) {
        final double hitRadius = 6.0;
        Point position = getResourcePosition(castle, resourceArea);
        Size size = getResourceSize(castle, resourceArea);

        double x = left + position.x() * scale;
        double y = bottom - (position.y() + size.height()) * scale;
        double width = size.width() * scale;
        double height = size.height() * scale;

        // Check bottom-left corner (index 0)
        double blX = x;
        double blY = y + height;
        if (clickPoint.distance(blX, blY) <= hitRadius) {
            return 0;
        }

        // Check bottom-right corner (index 1)
        double brX = x + width;
        double brY = y + height;
        if (clickPoint.distance(brX, brY) <= hitRadius) {
            return 1;
        }

        // Check top-left corner (index 2)
        double tlX = x;
        double tlY = y;
        if (clickPoint.distance(tlX, tlY) <= hitRadius) {
            return 2;
        }

        // Check top-right corner (index 3)
        double trX = x + width;
        double trY = y;
        if (clickPoint.distance(trX, trY) <= hitRadius) {
            return 3;
        }

        return -1;
    }

    private Point getResourcePosition(Castle castle, String resourceArea) {
        return switch (resourceArea) {
            case "money" -> castle.money();
            case "powder" -> castle.powder();
            case "balls" -> castle.balls();
            default -> new Point(0, 0);
        };
    }

    private Size getResourceSize(Castle castle, String resourceArea) {
        return switch (resourceArea) {
            case "money" -> castle.moneyRoom();
            case "powder" -> castle.powderRoom();
            case "balls" -> castle.ballRoom();
            default -> new Size(0, 0);
        };
    }

    private int getResourceStepX(String resourceArea) {
        return switch (resourceArea) {
            case "money" -> CastleFileFormat.MONEY_WIDTH;
            case "powder" -> CastleFileFormat.POWDER_WIDTH;
            case "balls" -> CastleFileFormat.CANNONBALL_WIDTH;
            default -> 1;
        };
    }

    private int getResourceStepY(String resourceArea) {
        return switch (resourceArea) {
            case "money" -> CastleFileFormat.MONEY_HEIGHT;
            case "powder" -> CastleFileFormat.POWDER_HEIGHT;
            case "balls" -> CastleFileFormat.CANNONBALL_HEIGHT;
            default -> 1;
        };
    }

    private int getResourceCount(Castle castle, String resourceArea) {
        return switch (resourceArea) {
            case "money" -> divideRoundedUp(castle.resources().money(), CastleFileFormat.MONEY_PER_UNIT);
            case "powder" -> divideRoundedUp(castle.resources().powder(), CastleFileFormat.POWDER_PER_UNIT);
            case "balls" -> castle.resources().balls();
            default -> 0;
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
        double diffWithFixedWidth = Math.abs(sizeWithFixedWidth.width() - targetWidth) +
                                     Math.abs(sizeWithFixedWidth.height() - targetHeight);
        double diffWithFixedHeight = Math.abs(sizeWithFixedHeight.width() - targetWidth) +
                                      Math.abs(sizeWithFixedHeight.height() - targetHeight);

        if (diffWithFixedWidth <= diffWithFixedHeight) {
            return sizeWithFixedWidth;
        } else {
            return sizeWithFixedHeight;
        }
    }
}
