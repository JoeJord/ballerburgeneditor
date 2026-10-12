package at.josefjordan.ballerburg.editor.ui.editor.castle;

import at.josefjordan.ballerburg.editor.model.CastleDocument.Castle;
import at.josefjordan.ballerburg.editor.model.CastleDocument.DrawingCommand;
import at.josefjordan.ballerburg.editor.model.CastleDocument.Point;
import at.josefjordan.ballerburg.editor.model.CastleDocument.Polygon;
import at.josefjordan.ballerburg.editor.model.CastleDocument.Size;
import at.josefjordan.ballerburg.editor.model.CastleFileFormat;

import java.util.List;

/**
 * Calculates castle-coordinate bounds occupied by the castle geometry and positioned objects.
 */
final class CastleBoundsCalculator {
    /**
     * Default castle width used when calculating bounds for an empty view.
     */
    private static final int EMPTY_CASTLE_DEFAULT_WIDTH = 100;
    /**
     * Lowest upper bound used for content that has no objects extending above the baseline.
     */
    private static final int MINIMUM_CONTENT_MAX_Y = 1;

    /**
     * Calculates the extents of the castle drawing and positioned objects.
     *
     * @param castle castle whose contents are measured, or {@code null} for the empty-view bounds
     * @return inclusive geometry bounds in castle coordinates
     */
    Bounds calculate(Castle castle) {
        int minimumX = 0;
        int minimumY = 0;
        int maximumY = MINIMUM_CONTENT_MAX_Y;
        int maximumX = castle == null ? EMPTY_CASTLE_DEFAULT_WIDTH : castle.width();
        if (castle == null) {
            return new Bounds(minimumX, minimumY, maximumX, maximumY);
        }

        for (DrawingCommand command : castle.drawing()) {
            if (command instanceof Polygon(List<Point> points)) {
                for (Point point : points) {
                    minimumX = Math.min(minimumX, point.x());
                    minimumY = Math.min(minimumY, point.y());
                    maximumX = Math.max(maximumX, point.x());
                    maximumY = Math.max(maximumY, point.y());
                }
            }
        }

        Point throne = castle.throne();
        if (throne.isSet()) {
            Size throneSize = new Size(CastleFileFormat.THRONE_WIDTH, CastleFileFormat.THRONE_HEIGHT);
            minimumX = Math.min(minimumX, throne.x());
            minimumY = Math.min(minimumY, throne.y());
            maximumX = Math.max(maximumX, throne.x() + CastleFileFormat.THRONE_WIDTH);
            maximumY = Math.max(maximumY, areaTop(throne, throneSize));
        }

        for (Point cannon : castle.cannons()) {
            minimumX = Math.min(minimumX, cannon.x());
            minimumY = Math.min(minimumY, cannon.y());
            maximumX = Math.max(maximumX, cannon.x() + CastleFileFormat.CANNON_WIDTH);
            maximumY = Math.max(maximumY, cannon.y() + CastleFileFormat.CANNON_HEIGHT);
        }

        for (ResourceArea area : ResourceArea.values()) {
            Point position = resourcePosition(castle, area);
            if (position.isSet()) {
                Size size = resourceSize(castle, area);
                minimumX = Math.min(minimumX, position.x());
                minimumY = Math.min(minimumY, position.y());
                maximumX = Math.max(maximumX, position.x() + size.width());
                maximumY = Math.max(maximumY, areaTop(position, size));
            }
        }

        Point windVane = castle.windVane();
        if (windVane.isSet()) {
            minimumX = Math.min(minimumX, windVane.x());
            minimumY = Math.min(minimumY, windVane.y());
            maximumX = Math.max(maximumX, windVane.x() + CastleFileFormat.WIND_VANE_WIDTH);
            maximumY = Math.max(maximumY, windVane.y() + CastleFileFormat.WIND_VANE_HEIGHT);
        }
        return new Bounds(minimumX, minimumY, maximumX, maximumY);
    }

    /**
     * Returns the lower-left position of the specified resource area.
     *
     * @param castle castle containing the resource area
     * @param area   resource area whose position is requested
     * @return the area's lower-left position in castle coordinates
     */
    private Point resourcePosition(Castle castle, ResourceArea area) {
        return switch (area) {
            case MONEY -> castle.money();
            case POWDER -> castle.powder();
            case BALLS -> castle.balls();
        };
    }

    /**
     * Returns the dimensions of the specified resource area.
     *
     * @param castle castle containing the resource area
     * @param area   resource area whose dimensions are requested
     * @return the area's dimensions in castle coordinates
     */
    private Size resourceSize(Castle castle, ResourceArea area) {
        return switch (area) {
            case MONEY -> castle.moneyRoom();
            case POWDER -> castle.powderRoom();
            case BALLS -> castle.ballRoom();
        };
    }

    /**
     * Returns the upper y-coordinate of an area given its lower-left position and size.
     *
     * @param position lower-left position in castle coordinates
     * @param size     area dimensions in castle coordinates
     * @return the area's upper y-coordinate
     */
    private int areaTop(Point position, Size size) {
        return position.y() + size.height();
    }

    /**
     * Content extents in castle coordinates.
     *
     * @param minimumX minimum horizontal coordinate
     * @param minimumY minimum vertical coordinate
     * @param maximumX maximum horizontal coordinate
     * @param maximumY maximum vertical coordinate
     */
    record Bounds(int minimumX, int minimumY, int maximumX, int maximumY) {
    }
}
