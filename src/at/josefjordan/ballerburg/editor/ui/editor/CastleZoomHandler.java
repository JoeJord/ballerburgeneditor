package at.josefjordan.ballerburg.editor.ui.editor;

import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;

/** Handles mouse wheel zoom for the castle view. */
final class CastleZoomHandler implements MouseWheelListener {
    private static final double ZOOM_STEP = 0.1;
    private final CastleViewPanel panel;

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

    private double calculateZoomMultiplier(MouseWheelEvent e) {
        double zoomMultiplier = 1.0 + ZOOM_STEP;
        return e.getWheelRotation() < 0 ? zoomMultiplier : 1.0 / zoomMultiplier;
    }
}
