package at.josefjordan.ballerburg.editor.ui.editor;

import at.josefjordan.ballerburg.editor.model.CastleDocument.*;
import at.josefjordan.ballerburg.editor.model.CastleDocument.Point;
import at.josefjordan.ballerburg.editor.model.CastleDocument.Polygon;
import at.josefjordan.ballerburg.editor.model.CastleFileFormat;
import hgb.gui.HGBasePanel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Draws one castle without owning or modifying editor state.
 */
final class CastleViewPanel extends HGBasePanel {
    /**
     * Minimum supported zoom factor (10%).
     */
    public static final double MIN_ZOOM_FACTOR = 0.10; // corresponds 10 %
    /**
     * Maximum supported zoom factor (1000%).
     */
    public static final double MAX_ZOOM_FACTOR = 10.0; // corresponds 1000 %
    /**
     * Additional multiplier applied to mouse-wheel scrolling.
     */
    private static final int WHEEL_SCROLL_MULTIPLIER = 4;
    /**
     * Base drawing scale at 100% zoom.
     */
    private static final double BASE_SCALE = 4.0; // Base scale multiplier for 100% zoom
    /**
     * Padding around castle contents in screen pixels.
     */
    private static final double MARGIN = 24.0;
    /**
     * Calculates the castle-coordinate bounds used to size and position rendered content.
     */
    private final CastleBoundsCalculator boundsCalculator = new CastleBoundsCalculator();
    /**
     * Renders the castle view from a snapshot of this panel's state.
     */
    private final CastleRenderer renderer = new CastleRenderer();
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
     * The current zoom factor.
     */
    private double zoomFactor = 1.0;
    /**
     * Horizontal screen-space correction used to keep the zoom anchor in place.
     */
    private double renderingOffsetX;
    /**
     * Vertical screen-space correction used to keep the zoom anchor in place.
     */
    private double renderingOffsetY;
    /**
     * The listener notified when zoom changes.
     */
    private transient Runnable zoomListener;
    /**
     * Whether clicks are currently adding vertices to a new polygon.
     */
    private boolean polygonCreationMode = false;
    /**
     * The fill category used for the polygon being created.
     */
    private String polygonType = null; // "wall" or "roof"
    /**
     * Vertices entered for the polygon being created, in click order.
     */
    private final List<Point> creationPoints = new ArrayList<>();
    /**
     * Current screen-space pointer position used to draw the polygon preview.
     */
    private transient Point2D mousePosition = null;
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
    CastleViewPanel() {
        addMouseListener(new CastleMouseHandler(this));
        addMouseWheelListener(new CastleZoomHandler(this));
    }

    /**
     * Associates the editor panel with this castle view.
     *
     * @param editorPanel editor that receives edits made in this view
     */
    void setEditorPanel(EditorPanel editorPanel) {
        this.editorPanel = editorPanel;
    }

    /**
     * Sets whether the castle grid is displayed.
     *
     * @param visible whether the grid should be visible
     */
    void setGridVisible(boolean visible) {
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
    boolean isGridVisible() {
        return gridVisible;
    }

    /**
     * Registers the callback invoked when zoom changes.
     *
     * @param zoomListener the callback notified when zoom changes
     */
    void setZoomListener(Runnable zoomListener) {
        this.zoomListener = zoomListener;
    }

    /**
     * Registers the callback invoked when the selected polygon changes.
     *
     * @param listener the callback to notify when selection changes
     */
    void setPolygonSelectionListener(Runnable listener) {
        this.polygonSelectionListener = listener;
    }

    /**
     * Sets the displayed castle and resets its selection, polygon creation, zoom, and rendering offsets.
     *
     * @param castle castle data to display, or {@code null} to clear the view
     */
    void setCastle(Castle castle) {
        this.castle = castle;
        selectedPolygon = null;
        selectedCannonIndex = -1;
        selectedThrone = false;
        selectedWindVane = false;
        selectedResourceArea = null;
        polygonCreationMode = false;
        polygonType = null;
        creationPoints.clear();
        edgeHoverPosition = null;
        zoomFactor = 1.0;
        renderingOffsetX = 0;
        renderingOffsetY = 0;
        notifyZoomChanged();
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
    void updateCastle(Castle castle) {
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
    void setSelectedPolygon(Polygon polygon) {
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
    Polygon getSelectedPolygon() {
        return selectedPolygon;
    }

    /**
     * Returns the selected point index.
     *
     * @return the selected vertex index, or -1 when none is selected
     */
    int getSelectedPointIndex() {
        return selectedPointIndex;
    }

    /**
     * Changes the selected polygon vertex and repaints when it changes.
     *
     * @param index selected vertex index, or {@code -1} to clear vertex selection
     */
    void setSelectedPointIndex(int index) {
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
    int getSelectedCannonIndex() {
        return selectedCannonIndex;
    }

    /**
     * Changes the selected cannon and repaints when it changes.
     *
     * @param index selected cannon index, or {@code -1} to clear cannon selection
     */
    void setSelectedCannonIndex(int index) {
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
    boolean isSelectedThrone() {
        return selectedThrone;
    }

    /**
     * Changes the throne selection and repaints when it changes.
     *
     * @param selected whether the throne should be selected
     */
    void setSelectedThrone(boolean selected) {
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
    boolean isSelectedWindVane() {
        return selectedWindVane;
    }

    /**
     * Changes the wind-vane selection and repaints when it changes.
     *
     * @param selected whether the wind vane should be selected
     */
    void setSelectedWindVane(boolean selected) {
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
    ResourceArea getSelectedResourceArea() {
        return selectedResourceArea;
    }

    /**
     * Changes the selected resource area and clears corner-resize state.
     *
     * @param resourceArea selected storage area, or {@code null} to clear the selection
     */
    void setSelectedResourceArea(ResourceArea resourceArea) {
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
     * Multiplies and clamps the current zoom factor.
     *
     * @param multiplier the zoom multiplier to apply
     */
    void adjustZoom(double multiplier) {
        adjustZoom(multiplier, null);
    }

    /**
     * Changes zoom while keeping the supplied cursor position anchored in the viewport.
     *
     * @param multiplier the zoom multiplier to apply
     * @param mousePoint the cursor point to keep fixed during zoom
     */
    void adjustZoom(double multiplier, java.awt.Point mousePoint) {
        JViewport viewport = getViewport();
        java.awt.Point oldViewPosition = viewport == null ? null : viewport.getViewPosition();
        double[] oldRenderingParameters = mousePoint == null ? null : getRenderingParameters();
        java.awt.Point mouseViewportPoint = viewport == null || mousePoint == null ? null : new java.awt.Point(
                mousePoint.x - oldViewPosition.x,
                mousePoint.y - oldViewPosition.y);

        double worldX = mousePoint == null ? 0 : (mousePoint.x - oldRenderingParameters[0]) / oldRenderingParameters[2];
        double worldY = mousePoint == null ? 0 : (oldRenderingParameters[1] - mousePoint.y) / oldRenderingParameters[2];
        zoomFactor *= multiplier;
        zoomFactor = Math.clamp(zoomFactor, MIN_ZOOM_FACTOR, MAX_ZOOM_FACTOR);

        // Calculate the new transform without the old cursor correction first.
        renderingOffsetX = 0;
        renderingOffsetY = 0;
        updatePreferredSize();
        setSize(getPreferredSize());

        if (mousePoint != null && viewport != null) {
            double[] newRenderingParameters = calculateRenderingParameters(false);
            double newPanelX = newRenderingParameters[0] + worldX * newRenderingParameters[2];
            double newPanelY = newRenderingParameters[1] - worldY * newRenderingParameters[2];

            java.awt.Point newViewPosition = new java.awt.Point(
                    (int) Math.round(newPanelX - mouseViewportPoint.x),
                    (int) Math.round(newPanelY - mouseViewportPoint.y));
            if (viewport.getParent() instanceof JScrollPane scrollPane) {
                scrollPane.validate();
            }
            java.awt.Dimension extent = viewport.getExtentSize();
            java.awt.Dimension viewSize = getPreferredSize();
            int maxScrollX = Math.max(0, viewSize.width - extent.width);
            int maxScrollY = Math.max(0, viewSize.height - extent.height);
            int scrollX = Math.clamp(newViewPosition.x, 0, maxScrollX);
            int scrollY = Math.clamp(newViewPosition.y, 0, maxScrollY);
            viewport.setViewPosition(new java.awt.Point(scrollX, scrollY));

            // If the view cannot scroll far enough, preserve the cursor point
            // by translating the content inside the viewport instead.
            renderingOffsetX = mouseViewportPoint.x + scrollX - newPanelX;
            renderingOffsetY = mouseViewportPoint.y + scrollY - newPanelY;
            updatePreferredSize();
            setSize(getPreferredSize());
            viewport.revalidate();
            if (viewport.getParent() != null) {
                viewport.getParent().revalidate();
            }
        }

        notifyZoomChanged();
        repaint();
    }

    /**
     * Returns the viewport.
     *
     * @return the containing viewport, or null when this panel is not in one
     */
    private JViewport getViewport() {
        return getParent() instanceof JViewport viewport ? viewport : null;
    }

    /**
     * Scrolls the viewport by the wheel's requested amount.
     *
     * @param event      wheel event whose scroll units determine the amount to move
     * @param horizontal whether to scroll horizontally
     */
    void scrollByWheel(MouseWheelEvent event, boolean horizontal) {
        JViewport viewport = getViewport();
        if (viewport == null || !(viewport.getParent() instanceof JScrollPane scrollPane)) {
            return;
        }

        JScrollBar scrollBar = horizontal
                ? scrollPane.getHorizontalScrollBar()
                : scrollPane.getVerticalScrollBar();
        int units = event.getUnitsToScroll();
        if (units == 0) {
            units = event.getWheelRotation();
        }
        int unitIncrement = scrollBar.getUnitIncrement(units);
        if (unitIncrement <= 0) {
            unitIncrement = Math.max(1, event.getScrollAmount());
        }
        scrollBar.setValue(scrollBar.getValue() + units * unitIncrement * WHEEL_SCROLL_MULTIPLIER);
    }

    /**
     * Returns the zoom factor.
     *
     * @return the current zoom factor
     */
    double getZoomFactor() {
        return zoomFactor;
    }

    /**
     * Clamps and applies the requested zoom factor.
     *
     * @param zoomFactor the desired zoom factor
     */
    void setZoomFactor(double zoomFactor) {
        double boundedZoomFactor = Math.clamp(zoomFactor, MIN_ZOOM_FACTOR, MAX_ZOOM_FACTOR);
        if (this.zoomFactor == boundedZoomFactor) {
            return;
        }
        adjustZoom(boundedZoomFactor / this.zoomFactor);
    }

    /**
     * Notifies the registered listener that the zoom factor changed.
     */
    private void notifyZoomChanged() {
        if (zoomListener != null) {
            zoomListener.run();
        }
    }

    /**
     * Recalculates the preferred drawing size from the castle bounds and zoom.
     */
    private void updatePreferredSize() {
        CastleBoundsCalculator.Bounds bounds = boundsCalculator.calculate(castle);
        double scale = BASE_SCALE * zoomFactor;
        int baseWidth = castle != null ? castle.width() : 100;
        int contentWidth = Math.max(baseWidth, bounds.maximumX() - bounds.minimumX());
        int contentHeight = bounds.maximumY() - bounds.minimumY();
        int preferredWidth = (int) Math.ceil(2 * MARGIN + contentWidth * scale);
        int preferredHeight = (int) Math.ceil(2 * MARGIN + contentHeight * scale);
        JViewport viewport = getViewport();
        if (viewport != null) {
            preferredWidth = adjustPreferredDimension(preferredWidth, viewport.getWidth(), renderingOffsetX);
            preferredHeight = adjustPreferredDimension(preferredHeight, viewport.getHeight(), renderingOffsetY);
        }
        double[] renderingOrigin = calculateRenderingOrigin(bounds, scale,
                renderingOffsetX == 0 && renderingOffsetY == 0);
        preferredWidth = Math.max(preferredWidth,
                (int) Math.ceil(renderingOrigin[0] + bounds.maximumX() * scale + MARGIN));
        preferredHeight = Math.max(preferredHeight,
                (int) Math.ceil(renderingOrigin[1] - bounds.minimumY() * scale + MARGIN));
        setPreferredSize(new java.awt.Dimension(preferredWidth, preferredHeight));
        revalidate();

        // Also revalidate the scroll pane viewport to update scrollbars
        if (getParent() instanceof javax.swing.JViewport parentViewport) {
            parentViewport.revalidate();
        }
    }

    /**
     * Adjusts the preferred dimension.
     *
     * @param preferred         current preferred panel dimension in pixels
     * @param viewportDimension viewport extent along this axis in pixels
     * @param renderingOffset   screen-space correction along this axis
     * @return a dimension large enough for both the preferred size and viewport
     */
    private int adjustPreferredDimension(int preferred, int viewportDimension, double renderingOffset) {
        if (preferred > viewportDimension) {
            preferred += (int) Math.ceil(Math.abs(renderingOffset));
        }
        return Math.max(preferred, viewportDimension);
    }

    /**
     * Centers the view within its scroll pane after the preferred size is updated.
     */
    private void centerCastle() {
        JViewport viewport = getViewport();
        if (viewport != null && viewport.getParent() instanceof JScrollPane) {
            viewport.revalidate();
            viewport.getParent().validate();
            java.awt.Dimension panelSize = getPreferredSize();
            java.awt.Dimension viewportSize = viewport.getSize();

            int scrollX = Math.max(0, (panelSize.width - viewportSize.width) / 2);
            int scrollY = Math.max(0, (panelSize.height - viewportSize.height) / 2);

            viewport.setViewPosition(new java.awt.Point(scrollX, scrollY));
        }
    }

    /**
     * Returns the rendering origin and scale used to transform castle coordinates to screen coordinates.
     *
     * @return an array containing the left origin, bottom origin, and scale
     */
    double[] getRenderingParameters() {
        return calculateRenderingParameters(renderingOffsetX == 0 && renderingOffsetY == 0);
    }

    /**
     * Returns the drawing origin and scale used for screen-coordinate conversion.
     *
     * @param center whether to center the content within the panel before applying offsets
     * @return an array containing the left origin, bottom origin, and scale
     */
    private double[] calculateRenderingParameters(boolean center) {
        CastleBoundsCalculator.Bounds bounds = boundsCalculator.calculate(castle);
        double scale = BASE_SCALE * zoomFactor;
        double[] origin = calculateRenderingOrigin(bounds, scale, center);
        double left = origin[0];
        double bottom = origin[1];
        return new double[]{left, bottom, scale};
    }

    /**
     * Calculates the on-screen origin from the content bounds, scale, and centering state.
     *
     * @param bounds castle-coordinate extents
     * @param scale  the rendering scale
     * @param center whether to center the content within the panel before applying offsets
     * @return left and bottom screen-space origins as {@code [x, y]}
     */
    private double[] calculateRenderingOrigin(CastleBoundsCalculator.Bounds bounds, double scale, boolean center) {
        int contentWidthInUnits = Math.max(castle != null ? castle.width() : 100,
                bounds.maximumX() - bounds.minimumX());
        double contentWidth = 2 * MARGIN + contentWidthInUnits * scale;
        double contentHeight = 2 * MARGIN + (bounds.maximumY() - bounds.minimumY()) * scale;
        double horizontalOffset = center
                ? Math.max(0, (getWidth() - contentWidth) / 2.0)
                : 0;
        double verticalOffset = center
                ? Math.max(0, (getHeight() - contentHeight) / 2.0)
                : 0;
        return new double[]{
                MARGIN - bounds.minimumX() * scale + horizontalOffset + renderingOffsetX,
                MARGIN + bounds.maximumY() * scale + verticalOffset + renderingOffsetY,
        };
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        if (castle == null && !polygonCreationMode) {
            return;
        }
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            CastleBoundsCalculator.Bounds bounds = boundsCalculator.calculate(castle);
            double scale = BASE_SCALE * zoomFactor;
            double[] origin = calculateRenderingOrigin(bounds, scale,
                    renderingOffsetX == 0 && renderingOffsetY == 0);
            Point2D edgeHoverMarker = castle != null && selectedPolygon != null && edgeHoverPosition != null
                    ? findClosestPointOnEdge(selectedPolygon, edgeHoverPosition, origin[0], origin[1], scale)
                    : null;
            CastleRenderer.RenderState state = new CastleRenderer.RenderState(castle, selectedPolygon,
                    selectedPointIndex, selectedCannonIndex, selectedThrone, selectedWindVane,
                    selectedResourceArea, draggingResourceCorner, gridVisible, polygonCreationMode,
                    polygonType, creationPoints, mousePosition, edgeHoverMarker,
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
     * @param type fill category for the new polygon ({@code "wall"} or {@code "roof"})
     */
    void startPolygonCreation(String type) {
        polygonCreationMode = true;
        polygonType = type;
        creationPoints.clear();
        mousePosition = null;
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
    void cancelPolygonCreation() {
        polygonCreationMode = false;
        polygonType = null;
        creationPoints.clear();
        updatePreferredSize();
        repaint();
    }

    /**
     * Returns whether polygon creation is active.
     *
     * @return {@code true} while the view is collecting vertices for a new polygon
     */
    boolean isInPolygonCreationMode() {
        return polygonCreationMode;
    }

    /**
     * Returns the polygon type.
     *
     * @return the active polygon category, or {@code null} when creation is inactive
     */
    String getPolygonType() {
        return polygonType;
    }

    /**
     * Returns the creation points.
     *
     * @return a copy of the vertices entered so far, in click order
     */
    List<Point> getCreationPoints() {
        return new ArrayList<>(creationPoints);
    }

    /**
     * Adds the creation point.
     *
     * @param point new vertex in castle coordinates
     */
    void addCreationPoint(Point point) {
        creationPoints.add(point);
        repaint();
    }

    /**
     * Clears the in-progress polygon and exits polygon-creation mode.
     */
    void finishPolygonCreation() {
        polygonCreationMode = false;
        polygonType = null;
        creationPoints.clear();
        mousePosition = null;
        repaint();
    }

    /**
     * Updates the pointer position used to preview polygon creation.
     *
     * @param position current pointer position in screen coordinates, or {@code null} when no preview is shown
     */
    void setMousePosition(Point2D position) {
        mousePosition = position;
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
