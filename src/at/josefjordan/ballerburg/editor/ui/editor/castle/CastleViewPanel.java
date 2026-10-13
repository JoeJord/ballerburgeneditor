package at.josefjordan.ballerburg.editor.ui.editor.castle;

import at.josefjordan.ballerburg.editor.model.CastleDocument.*;
import at.josefjordan.ballerburg.editor.model.CastleDocument.Point;
import at.josefjordan.ballerburg.editor.model.CastleDocument.Polygon;
import at.josefjordan.ballerburg.editor.model.CastleFileFormat;
import at.josefjordan.ballerburg.editor.ui.editor.EditorPanel;
import hgb.gui.HGBasePanel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;

/**
 * Draws one castle without owning or modifying editor state.
 */
public final class CastleViewPanel extends HGBasePanel {
    /**
     * Calculates the castle-coordinate bounds used to size and position rendered content.
     */
    private final transient CastleBoundsCalculator boundsCalculator = new CastleBoundsCalculator();
    /**
     * Coordinates zoom, viewport scrolling, and castle-to-screen transforms.
     */
    private final transient CastleViewTransformController transformController = new CastleViewTransformController(this, boundsCalculator);
    /**
     * Renders the castle view from a snapshot of this panel's state.
     */
    private final transient CastleRenderer renderer = new CastleRenderer();
    /**
     * Castle currently rendered by this view, or {@code null} when no castle is selected.
     */
    private transient Castle castle;
    /**
     * Currently selected polygon, or {@code null} when no polygon is selected.
     */
    private transient Polygon selectedPolygon;
    /**
     * Index of the selected vertex in the selected polygon, or {@code -1}.
     */
    private int selectedPointIndex = -1;
    /**
     * Index of the selected cannon, or {@code -1}.
     */
    private int selectedCannonIndex = -1;
    /**
     * Whether the throne is highlighted as selected.
     */
    private boolean selectedThrone = false;
    /**
     * Whether the wind vane is highlighted as selected.
     */
    private boolean selectedWindVane = false;
    /**
     * Whether the grid is displayed.
     */
    private boolean gridVisible;
    /**
     * Storage area currently selected for moving or resizing, if any.
     */
    private ResourceArea selectedResourceArea;
    /**
     * The corner currently being resized, or -1 when none is active.
     */
    private int draggingResourceCorner = -1; // 0-3 for corners, -1 if not dragging
    /**
     * Editor controller that receives view edits.
     */
    private EditorPanel editorPanel;
    /**
     * Owns the in-progress polygon's category, vertices, and preview pointer.
     */
    private final CastlePolygonCreation polygonCreation = new CastlePolygonCreation();
    /**
     * Current screen-space pointer position used to highlight a selected polygon edge.
     */
    private transient Point2D edgeHoverPosition = null;
    /**
     * The polygon selection listener.
     */
    private transient Runnable polygonSelectionListener;

    /**
     * Creates the castle canvas and installs its mouse and wheel handlers.
     */
    public CastleViewPanel() {
        addMouseListener(new CastleMouseHandler(this));
        addMouseWheelListener(new CastleZoomHandler(this));
    }

    /**
     * Associates the editor panel with this castle view.
     *
     * @param editorPanel editor that receives edits made in this view
     */
    public void setEditorPanel(EditorPanel editorPanel) {
        this.editorPanel = editorPanel;
    }

    /**
     * Sets whether the castle grid is displayed.
     *
     * @param visible whether the grid should be visible
     */
    public void setGridVisible(boolean visible) {
        if (gridVisible != visible) {
            gridVisible = visible;
            repaint();
        }
    }

    /**
     * Returns whether the grid is displayed.
     *
     * @return {@code true} if the castle grid is currently visible
     */
    public boolean isGridVisible() {
        return gridVisible;
    }

    /**
     * Registers the callback invoked when zoom changes.
     *
     * @param zoomListener the callback notified when zoom changes
     */
    public void setZoomListener(Runnable zoomListener) {
        transformController.setZoomListener(zoomListener);
    }

    /**
     * Registers the callback invoked when the selected polygon changes.
     *
     * @param listener the callback to notify when selection changes
     */
    public void setPolygonSelectionListener(Runnable listener) {
        this.polygonSelectionListener = listener;
    }

    /**
     * Sets the displayed castle and resets its selection, polygon creation, zoom, and rendering offsets.
     *
     * @param castle castle data to display, or {@code null} to clear the view
     */
    public void setCastle(Castle castle) {
        this.castle = castle;
        selectedPolygon = null;
        selectedCannonIndex = -1;
        selectedThrone = false;
        selectedWindVane = false;
        selectedResourceArea = null;
        polygonCreation.clearForCastleSelection();
        edgeHoverPosition = null;
        transformController.reset();
        updatePreferredSize();
        centerCastle();
        SwingUtilities.invokeLater(this::centerCastle);
        repaint();
    }

    /**
     * Replaces the displayed castle data and refreshes preferred size and painting.
     *
     * @param castle replacement castle data for the current view
     */
    public void updateCastle(Castle castle) {
        this.castle = castle;
        updatePreferredSize();
        repaint();
    }

    /**
     * Returns the castle data currently displayed.
     *
     * @return the currently displayed castle, or null when none is selected
     */
    Castle getCastle() {
        return castle;
    }

    /**
     * Returns the editor panel.
     *
     * @return the editor panel associated with this view
     */
    EditorPanel getEditorPanel() {
        return editorPanel;
    }

    /**
     * Changes the selected polygon and notifies the selection listener when it changes.
     *
     * @param polygon polygon to select, or {@code null} to clear polygon selection
     */
    public void setSelectedPolygon(Polygon polygon) {
        if (polygon != selectedPolygon) {
            selectedPolygon = polygon;
            edgeHoverPosition = null;
            repaint();
            if (polygonSelectionListener != null) {
                polygonSelectionListener.run();
            }
        }
    }

    /**
     * Returns the selected polygon.
     *
     * @return the selected polygon, or null when none is selected
     */
    public Polygon getSelectedPolygon() {
        return selectedPolygon;
    }

    /**
     * Returns the selected point index.
     *
     * @return the selected vertex index, or -1 when none is selected
     */
    public int getSelectedPointIndex() {
        return selectedPointIndex;
    }

    /**
     * Changes the selected polygon vertex and repaints when it changes.
     *
     * @param index selected vertex index, or {@code -1} to clear vertex selection
     */
    public void setSelectedPointIndex(int index) {
        if (selectedPointIndex != index) {
            selectedPointIndex = index;
            repaint();
        }
    }

    /**
     * Returns the selected cannon index.
     *
     * @return the selected cannon index, or -1 when none is selected
     */
    public int getSelectedCannonIndex() {
        return selectedCannonIndex;
    }

    /**
     * Changes the selected cannon and repaints when it changes.
     *
     * @param index selected cannon index, or {@code -1} to clear cannon selection
     */
    public void setSelectedCannonIndex(int index) {
        if (selectedCannonIndex != index) {
            selectedCannonIndex = index;
            repaint();
        }
    }

    /**
     * Returns whether the throne is selected.
     *
     * @return {@code true} if the throne is selected
     */
    public boolean isSelectedThrone() {
        return selectedThrone;
    }

    /**
     * Changes the throne selection and repaints when it changes.
     *
     * @param selected whether the throne should be selected
     */
    public void setSelectedThrone(boolean selected) {
        if (selectedThrone != selected) {
            selectedThrone = selected;
            repaint();
        }
    }

    /**
     * Returns whether the wind vane is selected.
     *
     * @return {@code true} if the wind vane is selected
     */
    public boolean isSelectedWindVane() {
        return selectedWindVane;
    }

    /**
     * Changes the wind-vane selection and repaints when it changes.
     *
     * @param selected whether the wind vane should be selected
     */
    public void setSelectedWindVane(boolean selected) {
        if (selectedWindVane != selected) {
            selectedWindVane = selected;
            repaint();
        }
    }

    /**
     * Returns the selected resource area.
     *
     * @return the selected resource area, or null when none is selected
     */
    public ResourceArea getSelectedResourceArea() {
        return selectedResourceArea;
    }

    /**
     * Changes the selected resource area and clears corner-resize state.
     *
     * @param resourceArea selected storage area, or {@code null} to clear the selection
     */
    public void setSelectedResourceArea(ResourceArea resourceArea) {
        if (!java.util.Objects.equals(selectedResourceArea, resourceArea)) {
            selectedResourceArea = resourceArea;
            draggingResourceCorner = -1;
            repaint();
        }
    }

    /**
     * Updates the resource-area corner currently being resized.
     *
     * @param cornerIndex active resize handle index, or {@code -1} when none is being dragged
     */
    void setDraggingResourceCorner(int cornerIndex) {
        if (draggingResourceCorner != cornerIndex) {
            draggingResourceCorner = cornerIndex;
            repaint();
        }
    }

    /**
     * Changes zoom while keeping the supplied cursor position anchored in the viewport.
     *
     * @param multiplier the zoom multiplier to apply
     * @param mousePoint the cursor point to keep fixed during zoom
     */
    void adjustZoom(double multiplier, java.awt.Point mousePoint) {
        transformController.adjustZoom(multiplier, mousePoint);
    }

    /**
     * Scrolls the viewport by the wheel's requested amount.
     *
     * @param event      wheel event whose scroll units determine the amount to move
     * @param horizontal whether to scroll horizontally
     */
    void scrollByWheel(MouseWheelEvent event, boolean horizontal) {
        transformController.scrollByWheel(event, horizontal);
    }

    /**
     * Returns the zoom factor.
     *
     * @return the current zoom factor
     */
    public double getZoomFactor() {
        return transformController.zoomFactor();
    }

    /**
     * Clamps and applies the requested zoom factor.
     *
     * @param zoomFactor the desired zoom factor
     */
    public void setZoomFactor(double zoomFactor) {
        transformController.setZoomFactor(zoomFactor);
    }

    /**
     * Recalculates the preferred drawing size from the castle bounds and zoom.
     */
    private void updatePreferredSize() {
        transformController.updatePreferredSize(castle);
    }

    /**
     * Centers the view within its scroll pane after the preferred size is updated.
     */
    private void centerCastle() {
        transformController.centerCastle();
    }

    /**
     * Returns the rendering origin and scale used to transform castle coordinates to screen coordinates.
     *
     * @return an array containing the left origin, bottom origin, and scale
     */
    double[] getRenderingParameters() {
        return transformController.renderingParameters(castle);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        if (castle == null && !polygonCreation.isActive()) {
            return;
        }
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            double[] transform = transformController.renderingParameters(castle);
            double scale = transform[2];
            double[] origin = {transform[0], transform[1]};
            Point2D edgeHoverMarker = castle != null && selectedPolygon != null && edgeHoverPosition != null
                    ? findClosestPointOnEdge(selectedPolygon, edgeHoverPosition, origin[0], origin[1], scale)
                    : null;
            CastleRenderer.RenderState state = new CastleRenderer.RenderState(castle, selectedPolygon,
                    selectedPointIndex, selectedCannonIndex, selectedThrone, selectedWindVane,
                    selectedResourceArea, draggingResourceCorner, gridVisible, polygonCreation.isActive(),
                    polygonCreation.type(), polygonCreation.points(), polygonCreation.mousePosition(), edgeHoverMarker,
                    origin[0], origin[1], scale, getWidth(), getHeight());
            renderer.draw(g, state);
        } finally {
            g.dispose();
        }
    }

    /**
     * Calculates the cannon bounds in screen coordinates.
     *
     * @param cannon cannon position in castle coordinates
     * @param left   left edge of the rendered castle
     * @param bottom bottom edge of the rendered castle
     * @param scale  scale from castle coordinates to screen coordinates
     * @return the screen-space rectangle occupied by the cannon
     */
    static Rectangle2D.Double calculateCannonRectangle(Point cannon, double left, double bottom, double scale) {
        double screenX = left + cannon.x() * scale;
        double screenY = bottom - cannon.y() * scale;
        double screenWidth = CastleFileFormat.CANNON_WIDTH * scale;
        double screenHeight = CastleFileFormat.CANNON_HEIGHT * scale;
        return new Rectangle2D.Double(screenX, screenY - (CastleFileFormat.CANNON_HEIGHT - 1) * scale, screenWidth, screenHeight);
    }

    /**
     * Calculates the wind-vane bounds in screen coordinates.
     *
     * @param windVane wind-vane position in castle coordinates
     * @param left     left edge of the rendered castle
     * @param bottom   bottom edge of the rendered castle
     * @param scale    scale from castle coordinates to screen coordinates
     * @return the screen-space rectangle occupied by the wind vane
     */
    static Rectangle2D.Double calculateWindVaneRectangle(Point windVane, double left, double bottom, double scale) {
        double screenX = left + windVane.x() * scale;
        double screenY = bottom - windVane.y() * scale;
        double screenWidth = CastleFileFormat.WIND_VANE_WIDTH * scale;
        double screenHeight = (CastleFileFormat.WIND_VANE_HEIGHT + 1) * scale;
        return new Rectangle2D.Double(screenX, screenY - (CastleFileFormat.WIND_VANE_HEIGHT + 1) * scale, screenWidth, screenHeight);
    }

    /**
     * Starts the polygon creation.
     *
     * @param type fill category for the new polygon
     */
    public void startPolygonCreation(CastlePolygonType type) {
        polygonCreation.start(type);
        selectedPolygon = null;
        selectedCannonIndex = -1;
        selectedThrone = false;
        selectedWindVane = false;
        selectedResourceArea = null;
        updatePreferredSize();
        repaint();
    }

    /**
     * Cancels polygon creation and clears its status-bar hint.
     */
    public void cancelPolygonCreation() {
        polygonCreation.cancel();
        updatePreferredSize();
        repaint();
    }

    /**
     * Returns whether polygon creation is active.
     *
     * @return {@code true} while the view is collecting vertices for a new polygon
     */
    public boolean isInPolygonCreationMode() {
        return polygonCreation.isActive();
    }

    /**
     * Returns the polygon type.
     *
     * @return the active polygon category, or {@code null} when creation is inactive
     */
    public CastlePolygonType getPolygonType() {
        return polygonCreation.type();
    }

    /**
     * Returns the creation points.
     *
     * @return a copy of the vertices entered so far, in click order
     */
    List<Point> getCreationPoints() {
        return polygonCreation.points();
    }

    /**
     * Adds the creation point.
     *
     * @param point new vertex in castle coordinates
     */
    void addCreationPoint(Point point) {
        polygonCreation.addPoint(point);
        repaint();
    }

    /**
     * Clears the in-progress polygon and exits polygon-creation mode.
     */
    public void finishPolygonCreation() {
        polygonCreation.finish();
        repaint();
    }

    /**
     * Updates the pointer position used to preview polygon creation.
     *
     * @param position current pointer position in screen coordinates, or {@code null} when no preview is shown
     */
    void setMousePosition(Point2D position) {
        polygonCreation.setMousePosition(position);
        repaint();
    }

    /**
     * Updates the pointer position used to highlight a selected polygon edge.
     *
     * @param position current pointer position in screen coordinates, or {@code null} to clear edge highlighting
     */
    void setEdgeHoverPosition(Point2D position) {
        edgeHoverPosition = position;
        repaint();
    }

    /**
     * Finds the selected polygon edge nearest to the current hover point.
     *
     * @param left   the castle origin x-coordinate
     * @param bottom the castle baseline y-coordinate
     * @param scale  the rendering scale
     * @return zero-based edge-start vertex index, or {@code -1} when the pointer is not near an eligible edge
     */
    int findEdgeIndexForHoverPosition(double left, double bottom, double scale) {
        if (selectedPolygon == null || edgeHoverPosition == null) {
            return -1;
        }
        Point2D closestPoint = findClosestPointOnEdge(selectedPolygon, edgeHoverPosition, left, bottom, scale);
        if (closestPoint == null) {
            return -1;
        }

        List<Point> points = selectedPolygon.points();
        final double edgeThreshold = 8.0;
        final double pointThreshold = 10.0;

        for (int i = 0; i < points.size(); i++) {
            Point p1 = points.get(i);
            Point p2 = points.get((i + 1) % points.size());

            double screenX1 = left + p1.x() * scale;
            double screenY1 = bottom - p1.y() * scale;
            double screenX2 = left + p2.x() * scale;
            double screenY2 = bottom - p2.y() * scale;

            double distToP1 = edgeHoverPosition.distance(screenX1, screenY1);
            double distToP2 = edgeHoverPosition.distance(screenX2, screenY2);

            if (distToP1 < pointThreshold || distToP2 < pointThreshold) {
                continue;
            }

            Point2D closestOnSegment = closestPointOnSegment(
                    edgeHoverPosition, screenX1, screenY1, screenX2, screenY2);

            double distance = edgeHoverPosition.distance(closestOnSegment);

            if (distance < edgeThreshold) {
                return i; // Return the edge index (point index where the edge starts)
            }
        }

        return -1;
    }

    /**
     * Finds the nearest eligible edge point within the hover threshold.
     *
     * @param polygon    polygon whose edges are searched
     * @param mousePoint pointer position in screen coordinates
     * @param left       the castle origin x-coordinate
     * @param bottom     the castle baseline y-coordinate
     * @param scale      the rendering scale
     * @return nearest screen-space edge point, or {@code null} when no edge is near the pointer
     */
    private Point2D findClosestPointOnEdge(Polygon polygon, Point2D mousePoint,
                                           double left, double bottom, double scale) {
        final double edgeThreshold = 8.0; // Maximum distance to consider as "near edge"
        final double pointThreshold = 10.0; // Distance to avoid showing near existing points

        List<Point> points = polygon.points();
        Point2D closestPoint = null;
        double minDistance = Double.MAX_VALUE;

        for (int i = 0; i < points.size(); i++) {
            Point p1 = points.get(i);
            Point p2 = points.get((i + 1) % points.size());

            double screenX1 = left + p1.x() * scale;
            double screenY1 = bottom - p1.y() * scale;
            double screenX2 = left + p2.x() * scale;
            double screenY2 = bottom - p2.y() * scale;

            // Check if mouse is too close to either endpoint
            double distToP1 = mousePoint.distance(screenX1, screenY1);
            double distToP2 = mousePoint.distance(screenX2, screenY2);

            if (distToP1 < pointThreshold || distToP2 < pointThreshold) {
                continue; // Skip this edge, too close to an existing point
            }

            // Find closest point on this line segment
            Point2D closestOnSegment = closestPointOnSegment(
                    mousePoint, screenX1, screenY1, screenX2, screenY2);

            double distance = mousePoint.distance(closestOnSegment);

            if (distance < edgeThreshold && distance < minDistance) {
                minDistance = distance;
                closestPoint = closestOnSegment;
            }
        }

        return closestPoint;
    }

    /**
     * Finds the point on a segment nearest to the supplied query point.
     *
     * @param point query point in screen coordinates
     * @param x1    first segment endpoint's x-coordinate
     * @param y1    first segment endpoint's y-coordinate
     * @param x2    second segment endpoint's x-coordinate
     * @param y2    second segment endpoint's y-coordinate
     * @return nearest point on the closed segment in screen coordinates
     */
    private Point2D closestPointOnSegment(Point2D point, double x1, double y1,
                                          double x2, double y2) {
        double dx = x2 - x1;
        double dy = y2 - y1;

        if (dx == 0 && dy == 0) {
            return new Point2D.Double(x1, y1);
        }

        double t = ((point.getX() - x1) * dx + (point.getY() - y1) * dy) / (dx * dx + dy * dy);

        // Clamp t to [0, 1] to stay on the segment
        t = t <= 0 ? 0 : Math.clamp(t, 0, 1);

        return new Point2D.Double(x1 + t * dx, y1 + t * dy);
    }
}
