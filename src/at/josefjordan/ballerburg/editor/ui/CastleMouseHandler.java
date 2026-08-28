package at.josefjordan.ballerburg.editor.ui;

import at.josefjordan.ballerburg.editor.model.BurgDocument.Castle;
import at.josefjordan.ballerburg.editor.model.BurgDocument.DrawingCommand;
import at.josefjordan.ballerburg.editor.model.BurgDocument.Point;
import at.josefjordan.ballerburg.editor.model.BurgDocument.Polygon;
import at.josefjordan.ballerburg.editor.model.BurgDocument.Size;
import at.josefjordan.ballerburg.editor.model.BurgenFileFormat;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;

/** Handles mouse interactions for the castle view (selection, editing, etc.). */
final class CastleMouseHandler extends MouseAdapter {
    private final CastleViewPanel panel;

    CastleMouseHandler(CastleViewPanel panel) {
        this.panel = panel;
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        Castle castle = panel.getCastle();
        if (castle == null) {
            return;
        }

        int maximumY = calculateMaximumY(castle);
        double scale = calculateScale(castle, maximumY);
        if (scale <= 0) {
            return;
        }
        double left = (panel.getWidth() - castle.width() * scale) / 2.0;
        double bottom = (panel.getHeight() + maximumY * scale) / 2.0;

        Point2D clickPoint = e.getPoint();
        Polygon clickedPolygon = findClickedPolygon(castle, left, bottom, scale, clickPoint);

        panel.setSelectedPolygon(clickedPolygon);
    }

    private int calculateMaximumY(Castle castle) {
        int maximumY = 1;
        for (DrawingCommand command : castle.drawing()) {
            if (command instanceof Polygon polygon) {
                for (Point point : polygon.points()) {
                    maximumY = Math.max(maximumY, point.y());
                }
            }
        }
        maximumY = Math.max(maximumY, areaTop(castle.throne(),
                new Size(BurgenFileFormat.THRONE_WIDTH, BurgenFileFormat.THRONE_HEIGHT)));
        for (Point cannon : castle.cannons()) {
            maximumY = Math.max(maximumY, cannon.y() + 12);
        }
        maximumY = Math.max(maximumY, areaTop(castle.money(), castle.moneyRoom()));
        maximumY = Math.max(maximumY, areaTop(castle.powder(), castle.powderRoom()));
        maximumY = Math.max(maximumY, areaTop(castle.balls(), castle.ballRoom()));
        return maximumY;
    }

    private double calculateScale(Castle castle, int maximumY) {
        double margin = 24.0;
        return Math.min((panel.getWidth() - 2.0 * margin) / Math.max(1, castle.width()),
                (panel.getHeight() - 2.0 * margin) / maximumY);
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
}
