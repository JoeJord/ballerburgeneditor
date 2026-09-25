package at.josefjordan.ballerburg.editor.ui.editor;

import at.josefjordan.ballerburg.editor.model.CastleDocument.*;
import at.josefjordan.ballerburg.editor.model.CastleDocument.Point;
import at.josefjordan.ballerburg.editor.model.CastleDocument.Polygon;
import at.josefjordan.ballerburg.editor.model.CastleFileFormat;
import hgb.gui.HGBasePanel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

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
     * Grid interval in castle-coordinate units.
     */
    private static final int GRID_SPACING = 1;
    /**
     * Grid-line color.
     */
    private static final Color COLOR_GRID = new Color(210, 210, 210);
    /**
     * Selection highlight color.
     */
    private static final Color COLOR_SELECTED = new Color(255, 255, 0);
    /**
     * Point and hover highlight color.
     */
    private static final Color COLOR_HIGHLIGHTED = new Color(255, 100, 100);
    /**
     * Polygon outline color.
     */
    private static final Color COLOR_OUTLINE = new Color(45, 45, 45);
    /**
     * Default polygon fill color.
     */
    private static final Color COLOR_FILL_DEFAULT = new Color(120, 130, 140);
    /**
     * Roof fill color.
     */
    private static final Color COLOR_FILL_ROOF = new Color(145, 65, 55);
    /**
     * Masonry fill color.
     */
    private static final Color COLOR_FILL_MASONRY = new Color(150, 150, 145);
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
        int[] bounds = calculateContentBounds();
        int minimumX = bounds[0];
        int minimumY = bounds[1];
        int maximumY = bounds[2];
        int maximumX = bounds[3];
        double scale = BASE_SCALE * zoomFactor;
        int baseWidth = castle != null ? castle.width() : 100;
        int contentWidth = Math.max(baseWidth, maximumX - minimumX);
        int contentHeight = maximumY - minimumY;
        int preferredWidth = (int) Math.ceil(2 * MARGIN + contentWidth * scale);
        int preferredHeight = (int) Math.ceil(2 * MARGIN + contentHeight * scale);
        JViewport viewport = getViewport();
        if (viewport != null) {
            preferredWidth = adjustPreferredDimension(preferredWidth, viewport.getWidth(), renderingOffsetX);
            preferredHeight = adjustPreferredDimension(preferredHeight, viewport.getHeight(), renderingOffsetY);
        }
        double[] renderingOrigin = calculateRenderingOrigin(minimumX, maximumY, scale,
                renderingOffsetX == 0 && renderingOffsetY == 0);
        preferredWidth = Math.max(preferredWidth,
                (int) Math.ceil(renderingOrigin[0] + maximumX * scale + MARGIN));
        preferredHeight = Math.max(preferredHeight,
                (int) Math.ceil(renderingOrigin[1] - minimumY * scale + MARGIN));
        setPreferredSize(new java.awt.Dimension(preferredWidth, preferredHeight));
        revalidate();

        // Also revalidate the scroll pane viewport to update scrollbars
        if (getParent() instanceof javax.swing.JViewport parentViewport) {
            parentViewport.revalidate();
        }
    }

    /**
     * Calculates the extents of the castle drawing and positioned objects.
     *
     * @return four castle-coordinate bounds in {@code [minX, minY, maxY, maxX]} order
     */
    private int[] calculateContentBounds() {
        int minimumX = 0;
        int minimumY = 0;
        int maximumY = 1;
        int maximumX = castle == null ? 100 : castle.width();
        if (castle == null) {
            return new int[]{minimumX, minimumY, maximumY, maximumX};
        }

        for (DrawingCommand command : castle.drawing()) {
            if (command instanceof Polygon(List<Point> points)) {
                for (Point point : points) {
                    minimumX = Math.min(minimumX, point.x());
                    minimumY = Math.min(minimumY, point.y());
                    maximumX = Math.max(maximumX, point.x());
                    maximumY = Math.max(maximumY, point.y());
                }
            }
        }
        if (isPositioned(castle.throne())) {
            Size throneSize = new Size(CastleFileFormat.THRONE_WIDTH, CastleFileFormat.THRONE_HEIGHT);
            minimumX = Math.min(minimumX, castle.throne().x());
            minimumY = Math.min(minimumY, castle.throne().y());
            maximumX = Math.max(maximumX, castle.throne().x() + CastleFileFormat.THRONE_WIDTH);
            maximumY = Math.max(maximumY, areaTop(castle.throne(), throneSize));
        }
        for (Point cannon : castle.cannons()) {
            minimumX = Math.min(minimumX, cannon.x());
            minimumY = Math.min(minimumY, cannon.y());
            maximumX = Math.max(maximumX, cannon.x() + CastleFileFormat.CANNON_WIDTH);
            maximumY = Math.max(maximumY, cannon.y() + CastleFileFormat.CANNON_HEIGHT);
        }
        int[] bounds = updateResourceBounds(minimumX, minimumY, maximumY, maximumX);
        minimumX = bounds[0];
        minimumY = bounds[1];
        maximumY = bounds[2];
        maximumX = bounds[3];
        if (isPositioned(castle.windVane())) {
            minimumX = Math.min(minimumX, castle.windVane().x());
            minimumY = Math.min(minimumY, castle.windVane().y());
            maximumX = Math.max(maximumX, castle.windVane().x() + CastleFileFormat.WIND_VANE_WIDTH);
            maximumY = Math.max(maximumY, castle.windVane().y() + CastleFileFormat.WIND_VANE_HEIGHT);
        }
        return new int[]{minimumX, minimumY, maximumY, maximumX};
    }

    /**
     * Expands the castle bounds to include positioned resource-storage areas.
     *
     * @param minimumX current minimum horizontal castle coordinate
     * @param minimumY current minimum vertical castle coordinate
     * @param maximumY current maximum vertical castle coordinate
     * @param maximumX current maximum horizontal castle coordinate
     * @return bounds in {@code [minX, minY, maxY, maxX]} order
     */
    private int[] updateResourceBounds(int minimumX, int minimumY, int maximumY, int maximumX) {
        Point money = castle.money();
        Size moneyRoom = castle.moneyRoom();
        if (isPositioned(money)) {
            minimumX = Math.min(minimumX, money.x());
            minimumY = Math.min(minimumY, money.y());
            maximumX = Math.max(maximumX, money.x() + moneyRoom.width());
            maximumY = Math.max(maximumY, areaTop(money, moneyRoom));
        }
        Point powder = castle.powder();
        Size powderRoom = castle.powderRoom();
        if (isPositioned(powder)) {
            minimumX = Math.min(minimumX, powder.x());
            minimumY = Math.min(minimumY, powder.y());
            maximumX = Math.max(maximumX, powder.x() + powderRoom.width());
            maximumY = Math.max(maximumY, areaTop(powder, powderRoom));
        }
        Point balls = castle.balls();
        Size ballRoom = castle.ballRoom();
        if (isPositioned(balls)) {
            minimumX = Math.min(minimumX, balls.x());
            minimumY = Math.min(minimumY, balls.y());
            maximumX = Math.max(maximumX, balls.x() + ballRoom.width());
            maximumY = Math.max(maximumY, areaTop(balls, ballRoom));
        }
        return new int[]{minimumX, minimumY, maximumY, maximumX};
    }

    /**
     * Returns whether both coordinates identify a positioned object.
     *
     * @param point coordinate pair to test for the unset-position sentinel
     * @return {@code true} if neither coordinate uses the unset-position sentinel
     */
    private boolean isPositioned(Point point) {
        return point.x() != Integer.MIN_VALUE && point.y() != Integer.MIN_VALUE;
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
     * Calculates the greatest y-coordinate occupied by the castle and its objects.
     *
     * @return greatest occupied vertical coordinate, or {@code 1} when no content extends above 1
     */
    private int calculateMaximumY() {
        int maximumY = 1;
        if (castle != null) {
            for (DrawingCommand command : castle.drawing()) {
                if (command instanceof Polygon(List<Point> points)) {
                    for (Point point : points) {
                        maximumY = Math.max(maximumY, point.y());
                    }
                }
            }
            if (castle.throne().x() != Integer.MIN_VALUE && castle.throne().y() != Integer.MIN_VALUE) {
                maximumY = Math.max(maximumY, areaTop(castle.throne(),
                        new Size(CastleFileFormat.THRONE_WIDTH, CastleFileFormat.THRONE_HEIGHT)));
            }
            maximumY = Math.max(maximumY, calculateMaximumCannonY(castle));
            maximumY = Math.max(maximumY, areaTop(castle.money(), castle.moneyRoom()));
            maximumY = Math.max(maximumY, areaTop(castle.powder(), castle.powderRoom()));
            maximumY = Math.max(maximumY, areaTop(castle.balls(), castle.ballRoom()));
            maximumY = Math.max(maximumY, castle.windVane().y() + CastleFileFormat.WIND_VANE_HEIGHT);
        }
        return maximumY;
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
        int[] bounds = calculateBounds();
        int minimumX = bounds[0];
        int baseMaximumY = calculateMaximumY();
        double scale = BASE_SCALE * zoomFactor;
        double[] origin = calculateRenderingOrigin(minimumX, baseMaximumY, scale, center);
        double left = origin[0];
        double bottom = origin[1];
        return new double[]{left, bottom, scale};
    }

    /**
     * Calculates the on-screen origin from the content bounds, scale, and centering state.
     *
     * @param minimumX minimum castle x-coordinate included in the content bounds
     * @param maximumY maximum castle y-coordinate included in the content bounds
     * @param scale    the rendering scale
     * @param center   whether to center the content within the panel before applying offsets
     * @return left and bottom screen-space origins as {@code [x, y]}
     */
    private double[] calculateRenderingOrigin(int minimumX, int maximumY, double scale, boolean center) {
        int[] bounds = calculateBounds();
        int minimumY = bounds[1];
        int contentWidthInUnits = Math.max(castle != null ? castle.width() : 100, bounds[2] - minimumX);
        double contentWidth = 2 * MARGIN + contentWidthInUnits * scale;
        double contentHeight = 2 * MARGIN + (maximumY - minimumY) * scale;
        double horizontalOffset = center
                ? Math.max(0, (getWidth() - contentWidth) / 2.0)
                : 0;
        double verticalOffset = center
                ? Math.max(0, (getHeight() - contentHeight) / 2.0)
                : 0;
        return new double[]{
                MARGIN - minimumX * scale + horizontalOffset + renderingOffsetX,
                MARGIN + maximumY * scale + verticalOffset + renderingOffsetY,
        };
    }

    /**
     * Returns the castle content bounds in minimum-x, minimum-y, maximum-x, maximum-y order.
     *
     * @return bounds in {@code [minX, minY, maxX, maxY]} order
     */
    private int[] calculateBounds() {
        int[] bounds = calculateContentBounds();
        return new int[]{bounds[0], bounds[1], bounds[3], bounds[2]};
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        if (castle == null && !polygonCreationMode) {
            return;
        }
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (castle != null) {
                if (gridVisible) {
                    drawGrid(g);
                }
                drawCastle(g);
            }
            if (polygonCreationMode) {
                drawPolygonCreation(g);
            }
        } finally {
            g.dispose();
        }
    }

    /**
     * Draws the grid.
     *
     * @param g graphics context used to draw the grid
     */
    private void drawGrid(Graphics2D g) {
        int[] bounds = calculateBounds();
        double scale = BASE_SCALE * zoomFactor;
        double[] origin = calculateRenderingOrigin(bounds[0], calculateMaximumY(), scale,
                renderingOffsetX == 0 && renderingOffsetY == 0);
        double left = origin[0];
        double bottom = origin[1];

        g.setColor(COLOR_GRID);
        g.setStroke(new BasicStroke(1.0f));
        int firstX = (int) Math.floor(-left / scale / GRID_SPACING) * GRID_SPACING;
        int lastX = (int) Math.ceil((getWidth() - left) / scale / GRID_SPACING) * GRID_SPACING;
        int firstY = (int) Math.floor((bottom - getHeight()) / scale / GRID_SPACING) * GRID_SPACING;
        int lastY = (int) Math.ceil(bottom / scale / GRID_SPACING) * GRID_SPACING;
        for (int x = firstX; x <= lastX; x += GRID_SPACING) {
            double screenX = left + x * scale;
            g.draw(new Line2D.Double(screenX, 0, screenX, getHeight()));
        }
        for (int y = firstY; y <= lastY; y += GRID_SPACING) {
            double screenY = bottom - y * scale;
            g.draw(new Line2D.Double(0, screenY, getWidth(), screenY));
        }
    }

    /**
     * Draws the castle.
     *
     * @param g graphics context used to draw the castle
     */
    private void drawCastle(Graphics2D g) {
        int[] bounds = calculateBounds();
        int minimumX = bounds[0];
        int baseMaximumY = calculateMaximumY();

        double scale = BASE_SCALE * zoomFactor;
        if (scale <= 0) {
            return;
        }
        double[] origin = calculateRenderingOrigin(minimumX, baseMaximumY, scale,
                renderingOffsetX == 0 && renderingOffsetY == 0);
        double left = origin[0];
        double bottom = origin[1];
        drawCastlePolygons(g, left, bottom, scale);
        drawCastleObjects(g, left, bottom, scale);
        drawEdgeHover(g, left, bottom, scale);
    }

    /**
     * Draws the castle polygons.
     *
     * @param g      graphics context used to draw the castle polygons
     * @param left   the castle origin x-coordinate
     * @param bottom the castle baseline y-coordinate
     * @param scale  the rendering scale
     */
    private void drawCastlePolygons(Graphics2D g, double left, double bottom, double scale) {
        // Start fill style is 2,9 (masonry) according to BALLER.DAT
        Color fill = colorFor(new FillStyle(CastleFileFormat.FILL_STYLE_PATTERN, CastleFileFormat.FILL_INDEX_MASONRY));
        g.setStroke(new BasicStroke(1.0f));
        for (DrawingCommand command : castle.drawing()) {
            if (command instanceof FillStyle style) {
                fill = colorFor(style);
            } else if (command instanceof Polygon polygon) {
                drawPolygon(g, polygon, fill, left, bottom, scale);
            }
        }
    }

    /**
     * Draws the polygon.
     *
     * @param g       graphics context used to draw the polygon
     * @param polygon castle polygon to render
     * @param fill    color used to fill the polygon
     * @param left    the castle origin x-coordinate
     * @param bottom  the castle baseline y-coordinate
     * @param scale   the rendering scale
     */
    private void drawPolygon(Graphics2D g, Polygon polygon, Color fill, double left, double bottom, double scale) {
        Path2D path = new Path2D.Double();
        Point first = polygon.points().getFirst();
        path.moveTo(left + first.x() * scale, bottom - first.y() * scale);
        for (int i = 1; i < polygon.points().size(); i++) {
            Point point = polygon.points().get(i);
            path.lineTo(left + point.x() * scale, bottom - point.y() * scale);
        }
        path.closePath();
        g.setColor(fill);
        g.fill(path);
        g.setColor(COLOR_OUTLINE);
        g.draw(path);
        if (polygon == selectedPolygon) {
            drawSelectedPolygonPoints(g, polygon, left, bottom, scale);
        }
    }

    /**
     * Draws the selected polygon points.
     *
     * @param g       graphics context used to draw the vertex markers
     * @param polygon selected polygon whose vertices are marked
     * @param left    the castle origin x-coordinate
     * @param bottom  the castle baseline y-coordinate
     * @param scale   the rendering scale
     */
    private void drawSelectedPolygonPoints(Graphics2D g, Polygon polygon, double left, double bottom, double scale) {
        for (int i = 0; i < polygon.points().size(); i++) {
            Point point = polygon.points().get(i);
            double screenX = left + point.x() * scale;
            double screenY = bottom - point.y() * scale;
            if (i == selectedPointIndex) {
                g.setColor(COLOR_SELECTED);
                g.fill(new Rectangle2D.Double(screenX - 4, screenY - 4, 8, 8));
            } else {
                g.setColor(COLOR_HIGHLIGHTED);
                g.fill(new Rectangle2D.Double(screenX - 3, screenY - 3, 6, 6));
            }
        }
    }

    /**
     * Draws the castle objects.
     *
     * @param g      graphics context used to draw the castle objects
     * @param left   the castle origin x-coordinate
     * @param bottom the castle baseline y-coordinate
     * @param scale  the rendering scale
     */
    private void drawCastleObjects(Graphics2D g, double left, double bottom, double scale) {
        drawThrone(g, left, bottom, scale);
        drawResourceAreas(g, left, bottom, scale);
        drawCannons(g, left, bottom, scale);
        drawResourceContents(g, left, bottom, scale);
        drawWindVane(g, left, bottom, scale);
    }

    /**
     * Draws the throne.
     *
     * @param g      graphics context used to draw the throne
     * @param left   the castle origin x-coordinate
     * @param bottom the castle baseline y-coordinate
     * @param scale  the rendering scale
     */
    private void drawThrone(Graphics2D g, double left, double bottom, double scale) {
        if (!isPositioned(castle.throne())) {
            return;
        }
        drawArea(g, castle.throne(),
                new Size(CastleFileFormat.THRONE_WIDTH, CastleFileFormat.THRONE_HEIGHT), left, bottom, scale);
        if (selectedThrone) {
            Rectangle2D throneRect = new Rectangle2D.Double(left + castle.throne().x() * scale,
                    bottom - (castle.throne().y() + CastleFileFormat.THRONE_HEIGHT) * scale,
                    CastleFileFormat.THRONE_WIDTH * scale, CastleFileFormat.THRONE_HEIGHT * scale);
            g.setColor(COLOR_SELECTED);
            g.setStroke(new BasicStroke(2.0f));
            g.draw(throneRect);
            g.setStroke(new BasicStroke(1.0f));
        }
        drawSprite(g, Sprites.KING, castle.throne(), left, bottom, scale);
    }

    /**
     * Draws the resource areas.
     *
     * @param g      graphics context used to draw storage areas
     * @param left   the castle origin x-coordinate
     * @param bottom the castle baseline y-coordinate
     * @param scale  the rendering scale
     */
    private void drawResourceAreas(Graphics2D g, double left, double bottom, double scale) {
        drawArea(g, castle.money(), castle.moneyRoom(), left, bottom, scale);
        if (selectedResourceArea == ResourceArea.MONEY) {
            drawCornerPoints(g, castle.money(), castle.moneyRoom(), left, bottom, scale);
        }
        drawArea(g, castle.powder(), castle.powderRoom(), left, bottom, scale);
        if (selectedResourceArea == ResourceArea.POWDER) {
            drawCornerPoints(g, castle.powder(), castle.powderRoom(), left, bottom, scale);
        }
        drawArea(g, castle.balls(), castle.ballRoom(), left, bottom, scale);
        if (selectedResourceArea == ResourceArea.BALLS) {
            drawCornerPoints(g, castle.balls(), castle.ballRoom(), left, bottom, scale);
        }
    }

    /**
     * Draws the cannons.
     *
     * @param g      graphics context used to draw cannons
     * @param left   the castle origin x-coordinate
     * @param bottom the castle baseline y-coordinate
     * @param scale  the rendering scale
     */
    private void drawCannons(Graphics2D g, double left, double bottom, double scale) {
        for (int i = 0; i < castle.cannons().size(); i++) {
            Point cannon = castle.cannons().get(i);
            drawSprite(g, Sprites.CANNON, cannon, left, bottom, scale);
            if (i == selectedCannonIndex) {
                g.setColor(COLOR_SELECTED);
                g.setStroke(new BasicStroke(2.0f));
                g.draw(calculateCannonRectangle(cannon, left, bottom, scale));
                g.setStroke(new BasicStroke(1.0f));
            }
        }
    }

    /**
     * Draws the resource contents.
     *
     * @param g      graphics context used to draw stored resource sprites
     * @param left   the castle origin x-coordinate
     * @param bottom the castle baseline y-coordinate
     * @param scale  the rendering scale
     */
    private void drawResourceContents(Graphics2D g, double left, double bottom, double scale) {
        drawResources(g, new ResourceDisplay(Sprites.MONEY, castle.money(), castle.moneyRoom(),
                CastleFileFormat.MONEY_WIDTH, CastleFileFormat.MONEY_HEIGHT,
                divideRoundedUp(castle.resources().money(), CastleFileFormat.MONEY_PER_UNIT)), left, bottom, scale);
        drawResources(g, new ResourceDisplay(Sprites.POWDER, castle.powder(), castle.powderRoom(),
                CastleFileFormat.POWDER_WIDTH, CastleFileFormat.POWDER_HEIGHT,
                divideRoundedUp(castle.resources().powder(), CastleFileFormat.POWDER_PER_UNIT)), left, bottom, scale);
        drawResources(g, new ResourceDisplay(Sprites.BALL, castle.balls(), castle.ballRoom(),
                CastleFileFormat.CANNONBALL_WIDTH, CastleFileFormat.CANNONBALL_HEIGHT,
                castle.resources().balls()), left, bottom, scale);
    }

    /**
     * Draws the wind vane.
     *
     * @param g      graphics context used to draw the wind vane
     * @param left   the castle origin x-coordinate
     * @param bottom the castle baseline y-coordinate
     * @param scale  the rendering scale
     */
    private void drawWindVane(Graphics2D g, double left, double bottom, double scale) {
        if (!isPositioned(castle.windVane())) {
            return;
        }
        drawSprite(g, Sprites.WIND_VANE, castle.windVane(), left, bottom, scale);
        if (selectedWindVane) {
            Rectangle2D windVaneRect = calculateWindVaneRectangle(castle.windVane(), left, bottom, scale);
            g.setColor(COLOR_SELECTED);
            g.setStroke(new BasicStroke(2.0f));
            g.draw(windVaneRect);
            g.setStroke(new BasicStroke(1.0f));
        }
    }

    /**
     * Draws the edge hover.
     *
     * @param g      graphics context used to draw the edge-hover marker
     * @param left   the castle origin x-coordinate
     * @param bottom the castle baseline y-coordinate
     * @param scale  the rendering scale
     */
    private void drawEdgeHover(Graphics2D g, double left, double bottom, double scale) {
        if (selectedPolygon != null && edgeHoverPosition != null) {
            Point2D hoverPoint = findClosestPointOnEdge(selectedPolygon, edgeHoverPosition, left, bottom, scale);
            if (hoverPoint != null) {
                g.setColor(COLOR_SELECTED);
                g.fill(new Rectangle2D.Double(hoverPoint.getX() - 4, hoverPoint.getY() - 4, 8, 8));
            }
        }
    }

    /**
     * Returns the upper y-coordinate of the positioned area.
     *
     * @param position lower-left castle coordinate of the area
     * @param size     width and height of the area
     * @return the y-coordinate of the top edge of the positioned area
     */
    private int areaTop(Point position, Size size) {
        return position.y() + size.height();
    }

    /**
     * Draws the area.
     *
     * @param g        graphics context used to draw the storage area
     * @param position lower-left castle coordinate of the area
     * @param size     width and height of the area
     * @param left     the castle origin x-coordinate
     * @param bottom   the castle baseline y-coordinate
     * @param scale    the rendering scale
     */
    private void drawArea(Graphics2D g, Point position, Size size,
                          double left, double bottom, double scale) {
        Rectangle2D area = new Rectangle2D.Double(left + position.x() * scale,
                bottom - (position.y() + size.height()) * scale,
                size.width() * scale, size.height() * scale);
        g.setColor(Color.WHITE);
        g.fill(area);
    }

    /**
     * Draws the sprite.
     *
     * @param g        graphics context used to draw the sprite
     * @param sprite   encoded vector data for the sprite
     * @param position lower-left castle coordinate where the sprite is placed
     * @param left     the castle origin x-coordinate
     * @param bottom   the castle baseline y-coordinate
     * @param scale    the rendering scale
     */
    private void drawSprite(Graphics2D g, int[] sprite, Point position,
                            double left, double bottom, double scale) {
        Sprites.draw(g, sprite, left + position.x() * scale,
                bottom - position.y() * scale, scale);
    }

    /**
     * Draws the resources.
     *
     * @param g       graphics context used to draw the slots
     * @param display sprite, slot spacing, area, and occupied count to render
     * @param left    the castle origin x-coordinate
     * @param bottom  the castle baseline y-coordinate
     * @param scale   the rendering scale
     */
    private void drawResources(Graphics2D g, ResourceDisplay display, double left, double bottom, double scale) {
        int remaining = Math.max(0, display.count());
        for (int y = 0; remaining > 0 && y < display.room().height(); y += display.stepY()) {
            for (int x = 0; remaining > 0 && x < display.room().width(); x += display.stepX(), remaining--) {
                drawSprite(g, display.sprite(),
                        new Point(display.position().x() + x, display.position().y() + y),
                        left, bottom, scale);
            }
        }
        // Draw semi-transparent placeholders for empty slots
        int slotIndex = 0;
        for (int y = 0; y < display.room().height(); y += display.stepY()) {
            for (int x = 0; x < display.room().width(); x += display.stepX()) {
                if (slotIndex >= display.count()) {
                    Sprites.draw(g, display.sprite(),
                            left + (display.position().x() + x) * scale,
                            bottom - (display.position().y() + y) * scale, scale, 0.15f);
                }
                slotIndex++;
            }
        }
    }

    /**
     * Describes the sprite and storage geometry used to render resource slots.
     *
     * @param sprite   sprite data to draw in each slot
     * @param position lower-left storage-area position
     * @param room     storage-area dimensions
     * @param stepX    horizontal spacing between slots
     * @param stepY    vertical spacing between slots
     * @param count    number of occupied slots to draw
     */
    private record ResourceDisplay(int[] sprite, Point position, Size room, int stepX, int stepY, int count) {
        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ResourceDisplay(
                    int[] sprite1, Point position1, Size room1, int x, int y, int count1
            ))) {
                return false;
            }
            return Arrays.equals(sprite, sprite1)
                    && Objects.equals(position, position1)
                    && Objects.equals(room, room1)
                    && stepX == x
                    && stepY == y
                    && count == count1;
        }

        @Override
        public int hashCode() {
            return Objects.hash(Arrays.hashCode(sprite), position, room, stepX, stepY, count);
        }

        @Override
        public String toString() {
            return "ResourceDisplay[sprite=" + Arrays.toString(sprite)
                    + ", position=" + position
                    + ", room=" + room
                    + ", stepX=" + stepX
                    + ", stepY=" + stepY
                    + ", count=" + count + "]";
        }
    }

    /**
     * Returns zero for non-positive values; otherwise rounds the quotient up to the next integer.
     *
     * @param value   starting resource quantity
     * @param divisor resource quantity represented by one occupied slot
     * @return zero for non-positive values, otherwise the quotient rounded upward
     */
    private int divideRoundedUp(int value, int divisor) {
        return value <= 0 ? 0 : (value + divisor - 1) / divisor;
    }

    /**
     * Chooses the display color corresponding to a drawing fill style.
     *
     * @param style the fill style value
     * @return the display color mapped from the fill style
     */
    private Color colorFor(FillStyle style) {
        if (style.style() == CastleFileFormat.FILL_STYLE_PATTERN && style.index() == CastleFileFormat.FILL_INDEX_ROOF) {
            return COLOR_FILL_ROOF;
        }
        if (style.style() == CastleFileFormat.FILL_STYLE_PATTERN && style.index() == CastleFileFormat.FILL_INDEX_MASONRY) {
            return COLOR_FILL_MASONRY;
        }
        return COLOR_FILL_DEFAULT;
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
     * Finds the highest y-coordinate reached by a cannon in the castle.
     *
     * @param castle castle whose cannon positions are inspected
     * @return the maximum cannon top coordinate, or 1 when the castle has no cannons
     */
    static int calculateMaximumCannonY(Castle castle) {
        int maximumY = 1;
        for (Point cannon : castle.cannons()) {
            maximumY = Math.max(maximumY, cannon.y() + CastleFileFormat.CANNON_HEIGHT);
        }
        return maximumY;
    }

    /**
     * Draws the corner points.
     *
     * @param g        graphics context used to draw the resize handles
     * @param position lower-left castle coordinate of the selected storage area
     * @param size     width and height of the selected storage area
     * @param left     the castle origin x-coordinate
     * @param bottom   the castle baseline y-coordinate
     * @param scale    the rendering scale
     */
    private void drawCornerPoints(Graphics2D g, Point position, Size size, double left, double bottom, double scale) {
        double x = left + position.x() * scale;
        double y = bottom - (position.y() + size.height()) * scale;
        double width = size.width() * scale;
        double height = size.height() * scale;

        // Bottom-left corner (index 0)
        if (draggingResourceCorner == 0) {
            g.setColor(COLOR_SELECTED);
            g.fill(new Rectangle2D.Double(x - 4, (y + height) - 4, 8, 8));
        } else {
            g.setColor(COLOR_HIGHLIGHTED);
            g.fill(new Rectangle2D.Double(x - 3, (y + height) - 3, 6, 6));
        }

        // Bottom-right corner (index 1)
        if (draggingResourceCorner == 1) {
            g.setColor(COLOR_SELECTED);
            g.fill(new Rectangle2D.Double((x + width) - 4, (y + height) - 4, 8, 8));
        } else {
            g.setColor(COLOR_HIGHLIGHTED);
            g.fill(new Rectangle2D.Double((x + width) - 3, (y + height) - 3, 6, 6));
        }

        // Top-left corner (index 2)
        if (draggingResourceCorner == 2) {
            g.setColor(COLOR_SELECTED);
            g.fill(new Rectangle2D.Double(x - 4, y - 4, 8, 8));
        } else {
            g.setColor(COLOR_HIGHLIGHTED);
            g.fill(new Rectangle2D.Double(x - 3, y - 3, 6, 6));
        }

        // Top-right corner (index 3)
        if (draggingResourceCorner == 3) {
            g.setColor(COLOR_SELECTED);
            g.fill(new Rectangle2D.Double((x + width) - 4, y - 4, 8, 8));
        } else {
            g.setColor(COLOR_HIGHLIGHTED);
            g.fill(new Rectangle2D.Double((x + width) - 3, y - 3, 6, 6));
        }
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
     * Draws the polygon creation.
     *
     * @param g graphics context used to draw the in-progress polygon
     */
    private void drawPolygonCreation(Graphics2D g) {
        if (creationPoints.isEmpty() && mousePosition == null) {
            return;
        }

        // Calculate scale and position
        int[] bounds = calculateBounds();
        int minimumX = bounds[0];
        int baseMaximumY = calculateMaximumY();

        double scale = BASE_SCALE * zoomFactor;
        if (scale <= 0) {
            return;
        }
        double[] origin = calculateRenderingOrigin(minimumX, baseMaximumY, scale,
                renderingOffsetX == 0 && renderingOffsetY == 0);
        double left = origin[0];
        double bottom = origin[1];

        // Set color based on polygon type
        Color fillColor = "wall".equals(polygonType) ? COLOR_FILL_MASONRY : COLOR_FILL_ROOF;

        // Draw polygon preview with mouse position as last point
        if (creationPoints.size() >= 2 && mousePosition != null) {
            Path2D path = createPolygonPreviewPath(left, bottom, scale);
            g.setColor(fillColor);
            g.fill(path);
            g.setColor(COLOR_OUTLINE);
            g.setStroke(new BasicStroke(1.0f));
            g.draw(path);
        } else if (creationPoints.size() >= 2) {
            // Draw lines between points without mouse position
            Path2D path = createPolygonPath(left, bottom, scale);
            g.setColor(fillColor);
            g.setStroke(new BasicStroke(1.0f));
            g.draw(path);
        }

        // Draw points
        for (Point point : creationPoints) {
            double screenX = left + point.x() * scale;
            double screenY = bottom - point.y() * scale;
            g.setColor(COLOR_SELECTED);
            g.fill(new Rectangle2D.Double(screenX - 4, screenY - 4, 8, 8));
        }

        // Draw yellow square at mouse position
        if (mousePosition != null) {
            g.setColor(Color.YELLOW);
            g.fill(new Rectangle2D.Double(mousePosition.getX() - 4, mousePosition.getY() - 4, 8, 8));
        }
    }

    /**
     * Creates the polygon preview path.
     *
     * @param left   the castle origin x-coordinate
     * @param bottom the castle baseline y-coordinate
     * @param scale  the rendering scale
     * @return the created polygon preview path
     */
    private Path2D createPolygonPreviewPath(double left, double bottom, double scale) {
        Path2D path = createPolygonPath(left, bottom, scale);
        path.lineTo(mousePosition.getX(), mousePosition.getY());
        path.closePath();
        return path;
    }

    /**
     * Creates the polygon path.
     *
     * @param left   the castle origin x-coordinate
     * @param bottom the castle baseline y-coordinate
     * @param scale  the rendering scale
     * @return the created polygon path
     */
    private Path2D createPolygonPath(double left, double bottom, double scale) {
        Path2D path = new Path2D.Double();
        Point first = creationPoints.getFirst();
        path.moveTo(left + first.x() * scale, bottom - first.y() * scale);
        for (int i = 1; i < creationPoints.size(); i++) {
            Point point = creationPoints.get(i);
            path.lineTo(left + point.x() * scale, bottom - point.y() * scale);
        }
        return path;
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
