package at.josefjordan.ballerburg.editor.ui;

import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;

/** Handles mouse wheel zoom for the castle view. */
final class CastleZoomHandler implements MouseWheelListener {
    private final CastleViewPanel panel;

    CastleZoomHandler(CastleViewPanel panel) {
        this.panel = panel;
    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        if (e.isControlDown()) {
            panel.adjustZoom(calculateZoomMultiplier(e));
            e.consume();
        }
    }

    private double calculateZoomMultiplier(MouseWheelEvent e) {
        return e.getWheelRotation() < 0 ? 1.1 : 0.9;
    }
}
