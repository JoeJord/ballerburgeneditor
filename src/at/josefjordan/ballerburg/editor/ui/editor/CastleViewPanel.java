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
    private Castle castle;
    private Polygon selectedPolygon;
    private int selectedPointIndex = -1;
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

    void adjustZoom(double multiplier) {
        zoomFactor *= multiplier;
        zoomFactor = Math.max(0.1, Math.min(10.0, zoomFactor));
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
        maximumY = Math.max(maximumY, areaTop(castle.throne(),
                new Size(CastleFileFormat.THRONE_WIDTH, CastleFileFormat.THRONE_HEIGHT)));
        for (Point cannon : castle.cannons()) {
            maximumY = Math.max(maximumY, cannon.y() + 12);
        }
        maximumY = Math.max(maximumY, areaTop(castle.money(), castle.moneyRoom()));
        maximumY = Math.max(maximumY, areaTop(castle.powder(), castle.powderRoom()));
        maximumY = Math.max(maximumY, areaTop(castle.balls(), castle.ballRoom()));

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
                g.setColor(new Color(45, 45, 45));
                g.draw(path);
                if (polygon == selectedPolygon) {
                    for (int i = 0; i < polygon.points().size(); i++) {
                        Point point = polygon.points().get(i);
                        double screenX = left + point.x() * scale;
                        double screenY = bottom - point.y() * scale;
                        if (i == selectedPointIndex) {
                            g.setColor(new Color(255, 255, 0));
                            g.fill(new Rectangle2D.Double(screenX - 4, screenY - 4, 8, 8));
                        } else {
                            g.setColor(new Color(255, 100, 100));
                            g.fill(new Rectangle2D.Double(screenX - 3, screenY - 3, 6, 6));
                        }
                    }
                }
            }
        }

        drawArea(g, castle.throne(),
                new Size(CastleFileFormat.THRONE_WIDTH, CastleFileFormat.THRONE_HEIGHT), left, bottom, scale);
        drawArea(g, castle.money(), castle.moneyRoom(), left, bottom, scale);
        drawArea(g, castle.powder(), castle.powderRoom(), left, bottom, scale);
        drawArea(g, castle.balls(), castle.ballRoom(), left, bottom, scale);
        drawSprite(g, Sprites.KING, castle.throne(), left, bottom, scale);
        for (Point cannon : castle.cannons()) {
            drawSprite(g, Sprites.CANNON, cannon, left, bottom, scale);
        }
        drawResources(g, Sprites.MONEY, castle.money(), castle.moneyRoom(), 7, 10,
                divideRoundedUp(castle.resources().money(), 150), left, bottom, scale);
        drawResources(g, Sprites.POWDER, castle.powder(), castle.powderRoom(), 9, 9,
                divideRoundedUp(castle.resources().powder(), 30), left, bottom, scale);
        drawResources(g, Sprites.BALL, castle.balls(), castle.ballRoom(), 6, 6,
                castle.resources().balls(), left, bottom, scale);
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
    }

    private int divideRoundedUp(int value, int divisor) {
        return value <= 0 ? 0 : (value + divisor - 1) / divisor;
    }

    private Color colorFor(FillStyle style) {
        if (style.style() == 2 && style.index() == 11) {
            return new Color(145, 65, 55);
        }
        if (style.style() == 2 && style.index() == 9) {
            return new Color(150, 150, 145);
        }
        return new Color(120, 130, 140);
    }
}
