package at.josefjordan.ballerburg.editor.ui.editor.castle;

import at.josefjordan.ballerburg.editor.model.CastleDocument.Castle;
import at.josefjordan.ballerburg.editor.model.CastleDocument.DrawingCommand;
import at.josefjordan.ballerburg.editor.model.CastleDocument.FillStyle;
import at.josefjordan.ballerburg.editor.model.CastleDocument.Point;
import at.josefjordan.ballerburg.editor.model.CastleDocument.Polygon;
import at.josefjordan.ballerburg.editor.model.CastleFileFormat;

import java.util.ArrayList;
import java.util.List;

/**
 * Applies immutable polygon and drawing-command edits to castle snapshots.
 */
public final class CastlePolygonEditor {
    /**
     * Appends a polygon with the fill style corresponding to its creation category.
     *
     * @param castle      source castle
     * @param points      ordered polygon vertices
     * @param polygonType polygon fill category
     * @return castle snapshot containing the appended fill-style and polygon commands
     */
    public Castle add(Castle castle, List<Point> points, CastlePolygonType polygonType) {
        List<DrawingCommand> drawing = new ArrayList<>(castle.drawing());
        drawing.add(new FillStyle(CastleFileFormat.FILL_STYLE_PATTERN, polygonType.fillIndex()));
        drawing.add(new Polygon(points));
        return withDrawing(castle, drawing);
    }

    /**
     * Replaces one polygon vertex.
     *
     * @param castle       source castle
     * @param polygonIndex zero-based index among polygon drawing commands
     * @param pointIndex   zero-based vertex index within the polygon
     * @param newPoint     replacement vertex
     * @return castle snapshot with the vertex replaced
     */
    public Castle updatePoint(Castle castle, int polygonIndex, int pointIndex, Point newPoint) {
        List<DrawingCommand> drawing = new ArrayList<>();
        int currentPolygonIndex = 0;
        for (DrawingCommand command : castle.drawing()) {
            if (command instanceof Polygon polygon) {
                if (currentPolygonIndex == polygonIndex) {
                    List<Point> points = new ArrayList<>(polygon.points());
                    points.set(pointIndex, newPoint);
                    drawing.add(new Polygon(points));
                } else {
                    drawing.add(polygon);
                }
                currentPolygonIndex++;
            } else {
                drawing.add(command);
            }
        }
        return withDrawing(castle, drawing);
    }

    /**
     * Replaces all vertices of one polygon.
     *
     * @param castle       source castle
     * @param polygonIndex zero-based index among polygon drawing commands
     * @param newPoints    replacement polygon vertices
     * @return castle snapshot with the polygon replaced
     */
    public Castle updatePoints(Castle castle, int polygonIndex, List<Point> newPoints) {
        List<DrawingCommand> drawing = new ArrayList<>();
        int currentPolygonIndex = 0;
        for (DrawingCommand command : castle.drawing()) {
            if (command instanceof Polygon polygon) {
                drawing.add(currentPolygonIndex == polygonIndex ? new Polygon(newPoints) : polygon);
                currentPolygonIndex++;
            } else {
                drawing.add(command);
            }
        }
        return withDrawing(castle, drawing);
    }

    /**
     * Removes every drawing command referring to the supplied polygon instance.
     *
     * @param castle  source castle
     * @param polygon polygon instance to remove
     * @return castle snapshot without the polygon commands
     */
    public Castle remove(Castle castle, Polygon polygon) {
        List<DrawingCommand> drawing = new ArrayList<>();
        for (DrawingCommand command : castle.drawing()) {
            if (!(command instanceof Polygon candidate) || candidate != polygon) {
                drawing.add(command);
            }
        }
        return withDrawing(castle, drawing);
    }

    /**
     * Finds a polygon's index by object identity.
     *
     * @param castle        castle whose drawing is searched
     * @param targetPolygon polygon instance to find
     * @return zero-based index among polygon commands, or {@code -1} when absent
     */
    public int indexOf(Castle castle, Polygon targetPolygon) {
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
     * Returns a polygon by its polygon-only drawing index.
     *
     * @param castle       castle whose drawing is searched
     * @param polygonIndex zero-based index among polygon commands
     * @return polygon at that index, or {@code null} when it is absent
     */
    public Polygon polygonAt(Castle castle, int polygonIndex) {
        int index = 0;
        for (DrawingCommand command : castle.drawing()) {
            if (command instanceof Polygon polygon) {
                if (index == polygonIndex) {
                    return polygon;
                }
                index++;
            }
        }
        return null;
    }

    /**
     * Creates a castle snapshot with the supplied drawing commands and all other data unchanged.
     *
     * @param castle  source castle whose non-drawing data is retained
     * @param drawing replacement ordered drawing-command list
     * @return a new castle snapshot containing the replacement drawing
     */
    private Castle withDrawing(Castle castle, List<DrawingCommand> drawing) {
        return new Castle(castle.width(), castle.cannons(), castle.throne(), castle.windVane(),
                castle.money(), castle.powder(), castle.balls(), castle.moneyRoom(), castle.powderRoom(),
                castle.ballRoom(), castle.resources(), castle.reserved(), drawing);
    }
}
