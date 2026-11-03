package at.josefjordan.ballerburg.editor.ui.editor.castle;

import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.Timer;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Point2D;

/**
 * Scrolls a castle view while a dragged pointer remains near its viewport edges.
 */
final class CastleDragAutoScroller {
    /**
     * Delay between successive automatic scroll steps, in milliseconds.
     */
    private static final int SCROLL_TIMER_DELAY_MILLIS = 50;
    /**
     * Distance from a viewport edge that activates automatic scrolling, in pixels.
     */
    private static final int EDGE_MARGIN_PIXELS = 50;
    /**
     * Viewport displacement applied on each automatic scroll step, in pixels.
     */
    private static final int SCROLL_STEP_PIXELS = 10;

    /**
     * Castle view whose containing viewport is scrolled.
     */
    private final CastleViewPanel panel;
    /**
     * Callback that adjusts the object being dragged after a scroll step.
     */
    private final ScrollListener scrollListener;
    /**
     * Timer that continues scrolling while the pointer remains near a viewport edge.
     */
    private final Timer scrollTimer;
    /**
     * Most recently observed drag pointer position in panel coordinates.
     */
    private Point2D lastDragPoint;

    /**
     * Creates an auto-scroller for the supplied view and drag-adjustment callback.
     *
     * @param panel          view whose containing scroll pane is controlled
     * @param scrollListener callback notified after each timed scroll step
     */
    CastleDragAutoScroller(CastleViewPanel panel, ScrollListener scrollListener) {
        this.panel = panel;
        this.scrollListener = scrollListener;
        scrollTimer = new Timer(SCROLL_TIMER_DELAY_MILLIS, event -> {
            if (event.getSource() instanceof Timer timer && timer.isRunning() && lastDragPoint != null) {
                scrollAtEdge();
            }
        });
    }

    /**
     * Records the pointer position used by subsequent edge-scroll timer ticks.
     *
     * @param dragPoint current pointer position in panel coordinates
     */
    void setDragPoint(Point2D dragPoint) {
        lastDragPoint = dragPoint;
    }

    /**
     * Scrolls immediately when the pointer is close to an edge and starts or stops continuous scrolling.
     *
     * @param dragPoint current pointer position in panel coordinates
     */
    void keepPointerVisible(Point2D dragPoint) {
        JViewport viewport = getScrollViewport();
        if (viewport == null) {
            return;
        }

        Rectangle viewRect = viewport.getViewRect();
        int mouseX = (int) dragPoint.getX();
        int mouseY = (int) dragPoint.getY();
        int viewportX = mouseX - viewRect.x;
        int viewportY = mouseY - viewRect.y;
        int scrollX = viewRect.x;
        int scrollY = viewRect.y;
        boolean needsScroll = false;

        if (viewportX < EDGE_MARGIN_PIXELS) {
            scrollX = Math.max(0, mouseX - EDGE_MARGIN_PIXELS);
            needsScroll = true;
        } else if (viewportX > viewRect.width - EDGE_MARGIN_PIXELS) {
            int maxScrollX = Math.max(0, panel.getPreferredSize().width - viewRect.width);
            scrollX = Math.clamp((long) mouseX - viewRect.width + EDGE_MARGIN_PIXELS, 0, maxScrollX);
            needsScroll = true;
        }

        if (viewportY < EDGE_MARGIN_PIXELS) {
            scrollY = Math.max(0, mouseY - EDGE_MARGIN_PIXELS);
            needsScroll = true;
        } else if (viewportY > viewRect.height - EDGE_MARGIN_PIXELS) {
            int maxScrollY = Math.max(0, panel.getPreferredSize().height - viewRect.height);
            scrollY = Math.clamp((long) mouseY - viewRect.height + EDGE_MARGIN_PIXELS, 0, maxScrollY);
            needsScroll = true;
        }

        if (needsScroll) {
            viewport.setViewPosition(new Point(scrollX, scrollY));
            if (!scrollTimer.isRunning()) {
                scrollTimer.start();
            }
        } else {
            scrollTimer.stop();
        }
    }

    /**
     * Stops continuous scrolling and forgets the current drag pointer.
     */
    void stop() {
        lastDragPoint = null;
        scrollTimer.stop();
    }

    /**
     * Scrolls one step toward each nearby edge and notifies the drag callback of the effective displacement.
     */
    private void scrollAtEdge() {
        JViewport viewport = getScrollViewport();
        if (viewport == null) {
            return;
        }

        Rectangle viewRect = viewport.getViewRect();
        int mouseX = (int) lastDragPoint.getX();
        int mouseY = (int) lastDragPoint.getY();
        int viewportX = mouseX - viewRect.x;
        int viewportY = mouseY - viewRect.y;
        boolean nearLeft = viewportX < EDGE_MARGIN_PIXELS;
        boolean nearRight = viewportX > viewRect.width - EDGE_MARGIN_PIXELS;
        boolean nearTop = viewportY < EDGE_MARGIN_PIXELS;
        boolean nearBottom = viewportY > viewRect.height - EDGE_MARGIN_PIXELS;
        if (!(nearLeft || nearRight || nearTop || nearBottom)) {
            scrollTimer.stop();
            return;
        }

        int scrollX = calculateEdgeScroll(viewRect.x,
                panel.getPreferredSize().width - viewRect.width, nearLeft, nearRight);
        int scrollY = calculateEdgeScroll(viewRect.y,
                panel.getPreferredSize().height - viewRect.height, nearTop, nearBottom);
        viewport.setViewPosition(new Point(scrollX, scrollY));

        int deltaX = effectiveScrollDelta(scrollX - viewRect.x, nearLeft, nearRight);
        int deltaY = effectiveScrollDelta(scrollY - viewRect.y, nearTop, nearBottom);
        scrollListener.scrolled(lastDragPoint, deltaX, deltaY);
    }

    /**
     * Finds the scroll viewport containing the castle view.
     *
     * @return the containing viewport, or {@code null} if the view is not inside a scroll pane
     */
    private JViewport getScrollViewport() {
        if (panel.getParent() instanceof JViewport viewport
                && viewport.getParent() instanceof JScrollPane) {
            return viewport;
        }
        return null;
    }

    /**
     * Computes the next bounded viewport position along one axis.
     *
     * @param currentPosition current viewport position along the axis
     * @param contentOverflow maximum scroll position along the axis
     * @param nearStart       whether the pointer is near the left or top edge
     * @param nearEnd         whether the pointer is near the right or bottom edge
     * @return next viewport position, advanced by one scroll step toward a nearby edge
     */
    private int calculateEdgeScroll(int currentPosition, int contentOverflow, boolean nearStart, boolean nearEnd) {
        if (nearStart) {
            return Math.max(0, currentPosition - SCROLL_STEP_PIXELS);
        }
        if (nearEnd) {
            int maxScroll = Math.max(0, contentOverflow);
            return Math.clamp((long) currentPosition + SCROLL_STEP_PIXELS, 0, maxScroll);
        }
        return currentPosition;
    }

    /**
     * Uses actual viewport movement, or a one-step fallback when scrolling is constrained at an edge.
     *
     * @param actualDelta actual viewport displacement along the axis
     * @param nearStart   whether the pointer is near the left or top edge
     * @param nearEnd     whether the pointer is near the right or bottom edge
     * @return actual displacement when nonzero, otherwise the fallback displacement toward the nearby edge
     */
    private int effectiveScrollDelta(int actualDelta, boolean nearStart, boolean nearEnd) {
        if (actualDelta != 0) {
            return actualDelta;
        }
        if (nearStart) {
            return -SCROLL_STEP_PIXELS;
        }
        return nearEnd ? SCROLL_STEP_PIXELS : 0;
    }

    /**
     * Callback for adapting the active drag to automatic viewport movement.
     */
    @FunctionalInterface
    interface ScrollListener {
        /**
         * Adjusts the active drag after the viewport moves.
         *
         * @param dragPoint current pointer position in panel coordinates
         * @param deltaX    horizontal viewport displacement in pixels
         * @param deltaY    vertical viewport displacement in pixels
         */
        void scrolled(Point2D dragPoint, int deltaX, int deltaY);
    }
}
