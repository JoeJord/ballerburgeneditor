package at.josefjordan.ballerburg.editor.ui.editor.castle;

import at.josefjordan.ballerburg.editor.model.CastleDocument.Castle;
import at.josefjordan.ballerburg.editor.model.CastleDocument.Point;

import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.util.List;

/**
 * Handles pointer and keyboard input while a polygon is being created.
 */
final class CastlePolygonCreationHandler {
    /**
     * Castle view receiving polygon-creation input and storing preview vertices.
     */
    private final CastleViewPanel panel;

    /**
     * Creates a handler for polygon-creation gestures in the specified view.
     *
     * @param panel view whose polygon-creation mode is handled
     */
    CastlePolygonCreationHandler(CastleViewPanel panel) {
        this.panel = panel;
    }

    /**
     * Processes polygon-creation shortcuts.
     *
     * @param event key event received by the castle view
     */
    void handleKeyPressed(KeyEvent event) {
        if (!panel.isInPolygonCreationMode()) {
            return;
        }
        if (event.getKeyCode() == KeyEvent.VK_ESCAPE) {
            panel.getEditorPanel().cancelPolygonCreation();
        } else if (event.getKeyCode() == KeyEvent.VK_ENTER) {
            finishOrCancelCreation();
        }
    }

    /**
     * Adds a vertex on left click or completes polygon creation on right click.
     *
     * @param event mouse click received while polygon creation is active
     */
    void handleMousePressed(MouseEvent event) {
        if (event.getButton() == MouseEvent.BUTTON1) {
            Castle castle = panel.getCastle();
            if (castle == null) {
                return;
            }
            double[] parameters = panel.getRenderingParameters();
            double scale = parameters[2];
            if (scale <= 0) {
                return;
            }
            panel.addCreationPoint(toCastlePoint(event.getPoint(), parameters[0], parameters[1], scale));
        } else if (event.getButton() == MouseEvent.BUTTON3) {
            finishOrCancelCreation();
        }
    }

    /**
     * Completes polygon creation if enough vertices exist; otherwise cancels it.
     */
    private void finishOrCancelCreation() {
        List<Point> points = panel.getCreationPoints();
        if (points.size() >= 3) {
            panel.getEditorPanel().finishPolygonCreation(points);
        } else {
            panel.getEditorPanel().cancelPolygonCreation();
        }
    }

    /**
     * Converts a screen-space location to the nearest integer castle coordinate.
     *
     * @param point  location in panel coordinates
     * @param left   castle origin's screen x-coordinate
     * @param bottom castle baseline's screen y-coordinate
     * @param scale  screen pixels per castle coordinate
     * @return converted location in castle coordinates
     */
    private Point toCastlePoint(Point2D point, double left, double bottom, double scale) {
        int x = (int) Math.round((point.getX() - left) / scale);
        int y = (int) Math.round((bottom - point.getY()) / scale);
        return new Point(x, y);
    }
}
