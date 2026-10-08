package at.josefjordan.ballerburg.editor.ui.editor;

import at.josefjordan.ballerburg.editor.model.CastleDocument.Castle;
import at.josefjordan.ballerburg.editor.model.CastleDocument.DrawingCommand;
import at.josefjordan.ballerburg.editor.model.CastleDocument.Point;
import at.josefjordan.ballerburg.editor.model.CastleDocument.Polygon;
import at.josefjordan.ballerburg.editor.model.CastleDocument.Size;
import at.josefjordan.ballerburg.editor.model.CastleFileFormat;

import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

/**
 * Performs geometric hit tests against castle elements in panel coordinates.
 */
final class CastleHitTester {
    /**
     * Maximum screen-space distance at which a polygon vertex is considered hit.
     */
    private static final double POINT_HIT_RADIUS = 6.0;
    /**
     * Maximum screen-space distance at which a resource-area corner handle is considered hit.
     */
    private static final double RESOURCE_CORNER_HIT_RADIUS = 6.0;

    /**
     * Returns the polygon at its zero-based index among drawing polygon commands.
     *
     * @param castle       castle whose drawing commands are searched
     * @param polygonIndex zero-based polygon index
     * @return the polygon, or {@code null} if the castle or index is unavailable
     */
    Polygon polygonAt(Castle castle, int polygonIndex) {
        if (castle == null) {
            return null;
        }
        int currentPolygonIndex = 0;
        for (DrawingCommand command : castle.drawing()) {
            if (command instanceof Polygon polygon) {
                if (currentPolygonIndex == polygonIndex) {
                    return polygon;
                }
                currentPolygonIndex++;
            }
        }
        return null;
    }

    /**
     * Returns a polygon vertex by its polygon-only and vertex indices.
     *
     * @param castle       castle containing the polygon
     * @param polygonIndex zero-based index among polygon drawing commands
     * @param pointIndex   zero-based vertex index within that polygon
     * @return the vertex, or {@code null} if either index is unavailable
     */
    Point polygonPointAt(Castle castle, int polygonIndex, int pointIndex) {
        if (castle == null) {
            return null;
        }
        Polygon polygon = polygonAt(castle, polygonIndex);
        if (polygon == null || pointIndex < 0 || pointIndex >= polygon.points().size()) {
            return null;
        }
        return polygon.points().get(pointIndex);
    }

    /**
     * Finds a polygon's position among the castle's polygon drawing commands by identity.
     *
     * @param castle        castle whose drawing commands are searched
     * @param targetPolygon polygon instance to locate
     * @return zero-based polygon index, or {@code -1} if the instance is absent
     */
    int polygonIndexOf(Castle castle, Polygon targetPolygon) {
        if (castle == null) {
            return -1;
        }
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

    /**
     * Finds the first polygon whose rendered interior contains the supplied point.
     *
     * @param castle     castle whose polygons are hit-tested
     * @param left       rendered castle origin x-coordinate
     * @param bottom     rendered castle baseline y-coordinate
     * @param scale      screen pixels per castle coordinate
     * @param clickPoint point in panel coordinates
     * @return the hit polygon, or {@code null} when no polygon contains the point
     */
    Polygon findClickedPolygon(Castle castle, double left, double bottom, double scale, Point2D clickPoint) {
        for (DrawingCommand command : castle.drawing()) {
            if (command instanceof Polygon polygon && polygonPath(polygon, left, bottom, scale).contains(clickPoint)) {
                return polygon;
            }
        }
        return null;
    }

    /**
     * Finds the polygon vertex within the pointer hit radius.
     *
     * @param polygon    polygon whose vertices are checked
     * @param left       rendered castle origin x-coordinate
     * @param bottom     rendered castle baseline y-coordinate
     * @param scale      screen pixels per castle coordinate
     * @param clickPoint point in panel coordinates
     * @return zero-based vertex index, or {@code -1} if no vertex is close enough
     */
    int findClickedPointIndex(Polygon polygon, double left, double bottom, double scale, Point2D clickPoint) {
        for (int index = 0; index < polygon.points().size(); index++) {
            Point point = polygon.points().get(index);
            double screenX = left + point.x() * scale;
            double screenY = bottom - point.y() * scale;
            if (clickPoint.distance(screenX, screenY) <= POINT_HIT_RADIUS) {
                return index;
            }
        }
        return -1;
    }

    /**
     * Creates a closed screen-space path for a castle polygon.
     *
     * @param polygon polygon whose vertices define the path
     * @param left    rendered castle origin x-coordinate
     * @param bottom  rendered castle baseline y-coordinate
     * @param scale   screen pixels per castle coordinate
     * @return the polygon path in panel coordinates
     */
    Path2D polygonPath(Polygon polygon, double left, double bottom, double scale) {
        Path2D path = new Path2D.Double();
        Point first = polygon.points().getFirst();
        path.moveTo(left + first.x() * scale, bottom - first.y() * scale);
        for (int index = 1; index < polygon.points().size(); index++) {
            Point point = polygon.points().get(index);
            path.lineTo(left + point.x() * scale, bottom - point.y() * scale);
        }
        path.closePath();
        return path;
    }

    /**
     * Finds the cannon whose rendered bounds contain the supplied point.
     *
     * @param castle     castle whose cannon positions are checked
     * @param left       rendered castle origin x-coordinate
     * @param bottom     rendered castle baseline y-coordinate
     * @param scale      screen pixels per castle coordinate
     * @param clickPoint point in panel coordinates
     * @return zero-based cannon index, or {@code -1} when no cannon is hit
     */
    int findClickedCannonIndex(Castle castle, double left, double bottom, double scale, Point2D clickPoint) {
        for (int index = 0; index < castle.cannons().size(); index++) {
            Point cannon = castle.cannons().get(index);
            if (CastleViewPanel.calculateCannonRectangle(cannon, left, bottom, scale).contains(clickPoint)) {
                return index;
            }
        }
        return -1;
    }

    /**
     * Checks whether a point is inside the positioned wind vane's rendered bounds.
     *
     * @param castle     castle whose wind vane is checked
     * @param left       rendered castle origin x-coordinate
     * @param bottom     rendered castle baseline y-coordinate
     * @param scale      screen pixels per castle coordinate
     * @param clickPoint point in panel coordinates
     * @return {@code true} if the wind vane is positioned and contains the point
     */
    boolean isWindVaneClicked(Castle castle, double left, double bottom, double scale, Point2D clickPoint) {
        Point windVane = castle.windVane();
        if (!isPositioned(windVane)) {
            return false;
        }
        return CastleViewPanel.calculateWindVaneRectangle(windVane, left, bottom, scale).contains(clickPoint);
    }

    /**
     * Checks whether a point is inside the positioned throne's rendered bounds.
     *
     * @param castle     castle whose throne is checked
     * @param left       rendered castle origin x-coordinate
     * @param bottom     rendered castle baseline y-coordinate
     * @param scale      screen pixels per castle coordinate
     * @param clickPoint point in panel coordinates
     * @return {@code true} if the throne is positioned and contains the point
     */
    boolean isThroneClicked(Castle castle, double left, double bottom, double scale, Point2D clickPoint) {
        Point throne = castle.throne();
        if (!isPositioned(throne)) {
            return false;
        }
        Rectangle2D throneRect = new Rectangle2D.Double(left + throne.x() * scale,
                bottom - (throne.y() + CastleFileFormat.THRONE_HEIGHT) * scale,
                CastleFileFormat.THRONE_WIDTH * scale, CastleFileFormat.THRONE_HEIGHT * scale);
        return throneRect.contains(clickPoint);
    }

    /**
     * Finds the resource-storage area whose rendered rectangle contains the point.
     *
     * @param castle     castle whose storage areas are checked
     * @param left       rendered castle origin x-coordinate
     * @param bottom     rendered castle baseline y-coordinate
     * @param scale      screen pixels per castle coordinate
     * @param clickPoint point in panel coordinates
     * @return the hit resource area, or {@code null} if none contains the point
     */
    ResourceArea findClickedResourceArea(Castle castle, double left, double bottom, double scale, Point2D clickPoint) {
        for (ResourceArea area : ResourceArea.values()) {
            Point position = resourcePosition(castle, area);
            Size size = resourceSize(castle, area);
            Rectangle2D bounds = resourceRectangle(position, size, left, bottom, scale);
            if (bounds.contains(clickPoint)) {
                return area;
            }
        }
        return null;
    }

    /**
     * Finds the resize handle within the pointer hit radius.
     *
     * @param castle       castle containing the selected storage area
     * @param resourceArea storage area whose corners are checked
     * @param left         rendered castle origin x-coordinate
     * @param bottom       rendered castle baseline y-coordinate
     * @param scale        screen pixels per castle coordinate
     * @param clickPoint   point in panel coordinates
     * @return corner index (bottom-left 0, bottom-right 1, top-left 2, top-right 3), or {@code -1}
     */
    int findClickedResourceCorner(Castle castle, ResourceArea resourceArea, double left, double bottom, double scale, Point2D clickPoint) {
        Point position = resourcePosition(castle, resourceArea);
        Size size = resourceSize(castle, resourceArea);
        double x = left + position.x() * scale;
        double y = bottom - (position.y() + size.height()) * scale;
        double width = size.width() * scale;
        double height = size.height() * scale;

        if (clickPoint.distance(x, y + height) <= RESOURCE_CORNER_HIT_RADIUS) {
            return 0;
        }
        if (clickPoint.distance(x + width, y + height) <= RESOURCE_CORNER_HIT_RADIUS) {
            return 1;
        }
        if (clickPoint.distance(x, y) <= RESOURCE_CORNER_HIT_RADIUS) {
            return 2;
        }
        if (clickPoint.distance(x + width, y) <= RESOURCE_CORNER_HIT_RADIUS) {
            return 3;
        }
        return -1;
    }

    /**
     * Returns the lower-left coordinate of a resource-storage area.
     *
     * @param castle       castle containing the area
     * @param resourceArea storage area to query
     * @return its lower-left position in castle coordinates
     */
    Point resourcePosition(Castle castle, ResourceArea resourceArea) {
        return switch (resourceArea) {
            case MONEY -> castle.money();
            case POWDER -> castle.powder();
            case BALLS -> castle.balls();
        };
    }

    /**
     * Returns the dimensions of a resource-storage area.
     *
     * @param castle       castle containing the area
     * @param resourceArea storage area to query
     * @return its dimensions in castle coordinates
     */
    Size resourceSize(Castle castle, ResourceArea resourceArea) {
        return switch (resourceArea) {
            case MONEY -> castle.moneyRoom();
            case POWDER -> castle.powderRoom();
            case BALLS -> castle.ballRoom();
        };
    }

    /**
     * Converts a resource area's castle-coordinate bounds to panel coordinates.
     *
     * @param position lower-left area position in castle coordinates
     * @param size     area dimensions in castle coordinates
     * @param left     rendered castle origin x-coordinate
     * @param bottom   rendered castle baseline y-coordinate
     * @param scale    screen pixels per castle coordinate
     * @return the area's axis-aligned bounds in panel coordinates
     */
    private Rectangle2D resourceRectangle(Point position, Size size,
                                          double left, double bottom, double scale) {
        return new Rectangle2D.Double(left + position.x() * scale,
                bottom - (position.y() + size.height()) * scale,
                size.width() * scale, size.height() * scale);
    }

    /**
     * Checks that a point does not use the unset-position sentinel.
     *
     * @param point point to inspect
     * @return {@code true} if both coordinates are set
     */
    // Keep the positive predicate name: callers use it to reject unset positions in guard clauses.
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean isPositioned(Point point) {
        return point.x() != Integer.MIN_VALUE && point.y() != Integer.MIN_VALUE;
    }
}
