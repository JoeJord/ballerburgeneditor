package at.josefjordan.ballerburg.editor.io;

import at.josefjordan.ballerburg.editor.model.CastleDocument;
import at.josefjordan.ballerburg.editor.model.CastleDocument.Castle;
import at.josefjordan.ballerburg.editor.model.CastleDocument.DrawingCommand;
import at.josefjordan.ballerburg.editor.model.CastleDocument.FillStyle;
import at.josefjordan.ballerburg.editor.model.CastleDocument.Point;
import at.josefjordan.ballerburg.editor.model.CastleDocument.Polygon;
import at.josefjordan.ballerburg.editor.model.CastleFileFormat;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Writes a canonical BALLER.DAT file using CP437 and CRLF line endings. */
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
                writeCastle(writer, castle);
                writer.write(NEW_LINE);
            }
            writer.write(CastleFileFormat.FILE_TERMINATOR + " * end of castle data *");
            writer.write(NEW_LINE);
        }
    }

    private void writeCastle(BufferedWriter writer, Castle castle) throws IOException {
        writeLine(writer, List.of(castle.width()));
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
            if (command instanceof FillStyle fill) {
                drawing.add(CastleFileFormat.FILL_STYLE_COMMAND);
                drawing.add(fill.style());
                drawing.add(fill.index());
            } else if (command instanceof Polygon polygon) {
                drawing.addAll(points(polygon.points()));
                drawing.add(CastleFileFormat.POLYGON_TERMINATOR);
            }
        }
        drawing.add(CastleFileFormat.CASTLE_TERMINATOR);
        writeLine(writer, drawing);
    }

    private List<Integer> point(Point point) {
        return List.of(point.x(), point.y());
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
    private final List<Integer> concat(List<Integer>... parts) {
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
}
