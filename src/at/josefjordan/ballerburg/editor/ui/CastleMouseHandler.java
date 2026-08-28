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
import java.awt.event.MouseMotionAdapter;
import java.util.ArrayList;
import java.util.List;

/** Handles mouse interactions for the castle view (selection, editing, etc.). */
final class CastleMouseHandler extends MouseAdapter {
    private final CastleViewPanel panel;
    private int draggingPolygonIndex = -1;
    private int draggingPointIndex = -1;
    private boolean draggingEntirePolygon = false;
    private double dragScale = 0;
    private double dragLeft = 0;
    private double dragBottom = 0;
    private Point2D dragStartPoint;
    private List<Point> originalPoints;

    CastleMouseHandler(CastleViewPanel panel) {
        this.panel = panel;
        panel.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                handleDrag(e);
            }
        });
    }

    @Override
    public void mousePressed(MouseEvent e) {
        Castle castle = panel.getCastle();
        if (castle == null) {
            return;
        }

        int maximumY = calculateMaximumY(castle);
        double baseScale = calculateScale(castle, maximumY);
        double scale = baseScale * panel.getZoomFactor();
        if (scale <= 0) {
            return;
        }
        double left = (panel.getWidth() - castle.width() * scale) / 2.0;
        double bottom = (panel.getHeight() + maximumY * scale) / 2.0;

        Point2D clickPoint = e.getPoint();
        
        // First check if clicking on a point of the selected polygon
        Polygon selectedPolygon = panel.getSelectedPolygon();
        if (selectedPolygon != null) {
            int polygonIndex = findPolygonIndex(castle, selectedPolygon);
            if (polygonIndex >= 0) {
                int pointIndex = findClickedPointIndex(selectedPolygon, left, bottom, scale, clickPoint);
                if (pointIndex >= 0) {
                    draggingPolygonIndex = polygonIndex;
                    draggingPointIndex = pointIndex;
                    draggingEntirePolygon = false;
                    dragScale = baseScale;
                    dragLeft = left;
                    dragBottom = bottom;
                    return;
                }
                // Check if clicking inside the selected polygon (but not on a point)
                Path2D path = createPolygonPath(selectedPolygon, left, bottom, scale);
                if (path.contains(clickPoint)) {
                    draggingPolygonIndex = polygonIndex;
                    draggingPointIndex = -1;
                    draggingEntirePolygon = true;
                    dragScale = baseScale;
                    dragLeft = left;
                    dragBottom = bottom;
                    dragStartPoint = clickPoint;
                    originalPoints = new ArrayList<>(selectedPolygon.points());
                    return;
                }
            }
        }
        
        // If not clicking on a point or inside selected polygon, select the polygon
        Polygon clickedPolygon = findClickedPolygon(castle, left, bottom, scale, clickPoint);
        panel.setSelectedPolygon(clickedPolygon);
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        draggingPolygonIndex = -1;
        draggingPointIndex = -1;
        draggingEntirePolygon = false;
        dragStartPoint = null;
        originalPoints = null;
    }

    private void handleDrag(MouseEvent e) {
        if (draggingPolygonIndex < 0) {
            return;
        }
        
        Castle castle = panel.getCastle();
        if (castle == null) {
            return;
        }

        double currentScale = dragScale * panel.getZoomFactor();

        if (draggingEntirePolygon) {
            // Drag the entire polygon
            Point2D dragPoint = e.getPoint();
            double deltaX = (dragPoint.getX() - dragStartPoint.getX()) / currentScale;
            double deltaY = (dragStartPoint.getY() - dragPoint.getY()) / currentScale;
            
            List<Point> newPoints = new ArrayList<>();
            for (Point originalPoint : originalPoints) {
                int newX = (int) Math.round(originalPoint.x() + deltaX);
                int newY = (int) Math.round(originalPoint.y() + deltaY);
                newPoints.add(new Point(newX, newY));
            }
            
            panel.getEditorPanel().updatePolygonPoints(draggingPolygonIndex, newPoints);
            
            // Update the selected polygon reference to the new polygon
            Castle updatedCastle = panel.getCastle();
            int currentPolygonIndex = 0;
            for (DrawingCommand command : updatedCastle.drawing()) {
                if (command instanceof Polygon polygon) {
                    if (currentPolygonIndex == draggingPolygonIndex) {
                        panel.setSelectedPolygon(polygon);
                        break;
                    }
                    currentPolygonIndex++;
                }
            }
        } else if (draggingPointIndex >= 0) {
            // Drag a single point
            Point2D dragPoint = e.getPoint();
            double currentLeft = (panel.getWidth() - castle.width() * currentScale) / 2.0;
            double currentBottom = (panel.getHeight() + calculateMaximumY(castle) * currentScale) / 2.0;
            int newX = (int) Math.round((dragPoint.getX() - currentLeft) / currentScale);
            int newY = (int) Math.round((currentBottom - dragPoint.getY()) / currentScale);
            
            Point newPoint = new Point(newX, newY);
            panel.getEditorPanel().updatePolygonPoint(draggingPolygonIndex, draggingPointIndex, newPoint);
            
            // Update the selected polygon reference to the new polygon
            Castle updatedCastle = panel.getCastle();
            int currentPolygonIndex = 0;
            for (DrawingCommand command : updatedCastle.drawing()) {
                if (command instanceof Polygon polygon) {
                    if (currentPolygonIndex == draggingPolygonIndex) {
                        panel.setSelectedPolygon(polygon);
                        break;
                    }
                    currentPolygonIndex++;
                }
            }
        }
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

    private int findPolygonIndex(Castle castle, Polygon targetPolygon) {
        int index = 0;
        for (DrawingCommand command : castle.drawing()) {
            if (command instanceof Polygon polygon) {
                if (polygon == targetPolygon) {
                    return index;
                }
                index++;
            }
        }
        return -1;
    }

    private int findClickedPointIndex(Polygon polygon, double left, double bottom, double scale, Point2D clickPoint) {
        final double hitRadius = 6.0;
        for (int i = 0; i < polygon.points().size(); i++) {
            Point point = polygon.points().get(i);
            double screenX = left + point.x() * scale;
            double screenY = bottom - point.y() * scale;
            double distance = clickPoint.distance(screenX, screenY);
            if (distance <= hitRadius) {
                return i;
            }
        }
        return -1;
    }
}
