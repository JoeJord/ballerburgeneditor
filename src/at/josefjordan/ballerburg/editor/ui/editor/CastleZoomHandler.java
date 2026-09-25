package at.josefjordan.ballerburg.editor.ui.editor;

import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;

/**
 * Handles mouse wheel zoom for the castle view.
 */
final class CastleZoomHandler implements MouseWheelListener {
    /**
     * The multiplicative zoom step.
     */
    private static final double ZOOM_STEP = 0.1;
    /**
     * Castle view whose zoom and scrolling this handler controls.
     */
    private final CastleViewPanel panel;

    /**
     * Attaches the handler to the castle view.
     *
     * @param panel view that receives zoom and scroll input
     */
    CastleZoomHandler(CastleViewPanel panel) {
        this.panel = panel;
    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        if (e.isControlDown()) {
            panel.adjustZoom(calculateZoomMultiplier(e), e.getPoint());
        } else {
            panel.scrollByWheel(e, e.isShiftDown());
        }
        e.consume();
    }

    /**
     * Calculates the zoom multiplier for the wheel rotation.
     *
     * @param e wheel event whose rotation determines the zoom direction
     * @return the calculated zoom multiplier
     */
    private double calculateZoomMultiplier(MouseWheelEvent e) {
        double zoomMultiplier = 1.0 + ZOOM_STEP;
        return e.getWheelRotation() < 0 ? zoomMultiplier : 1.0 / zoomMultiplier;
    }
}
