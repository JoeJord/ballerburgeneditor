package at.josefjordan.ballerburg.editor.ui.editor.castle;

import at.josefjordan.ballerburg.editor.model.CastleDocument.Point;
import at.josefjordan.ballerburg.editor.model.CastleDocument.Polygon;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Owns the state and coordinate updates for dragging polygon vertices and polygons.
 */
final class CastlePolygonDragHandler {
    /**
     * View containing the edited castle and exposing its current selection.
     */
    private final CastleViewPanel panel;
    /**
     * Resolves the current polygon and vertex from the castle drawing.
     */
    private final CastleHitTester hitTester;
    /**
     * Polygon-only drawing index of the active drag, or {@code -1} when idle.
     */
    private int polygonIndex = -1;
    /**
     * Vertex index of the active point drag, or {@code -1} when moving the whole polygon.
     */
    private int pointIndex = -1;
    /**
     * Whether the active gesture translates every vertex of the polygon.
     */
    private boolean draggingEntirePolygon;
    /**
     * Screen-space pointer offset from the polygon's first vertex while moving it.
     */
    private Point2D polygonDragOffset;

    /**
     * Creates a polygon drag handler for the specified view.
     *
     * @param panel     view whose polygon is being edited
     * @param hitTester helper used to resolve polygon data from the current castle
     */
    CastlePolygonDragHandler(CastleViewPanel panel, CastleHitTester hitTester) {
        this.panel = panel;
        this.hitTester = hitTester;
    }

    /**
     * Begins dragging a single vertex of a polygon.
     *
     * @param polygonIndex zero-based index among polygon drawing commands
     * @param pointIndex   zero-based index of the vertex within that polygon
     */
    void startPointDrag(int polygonIndex, int pointIndex) {
        this.polygonIndex = polygonIndex;
        this.pointIndex = pointIndex;
        draggingEntirePolygon = false;
        polygonDragOffset = null;
    }

    /**
     * Begins translating an entire polygon while preserving the pointer's position relative to its anchor.
     *
     * @param polygonIndex zero-based index among polygon drawing commands
     * @param polygon      polygon being moved
     * @param left         castle origin's screen x-coordinate
     * @param bottom       castle baseline's screen y-coordinate
     * @param scale        screen pixels per castle coordinate
     * @param clickPoint   initial pointer position in panel coordinates
     */
    void startPolygonMove(int polygonIndex, Polygon polygon, double left, double bottom, double scale, Point2D clickPoint) {
        this.polygonIndex = polygonIndex;
        pointIndex = -1;
        draggingEntirePolygon = true;
        Point anchor = polygon.points().getFirst();
        double anchorX = left + anchor.x() * scale;
        double anchorY = bottom - anchor.y() * scale;
        polygonDragOffset = new Point2D.Double(clickPoint.getX() - anchorX, clickPoint.getY() - anchorY);
    }

    /**
     * Reports whether a polygon or one of its vertices is currently being dragged.
     *
     * @return {@code true} when a polygon drag is active
     */
    boolean isDragging() {
        return polygonIndex >= 0;
    }

    /**
     * Updates the active polygon drag to the current pointer position.
     *
     * @param dragPoint current pointer position in panel coordinates
     * @param left      castle origin's screen x-coordinate
     * @param bottom    castle baseline's screen y-coordinate
     * @param scale     screen pixels per castle coordinate
     */
    void dragTo(Point2D dragPoint, double left, double bottom, double scale) {
        if (draggingEntirePolygon) {
            updatePolygonPosition(dragPoint, left, bottom, scale);
        } else if (pointIndex >= 0) {
            int x = (int) Math.round((dragPoint.getX() - left) / scale);
            int y = (int) Math.round((bottom - dragPoint.getY()) / scale);
            panel.getEditorPanel().updatePolygonPoint(polygonIndex, pointIndex, new Point(x, y));
            selectUpdatedPolygon();
        }
    }

    /**
     * Keeps the active polygon gesture aligned with the pointer after the viewport scrolls.
     *
     * @param dragPoint current pointer position in panel coordinates
     * @param deltaX    horizontal scroll displacement in pixels
     * @param deltaY    vertical scroll displacement in pixels
     * @param left      castle origin's current screen x-coordinate
     * @param bottom    castle baseline's current screen y-coordinate
     * @param scale     current screen pixels per castle coordinate
     */
    void adjustForScroll(Point2D dragPoint, int deltaX, int deltaY, double left, double bottom, double scale) {
        if (draggingEntirePolygon) {
            if (polygonDragOffset != null) {
                polygonDragOffset = new Point2D.Double(
                        polygonDragOffset.getX() - deltaX, polygonDragOffset.getY() - deltaY);
            }
            updatePolygonPosition(dragPoint, left, bottom, scale);
        } else if (pointIndex >= 0) {
            Point point = hitTester.polygonPointAt(panel.getCastle(), polygonIndex, pointIndex);
            if (point != null) {
                int x = (int) Math.round(point.x() + deltaX / scale);
                int y = (int) Math.round(point.y() - deltaY / scale);
                panel.getEditorPanel().updatePolygonPoint(polygonIndex, pointIndex, new Point(x, y));
                selectUpdatedPolygon();
            }
        }
    }

    /**
     * Clears the active drag gesture and its pointer offset.
     */
    void clear() {
        polygonIndex = -1;
        pointIndex = -1;
        draggingEntirePolygon = false;
        polygonDragOffset = null;
    }

    /**
     * Translates every polygon vertex so its first vertex tracks the pointer.
     *
     * @param dragPoint current pointer position in panel coordinates
     * @param left      castle origin's screen x-coordinate
     * @param bottom    castle baseline's screen y-coordinate
     * @param scale     screen pixels per castle coordinate
     */
    private void updatePolygonPosition(Point2D dragPoint, double left, double bottom, double scale) {
        Polygon polygon = hitTester.polygonAt(panel.getCastle(), polygonIndex);
        if (polygon == null || polygonDragOffset == null) {
            return;
        }

        Point anchor = polygon.points().getFirst();
        int targetX = (int) Math.round((dragPoint.getX() - polygonDragOffset.getX() - left) / scale);
        int targetY = (int) Math.round((bottom - (dragPoint.getY() - polygonDragOffset.getY())) / scale);
        int deltaX = targetX - anchor.x();
        int deltaY = targetY - anchor.y();
        List<Point> newPoints = new ArrayList<>();
        for (Point point : polygon.points()) {
            newPoints.add(new Point(point.x() + deltaX, point.y() + deltaY));
        }
        panel.getEditorPanel().updatePolygonPoints(polygonIndex, newPoints);
        selectUpdatedPolygon();
    }

    /**
     * Refreshes the view selection to reference the latest immutable polygon snapshot.
     */
    private void selectUpdatedPolygon() {
        Polygon updatedPolygon = hitTester.polygonAt(panel.getCastle(), polygonIndex);
        if (updatedPolygon != null) {
            panel.setSelectedPolygon(updatedPolygon);
        }
    }
}
