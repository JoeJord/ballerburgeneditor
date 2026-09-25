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
    /**
     * CP437 charset used to decode original BALLER.DAT files.
     */
    public static final Charset CHARSET = Charset.forName("IBM437");
    /**
     * Pattern used to find signed decimal integers in the comment-stripped file.
     */
    private static final Pattern INTEGER = Pattern.compile("[+-]?\\d+");

    /**
     * Reads a BALLER.DAT file and returns its castle document.
     *
     * @param path the BALLER.DAT file path
     * @return the parsed document
     * @throws IOException if the file cannot be read or its text contains malformed or invalid BALLER.DAT data
     */
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

    /**
     * Reads one castle record, beginning with its width and ending after its drawing commands.
     *
     * @param cursor the cursor supplying numeric file values
     * @param width  the castle width read from the file
     * @return the parsed castle
     * @throws IOException if required numeric fields are missing or a drawing command is invalid
     */
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

    /**
     * Reads drawing commands until the castle terminator is reached.
     *
     * @param cursor the cursor supplying numeric file values
     * @return the parsed drawing
     * @throws IOException if a drawing command or one of its required values is missing or invalid
     */
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

    /**
     * Decodes a fill-style command or polygon from its leading numeric marker.
     *
     * @param cursor  the cursor supplying numeric file values
     * @param command the leading value that identifies the drawing command
     * @return the parsed drawing command
     * @throws IOException if a command marker is unknown or its required numeric values are missing
     */
    private DrawingCommand readDrawingCommand(Cursor cursor, int command) throws IOException {
        if (command == CastleFileFormat.FILL_STYLE_COMMAND) {
            return new FillStyle(cursor.next("fill style"), cursor.next("fill index"));
        }
        if (command >= 0) {
            return readPolygon(cursor, command);
        }
        throw cursor.error("Unknown drawing command " + command);
    }

    /**
     * Reads polygon vertices through the polygon terminator.
     *
     * @param cursor the cursor supplying numeric file values
     * @param firstX the first vertex's x-coordinate, already read as the command marker
     * @return the parsed polygon
     * @throws IOException if a vertex coordinate is missing or an unexpected negative marker occurs
     */
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

    /**
     * Reads a fixed number of coordinate pairs.
     *
     * @param cursor the cursor supplying numeric file values
     * @param count  the number of coordinate pairs to read
     * @return the points in file order
     * @throws IOException if any requested coordinate pair is missing
     */
    @SuppressWarnings("SameParameterValue") // Keep this helper reusable for point groups with different fixed sizes.
    private List<Point> readPoints(Cursor cursor, int count) throws IOException {
        List<Point> points = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            points.add(readPoint(cursor));
        }
        return points;
    }

    /**
     * Reads the fixed cannon-position slots and removes unused positions.
     *
     * @param cursor the cursor supplying numeric file values
     * @return the parsed cannons
     * @throws IOException if any fixed cannon-position pair is missing
     */
    private List<Point> readCannons(Cursor cursor) throws IOException {
        Point unusedPosition = new Point(CastleFileFormat.UNUSED_CANNON_COORDINATE, CastleFileFormat.UNUSED_CANNON_COORDINATE);
        List<Point> cannons = readPoints(cursor, CastleFileFormat.CANNON_POSITION_COUNT);
        cannons.removeIf(unusedPosition::equals);
        return cannons;
    }

    /**
     * Reads a fixed number of numeric values with the supplied error description.
     *
     * @param cursor   the cursor supplying numeric file values
     * @param count    the number of values to read
     * @param expected the label used if the input ends before all values are read
     * @return the requested integer values in input order
     * @throws IOException if any requested value is missing
     */
    @SuppressWarnings("SameParameterValue") // Keep this helper reusable for value groups with different fixed sizes.
    private List<Integer> readValues(Cursor cursor, int count, String expected) throws IOException {
        List<Integer> values = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            values.add(cursor.next(expected));
        }
        return values;
    }

    /**
     * Reads one x/y coordinate pair.
     *
     * @param cursor the cursor supplying numeric file values
     * @return the point represented by the next two numeric values
     * @throws IOException if either coordinate value is missing
     */
    private Point readPoint(Cursor cursor) throws IOException {
        return new Point(cursor.next("x coordinate"), cursor.next("y coordinate"));
    }

    /**
     * Reads a point and converts the unused-position marker to an unset point.
     *
     * @param cursor the cursor supplying numeric file values
     * @return the parsed optional point
     * @throws IOException if either coordinate value is missing
     */
    private Point readOptionalPoint(Cursor cursor) throws IOException {
        Point point = readPoint(cursor);
        if (point.x() == CastleFileFormat.UNUSED_CANNON_COORDINATE
                && point.y() == CastleFileFormat.UNUSED_CANNON_COORDINATE) {
            return new Point(Integer.MIN_VALUE, Integer.MIN_VALUE);
        }
        return point;
    }

    /**
     * Reads a width/height pair.
     *
     * @param cursor the cursor supplying numeric file values
     * @return the size represented by the next two numeric values
     * @throws IOException if either dimension value is missing
     */
    private Size readSize(Cursor cursor) throws IOException {
        return new Size(cursor.next("width"), cursor.next("height"));
    }

    /**
     * Replaces BALLER.DAT comments with whitespace and rejects unterminated comments.
     *
     * @param source decoded BALLER.DAT text
     * @return the text with comment contents replaced by whitespace
     * @throws IOException if a comment delimiter is left unmatched
     */
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

    /**
     * Parses integers and validates the separators between them.
     *
     * @param source comment-stripped BALLER.DAT text
     * @return the parsed numbers
     * @throws IOException if a non-separator character occurs outside an integer or an integer is out of range
     */
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

    /**
     * Rejects characters other than whitespace and commas in a separator range.
     *
     * @param source comment-stripped BALLER.DAT text to validate
     * @param start  the start of the separator range
     * @param end    the end of the separator range
     * @throws IOException if the separator range contains a character other than whitespace or a comma
     */
    private void validateSeparator(String source, int start, int end) throws IOException {
        for (int i = start; i < end; i++) {
            char character = source.charAt(i);
            if (!Character.isWhitespace(character) && character != ',') {
                throw new IOException("Unexpected character '" + character + "' in BALLER.DAT");
            }
        }
    }

    /**
     * Reads numeric values sequentially and reports format errors.
     */
    private static final class Cursor {
        /**
         * The parsed numeric values.
         */
        private final List<Integer> numbers;
        /**
         * The next numeric value position.
         */
        private int index;

        /**
         * Creates a cursor over the parsed numeric values.
         *
         * @param numbers numeric values in file order
         */
        private Cursor(List<Integer> numbers) {
            this.numbers = numbers;
        }

        /**
         * Returns the next numeric value, or throws an I/O error if no value remains.
         *
         * @param expected the description of the expected value
         * @return the next numeric value in the input sequence
         * @throws IOException if no numeric value remains in the input sequence
         */
        private int next(String expected) throws IOException {
            if (index >= numbers.size()) {
                throw error("Expected " + expected + ", but reached end of file");
            }
            return numbers.get(index++);
        }

        /**
         * Ensures that no numeric data follows the file terminator.
         *
         * @throws IOException if numeric values remain after the file terminator
         */
        private void requireEnd() throws IOException {
            if (index != numbers.size()) {
                throw error("Data found after " + CastleFileFormat.FILE_TERMINATOR + " file terminator");
            }
        }

        /**
         * Creates an I/O error annotated with the current numeric-value position.
         *
         * @param message the format error to annotate
         * @return an I/O exception annotated with the current numeric value position
         */
        private IOException error(String message) {
            return new IOException(message + " (numeric value " + (index + 1) + ")");
        }
    }
}
