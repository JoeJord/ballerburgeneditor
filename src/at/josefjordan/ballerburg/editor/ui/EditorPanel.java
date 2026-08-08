package at.josefjordan.ballerburg.editor.ui;

import at.josefjordan.ballerburg.editor.model.BurgDocument;
import at.josefjordan.ballerburg.editor.model.BurgDocument.Castle;
import at.josefjordan.ballerburg.editor.model.BurgDocument.DrawingCommand;
import at.josefjordan.ballerburg.editor.model.BurgDocument.FillStyle;
import at.josefjordan.ballerburg.editor.model.BurgDocument.Point;
import at.josefjordan.ballerburg.editor.model.BurgDocument.Polygon;
import hgb.gui.HGBasePanel;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;

public class EditorPanel extends HGBasePanel {
    private BurgDocument document;
    private int castleIndex = -1;
    private Runnable selectionListener;

    public BurgDocument getDocument() {
        return document;
    }

    public void setDocument(BurgDocument document) {
        this.document = document;
        castleIndex = document == null || document.castles().isEmpty() ? -1 : 0;
        notifySelectionChanged();
        repaint();
    }

    public int getCastleIndex() {
        return castleIndex;
    }

    public int getCastleCount() {
        return document == null ? 0 : document.castles().size();
    }

    public void showPreviousCastle() {
        setCastleIndex(castleIndex - 1);
    }

    public void showNextCastle() {
        setCastleIndex(castleIndex + 1);
    }

    public void setSelectionListener(Runnable selectionListener) {
        this.selectionListener = selectionListener;
    }

    private void setCastleIndex(int index) {
        if (index >= 0 && index < getCastleCount() && index != castleIndex) {
            castleIndex = index;
            notifySelectionChanged();
            repaint();
        }
    }

    private void notifySelectionChanged() {
        if (selectionListener != null) {
            selectionListener.run();
        }
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        if (castleIndex < 0 || castleIndex >= getCastleCount()) {
            return;
        }

        Castle castle = document.castles().get(castleIndex);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            drawCastle(g, castle);
        } finally {
            g.dispose();
        }
    }

    private void drawCastle(Graphics2D g, Castle castle) {
        int maximumY = 1;
        for (DrawingCommand command : castle.drawing()) {
            if (command instanceof Polygon polygon) {
                for (Point point : polygon.points()) {
                    maximumY = Math.max(maximumY, point.y());
                }
            }
        }

        double margin = 24.0;
        double scale = Math.min((getWidth() - 2.0 * margin) / Math.max(1, castle.width()),
                (getHeight() - 2.0 * margin) / maximumY);
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
            }
        }
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
