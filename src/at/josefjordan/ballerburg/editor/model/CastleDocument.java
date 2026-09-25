package at.josefjordan.ballerburg.editor.model;

import java.util.List;

/**
 * The editable contents of a BALLER.DAT file.
 */
public record CastleDocument(List<Castle> castles) {
    public CastleDocument {
        castles = List.copyOf(castles);
    }

    public record Point(int x, int y) {
    }

    public record Size(int width, int height) {
    }

    public record Resources(int money, int powder, int balls, int people) {
    }

    public record Castle(int width, List<Point> cannons, Point throne, Point windVane, Point money,
                         Point powder, Point balls, Size moneyRoom, Size powderRoom, Size ballRoom,
                         Resources resources, List<Integer> reserved, List<DrawingCommand> drawing) {
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

    public sealed interface DrawingCommand permits FillStyle, Polygon {
    }

    /**
     * The BALLER.DAT command {@code -2, style, index}.
     */
    public record FillStyle(int style, int index) implements DrawingCommand {
    }

    /**
     * A polygon terminated by {@code -9} in BALLER.DAT.
     */
    public record Polygon(List<Point> points) implements DrawingCommand {
        public Polygon {
            points = List.copyOf(points);
            if (points.isEmpty()) {
                throw new IllegalArgumentException("A polygon must contain at least one point");
            }
        }
    }
}
