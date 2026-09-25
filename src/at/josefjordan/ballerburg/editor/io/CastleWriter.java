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
    private static final String NEW_LINE = "\r\n";
    private static final int FIRST_CASTLE_NUMBER = 1;

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

    private boolean isSet(Point point) {
        return point.x() != Integer.MIN_VALUE && point.y() != Integer.MIN_VALUE;
    }

    private Point translateIfSet(Point point, CoordinateOffset offset) {
        return isSet(point) ? translate(point, offset) : point;
    }

    private Point translate(Point point, CoordinateOffset offset) {
        return new Point(point.x() + offset.x(), point.y() + offset.y());
    }

    private List<Integer> point(Point point) {
        return isSet(point)
                ? List.of(point.x(), point.y())
                : List.of(CastleFileFormat.UNUSED_CANNON_COORDINATE,
                CastleFileFormat.UNUSED_CANNON_COORDINATE);
    }

    private List<Integer> points(List<Point> points) {
        List<Integer> values = new ArrayList<>(points.size() * CastleFileFormat.COORDINATE_COMPONENT_COUNT);
        for (Point point : points) {
            values.add(point.x());
            values.add(point.y());
        }
        return values;
    }

    private List<Integer> cannonPoints(List<Point> cannons) {
        List<Point> positions = new ArrayList<>(cannons);
        while (positions.size() < CastleFileFormat.CANNON_POSITION_COUNT) {
            positions.add(new Point(CastleFileFormat.UNUSED_CANNON_COORDINATE, CastleFileFormat.UNUSED_CANNON_COORDINATE));
        }
        return points(positions);
    }

    @SafeVarargs
    private List<Integer> concat(List<Integer>... parts) {
        List<Integer> values = new ArrayList<>();
        for (List<Integer> part : parts) {
            values.addAll(part);
        }
        return values;
    }

    private void writeLine(BufferedWriter writer, List<Integer> values) throws IOException {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                writer.write(',');
            }
            writer.write(Integer.toString(values.get(i)));
        }
        writer.write(NEW_LINE);
    }

    private record CoordinateOffset(int x, int y) {
    }
}
