package at.josefjordan.ballerburg.editor.ui.editor.castle;

import at.josefjordan.ballerburg.editor.model.CastleDocument.Castle;
import at.josefjordan.ballerburg.editor.model.CastleDocument.DrawingCommand;
import at.josefjordan.ballerburg.editor.model.CastleDocument.FillStyle;
import at.josefjordan.ballerburg.editor.model.CastleDocument.Point;
import at.josefjordan.ballerburg.editor.model.CastleDocument.Polygon;
import at.josefjordan.ballerburg.editor.model.CastleDocument.Size;
import at.josefjordan.ballerburg.editor.model.CastleFileFormat;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.RenderingHints;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.TexturePaint;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Renders a snapshot of a castle view without owning or modifying editor state.
 */
final class CastleRenderer {
    /**
     * Grid-line interval in castle-coordinate units.
     */
    private static final int GRID_SPACING = 1;
    /**
     * Color used for grid lines.
     */
    private static final Color COLOR_GRID = new Color(210, 210, 210);
    /**
     * Color used for selected objects and vertices.
     */
    private static final Color COLOR_SELECTED = new Color(255, 255, 0);
    /**
     * Color used for unselected polygon vertices and resize handles.
     */
    private static final Color COLOR_HIGHLIGHTED = new Color(255, 100, 100);
    /**
     * Outline color used for castle polygons.
     */
    private static final Color COLOR_OUTLINE = new Color(45, 45, 45);
    /**
     * Fallback fill color for unrecognized polygon fill styles.
     */
    private static final Color COLOR_FILL_DEFAULT = new Color(120, 130, 140);
    /**
     * Brick color used by the monochrome masonry fill pattern.
     */
    private static final Color COLOR_PATTERN_BRICK = new Color(35, 35, 35);
    /**
     * Mortar color used by the monochrome fill patterns.
     */
    private static final Color COLOR_PATTERN_MORTAR = new Color(245, 245, 245);
    /**
     * Pattern raster resolution matching the view's pixels per castle unit at 100% zoom.
     */
    private static final int PATTERN_PIXELS_PER_UNIT = 4;
    /**
     * Logical pattern width and roof-repeat height, in castle units.
     */
    private static final int PATTERN_PERIOD = 12;
    /**
     * Logical height of the masonry pattern's two brick courses, in castle units.
     */
    private static final int MASONRY_PATTERN_HEIGHT = 6;
    /**
     * Repeating brick pattern with alternating, offset vertical joints.
     */
    private static final BufferedImage MASONRY_PATTERN = createMasonryPattern();
    /**
     * Repeating diagonal tile pattern used for roofs.
     */
    private static final BufferedImage ROOF_PATTERN = createRoofPattern();

    /**
     * Draws the grid, castle, selection markers, and in-progress polygon preview.
     *
     * @param graphics graphics context owned by the caller
     * @param state    immutable snapshot of the view state needed for painting
     */
    void draw(Graphics2D graphics, RenderState state) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        if (state.castle() != null) {
            if (state.gridVisible()) {
                drawGrid(graphics, state);
            }
            drawCastle(graphics, state);
        }
        if (state.polygonCreationMode()) {
            drawPolygonCreation(graphics, state);
        }
    }

    /**
     * Draws grid lines across the visible view area.
     *
     * @param graphics graphics context to draw with
     * @param state    view state containing the transform and viewport dimensions
     */
    private void drawGrid(Graphics2D graphics, RenderState state) {
        double left = state.left();
        double bottom = state.bottom();
        double scale = state.scale();
        graphics.setColor(COLOR_GRID);
        graphics.setStroke(new BasicStroke(1.0f));
        int firstX = (int) Math.floor(-left / scale / GRID_SPACING) * GRID_SPACING;
        int lastX = (int) Math.ceil((state.width() - left) / scale / GRID_SPACING) * GRID_SPACING;
        int firstY = (int) Math.floor((bottom - state.height()) / scale / GRID_SPACING) * GRID_SPACING;
        int lastY = (int) Math.ceil(bottom / scale / GRID_SPACING) * GRID_SPACING;
        for (int x = firstX; x <= lastX; x += GRID_SPACING) {
            double screenX = left + x * scale;
            graphics.draw(new Line2D.Double(screenX, 0, screenX, state.height()));
        }
        for (int y = firstY; y <= lastY; y += GRID_SPACING) {
            double screenY = bottom - y * scale;
            graphics.draw(new Line2D.Double(0, screenY, state.width(), screenY));
        }
    }

    /**
     * Draws castle geometry and objects in their required back-to-front order.
     *
     * @param graphics graphics context to draw with
     * @param state    view state containing castle geometry and selection state
     */
    private void drawCastle(Graphics2D graphics, RenderState state) {
        drawCastlePolygons(graphics, state);
        drawThrone(graphics, state);
        drawResourceAreas(graphics, state);
        drawCannons(graphics, state);
        drawResourceContents(graphics, state);
        drawWindVane(graphics, state);
        drawEdgeHover(graphics, state.edgeHoverMarker());
    }

    /**
     * Draws polygons while applying fill-style commands in drawing order.
     *
     * @param graphics graphics context to draw with
     * @param state    view state containing ordered drawing commands
     */
    private void drawCastlePolygons(Graphics2D graphics, RenderState state) {
        Paint fill = paintFor(new FillStyle(CastleFileFormat.FILL_STYLE_PATTERN,
                CastleFileFormat.FILL_INDEX_MASONRY), state);
        graphics.setStroke(new BasicStroke(1.0f));
        for (DrawingCommand command : state.castle().drawing()) {
            if (command instanceof FillStyle style) {
                fill = paintFor(style, state);
            } else if (command instanceof Polygon polygon) {
                drawPolygon(graphics, polygon, fill, state);
            }
        }
    }

    /**
     * Fills and outlines a polygon, then marks its vertices when it is selected.
     *
     * @param graphics graphics context to draw with
     * @param polygon  polygon to render
     * @param fill     fill paint active for the polygon
     * @param state    view transform and polygon-selection state
     */
    private void drawPolygon(Graphics2D graphics, Polygon polygon, Paint fill, RenderState state) {
        Path2D path = createPolygonPath(polygon.points(), state.left(), state.bottom(), state.scale(), true);
        graphics.setPaint(fill);
        graphics.fill(path);
        graphics.setColor(COLOR_OUTLINE);
        graphics.draw(path);
        if (polygon == state.selectedPolygon()) {
            drawSelectedPolygonPoints(graphics, polygon, state);
        }
    }

    /**
     * Draws the selected polygon's vertices with a distinct marker for the selected vertex.
     *
     * @param graphics graphics context to draw with
     * @param polygon  selected polygon whose vertices are marked
     * @param state    view transform and selected-vertex state
     */
    private void drawSelectedPolygonPoints(Graphics2D graphics, Polygon polygon, RenderState state) {
        for (int index = 0; index < polygon.points().size(); index++) {
            Point point = polygon.points().get(index);
            double screenX = state.left() + point.x() * state.scale();
            double screenY = state.bottom() - point.y() * state.scale();
            if (index == state.selectedPointIndex()) {
                graphics.setColor(COLOR_SELECTED);
                graphics.fill(new Rectangle2D.Double(screenX - 4, screenY - 4, 8, 8));
            } else {
                graphics.setColor(COLOR_HIGHLIGHTED);
                graphics.fill(new Rectangle2D.Double(screenX - 3, screenY - 3, 6, 6));
            }
        }
    }

    /**
     * Draws the throne's backing area, selection outline, and king sprite when positioned.
     *
     * @param graphics graphics context to draw with
     * @param state    view state containing the throne position and selection
     */
    private void drawThrone(Graphics2D graphics, RenderState state) {
        Castle castle = state.castle();
        Point throne = castle.throne();
        if (!isPositioned(throne)) {
            return;
        }
        Size size = new Size(CastleFileFormat.THRONE_WIDTH, CastleFileFormat.THRONE_HEIGHT);
        drawArea(graphics, throne, size, state);
        if (state.selectedThrone()) {
            Rectangle2D throneBounds = new Rectangle2D.Double(
                    state.left() + throne.x() * state.scale(),
                    state.bottom() - (throne.y() + size.height()) * state.scale(),
                    size.width() * state.scale(), size.height() * state.scale());
            drawSelectionOutline(graphics, throneBounds);
        }
        drawSprite(graphics, Sprites.KING, throne, state);
    }

    /**
     * Draws all resource-storage area backgrounds and any selected area's resize handles.
     *
     * @param graphics graphics context to draw with
     * @param state    view state containing storage geometry and selection
     */
    private void drawResourceAreas(Graphics2D graphics, RenderState state) {
        Castle castle = state.castle();
        drawResourceArea(graphics, castle.money(), castle.moneyRoom(), ResourceArea.MONEY, state);
        drawResourceArea(graphics, castle.powder(), castle.powderRoom(), ResourceArea.POWDER, state);
        drawResourceArea(graphics, castle.balls(), castle.ballRoom(), ResourceArea.BALLS, state);
    }

    /**
     * Draws one storage-area background and, when selected, its resize handles.
     *
     * @param graphics graphics context to draw with
     * @param position area's lower-left position
     * @param size     area's dimensions
     * @param area     resource-area identity
     * @param state    view transform and current selection state
     */
    private void drawResourceArea(Graphics2D graphics, Point position, Size size, ResourceArea area, RenderState state) {
        drawArea(graphics, position, size, state);
        if (state.selectedResourceArea() == area) {
            drawCornerPoints(graphics, position, size, state);
        }
    }

    /**
     * Draws all cannon sprites and outlines the selected cannon.
     *
     * @param graphics graphics context to draw with
     * @param state    view state containing cannon positions and selection
     */
    private void drawCannons(Graphics2D graphics, RenderState state) {
        for (int index = 0; index < state.castle().cannons().size(); index++) {
            Point cannon = state.castle().cannons().get(index);
            drawSprite(graphics, Sprites.CANNON, cannon, state);
            if (index == state.selectedCannonIndex()) {
                drawSelectionOutline(graphics,
                        CastleViewPanel.calculateCannonRectangle(cannon, state.left(), state.bottom(), state.scale()));
            }
        }
    }

    /**
     * Draws occupied resource sprites and translucent placeholders for unoccupied slots.
     *
     * @param graphics graphics context to draw with
     * @param state    view state containing resource quantities and storage geometry
     */
    private void drawResourceContents(Graphics2D graphics, RenderState state) {
        Castle castle = state.castle();
        drawResources(graphics, new ResourceDisplay(Sprites.MONEY, castle.money(), castle.moneyRoom(),
                CastleFileFormat.MONEY_WIDTH, CastleFileFormat.MONEY_HEIGHT,
                divideRoundedUp(castle.resources().money(), CastleFileFormat.MONEY_PER_UNIT)), state);
        drawResources(graphics, new ResourceDisplay(Sprites.POWDER, castle.powder(), castle.powderRoom(),
                CastleFileFormat.POWDER_WIDTH, CastleFileFormat.POWDER_HEIGHT,
                divideRoundedUp(castle.resources().powder(), CastleFileFormat.POWDER_PER_UNIT)), state);
        drawResources(graphics, new ResourceDisplay(Sprites.BALL, castle.balls(), castle.ballRoom(),
                CastleFileFormat.CANNONBALL_WIDTH, CastleFileFormat.CANNONBALL_HEIGHT,
                castle.resources().balls()), state);
    }

    /**
     * Draws the wind-vane sprite and its selection outline when positioned.
     *
     * @param graphics graphics context to draw with
     * @param state    view state containing the wind-vane position and selection
     */
    private void drawWindVane(Graphics2D graphics, RenderState state) {
        Point windVane = state.castle().windVane();
        if (!isPositioned(windVane)) {
            return;
        }
        drawSprite(graphics, Sprites.WIND_VANE, windVane, state);
        if (state.selectedWindVane()) {
            drawSelectionOutline(graphics,
                    CastleViewPanel.calculateWindVaneRectangle(windVane, state.left(), state.bottom(), state.scale()));
        }
    }

    /**
     * Draws the marker for the polygon edge nearest the pointer, when one is eligible.
     *
     * @param graphics graphics context to draw with
     * @param marker   screen-space marker position, or {@code null} when no edge is hovered
     */
    private void drawEdgeHover(Graphics2D graphics, Point2D marker) {
        if (marker != null) {
            graphics.setColor(COLOR_SELECTED);
            graphics.fill(new Rectangle2D.Double(marker.getX() - 4, marker.getY() - 4, 8, 8));
        }
    }

    /**
     * Fills the screen-space rectangle occupied by a castle area.
     *
     * @param graphics graphics context to draw with
     * @param position area's lower-left position in castle coordinates
     * @param size     area's dimensions in castle coordinates
     * @param state    view transform
     */
    private void drawArea(Graphics2D graphics, Point position, Size size, RenderState state) {
        Rectangle2D area = new Rectangle2D.Double(
                state.left() + position.x() * state.scale(),
                state.bottom() - (position.y() + size.height()) * state.scale(),
                size.width() * state.scale(), size.height() * state.scale());
        graphics.setColor(Color.WHITE);
        graphics.fill(area);
    }

    /**
     * Draws a sprite at a castle-coordinate position.
     *
     * @param graphics graphics context to draw with
     * @param sprite   sprite raster data
     * @param position sprite's lower-left position in castle coordinates
     * @param state    view transform
     */
    private void drawSprite(Graphics2D graphics, int[] sprite, Point position, RenderState state) {
        Sprites.draw(graphics, sprite,
                state.left() + position.x() * state.scale(),
                state.bottom() - position.y() * state.scale(), state.scale());
    }

    /**
     * Draws resource sprites into available slots and placeholders into the remaining slots.
     *
     * @param graphics graphics context to draw with
     * @param display  sprite, storage geometry, and occupied slot count
     * @param state    view transform
     */
    private void drawResources(Graphics2D graphics, ResourceDisplay display, RenderState state) {
        int remaining = Math.max(0, display.count());
        for (int y = 0; remaining > 0 && y < display.room().height(); y += display.stepY()) {
            for (int x = 0; remaining > 0 && x < display.room().width(); x += display.stepX(), remaining--) {
                drawSprite(graphics, display.sprite(),
                        new Point(display.position().x() + x, display.position().y() + y), state);
            }
        }
        int slotIndex = 0;
        for (int y = 0; y < display.room().height(); y += display.stepY()) {
            for (int x = 0; x < display.room().width(); x += display.stepX()) {
                if (slotIndex >= display.count()) {
                    Sprites.draw(graphics, display.sprite(),
                            state.left() + (display.position().x() + x) * state.scale(),
                            state.bottom() - (display.position().y() + y) * state.scale(),
                            state.scale(), 0.15f);
                }
                slotIndex++;
            }
        }
    }

    /**
     * Draws a two-pixel selection outline around the supplied screen-space bounds.
     *
     * @param graphics graphics context to draw with
     * @param bounds   screen-space bounds to outline
     */
    private void drawSelectionOutline(Graphics2D graphics, Rectangle2D bounds) {
        graphics.setColor(COLOR_SELECTED);
        graphics.setStroke(new BasicStroke(2.0f));
        graphics.draw(bounds);
        graphics.setStroke(new BasicStroke(1.0f));
    }

    /**
     * Divides a positive quantity by its unit size, rounding any remainder up.
     *
     * @param value   quantity to divide
     * @param divisor quantity represented by one slot
     * @return zero for non-positive values, otherwise the quotient rounded upward
     */
    private int divideRoundedUp(int value, int divisor) {
        return value <= 0 ? 0 : (value + divisor - 1) / divisor;
    }

    /**
     * Maps a file-format fill style to its zoom-scaled display pattern.
     *
     * @param style fill-style command to map
     * @param state view transform used to anchor and scale the pattern
     * @return the roof, masonry, or default polygon fill paint
     */
    private Paint paintFor(FillStyle style, RenderState state) {
        BufferedImage pattern;
        if (style.style() == CastleFileFormat.FILL_STYLE_PATTERN
                && style.index() == CastleFileFormat.FILL_INDEX_ROOF) {
            pattern = ROOF_PATTERN;
        } else if (style.style() == CastleFileFormat.FILL_STYLE_PATTERN
                && style.index() == CastleFileFormat.FILL_INDEX_MASONRY) {
            pattern = MASONRY_PATTERN;
        } else {
            return COLOR_FILL_DEFAULT;
        }

        double scale = state.scale();
        double width = PATTERN_PERIOD * scale;
        double height = (pattern == MASONRY_PATTERN ? MASONRY_PATTERN_HEIGHT : PATTERN_PERIOD) * scale;
        Rectangle2D anchor = new Rectangle2D.Double(
                state.left(), state.bottom() - height, width, height);
        return new TexturePaint(pattern, anchor);
    }

    /**
     * Creates the repeating brick image used by masonry polygons.
     *
     * @return a monochrome brick pattern
     */
    private static BufferedImage createMasonryPattern() {
        int width = PATTERN_PERIOD * PATTERN_PIXELS_PER_UNIT;
        int courseHeight = 3 * PATTERN_PIXELS_PER_UNIT;
        BufferedImage pattern = new BufferedImage(width, courseHeight * 2, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < pattern.getHeight(); y++) {
            int course = y / courseHeight;
            int verticalOffset = course == 0 ? 0 : width / 4;
            for (int x = 0; x < width; x++) {
                boolean mortar = y % courseHeight < 2 || (x + verticalOffset) % (width / 2) < 2;
                pattern.setRGB(x, y, (mortar ? COLOR_PATTERN_MORTAR : COLOR_PATTERN_BRICK).getRGB());
            }
        }
        return pattern;
    }

    /**
     * Creates the repeating diagonal tile image used by roof polygons.
     *
     * @return a monochrome roof-tile pattern
     */
    private static BufferedImage createRoofPattern() {
        int tileSpacing = 6 * PATTERN_PIXELS_PER_UNIT;
        int period = tileSpacing * 2;
        int tileRadius = 4;
        BufferedImage pattern = new BufferedImage(period, period, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < period; y++) {
            for (int x = 0; x < period; x++) {
                pattern.setRGB(x, y, COLOR_PATTERN_BRICK.getRGB());
            }
        }

        for (int row = 0; row < period / tileSpacing; row++) {
            int centerY = tileSpacing / 2 + row * tileSpacing;
            int horizontalOffset = (row & 1) == 0 ? tileSpacing / 2 : 0;
            for (int centerX = horizontalOffset; centerX < period + tileSpacing; centerX += tileSpacing) {
                drawRoofTile(pattern, centerX, centerY, tileRadius);
            }
        }
        return pattern;
    }

    /**
     * Draws one diamond-shaped roof-tile outline into the repeating pattern.
     *
     * @param pattern    roof pattern image
     * @param centerX    tile center's horizontal coordinate
     * @param centerY    tile center's vertical coordinate
     * @param tileRadius distance from the center to each diamond tip
     */
    private static void drawRoofTile(BufferedImage pattern, int centerX, int centerY, int tileRadius) {
        int period = pattern.getWidth();
        for (int offsetY = -tileRadius; offsetY <= tileRadius; offsetY++) {
            for (int offsetX = -tileRadius; offsetX <= tileRadius; offsetX++) {
                if (Math.abs(offsetX) + Math.abs(offsetY) == tileRadius) {
                    int x = Math.floorMod(centerX + offsetX, period);
                    int y = Math.floorMod(centerY + offsetY, period);
                    pattern.setRGB(x, y, COLOR_PATTERN_MORTAR.getRGB());
                }
            }
        }
    }

    /**
     * Draws the four resize handles at the corners of a selected resource area.
     *
     * @param graphics graphics context to draw with
     * @param position area's lower-left position in castle coordinates
     * @param size     area's dimensions in castle coordinates
     * @param state    view transform and currently dragged corner
     */
    private void drawCornerPoints(Graphics2D graphics, Point position, Size size, RenderState state) {
        double x = state.left() + position.x() * state.scale();
        double y = state.bottom() - (position.y() + size.height()) * state.scale();
        double width = size.width() * state.scale();
        double height = size.height() * state.scale();
        drawCornerPoint(graphics, 0, x, y + height, state);
        drawCornerPoint(graphics, 1, x + width, y + height, state);
        drawCornerPoint(graphics, 2, x, y, state);
        drawCornerPoint(graphics, 3, x + width, y, state);
    }

    /**
     * Draws one resize handle, highlighting it when it is the active handle.
     *
     * @param graphics graphics context to draw with
     * @param corner   index of the handle
     * @param x        screen-space horizontal center
     * @param y        screen-space vertical center
     * @param state    active resize-handle state
     */
    private void drawCornerPoint(Graphics2D graphics, int corner, double x, double y, RenderState state) {
        boolean dragging = state.draggingResourceCorner() == corner;
        double halfSize = dragging ? 4 : 3;
        graphics.setColor(dragging ? COLOR_SELECTED : COLOR_HIGHLIGHTED);
        graphics.fill(new Rectangle2D.Double(x - halfSize, y - halfSize, halfSize * 2, halfSize * 2));
    }

    /**
     * Draws the current polygon-creation path, entered vertices, and pointer marker.
     *
     * @param graphics graphics context to draw with
     * @param state    view transform and in-progress polygon state
     */
    private void drawPolygonCreation(Graphics2D graphics, RenderState state) {
        List<Point> points = state.creationPoints();
        if (points.isEmpty() && state.mousePosition() == null) {
            return;
        }

        int fillIndex = switch (state.polygonType()) {
            case WALL -> CastleFileFormat.FILL_INDEX_MASONRY;
            case ROOF -> CastleFileFormat.FILL_INDEX_ROOF;
        };
        Paint fill = paintFor(new FillStyle(CastleFileFormat.FILL_STYLE_PATTERN, fillIndex), state);
        if (points.size() >= 2 && state.mousePosition() != null) {
            Path2D path = createPolygonPath(points, state.left(), state.bottom(), state.scale(), false);
            path.lineTo(state.mousePosition().getX(), state.mousePosition().getY());
            path.closePath();
            graphics.setPaint(fill);
            graphics.fill(path);
            graphics.setColor(COLOR_OUTLINE);
            graphics.setStroke(new BasicStroke(1.0f));
            graphics.draw(path);
        } else if (points.size() >= 2) {
            Path2D path = createPolygonPath(points, state.left(), state.bottom(), state.scale(), false);
            graphics.setColor(COLOR_OUTLINE);
            graphics.setStroke(new BasicStroke(1.0f));
            graphics.draw(path);
        }

        for (Point point : points) {
            double screenX = state.left() + point.x() * state.scale();
            double screenY = state.bottom() - point.y() * state.scale();
            graphics.setColor(COLOR_SELECTED);
            graphics.fill(new Rectangle2D.Double(screenX - 4, screenY - 4, 8, 8));
        }
        if (state.mousePosition() != null) {
            graphics.setColor(Color.YELLOW);
            graphics.fill(new Rectangle2D.Double(
                    state.mousePosition().getX() - 4, state.mousePosition().getY() - 4, 8, 8));
        }
    }

    /**
     * Builds a screen-space path from ordered castle-coordinate points.
     *
     * @param points ordered path vertices
     * @param left   screen-space castle origin
     * @param bottom screen-space castle baseline
     * @param scale  screen pixels per castle coordinate
     * @param close  whether to close the path back to its first point
     * @return the transformed path
     */
    private Path2D createPolygonPath(List<Point> points, double left, double bottom, double scale, boolean close) {
        Path2D path = new Path2D.Double();
        Point first = points.getFirst();
        path.moveTo(left + first.x() * scale, bottom - first.y() * scale);
        for (int index = 1; index < points.size(); index++) {
            Point point = points.get(index);
            path.lineTo(left + point.x() * scale, bottom - point.y() * scale);
        }
        if (close) {
            path.closePath();
        }
        return path;
    }

    /**
     * Checks whether a point does not use the unset-position sentinel.
     *
     * @param point point to inspect
     * @return {@code true} if both coordinates are set
     */
    // Keep the positive predicate name to make callers' unset-position guard clauses read naturally.
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean isPositioned(Point point) {
        return point.isSet();
    }

    /**
     * Resource sprite and slot layout used to render one storage area.
     *
     * @param sprite   sprite raster data for the resource
     * @param position lower-left storage position
     * @param room     storage dimensions
     * @param stepX    horizontal slot spacing
     * @param stepY    vertical slot spacing
     * @param count    number of occupied slots
     */
    private record ResourceDisplay(int[] sprite, Point position, Size room, int stepX, int stepY, int count) {
        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ResourceDisplay(
                    int[] otherSprite, Point otherPosition, Size otherRoom,
                    int otherStepX, int otherStepY, int otherCount
            ))) {
                return false;
            }
            return Arrays.equals(sprite, otherSprite)
                    && Objects.equals(position, otherPosition)
                    && Objects.equals(room, otherRoom)
                    && stepX == otherStepX
                    && stepY == otherStepY
                    && count == otherCount;
        }

        @Override
        public int hashCode() {
            return Objects.hash(Arrays.hashCode(sprite), position, room, stepX, stepY, count);
        }

        @Override
        public String toString() {
            return "ResourceDisplay[sprite=" + Arrays.toString(sprite)
                    + ", position=" + position
                    + ", room=" + room
                    + ", stepX=" + stepX
                    + ", stepY=" + stepY
                    + ", count=" + count + "]";
        }
    }

    /**
     * Immutable view data required to paint the castle.
     *
     * @param castle                 castle to render
     * @param selectedPolygon        selected polygon, if any
     * @param selectedPointIndex     selected polygon-vertex index
     * @param selectedCannonIndex    selected cannon index
     * @param selectedThrone         whether the throne is selected
     * @param selectedWindVane       whether the wind vane is selected
     * @param selectedResourceArea   selected resource area
     * @param draggingResourceCorner active resource-area corner index
     * @param gridVisible            whether to render the grid
     * @param polygonCreationMode    whether to render the polygon preview
     * @param polygonType            active polygon fill category
     * @param creationPoints         points entered for the active polygon
     * @param mousePosition          preview pointer position
     * @param edgeHoverMarker        screen-space marker for the hovered polygon edge
     * @param left                   screen-space castle origin
     * @param bottom                 screen-space castle baseline
     * @param scale                  screen pixels per castle coordinate
     * @param width                  view width in pixels
     * @param height                 view height in pixels
     */
    record RenderState(Castle castle, Polygon selectedPolygon, int selectedPointIndex, int selectedCannonIndex,
                       boolean selectedThrone, boolean selectedWindVane, ResourceArea selectedResourceArea,
                       int draggingResourceCorner, boolean gridVisible, boolean polygonCreationMode,
                       CastlePolygonType polygonType, List<Point> creationPoints, Point2D mousePosition,
                       Point2D edgeHoverMarker, double left, double bottom, double scale, int width, int height) {
        RenderState {
            creationPoints = List.copyOf(creationPoints);
            mousePosition = copyPoint(mousePosition);
            edgeHoverMarker = copyPoint(edgeHoverMarker);
        }

        /**
         * Creates an immutable snapshot of the pointer position.
         *
         * @param point point to copy, or {@code null}
         * @return a defensive copy, or {@code null}
         */
        private static Point2D copyPoint(Point2D point) {
            return point == null ? null : new Point2D.Double(point.getX(), point.getY());
        }
    }
}
