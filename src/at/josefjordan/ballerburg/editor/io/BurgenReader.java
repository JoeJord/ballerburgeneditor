package at.josefjordan.ballerburg.editor.io;

import at.josefjordan.ballerburg.editor.model.BurgDocument;
import at.josefjordan.ballerburg.editor.model.BurgDocument.Castle;
import at.josefjordan.ballerburg.editor.model.BurgDocument.DrawingCommand;
import at.josefjordan.ballerburg.editor.model.BurgDocument.FillStyle;
import at.josefjordan.ballerburg.editor.model.BurgDocument.Point;
import at.josefjordan.ballerburg.editor.model.BurgDocument.Polygon;
import at.josefjordan.ballerburg.editor.model.BurgDocument.Resources;
import at.josefjordan.ballerburg.editor.model.BurgDocument.Size;
import at.josefjordan.ballerburg.editor.model.BurgenFileFormat;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads the CP437 encoded, comment-aware BALLER.DAT format. */
public final class BurgenReader {
    public static final Charset CHARSET = Charset.forName("IBM437");
    private static final Pattern INTEGER = Pattern.compile("[+-]?\\d+");

    public BurgDocument read(Path path) throws IOException {
        String source = Files.readString(path, CHARSET);
        Cursor cursor = new Cursor(readNumbers(removeComments(source)));
        List<Castle> castles = new ArrayList<>();
        while (true) {
            int width = cursor.next("castle width or " + BurgenFileFormat.FILE_TERMINATOR + " file terminator");
            if (width == BurgenFileFormat.FILE_TERMINATOR) {
                cursor.requireEnd();
                return new BurgDocument(castles);
            }
            if (width < 0) {
                throw cursor.error("Invalid castle width " + width);
            }
            castles.add(readCastle(cursor, width));
        }
    }

    private Castle readCastle(Cursor cursor, int width) throws IOException {
        List<Point> cannons = readCannons(cursor);
        Point throne = readPoint(cursor);
        Point flag = readPoint(cursor);
        Point money = readPoint(cursor);
        Point powder = readPoint(cursor);
        Point balls = readPoint(cursor);
        Size moneyRoom = readSize(cursor);
        Size powderRoom = readSize(cursor);
        Size ballRoom = readSize(cursor);
        Resources resources = new Resources(cursor.next("start money"), cursor.next("start powder"),
                cursor.next("start balls"), cursor.next("start people"));
        List<Integer> reserved = readValues(cursor, BurgenFileFormat.RESERVED_VALUE_COUNT, "reserved value");
        List<DrawingCommand> drawing = readDrawing(cursor);
        return new Castle(width, cannons, throne, flag, money, powder, balls, moneyRoom, powderRoom,
                ballRoom, resources, reserved, drawing);
    }

    private List<DrawingCommand> readDrawing(Cursor cursor) throws IOException {
        List<DrawingCommand> commands = new ArrayList<>();
        while (true) {
            int command = cursor.next("drawing command");
            if (command == BurgenFileFormat.CASTLE_TERMINATOR) {
                return commands;
            }
            if (command == BurgenFileFormat.FILL_STYLE_COMMAND) {
                commands.add(new FillStyle(cursor.next("fill style"), cursor.next("fill index")));
            } else if (command >= 0) {
                List<Point> points = new ArrayList<>();
                points.add(new Point(command, cursor.next("polygon y coordinate")));
                while (true) {
                    int x = cursor.next("polygon x coordinate or "
                            + BurgenFileFormat.POLYGON_TERMINATOR + " terminator");
                    if (x == BurgenFileFormat.POLYGON_TERMINATOR) {
                        break;
                    }
                    if (x < 0) {
                        throw cursor.error("Unexpected polygon marker " + x);
                    }
                    points.add(new Point(x, cursor.next("polygon y coordinate")));
                }
                commands.add(new Polygon(points));
            } else {
                throw cursor.error("Unknown drawing command " + command);
            }
        }
    }

    private List<Point> readPoints(Cursor cursor, int count) throws IOException {
        List<Point> points = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            points.add(readPoint(cursor));
        }
        return points;
    }

    private List<Point> readCannons(Cursor cursor) throws IOException {
        Point unusedPosition = new Point(-1, -1);
        List<Point> cannons = readPoints(cursor, BurgenFileFormat.CANNON_POSITION_COUNT);
        cannons.removeIf(unusedPosition::equals);
        return cannons;
    }

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
                data.append(comment ? (character == '\n' ? '\n' : ' ') : character);
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
                throw error("Data found after " + BurgenFileFormat.FILE_TERMINATOR + " file terminator");
            }
        }

        private IOException error(String message) {
            return new IOException(message + " (numeric value " + (index + 1) + ")");
        }
    }
}
