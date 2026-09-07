package at.josefjordan.ballerburg.editor.ui.editor;

import at.josefjordan.ballerburg.editor.model.CastleDocument.Castle;
import at.josefjordan.ballerburg.editor.model.CastleDocument.DrawingCommand;
import at.josefjordan.ballerburg.editor.model.CastleDocument.FillStyle;
import at.josefjordan.ballerburg.editor.model.CastleDocument.Point;
import at.josefjordan.ballerburg.editor.model.CastleDocument.Polygon;
import at.josefjordan.ballerburg.editor.model.CastleDocument.Size;
import at.josefjordan.ballerburg.editor.model.CastleFileFormat;
import hgb.gui.HGBasePanel;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;

/** Draws one castle without owning or modifying editor state. */
final class CastleViewPanel extends HGBasePanel {
    public static final double MIN_ZOOM_FACTOR = 0.10; // corresponds 10 %
    public static final double MAX_ZOOM_FACTOR = 10.0; // corresponds 1000 %
    private static final Color COLOR_SELECTED = new Color(255, 255, 0);
    private static final Color COLOR_HIGHLIGHTED = new Color(255, 100, 100);
    private static final Color COLOR_OUTLINE = new Color(45, 45, 45);
    private static final Color COLOR_FILL_DEFAULT = new Color(120, 130, 140);
    private static final Color COLOR_FILL_ROOF = new Color(145, 65, 55);
    private static final Color COLOR_FILL_MASONRY = new Color(150, 150, 145);
    private Castle castle;
    private Polygon selectedPolygon;
    private int selectedPointIndex = -1;
    private int selectedCannonIndex = -1;
    private boolean selectedThrone = false;
    private String selectedResourceArea = null; // "money", "powder", or "balls"
    private int draggingResourceCorner = -1; // 0-3 for corners, -1 if not dragging
    private EditorPanel editorPanel;
    private double zoomFactor = 1.0;
    private Runnable zoomListener;

    CastleViewPanel() {
        addMouseListener(new CastleMouseHandler(this));
        addMouseWheelListener(new CastleZoomHandler(this));
    }

    void setEditorPanel(EditorPanel editorPanel) {
        this.editorPanel = editorPanel;
    }

    void setZoomListener(Runnable zoomListener) {
        this.zoomListener = zoomListener;
    }

    void setCastle(Castle castle) {
        this.castle = castle;
        selectedPolygon = null;
        selectedCannonIndex = -1;
        selectedThrone = false;
        selectedResourceArea = null;
        zoomFactor = 1.0;
        notifyZoomChanged();
        repaint();
    }

    void updateCastle(Castle castle) {
        this.castle = castle;
        repaint();
    }

    Castle getCastle() {
        return castle;
    }

    EditorPanel getEditorPanel() {
        return editorPanel;
    }

    void setSelectedPolygon(Polygon polygon) {
        if (polygon != selectedPolygon) {
            selectedPolygon = polygon;
            repaint();
        }
    }

    Polygon getSelectedPolygon() {
        return selectedPolygon;
    }

    int getSelectedPointIndex() {
        return selectedPointIndex;
    }

    void setSelectedPointIndex(int index) {
        if (selectedPointIndex != index) {
            selectedPointIndex = index;
            repaint();
        }
    }

    int getSelectedCannonIndex() {
        return selectedCannonIndex;
    }

    void setSelectedCannonIndex(int index) {
        if (selectedCannonIndex != index) {
            selectedCannonIndex = index;
            repaint();
        }
    }

    boolean isSelectedThrone() {
        return selectedThrone;
    }

    void setSelectedThrone(boolean selected) {
        if (selectedThrone != selected) {
            selectedThrone = selected;
            repaint();
        }
    }

    String getSelectedResourceArea() {
        return selectedResourceArea;
    }

    void setSelectedResourceArea(String resourceArea) {
        if (!java.util.Objects.equals(selectedResourceArea, resourceArea)) {
            selectedResourceArea = resourceArea;
            draggingResourceCorner = -1;
            repaint();
        }
    }

    void setDraggingResourceCorner(int cornerIndex) {
        if (draggingResourceCorner != cornerIndex) {
            draggingResourceCorner = cornerIndex;
            repaint();
        }
    }

    void adjustZoom(double multiplier) {
        zoomFactor *= multiplier;
        zoomFactor = Math.max(MIN_ZOOM_FACTOR, Math.min(MAX_ZOOM_FACTOR, zoomFactor));
        notifyZoomChanged();
        repaint();
    }

    double getZoomFactor() {
        return zoomFactor;
    }

    private void notifyZoomChanged() {
        if (zoomListener != null) {
            zoomListener.run();
        }
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        if (castle == null) {
            return;
        }
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            drawCastle(g);
        } finally {
            g.dispose();
        }
    }

    private void drawCastle(Graphics2D g) {
        int maximumY = 1;
        for (DrawingCommand command : castle.drawing()) {
            if (command instanceof Polygon polygon) {
                for (Point point : polygon.points()) {
                    maximumY = Math.max(maximumY, point.y());
                }
            }
        }
        if (castle.throne().x() != Integer.MIN_VALUE && castle.throne().y() != Integer.MIN_VALUE) {
            maximumY = Math.max(maximumY, areaTop(castle.throne(),
                    new Size(CastleFileFormat.THRONE_WIDTH, CastleFileFormat.THRONE_HEIGHT)));
        }
        maximumY = Math.max(maximumY, calculateMaximumCannonY(castle));
        maximumY = Math.max(maximumY, areaTop(castle.money(), castle.moneyRoom()));
        maximumY = Math.max(maximumY, areaTop(castle.powder(), castle.powderRoom()));
        maximumY = Math.max(maximumY, areaTop(castle.balls(), castle.ballRoom()));
        maximumY = Math.max(maximumY, castle.flag().y() + CastleFileFormat.FLAG_HEIGHT);

        double margin = 24.0;
        double scale = Math.min((getWidth() - 2.0 * margin) / Math.max(1, castle.width()),
                (getHeight() - 2.0 * margin) / maximumY) * zoomFactor;
        if (scale <= 0) {
            return;
        }
        double left = (getWidth() - castle.width() * scale) / 2.0;
        double bottom = (getHeight() + maximumY * scale) / 2.0;

        Color fill = new Color(150, 150, 145);
        g.setStroke(new BasicStroke(1.0f));
        for (DrawingCommand command : castle.drawing()) {
            if (command instanceof FillStyle style) {
                fill = colorFor(style);
            } else if (command instanceof Polygon polygon) {
                Path2D path = new Path2D.Double();
                Point first = polygon.points().get(0);
                path.moveTo(left + first.x() * scale, bottom - first.y() * scale);
                for (int i = 1; i < polygon.points().size(); i++) {
                    Point point = polygon.points().get(i);
                    path.lineTo(left + point.x() * scale, bottom - point.y() * scale);
                }
                path.closePath();
                g.setColor(fill);
                g.fill(path);
                g.setColor(COLOR_OUTLINE);
                g.draw(path);
                if (polygon == selectedPolygon) {
                    for (int i = 0; i < polygon.points().size(); i++) {
                        Point point = polygon.points().get(i);
                        double screenX = left + point.x() * scale;
                        double screenY = bottom - point.y() * scale;
                        if (i == selectedPointIndex) {
                            g.setColor(COLOR_SELECTED);
                            g.fill(new Rectangle2D.Double(screenX - 4, screenY - 4, 8, 8));
                        } else {
                            g.setColor(COLOR_HIGHLIGHTED);
                            g.fill(new Rectangle2D.Double(screenX - 3, screenY - 3, 6, 6));
                        }
                    }
                }
            }
        }

        if (castle.throne().x() != Integer.MIN_VALUE && castle.throne().y() != Integer.MIN_VALUE) {
            drawArea(g, castle.throne(),
                    new Size(CastleFileFormat.THRONE_WIDTH, CastleFileFormat.THRONE_HEIGHT), left, bottom, scale);
            if (selectedThrone) {
                Rectangle2D throneRect = new Rectangle2D.Double(left + castle.throne().x() * scale,
                        bottom - (castle.throne().y() + CastleFileFormat.THRONE_HEIGHT) * scale,
                        CastleFileFormat.THRONE_WIDTH * scale, CastleFileFormat.THRONE_HEIGHT * scale);
                g.setColor(COLOR_SELECTED);
                g.setStroke(new BasicStroke(2.0f));
                g.draw(throneRect);
                g.setStroke(new BasicStroke(1.0f));
            }
            drawSprite(g, Sprites.KING, castle.throne(), left, bottom, scale);
        }
        drawArea(g, castle.money(), castle.moneyRoom(), left, bottom, scale);
        if ("money".equals(selectedResourceArea)) {
            drawCornerPoints(g, castle.money(), castle.moneyRoom(), left, bottom, scale);
        }
        drawArea(g, castle.powder(), castle.powderRoom(), left, bottom, scale);
        if ("powder".equals(selectedResourceArea)) {
            drawCornerPoints(g, castle.powder(), castle.powderRoom(), left, bottom, scale);
        }
        drawArea(g, castle.balls(), castle.ballRoom(), left, bottom, scale);
        if ("balls".equals(selectedResourceArea)) {
            drawCornerPoints(g, castle.balls(), castle.ballRoom(), left, bottom, scale);
        }
        for (int i = 0; i < castle.cannons().size(); i++) {
            Point cannon = castle.cannons().get(i);
            drawSprite(g, Sprites.CANNON, cannon, left, bottom, scale);
            if (i == selectedCannonIndex) {
                g.setColor(COLOR_SELECTED);
                g.setStroke(new BasicStroke(2.0f));
                g.draw(calculateCannonRectangle(cannon, left, bottom, scale));
                g.setStroke(new BasicStroke(1.0f));
            }
        }
        drawResources(g, Sprites.MONEY, castle.money(), castle.moneyRoom(), CastleFileFormat.MONEY_WIDTH, CastleFileFormat.MONEY_HEIGHT,
                divideRoundedUp(castle.resources().money(), CastleFileFormat.MONEY_PER_UNIT), left, bottom, scale);
        drawResources(g, Sprites.POWDER, castle.powder(), castle.powderRoom(), CastleFileFormat.POWDER_WIDTH, CastleFileFormat.POWDER_HEIGHT,
                divideRoundedUp(castle.resources().powder(), CastleFileFormat.POWDER_PER_UNIT), left, bottom, scale);
        drawResources(g, Sprites.BALL, castle.balls(), castle.ballRoom(), CastleFileFormat.CANNONBALL_WIDTH, CastleFileFormat.CANNONBALL_HEIGHT,
                castle.resources().balls(), left, bottom, scale);
        drawSprite(g, Sprites.FLAG, castle.flag(), left, bottom, scale);
    }

    private int areaTop(Point position, Size size) {
        return position.y() + size.height();
    }

    private void drawArea(Graphics2D g, Point position, Size size,
                          double left, double bottom, double scale) {
        Rectangle2D area = new Rectangle2D.Double(left + position.x() * scale,
                bottom - (position.y() + size.height()) * scale,
                size.width() * scale, size.height() * scale);
        g.setColor(Color.WHITE);
        g.fill(area);
    }

    private void drawSprite(Graphics2D g, int[] sprite, Point position,
                            double left, double bottom, double scale) {
        Sprites.draw(g, sprite, left + position.x() * scale,
                bottom - position.y() * scale, scale);
    }

    private void drawResources(Graphics2D g, int[] sprite, Point position, Size room,
                               int stepX, int stepY, int count,
                               double left, double bottom, double scale) {
        int remaining = Math.max(0, count);
        for (int y = 0; remaining > 0 && y < room.height(); y += stepY) {
            for (int x = 0; remaining > 0 && x < room.width(); x += stepX) {
                drawSprite(g, sprite, new Point(position.x() + x, position.y() + y),
                        left, bottom, scale);
                remaining--;
            }
        }
        // Draw semi-transparent placeholders for empty slots
        int slotIndex = 0;
        for (int y = 0; y < room.height(); y += stepY) {
            for (int x = 0; x < room.width(); x += stepX) {
                if (slotIndex >= count) {
                    Sprites.draw(g, sprite, left + (position.x() + x) * scale,
                            bottom - (position.y() + y) * scale, scale, 0.15f);
                }
                slotIndex++;
            }
        }
    }

    private int divideRoundedUp(int value, int divisor) {
        return value <= 0 ? 0 : (value + divisor - 1) / divisor;
    }

    private Color colorFor(FillStyle style) {
        if (style.style() == CastleFileFormat.FILL_STYLE_PATTERN && style.index() == CastleFileFormat.FILL_INDEX_ROOF) {
            return COLOR_FILL_ROOF;
        }
        if (style.style() == CastleFileFormat.FILL_STYLE_PATTERN && style.index() == CastleFileFormat.FILL_INDEX_MASONRY) {
            return COLOR_FILL_MASONRY;
        }
        return COLOR_FILL_DEFAULT;
    }

    static Rectangle2D.Double calculateCannonRectangle(Point cannon, double left, double bottom, double scale) {
        double screenX = left + cannon.x() * scale;
        double screenY = bottom - cannon.y() * scale;
        double screenWidth = CastleFileFormat.CANNON_WIDTH * scale;
        double screenHeight = CastleFileFormat.CANNON_HEIGHT * scale;
        return new Rectangle2D.Double(screenX, screenY - (CastleFileFormat.CANNON_HEIGHT - 1) * scale, screenWidth, screenHeight);
    }

    static int calculateMaximumCannonY(Castle castle) {
        int maximumY = 1;
        for (Point cannon : castle.cannons()) {
            maximumY = Math.max(maximumY, cannon.y() + CastleFileFormat.CANNON_HEIGHT);
        }
        return maximumY;
    }

    private void drawCornerPoints(Graphics2D g, Point position, Size size, double left, double bottom, double scale) {
        double x = left + position.x() * scale;
        double y = bottom - (position.y() + size.height()) * scale;
        double width = size.width() * scale;
        double height = size.height() * scale;

        // Bottom-left corner (index 0)
        if (draggingResourceCorner == 0) {
            g.setColor(COLOR_SELECTED);
            g.fill(new Rectangle2D.Double(x - 4, (y + height) - 4, 8, 8));
        } else {
            g.setColor(COLOR_HIGHLIGHTED);
            g.fill(new Rectangle2D.Double(x - 3, (y + height) - 3, 6, 6));
        }

        // Bottom-right corner (index 1)
        if (draggingResourceCorner == 1) {
            g.setColor(COLOR_SELECTED);
            g.fill(new Rectangle2D.Double((x + width) - 4, (y + height) - 4, 8, 8));
        } else {
            g.setColor(COLOR_HIGHLIGHTED);
            g.fill(new Rectangle2D.Double((x + width) - 3, (y + height) - 3, 6, 6));
        }

        // Top-left corner (index 2)
        if (draggingResourceCorner == 2) {
            g.setColor(COLOR_SELECTED);
            g.fill(new Rectangle2D.Double(x - 4, y - 4, 8, 8));
        } else {
            g.setColor(COLOR_HIGHLIGHTED);
            g.fill(new Rectangle2D.Double(x - 3, y - 3, 6, 6));
        }

        // Top-right corner (index 3)
        if (draggingResourceCorner == 3) {
            g.setColor(COLOR_SELECTED);
            g.fill(new Rectangle2D.Double((x + width) - 4, y - 4, 8, 8));
        } else {
            g.setColor(COLOR_HIGHLIGHTED);
            g.fill(new Rectangle2D.Double((x + width) - 3, y - 3, 6, 6));
        }
    }
}
