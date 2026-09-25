package at.josefjordan.ballerburg.editor.io;

import at.josefjordan.ballerburg.editor.model.CastleDocument;
import at.josefjordan.ballerburg.editor.model.CastleDocument.*;
import at.josefjordan.ballerburg.editor.model.CastleFileFormat;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes a canonical BALLER.DAT file using CP437 and CRLF line endings.
 */
public final class CastleWriter {
    /**
     * The line separator used in output.
     */
    private static final String NEW_LINE = "\r\n";
    /**
     * The first castle number.
     */
    private static final int FIRST_CASTLE_NUMBER = 1;

    /**
     * Writes the document as a canonical CP437-encoded BALLER.DAT file with CRLF line endings.
     *
     * @param path     the BALLER.DAT file path
     * @param document the document to process
     * @throws IOException if the output file cannot be written or coordinates are invalid
     */
    public void write(Path path, CastleDocument document) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path, CastleReader.CHARSET)) {
            writer.write("* BALLER.DAT written by Ballerburgen Editor *");
            writer.write(NEW_LINE);
            writer.write(NEW_LINE);
            int number = FIRST_CASTLE_NUMBER;
            for (Castle castle : document.castles()) {
                writer.write("* Castle " + number++ + " *");
                writer.write(NEW_LINE);
                writeCastle(writer, normalize(castle));
                writer.write(NEW_LINE);
            }
            writer.write(CastleFileFormat.FILE_TERMINATOR + " * end of castle data *");
            writer.write(NEW_LINE);
        }
    }

    /**
     * Writes a castle's fields and drawing commands in BALLER.DAT order.
     *
     * @param writer destination for the serialized values
     * @param castle normalized castle data to serialize
     * @throws IOException if output cannot be written or normalized polygon coordinates are negative
     */
    private void writeCastle(BufferedWriter writer, Castle castle) throws IOException {
        validateCoordinates(castle);
        writeLine(writer, List.of(castleWidth(castle)));
        writeLine(writer, cannonPoints(castle.cannons()));
        writeLine(writer, point(castle.throne()));
        writeLine(writer, point(castle.windVane()));
        writeLine(writer, concat(point(castle.money()), point(castle.powder()), point(castle.balls())));
        writeLine(writer, List.of(castle.moneyRoom().width(), castle.moneyRoom().height(),
                castle.powderRoom().width(), castle.powderRoom().height(),
                castle.ballRoom().width(), castle.ballRoom().height()));
        writeLine(writer, List.of(castle.resources().money(), castle.resources().powder(),
                castle.resources().balls(), castle.resources().people()));
        writeLine(writer, castle.reserved());
        List<Integer> drawing = new ArrayList<>();
        for (DrawingCommand command : castle.drawing()) {
            if (command instanceof FillStyle(int style, int index)) {
                drawing.add(CastleFileFormat.FILL_STYLE_COMMAND);
                drawing.add(style);
                drawing.add(index);
            } else if (command instanceof Polygon(List<Point> points)) {
                drawing.addAll(points(points));
                drawing.add(CastleFileFormat.POLYGON_TERMINATOR);
            }
        }
        drawing.add(CastleFileFormat.CASTLE_TERMINATOR);
        writeLine(writer, drawing);
    }

    /**
     * Returns the greatest vertex x-coordinate among the castle polygons, or zero if no polygon exists.
     *
     * @param castle castle whose polygon vertices determine the width
     * @return the maximum polygon x-coordinate, or zero if there are no polygons
     */
    private int castleWidth(Castle castle) {
        int width = 0;
        for (DrawingCommand command : castle.drawing()) {
            if (command instanceof Polygon(List<Point> points)) {
                for (Point point : points) {
                    width = Math.max(width, point.x());
                }
            }
        }
        return width;
    }

    /**
     * Translates set castle coordinates so the minimum x and y coordinates are zero.
     *
     * @param castle castle whose set coordinates are translated
     * @return a castle translated so set coordinates start at a non-negative origin
     */
    private Castle normalize(Castle castle) {
        CoordinateOffset offset = coordinateOffset(castle);
        if (offset.x() == 0 && offset.y() == 0) {
            return castle;
        }

        List<Point> cannons = castle.cannons().stream()
                .map(point -> translate(point, offset))
                .toList();
        return new Castle(castle.width(), cannons, translateIfSet(castle.throne(), offset),
                translateIfSet(castle.windVane(), offset), translateIfSet(castle.money(), offset),
                translateIfSet(castle.powder(), offset), translateIfSet(castle.balls(), offset),
                castle.moneyRoom(), castle.powderRoom(), castle.ballRoom(), castle.resources(),
                castle.reserved(), castle.drawing().stream().map(command -> {
            if (command instanceof FillStyle) {
                return command;
            }
            Polygon polygon = (Polygon) command;
            return new Polygon(polygon.points().stream()
                    .map(point -> translate(point, offset))
                    .toList());
        }).toList());
    }

    /**
     * Calculates the translation that moves set coordinates to a non-negative origin.
     *
     * @param castle castle whose set coordinates determine the translation
     * @return the x and y translation needed to normalize set coordinates
     */
    private CoordinateOffset coordinateOffset(Castle castle) {
        int minimumX = Integer.MAX_VALUE;
        int minimumY = Integer.MAX_VALUE;
        List<Point> points = new ArrayList<>();
        points.addAll(castle.cannons());
        points.addAll(castle.drawing().stream()
                .filter(Polygon.class::isInstance)
                .map(Polygon.class::cast)
                .flatMap(polygon -> polygon.points().stream())
                .toList());
        points.add(castle.throne());
        points.add(castle.windVane());
        points.add(castle.money());
        points.add(castle.powder());
        points.add(castle.balls());
        for (Point point : points) {
            if (!isSet(point)) {
                continue;
            }
            minimumX = Math.min(minimumX, point.x());
            minimumY = Math.min(minimumY, point.y());
        }
        return minimumX == Integer.MAX_VALUE
                ? new CoordinateOffset(0, 0)
                : new CoordinateOffset(-minimumX, -minimumY);
    }

    /**
     * Rejects negative polygon coordinates after coordinate normalization.
     *
     * @param castle normalized castle to validate
     * @throws IOException if any polygon coordinate is negative after normalization
     */
    private void validateCoordinates(Castle castle) throws IOException {
        for (DrawingCommand command : castle.drawing()) {
            if (command instanceof Polygon(List<Point> points)) {
                for (Point point : points) {
                    if (point.x() < 0 || point.y() < 0) {
                        throw new IOException("Negative polygon coordinate after normalization: "
                                + point.x() + "," + point.y());
                    }
                }
            }
        }
    }

    /**
     * Returns whether both coordinates identify a set point.
     *
     * @param point point to test
     * @return {@code true} unless either coordinate uses the unset-point sentinel
     */
    private boolean isSet(Point point) {
        return point.x() != Integer.MIN_VALUE && point.y() != Integer.MIN_VALUE;
    }

    /**
     * Translates the point only when it is set.
     *
     * @param point  point to translate if set
     * @param offset horizontal and vertical translation
     * @return the original unset point, or the translated point when it is set
     */
    private Point translateIfSet(Point point, CoordinateOffset offset) {
        return isSet(point) ? translate(point, offset) : point;
    }

    /**
     * Translates a castle point by the supplied coordinate offset.
     *
     * @param point  point to translate
     * @param offset horizontal and vertical translation
     * @return the point translated by the supplied x and y offset
     */
    private Point translate(Point point, CoordinateOffset offset) {
        return new Point(point.x() + offset.x(), point.y() + offset.y());
    }

    /**
     * Returns the two-value coordinate representation used in the file.
     *
     * @param point point to encode
     * @return its x/y values, or the file-format unused-coordinate markers when unset
     */
    private List<Integer> point(Point point) {
        return isSet(point)
                ? List.of(point.x(), point.y())
                : List.of(CastleFileFormat.UNUSED_CANNON_COORDINATE,
                CastleFileFormat.UNUSED_CANNON_COORDINATE);
    }

    /**
     * Flattens ordered points into alternating x and y values.
     *
     * @param points the ordered polygon vertices
     * @return the flattened x and y coordinates of the supplied points
     */
    private List<Integer> points(List<Point> points) {
        List<Integer> values = new ArrayList<>(points.size() * CastleFileFormat.COORDINATE_COMPONENT_COUNT);
        for (Point point : points) {
            values.add(point.x());
            values.add(point.y());
        }
        return values;
    }

    /**
     * Pads cannon coordinates with unused markers to the required slot count.
     *
     * @param cannons cannon positions in castle order
     * @return cannon coordinates padded to the fixed file-format count and flattened
     */
    private List<Integer> cannonPoints(List<Point> cannons) {
        List<Point> positions = new ArrayList<>(cannons);
        while (positions.size() < CastleFileFormat.CANNON_POSITION_COUNT) {
            positions.add(new Point(CastleFileFormat.UNUSED_CANNON_COORDINATE, CastleFileFormat.UNUSED_CANNON_COORDINATE));
        }
        return points(positions);
    }

    /**
     * Concatenates the supplied integer lists in order.
     *
     * @param parts integer lists to append, in output order
     * @return the input integer lists joined in order
     */
    @SafeVarargs
    private List<Integer> concat(List<Integer>... parts) {
        List<Integer> values = new ArrayList<>();
        for (List<Integer> part : parts) {
            values.addAll(part);
        }
        return values;
    }

    /**
     * Writes comma-separated values followed by the configured line ending.
     *
     * @param writer destination for the output line
     * @param values numeric fields to write, in file order
     * @throws IOException if writing the output line fails
     */
    private void writeLine(BufferedWriter writer, List<Integer> values) throws IOException {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                writer.write(',');
            }
            writer.write(Integer.toString(values.get(i)));
        }
        writer.write(NEW_LINE);
    }

    /**
     * Coordinate translation required to move set geometry to a non-negative origin.
     *
     * @param x horizontal translation
     * @param y vertical translation
     */
    private record CoordinateOffset(int x, int y) {
    }
}
