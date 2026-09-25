package at.josefjordan.ballerburg.editor.model;

import java.util.List;

/**
 * Editable castle data parsed from or serialized to a BALLER.DAT file.
 *
 * @param castles castles in BALLER.DAT order
 */
public record CastleDocument(List<Castle> castles) {
    /**
     * Creates a document snapshot with an immutable castle list.
     *
     * @param castles castles in BALLER.DAT order
     */
    public CastleDocument {
        castles = List.copyOf(castles);
    }

    /**
     * An x/y location in castle coordinates; {@link Integer#MIN_VALUE} in both axes denotes an unset point.
     *
     * @param x horizontal castle coordinate
     * @param y vertical castle coordinate
     */
    public record Point(int x, int y) {
    }

    /**
     * A width and height measured in castle coordinates.
     *
     * @param width  horizontal extent
     * @param height vertical extent
     */
    public record Size(int width, int height) {
    }

    /**
     * Starting quantities of the castle's resources.
     *
     * @param money  starting money value
     * @param powder starting powder quantity
     * @param balls  starting cannonball quantity
     * @param people starting people quantity
     */
    public record Resources(int money, int powder, int balls, int people) {
    }

    /**
     * The geometry, objects, storage areas, and starting values for one castle.
     *
     * @param width      nominal castle width
     * @param cannons    cannon positions
     * @param throne     throne's lower-left position, or an unset point
     * @param windVane   wind vane's lower-left position, or an unset point
     * @param money      money-storage area's lower-left position, or an unset point
     * @param powder     powder-storage area's lower-left position, or an unset point
     * @param balls      cannonball-storage area's lower-left position, or an unset point
     * @param moneyRoom  money-storage dimensions
     * @param powderRoom powder-storage dimensions
     * @param ballRoom   cannonball-storage dimensions
     * @param resources  starting resource values
     * @param reserved   reserved integer fields retained from the file, in file order
     * @param drawing    fill-style and polygon commands in file order
     */
    public record Castle(int width, List<Point> cannons, Point throne, Point windVane, Point money,
                         Point powder, Point balls, Size moneyRoom, Size powderRoom, Size ballRoom,
                         Resources resources, List<Integer> reserved, List<DrawingCommand> drawing) {
        /**
         * Creates a castle snapshot and copies its collection components.
         *
         * @param width      nominal castle width
         * @param cannons    cannon positions
         * @param throne     throne position, or an unset point
         * @param windVane   wind-vane position, or an unset point
         * @param money      money-storage position, or an unset point
         * @param powder     powder-storage position, or an unset point
         * @param balls      cannonball-storage position, or an unset point
         * @param moneyRoom  money-storage dimensions
         * @param powderRoom powder-storage dimensions
         * @param ballRoom   cannonball-storage dimensions
         * @param resources  starting resource values
         * @param reserved   reserved integer fields retained from the file, in file order
         * @param drawing    ordered fill-style and polygon commands
         * @throws IllegalArgumentException if cannon or reserved-value counts violate the file format
         */
        public Castle {
            cannons = List.copyOf(cannons);
            reserved = List.copyOf(reserved);
            drawing = List.copyOf(drawing);
            if (cannons.size() > CastleFileFormat.CANNON_POSITION_COUNT) {
                throw new IllegalArgumentException("A castle may contain at most "
                        + CastleFileFormat.CANNON_POSITION_COUNT + " cannon positions");
            }
            if (reserved.size() != CastleFileFormat.RESERVED_VALUE_COUNT) {
                throw new IllegalArgumentException("A castle must contain exactly "
                        + CastleFileFormat.RESERVED_VALUE_COUNT + " reserved values");
            }
        }
    }

    /**
     * A command in the ordered castle drawing data.
     */
    public sealed interface DrawingCommand permits FillStyle, Polygon {
    }

    /**
     * The BALLER.DAT command {@code -2, style, index}.
     *
     * @param style encoded fill pattern or interior mode
     * @param index encoded fill color/pattern index
     */
    public record FillStyle(int style, int index) implements DrawingCommand {
    }

    /**
     * A polygon terminated by {@code -9} in BALLER.DAT.
     *
     * @param points nonempty polygon vertices in drawing order
     */
    public record Polygon(List<Point> points) implements DrawingCommand {
        /**
         * Creates a polygon snapshot and copies its point list.
         *
         * @param points nonempty polygon vertices in drawing order
         * @throws IllegalArgumentException if {@code points} is empty
         */
        public Polygon {
            points = List.copyOf(points);
            if (points.isEmpty()) {
                throw new IllegalArgumentException("A polygon must contain at least one point");
            }
        }
    }
}
