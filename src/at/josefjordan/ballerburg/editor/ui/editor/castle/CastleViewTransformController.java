package at.josefjordan.ballerburg.editor.ui.editor.castle;

import at.josefjordan.ballerburg.editor.model.CastleDocument.Castle;

import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.MouseWheelEvent;

/**
 * Manages zoom, viewport scrolling, and coordinate transforms for a castle view.
 */
final class CastleViewTransformController {
    /**
     * Lowest zoom factor supported by the castle view.
     */
    static final double MIN_ZOOM_FACTOR = 0.10;
    /**
     * Highest zoom factor supported by the castle view.
     */
    static final double MAX_ZOOM_FACTOR = 10.0;
    /**
     * Additional multiplier applied to wheel-based scrolling.
     */
    private static final int WHEEL_SCROLL_MULTIPLIER = 4;
    /**
     * Castle-coordinate-to-screen scale at 100% zoom.
     */
    private static final double BASE_SCALE = 4.0;
    /**
     * Screen-space padding around castle contents.
     */
    private static final double MARGIN = 24.0;

    /**
     * View whose dimensions and repainting are coordinated by this controller.
     */
    private final CastleViewPanel panel;
    /**
     * Calculates castle-coordinate bounds for the currently displayed castle.
     */
    private final CastleBoundsCalculator boundsCalculator;
    /**
     * Current zoom factor.
     */
    private double zoomFactor = 1.0;
    /**
     * Horizontal correction used to keep the zoom anchor fixed when scrolling is constrained.
     */
    private double renderingOffsetX;
    /**
     * Vertical correction used to keep the zoom anchor fixed when scrolling is constrained.
     */
    private double renderingOffsetY;
    /**
     * Listener notified after the zoom factor changes or is reset.
     */
    private Runnable zoomListener;

    /**
     * Creates a transform controller for the specified castle view.
     *
     * @param panel            view whose viewport and dimensions are managed
     * @param boundsCalculator calculator used to determine castle content bounds
     */
    CastleViewTransformController(CastleViewPanel panel, CastleBoundsCalculator boundsCalculator) {
        this.panel = panel;
        this.boundsCalculator = boundsCalculator;
    }

    /**
     * Returns the current zoom factor.
     *
     * @return current zoom factor
     */
    double zoomFactor() {
        return zoomFactor;
    }

    /**
     * Registers a listener for zoom changes.
     *
     * @param zoomListener callback to invoke when zoom changes
     */
    void setZoomListener(Runnable zoomListener) {
        this.zoomListener = zoomListener;
    }

    /**
     * Resets zoom and cursor-anchor corrections to their initial state.
     */
    void reset() {
        zoomFactor = 1.0;
        renderingOffsetX = 0;
        renderingOffsetY = 0;
        notifyZoomChanged();
    }

    /**
     * Clamps and applies the requested zoom factor.
     *
     * @param zoomFactor requested zoom factor
     */
    void setZoomFactor(double zoomFactor) {
        double boundedZoomFactor = Math.clamp(zoomFactor, MIN_ZOOM_FACTOR, MAX_ZOOM_FACTOR);
        if (this.zoomFactor != boundedZoomFactor) {
            adjustZoom(boundedZoomFactor / this.zoomFactor);
        }
    }

    /**
     * Applies a relative zoom change without a cursor anchor.
     *
     * @param multiplier factor by which to change the current zoom
     */
    void adjustZoom(double multiplier) {
        adjustZoom(multiplier, null);
    }

    /**
     * Applies a relative zoom change, preserving the castle coordinate under the cursor when possible.
     *
     * @param multiplier factor by which to change the current zoom
     * @param mousePoint cursor position in panel coordinates, or {@code null} for centered zoom
     */
    void adjustZoom(double multiplier, Point mousePoint) {
        JViewport viewport = getViewport();
        Point oldViewPosition = viewport == null ? null : viewport.getViewPosition();
        double[] oldRenderingParameters = mousePoint == null ? null : renderingParameters(panel.getCastle());
        Point mouseViewportPoint = viewport == null || mousePoint == null ? null : new Point(
                mousePoint.x - oldViewPosition.x,
                mousePoint.y - oldViewPosition.y);

        double worldX = mousePoint == null ? 0 : (mousePoint.x - oldRenderingParameters[0]) / oldRenderingParameters[2];
        double worldY = mousePoint == null ? 0 : (oldRenderingParameters[1] - mousePoint.y) / oldRenderingParameters[2];
        zoomFactor *= multiplier;
        zoomFactor = Math.clamp(zoomFactor, MIN_ZOOM_FACTOR, MAX_ZOOM_FACTOR);

        renderingOffsetX = 0;
        renderingOffsetY = 0;
        updatePreferredSize(panel.getCastle());
        panel.setSize(panel.getPreferredSize());

        if (mousePoint != null && viewport != null) {
            double[] newRenderingParameters = renderingParameters(panel.getCastle(), false);
            double newPanelX = newRenderingParameters[0] + worldX * newRenderingParameters[2];
            double newPanelY = newRenderingParameters[1] - worldY * newRenderingParameters[2];
            Point newViewPosition = new Point(
                    (int) Math.round(newPanelX - mouseViewportPoint.x),
                    (int) Math.round(newPanelY - mouseViewportPoint.y));
            if (viewport.getParent() instanceof JScrollPane scrollPane) {
                scrollPane.validate();
            }
            Dimension extent = viewport.getExtentSize();
            Dimension viewSize = panel.getPreferredSize();
            int maxScrollX = Math.max(0, viewSize.width - extent.width);
            int maxScrollY = Math.max(0, viewSize.height - extent.height);
            int scrollX = Math.clamp(newViewPosition.x, 0, maxScrollX);
            int scrollY = Math.clamp(newViewPosition.y, 0, maxScrollY);
            viewport.setViewPosition(new Point(scrollX, scrollY));

            renderingOffsetX = mouseViewportPoint.x + scrollX - newPanelX;
            renderingOffsetY = mouseViewportPoint.y + scrollY - newPanelY;
            updatePreferredSize(panel.getCastle());
            panel.setSize(panel.getPreferredSize());
            viewport.revalidate();
            if (viewport.getParent() != null) {
                viewport.getParent().revalidate();
            }
        }

        notifyZoomChanged();
        panel.repaint();
    }

    /**
     * Scrolls the view by the amount requested by a mouse-wheel event.
     *
     * @param event      wheel event determining the scroll distance
     * @param horizontal whether to scroll horizontally rather than vertically
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
     * Recomputes the panel's preferred size from castle bounds, zoom, and viewport dimensions.
     *
     * @param castle castle whose bounds determine the content size, or {@code null} for an empty view
     */
    void updatePreferredSize(Castle castle) {
        CastleBoundsCalculator.Bounds bounds = boundsCalculator.calculate(castle);
        double scale = BASE_SCALE * zoomFactor;
        int contentWidth = bounds.maximumX() - bounds.minimumX();
        int contentHeight = bounds.maximumY() - bounds.minimumY();
        int preferredWidth = (int) Math.ceil(2 * MARGIN + contentWidth * scale);
        int preferredHeight = (int) Math.ceil(2 * MARGIN + contentHeight * scale);
        JViewport viewport = getViewport();
        if (viewport != null) {
            preferredWidth = adjustPreferredDimension(preferredWidth, viewport.getWidth(), renderingOffsetX);
            preferredHeight = adjustPreferredDimension(preferredHeight, viewport.getHeight(), renderingOffsetY);
        }
        double[] origin = renderingOrigin(bounds, scale, renderingOffsetX == 0 && renderingOffsetY == 0);
        preferredWidth = Math.max(preferredWidth,
                (int) Math.ceil(origin[0] + bounds.maximumX() * scale + MARGIN));
        preferredHeight = Math.max(preferredHeight,
                (int) Math.ceil(origin[1] - bounds.minimumY() * scale + MARGIN));
        panel.setPreferredSize(new Dimension(preferredWidth, preferredHeight));
        panel.revalidate();
        if (panel.getParent() instanceof JViewport parentViewport) {
            parentViewport.revalidate();
        }
    }

    /**
     * Centers the view in its viewport when the content is smaller than the viewport.
     */
    void centerCastle() {
        JViewport viewport = getViewport();
        if (viewport != null && viewport.getParent() instanceof JScrollPane) {
            viewport.revalidate();
            viewport.getParent().validate();
            Dimension panelSize = panel.getPreferredSize();
            Dimension viewportSize = viewport.getSize();
            int scrollX = Math.max(0, (panelSize.width - viewportSize.width) / 2);
            int scrollY = Math.max(0, (panelSize.height - viewportSize.height) / 2);
            viewport.setViewPosition(new Point(scrollX, scrollY));
        }
    }

    /**
     * Returns the current castle-to-screen transform, centering content only when there is no zoom correction.
     *
     * @param castle castle whose coordinates are transformed, or {@code null} for an empty view
     * @return array containing the left origin, bottom origin, and scale
     */
    double[] renderingParameters(Castle castle) {
        return renderingParameters(castle, renderingOffsetX == 0 && renderingOffsetY == 0);
    }

    /**
     * Returns the castle-to-screen transform with explicit centering behavior.
     *
     * @param castle castle whose coordinates are transformed, or {@code null} for an empty view
     * @param center whether to center content within the panel
     * @return array containing the left origin, bottom origin, and scale
     */
    double[] renderingParameters(Castle castle, boolean center) {
        CastleBoundsCalculator.Bounds bounds = boundsCalculator.calculate(castle);
        double scale = BASE_SCALE * zoomFactor;
        double[] origin = renderingOrigin(bounds, scale, center);
        return new double[]{origin[0], origin[1], scale};
    }

    /**
     * Calculates the screen-space origin for the specified bounds and scale.
     *
     * @param bounds castle-coordinate content bounds
     * @param scale  screen pixels per castle-coordinate unit
     * @param center whether to center content within the panel
     * @return array containing the left and bottom origins
     */
    private double[] renderingOrigin(CastleBoundsCalculator.Bounds bounds, double scale, boolean center) {
        int contentWidthInUnits = bounds.maximumX() - bounds.minimumX();
        double contentWidth = 2 * MARGIN + contentWidthInUnits * scale;
        double contentHeight = 2 * MARGIN + (bounds.maximumY() - bounds.minimumY()) * scale;
        double horizontalOffset = center ? Math.max(0, (panel.getWidth() - contentWidth) / 2.0) : 0;
        double verticalOffset = center ? Math.max(0, (panel.getHeight() - contentHeight) / 2.0) : 0;
        return new double[]{
                MARGIN - bounds.minimumX() * scale + horizontalOffset + renderingOffsetX,
                MARGIN + bounds.maximumY() * scale + verticalOffset + renderingOffsetY,
        };
    }

    /**
     * Finds the viewport containing the castle panel.
     *
     * @return containing viewport, or {@code null} when the panel is not in one
     */
    private JViewport getViewport() {
        return panel.getParent() instanceof JViewport viewport ? viewport : null;
    }

    /**
     * Ensures a preferred panel dimension accommodates the viewport and any rendering correction.
     *
     * @param preferred         calculated preferred dimension
     * @param viewportDimension visible viewport dimension
     * @param renderingOffset   screen-space correction along this axis
     * @return adjusted preferred dimension
     */
    private int adjustPreferredDimension(int preferred, int viewportDimension, double renderingOffset) {
        if (preferred > viewportDimension) {
            preferred += (int) Math.ceil(Math.abs(renderingOffset));
        }
        return Math.max(preferred, viewportDimension);
    }

    /**
     * Notifies the registered listener that zoom state changed.
     */
    private void notifyZoomChanged() {
        if (zoomListener != null) {
            zoomListener.run();
        }
    }
}
