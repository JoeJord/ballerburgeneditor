package at.josefjordan.ballerburg.editor.io;

import at.josefjordan.ballerburg.editor.model.CastleDocument;
import at.josefjordan.ballerburg.editor.model.CastleDocument.*;
import at.josefjordan.ballerburg.editor.model.CastleFileFormat;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the CP437 encoded, comment-aware BALLER.DAT format.
 */
public final class CastleReader {
    public static final Charset CHARSET = Charset.forName("IBM437");
    private static final Pattern INTEGER = Pattern.compile("[+-]?\\d+");

    public CastleDocument read(Path path) throws IOException {
        String source = Files.readString(path, CHARSET);
        Cursor cursor = new Cursor(readNumbers(removeComments(source)));
        List<Castle> castles = new ArrayList<>();
        while (true) {
            int width = cursor.next("castle width or " + CastleFileFormat.FILE_TERMINATOR + " file terminator");
            if (width == CastleFileFormat.FILE_TERMINATOR) {
                cursor.requireEnd();
                return new CastleDocument(castles);
            }
            if (width < 0) {
                throw cursor.error("Invalid castle width " + width);
            }
            castles.add(readCastle(cursor, width));
        }
    }

    private Castle readCastle(Cursor cursor, int width) throws IOException {
        List<Point> cannons = readCannons(cursor);
        Point throne = readOptionalPoint(cursor);
        Point windVane = readOptionalPoint(cursor);
        Point money = readOptionalPoint(cursor);
        Point powder = readOptionalPoint(cursor);
        Point balls = readOptionalPoint(cursor);
        Size moneyRoom = readSize(cursor);
        Size powderRoom = readSize(cursor);
        Size ballRoom = readSize(cursor);
        Resources resources = new Resources(cursor.next("start money"), cursor.next("start powder"),
                cursor.next("start balls"), cursor.next("start people"));
        List<Integer> reserved = readValues(cursor, CastleFileFormat.RESERVED_VALUE_COUNT, "reserved value");
        List<DrawingCommand> drawing = readDrawing(cursor);
        return new Castle(width, cannons, throne, windVane, money, powder, balls, moneyRoom, powderRoom,
                ballRoom, resources, reserved, drawing);
    }

    private List<DrawingCommand> readDrawing(Cursor cursor) throws IOException {
        List<DrawingCommand> commands = new ArrayList<>();
        while (true) {
            int command = cursor.next("drawing command");
            if (command == CastleFileFormat.CASTLE_TERMINATOR) {
                return commands;
            }
            commands.add(readDrawingCommand(cursor, command));
        }
    }

    private DrawingCommand readDrawingCommand(Cursor cursor, int command) throws IOException {
        if (command == CastleFileFormat.FILL_STYLE_COMMAND) {
            return new FillStyle(cursor.next("fill style"), cursor.next("fill index"));
        }
        if (command >= 0) {
            return readPolygon(cursor, command);
        }
        throw cursor.error("Unknown drawing command " + command);
    }

    private Polygon readPolygon(Cursor cursor, int firstX) throws IOException {
        List<Point> points = new ArrayList<>();
        points.add(new Point(firstX, cursor.next("polygon y coordinate")));
        while (true) {
            int x = cursor.next("polygon x coordinate or "
                    + CastleFileFormat.POLYGON_TERMINATOR + " terminator");
            if (x == CastleFileFormat.POLYGON_TERMINATOR) {
                return new Polygon(points);
            }
            if (x < 0) {
                throw cursor.error("Unexpected polygon marker " + x);
            }
            points.add(new Point(x, cursor.next("polygon y coordinate")));
        }
    }

    @SuppressWarnings("SameParameterValue") // Keep this helper reusable for point groups with different fixed sizes.
    private List<Point> readPoints(Cursor cursor, int count) throws IOException {
        List<Point> points = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            points.add(readPoint(cursor));
        }
        return points;
    }

    private List<Point> readCannons(Cursor cursor) throws IOException {
        Point unusedPosition = new Point(CastleFileFormat.UNUSED_CANNON_COORDINATE, CastleFileFormat.UNUSED_CANNON_COORDINATE);
        List<Point> cannons = readPoints(cursor, CastleFileFormat.CANNON_POSITION_COUNT);
        cannons.removeIf(unusedPosition::equals);
        return cannons;
    }

    @SuppressWarnings("SameParameterValue") // Keep this helper reusable for value groups with different fixed sizes.
    private List<Integer> readValues(Cursor cursor, int count, String expected) throws IOException {
        List<Integer> values = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            values.add(cursor.next(expected));
        }
        return values;
    }

    private Point readPoint(Cursor cursor) throws IOException {
        return new Point(cursor.next("x coordinate"), cursor.next("y coordinate"));
    }

    private Point readOptionalPoint(Cursor cursor) throws IOException {
        Point point = readPoint(cursor);
        if (point.x() == CastleFileFormat.UNUSED_CANNON_COORDINATE
                && point.y() == CastleFileFormat.UNUSED_CANNON_COORDINATE) {
            return new Point(Integer.MIN_VALUE, Integer.MIN_VALUE);
        }
        return point;
    }

    private Size readSize(Cursor cursor) throws IOException {
        return new Size(cursor.next("width"), cursor.next("height"));
    }

    private String removeComments(String source) throws IOException {
        StringBuilder data = new StringBuilder(source.length());
        boolean comment = false;
        for (int i = 0; i < source.length(); i++) {
            char character = source.charAt(i);
            if (character == '*') {
                comment = !comment;
                data.append(' ');
            } else {
                char outputCharacter = character;
                if (comment && character != '\n') {
                    outputCharacter = ' ';
                }
                data.append(outputCharacter);
            }
        }
        if (comment) {
            throw new IOException("Unterminated comment in BALLER.DAT");
        }
        return data.toString();
    }

    private List<Integer> readNumbers(String source) throws IOException {
        List<Integer> numbers = new ArrayList<>();
        Matcher matcher = INTEGER.matcher(source);
        int previousEnd = 0;
        while (matcher.find()) {
            validateSeparator(source, previousEnd, matcher.start());
            try {
                numbers.add(Integer.parseInt(matcher.group()));
            } catch (NumberFormatException exception) {
                throw new IOException("Integer outside the supported range: " + matcher.group(), exception);
            }
            previousEnd = matcher.end();
        }
        validateSeparator(source, previousEnd, source.length());
        return numbers;
    }

    private void validateSeparator(String source, int start, int end) throws IOException {
        for (int i = start; i < end; i++) {
            char character = source.charAt(i);
            if (!Character.isWhitespace(character) && character != ',') {
                throw new IOException("Unexpected character '" + character + "' in BALLER.DAT");
            }
        }
    }

    private static final class Cursor {
        private final List<Integer> numbers;
        private int index;

        private Cursor(List<Integer> numbers) {
            this.numbers = numbers;
        }

        private int next(String expected) throws IOException {
            if (index >= numbers.size()) {
                throw error("Expected " + expected + ", but reached end of file");
            }
            return numbers.get(index++);
        }

        private void requireEnd() throws IOException {
            if (index != numbers.size()) {
                throw error("Data found after " + CastleFileFormat.FILE_TERMINATOR + " file terminator");
            }
        }

        private IOException error(String message) {
            return new IOException(message + " (numeric value " + (index + 1) + ")");
        }
    }
}
