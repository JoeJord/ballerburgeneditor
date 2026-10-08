package at.josefjordan.ballerburg.editor.ui.editor;

import at.josefjordan.ballerburg.editor.model.CastleDocument;
import at.josefjordan.ballerburg.editor.model.CastleDocument.Castle;
import at.josefjordan.ballerburg.editor.model.CastleDocument.Point;
import at.josefjordan.ballerburg.editor.model.CastleDocument.Size;
import at.josefjordan.ballerburg.editor.model.CastleFileFormat;
import hgb.lib.HGBaseText;

import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Validates castle data and produces localized findings for the editor.
 */
final class CastleValidator {
    /**
     * Maximum permitted horizontal span of positioned castle elements, in castle coordinates.
     */
    private static final int MAX_CASTLE_ELEMENT_WIDTH = 200;
    /**
     * Canonical dimensions of a throne.
     */
    private static final Size THRONE_SIZE = new Size(CastleFileFormat.THRONE_WIDTH, CastleFileFormat.THRONE_HEIGHT);
    /**
     * Canonical dimensions of a cannon.
     */
    private static final Size CANNON_SIZE = new Size(CastleFileFormat.CANNON_WIDTH, CastleFileFormat.CANNON_HEIGHT);
    /**
     * Canonical dimensions of a wind vane.
     */
    private static final Size WIND_VANE_SIZE = new Size(CastleFileFormat.WIND_VANE_WIDTH, CastleFileFormat.WIND_VANE_HEIGHT);

    /**
     * Returns all validation findings for a castle, or an empty list when there is no castle.
     *
     * @param castle castle to validate
     * @return validation findings in display order
     */
    List<ProblemsPanel.Problem> validate(Castle castle) {
        List<ProblemsPanel.Problem> problems = new ArrayList<>();
        if (castle == null) {
            return problems;
        }

        boolean wallPresent = hasWall(castle);
        addMissingProblem(problems, hasPoint(castle.throne()), "problems.missing_throne");
        addMissingProblem(problems, hasPoint(castle.money()), "problems.missing_money");
        addMissingProblem(problems, hasPoint(castle.balls()), "problems.missing_balls");
        addMissingProblem(problems, hasPoint(castle.powder()), "problems.missing_powder");
        addMissingProblem(problems, hasPoint(castle.windVane()), "problems.missing_windvane");
        addMissingProblem(problems, wallPresent, "problems.missing_wall");
        addMissingProblem(problems, hasElementWidth(castle), "problems.elements_too_wide");
        if (wallPresent) {
            addMissingProblem(problems, isInsideWall(castle, castle.throne(), THRONE_SIZE), "problems.throne_outside_wall");
            addMissingProblem(problems, isInsideWall(castle, castle.money(), castle.moneyRoom()), "problems.money_outside_wall");
            addMissingProblem(problems, isInsideWall(castle, castle.balls(), castle.ballRoom()), "problems.balls_outside_wall");
            addMissingProblem(problems, isInsideWall(castle, castle.powder(), castle.powderRoom()), "problems.powder_outside_wall");
        }

        addOverlapProblem(problems, castle.throne(), THRONE_SIZE, castle.money(), castle.moneyRoom(), "problems.overlap_throne_money");
        addOverlapProblem(problems, castle.throne(), THRONE_SIZE, castle.balls(), castle.ballRoom(), "problems.overlap_throne_balls");
        addOverlapProblem(problems, castle.throne(), THRONE_SIZE, castle.powder(), castle.powderRoom(), "problems.overlap_throne_powder");
        addOverlapProblem(problems, castle.money(), castle.moneyRoom(), castle.balls(), castle.ballRoom(), "problems.overlap_money_balls");
        addOverlapProblem(problems, castle.money(), castle.moneyRoom(), castle.powder(), castle.powderRoom(), "problems.overlap_money_powder");
        addOverlapProblem(problems, castle.balls(), castle.ballRoom(), castle.powder(), castle.powderRoom(), "problems.overlap_balls_powder");
        addCannonOverlapProblems(problems, castle);
        addWindVaneOverlapProblems(problems, castle);
        addZeroWarning(problems, hasPoint(castle.money()), castle.resources().money(), "problems.zero_money");
        addZeroWarning(problems, hasPoint(castle.powder()), castle.resources().powder(), "problems.zero_powder");
        addZeroWarning(problems, hasPoint(castle.balls()), castle.resources().balls(), "problems.zero_balls");
        addZeroWarning(problems, castle.resources().people() >= 0, castle.resources().people(), "problems.zero_people");
        if (castle.cannons().isEmpty()) {
            problems.add(problem(ProblemsPanel.ProblemSeverity.WARNING, "problems.no_cannons"));
        }
        return problems;
    }

    /**
     * Determines whether the drawing contains a polygon using the masonry fill style.
     *
     * @param castle castle whose drawing commands are inspected
     * @return {@code true} if at least one polygon is drawn as masonry
     */
    private boolean hasWall(Castle castle) {
        int fillStyle = CastleFileFormat.FILL_STYLE_PATTERN;
        int fillIndex = CastleFileFormat.FILL_INDEX_MASONRY;
        for (CastleDocument.DrawingCommand command : castle.drawing()) {
            if (command instanceof CastleDocument.FillStyle(int style, int index)) {
                fillStyle = style;
                fillIndex = index;
            } else if (command instanceof CastleDocument.Polygon
                    && isMasonry(fillStyle, fillIndex)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks whether the horizontal span of all positioned castle elements is within the allowed width.
     *
     * @param castle castle whose element bounds are measured
     * @return {@code true} if there are no positioned elements or their span does not exceed the limit
     */
    private boolean hasElementWidth(Castle castle) {
        int minimumX = Integer.MAX_VALUE;
        int maximumX = Integer.MIN_VALUE;
        for (CastleDocument.DrawingCommand command : castle.drawing()) {
            if (command instanceof CastleDocument.Polygon(List<Point> points)) {
                for (Point point : points) {
                    minimumX = Math.min(minimumX, point.x());
                    maximumX = Math.max(maximumX, point.x());
                }
            }
        }
        if (hasPoint(castle.throne())) {
            minimumX = Math.min(minimumX, castle.throne().x());
            maximumX = Math.max(maximumX, castle.throne().x() + CastleFileFormat.THRONE_WIDTH);
        }
        if (hasPoint(castle.money())) {
            minimumX = Math.min(minimumX, castle.money().x());
            maximumX = Math.max(maximumX, castle.money().x() + castle.moneyRoom().width());
        }
        if (hasPoint(castle.powder())) {
            minimumX = Math.min(minimumX, castle.powder().x());
            maximumX = Math.max(maximumX, castle.powder().x() + castle.powderRoom().width());
        }
        if (hasPoint(castle.balls())) {
            minimumX = Math.min(minimumX, castle.balls().x());
            maximumX = Math.max(maximumX, castle.balls().x() + castle.ballRoom().width());
        }
        if (hasPoint(castle.windVane())) {
            minimumX = Math.min(minimumX, castle.windVane().x());
            maximumX = Math.max(maximumX, castle.windVane().x() + CastleFileFormat.WIND_VANE_WIDTH);
        }
        for (Point cannon : castle.cannons()) {
            minimumX = Math.min(minimumX, cannon.x());
            maximumX = Math.max(maximumX, cannon.x() + CastleFileFormat.CANNON_WIDTH);
        }
        return minimumX == Integer.MAX_VALUE || maximumX - minimumX <= MAX_CASTLE_ELEMENT_WIDTH;
    }

    /**
     * Adds findings for each cannon overlapping another castle element or polygon.
     *
     * @param problems findings list to append to
     * @param castle   castle whose cannons are checked
     */
    private void addCannonOverlapProblems(List<ProblemsPanel.Problem> problems, Castle castle) {
        for (int index = 0; index < castle.cannons().size(); index++) {
            Point cannon = castle.cannons().get(index);
            addOverlapProblem(problems, cannon, CANNON_SIZE, castle.throne(), THRONE_SIZE, "problems.overlap_cannon_throne");
            addOverlapProblem(problems, cannon, CANNON_SIZE, castle.money(), castle.moneyRoom(), "problems.overlap_cannon_money");
            addOverlapProblem(problems, cannon, CANNON_SIZE, castle.balls(), castle.ballRoom(), "problems.overlap_cannon_balls");
            addOverlapProblem(problems, cannon, CANNON_SIZE, castle.powder(), castle.powderRoom(), "problems.overlap_cannon_powder");
            addPolygonOverlapProblems(problems, cannon, CANNON_SIZE, castle, "problems.overlap_cannon_wall", "problems.overlap_cannon_roof");
            for (int otherIndex = index + 1; otherIndex < castle.cannons().size(); otherIndex++) {
                addOverlapProblem(problems, cannon, CANNON_SIZE, castle.cannons().get(otherIndex), CANNON_SIZE, "problems.overlap_cannon_cannon");
            }
        }
    }

    /**
     * Adds findings for the wind vane overlapping another castle element or polygon.
     *
     * @param problems findings list to append to
     * @param castle   castle whose wind vane is checked
     */
    private void addWindVaneOverlapProblems(List<ProblemsPanel.Problem> problems, Castle castle) {
        Point windVane = castle.windVane();
        addOverlapProblem(problems, windVane, WIND_VANE_SIZE, castle.throne(), THRONE_SIZE, "problems.overlap_windvane_throne");
        addOverlapProblem(problems, windVane, WIND_VANE_SIZE, castle.money(), castle.moneyRoom(), "problems.overlap_windvane_money");
        addOverlapProblem(problems, windVane, WIND_VANE_SIZE, castle.balls(), castle.ballRoom(), "problems.overlap_windvane_balls");
        addOverlapProblem(problems, windVane, WIND_VANE_SIZE, castle.powder(), castle.powderRoom(), "problems.overlap_windvane_powder");
        for (Point cannon : castle.cannons()) {
            addOverlapProblem(problems, windVane, WIND_VANE_SIZE, cannon, CANNON_SIZE, "problems.overlap_windvane_cannon");
        }
        addPolygonOverlapProblems(problems, windVane, WIND_VANE_SIZE, castle, "problems.overlap_windvane_wall", "problems.overlap_windvane_roof");
    }

    /**
     * Adds a finding for each drawing polygon intersected by the supplied positioned element.
     *
     * @param problems    findings list to append to
     * @param position    lower-left position of the element in castle coordinates
     * @param size        element dimensions in castle coordinates
     * @param castle      castle whose drawing commands are checked
     * @param wallTextKey localization key used when an intersected polygon is masonry
     * @param roofTextKey localization key used when an intersected polygon is not masonry
     */
    private void addPolygonOverlapProblems(List<ProblemsPanel.Problem> problems, Point position, Size size, Castle castle, String wallTextKey, String roofTextKey) {
        if (!hasPoint(position)) {
            return;
        }
        Rectangle2D rectangle = rectangle(position, size);
        int fillStyle = CastleFileFormat.FILL_STYLE_PATTERN;
        int fillIndex = CastleFileFormat.FILL_INDEX_MASONRY;
        for (CastleDocument.DrawingCommand command : castle.drawing()) {
            if (command instanceof CastleDocument.FillStyle(int style, int index)) {
                fillStyle = style;
                fillIndex = index;
            } else if (command instanceof CastleDocument.Polygon polygon && polygonIntersects(polygon, rectangle)) {
                problems.add(problem(ProblemsPanel.ProblemSeverity.ERROR, isMasonry(fillStyle, fillIndex) ? wallTextKey : roofTextKey));
            }
        }
    }

    /**
     * Checks whether a positioned area is fully contained in any masonry polygon.
     *
     * @param castle   castle whose drawing commands are checked
     * @param position lower-left position of the area in castle coordinates
     * @param size     area dimensions in castle coordinates
     * @return {@code true} if the area is unset or is contained in a masonry polygon
     */
    private boolean isInsideWall(Castle castle, Point position, Size size) {
        if (!hasPoint(position)) {
            return true;
        }
        Rectangle2D area = rectangle(position, size);
        int fillStyle = CastleFileFormat.FILL_STYLE_PATTERN;
        int fillIndex = CastleFileFormat.FILL_INDEX_MASONRY;
        for (CastleDocument.DrawingCommand command : castle.drawing()) {
            if (command instanceof CastleDocument.FillStyle(int style, int index)) {
                fillStyle = style;
                fillIndex = index;
            } else if (command instanceof CastleDocument.Polygon polygon && isMasonry(fillStyle, fillIndex) && polygonContains(polygon, area)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Adds an error finding when two positioned areas overlap.
     *
     * @param problems       findings list to append to
     * @param firstPosition  lower-left position of the first area
     * @param firstSize      dimensions of the first area
     * @param secondPosition lower-left position of the second area
     * @param secondSize     dimensions of the second area
     * @param textKey        localization key for the overlap finding
     */
    private void addOverlapProblem(List<ProblemsPanel.Problem> problems, Point firstPosition, Size firstSize, Point secondPosition, Size secondSize, String textKey) {
        if (hasPoint(firstPosition) && hasPoint(secondPosition) && rectangle(firstPosition, firstSize).intersects(rectangle(secondPosition, secondSize))) {
            problems.add(problem(ProblemsPanel.ProblemSeverity.ERROR, textKey));
        }
    }

    /**
     * Adds an error finding when a required castle element is absent.
     *
     * @param problems findings list to append to
     * @param present  whether the required element is present
     * @param textKey  localization key for the missing-element finding
     */
    private void addMissingProblem(List<ProblemsPanel.Problem> problems, boolean present, String textKey) {
        if (!present) {
            problems.add(problem(ProblemsPanel.ProblemSeverity.ERROR, textKey));
        }
    }

    /**
     * Adds a warning when an available resource has a zero starting value.
     *
     * @param problems    findings list to append to
     * @param areaPresent whether the resource storage area is present
     * @param value       starting quantity of the resource
     * @param textKey     localization key for the zero-value warning
     */
    private void addZeroWarning(List<ProblemsPanel.Problem> problems, boolean areaPresent, int value, String textKey) {
        if (areaPresent && value == 0) {
            problems.add(problem(ProblemsPanel.ProblemSeverity.WARNING, textKey));
        }
    }

    /**
     * Creates a localized validation finding.
     *
     * @param severity severity assigned to the finding
     * @param textKey  localization key for its description
     * @return the localized problem
     */
    private ProblemsPanel.Problem problem(ProblemsPanel.ProblemSeverity severity, String textKey) {
        return new ProblemsPanel.Problem(severity, HGBaseText.getText(textKey));
    }

    /**
     * Creates the rectangle occupied by an area in castle coordinates.
     *
     * @param position lower-left position of the area
     * @param size     width and height of the area
     * @return the corresponding axis-aligned rectangle
     */
    private Rectangle2D rectangle(Point position, Size size) {
        return new Rectangle2D.Double(position.x(), position.y(), size.width(), size.height());
    }

    /**
     * Checks whether a point uses ordinary coordinates rather than the unset-position sentinel.
     *
     * @param point point to inspect
     * @return {@code true} if both coordinates are set
     */
    private boolean hasPoint(Point point) {
        return point.x() != Integer.MIN_VALUE && point.y() != Integer.MIN_VALUE;
    }

    /**
     * Checks whether a fill-style pair identifies the masonry pattern.
     *
     * @param fillStyle encoded fill style
     * @param fillIndex encoded fill index
     * @return {@code true} if the pair represents masonry
     */
    private boolean isMasonry(int fillStyle, int fillIndex) {
        return fillStyle == CastleFileFormat.FILL_STYLE_PATTERN && fillIndex == CastleFileFormat.FILL_INDEX_MASONRY;
    }

    /**
     * Checks whether a polygon contains the supplied rectangle.
     *
     * @param polygon polygon to inspect
     * @param area    rectangle whose full area must be contained
     * @return {@code true} if the polygon contains the rectangle
     */
    private boolean polygonContains(CastleDocument.Polygon polygon, Rectangle2D area) {
        return polygonPath(polygon).contains(area);
    }

    /**
     * Checks whether a polygon intersects the supplied rectangle.
     *
     * @param polygon   polygon to inspect
     * @param rectangle rectangle to test for intersection
     * @return {@code true} if the polygon intersects the rectangle
     */
    private boolean polygonIntersects(CastleDocument.Polygon polygon, Rectangle2D rectangle) {
        return polygonPath(polygon).intersects(rectangle);
    }

    /**
     * Builds a closed geometric path from a polygon's castle-coordinate vertices.
     *
     * @param polygon polygon whose vertices define the path
     * @return a closed path in castle coordinates
     */
    private Path2D polygonPath(CastleDocument.Polygon polygon) {
        Path2D path = new Path2D.Double();
        Point first = polygon.points().getFirst();
        path.moveTo(first.x(), first.y());
        for (int index = 1; index < polygon.points().size(); index++) {
            Point point = polygon.points().get(index);
            path.lineTo(point.x(), point.y());
        }
        path.closePath();
        return path;
    }
}
