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
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

/** Draws one castle without owning or modifying editor state. */
final class CastleViewPanel extends HGBasePanel {
    public static final double MIN_ZOOM_FACTOR = 0.10; // corresponds 10 %
    public static final double MAX_ZOOM_FACTOR = 10.0; // corresponds 1000 %
    private static final double BASE_SCALE = 4.0; // Base scale multiplier for 100% zoom
    private static final double MARGIN = 24.0;
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
    private boolean selectedWindVane = false;
    private String selectedResourceArea = null; // "money", "powder", or "balls"
    private int draggingResourceCorner = -1; // 0-3 for corners, -1 if not dragging
    private EditorPanel editorPanel;
    private double zoomFactor = 1.0;
    private Runnable zoomListener;
    
    // Polygon creation mode
    private boolean polygonCreationMode = false;
    private String polygonType = null; // "wall" or "roof"
    private List<Point> creationPoints = new ArrayList<>();
    private Point2D mousePosition = null;
    
    // Edge hover for selected polygon
    private Point2D edgeHoverPosition = null;
    private Runnable polygonSelectionListener;

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

    void setPolygonSelectionListener(Runnable listener) {
        this.polygonSelectionListener = listener;
    }

    void setCastle(Castle castle) {
        this.castle = castle;
        selectedPolygon = null;
        selectedCannonIndex = -1;
        selectedThrone = false;
        selectedWindVane = false;
        selectedResourceArea = null;
        polygonCreationMode = false;
        polygonType = null;
        creationPoints.clear();
        edgeHoverPosition = null;
        zoomFactor = 1.0;
        notifyZoomChanged();
        updatePreferredSize();
        centerCastle();
        repaint();
    }

    void updateCastle(Castle castle) {
        this.castle = castle;
        updatePreferredSize();
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
            edgeHoverPosition = null;
            repaint();
            if (polygonSelectionListener != null) {
                polygonSelectionListener.run();
            }
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

    boolean isSelectedWindVane() {
        return selectedWindVane;
    }

    void setSelectedWindVane(boolean selected) {
        if (selectedWindVane != selected) {
            selectedWindVane = selected;
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
        adjustZoom(multiplier, null);
    }

    void adjustZoom(double multiplier, java.awt.Point mousePoint) {
        double oldZoomFactor = zoomFactor;
        zoomFactor *= multiplier;
        zoomFactor = Math.max(MIN_ZOOM_FACTOR, Math.min(MAX_ZOOM_FACTOR, zoomFactor));

        if (mousePoint != null && getParent() instanceof javax.swing.JScrollPane scrollPane) {
            // Calculate world coordinates under mouse before zoom
            double oldScale = BASE_SCALE * oldZoomFactor;
            double newScale = BASE_SCALE * zoomFactor;

            int[] oldBounds = calculateBounds();
            int oldMinimumX = oldBounds[0];
            int oldBaseMaximumY = calculateMaximumY();
            double oldLeft = MARGIN - oldMinimumX * oldScale;
            double oldBottom = MARGIN + oldBaseMaximumY * oldScale;

            // Convert mouse screen coordinates to world coordinates
            double worldX = (mousePoint.x - oldLeft) / oldScale;
            double worldY = (oldBottom - mousePoint.y) / oldScale;

            // Update preferred size with new zoom
            updatePreferredSize();

            // Calculate new screen position for the same world coordinates
            int[] newBounds = calculateBounds();
            int newMinimumX = newBounds[0];
            int newBaseMaximumY = calculateMaximumY();
            double newLeft = MARGIN - newMinimumX * newScale;
            double newBottom = MARGIN + newBaseMaximumY * newScale;
            double newScreenX = newLeft + worldX * newScale;
            double newScreenY = newBottom - worldY * newScale;

            // Adjust scroll position to keep the same point under mouse
            java.awt.Point oldViewPos = scrollPane.getViewport().getViewPosition();
            int deltaX = (int) Math.round(newScreenX - mousePoint.x);
            int deltaY = (int) Math.round(newScreenY - mousePoint.y);

            int newX = Math.max(0, oldViewPos.x + deltaX);
            int newY = Math.max(0, oldViewPos.y + deltaY);

            scrollPane.getViewport().setViewPosition(new java.awt.Point(newX, newY));
        } else {
            updatePreferredSize();
        }

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

    private void updatePreferredSize() {
        int minimumX = 0;
        int minimumY = 0;
        int maximumY = 1;
        int maximumX = 100;

        if (castle != null) {
            maximumX = castle.width();
            for (DrawingCommand command : castle.drawing()) {
                if (command instanceof Polygon polygon) {
                    for (Point point : polygon.points()) {
                        minimumY = Math.min(minimumY, point.y());
                        minimumX = Math.min(minimumX, point.x());
                        maximumY = Math.max(maximumY, point.y());
                        maximumX = Math.max(maximumX, point.x());
                    }
                }
            }
            if (castle.throne().x() != Integer.MIN_VALUE && castle.throne().y() != Integer.MIN_VALUE) {
                minimumY = Math.min(minimumY, castle.throne().y());
                minimumX = Math.min(minimumX, castle.throne().x());
                maximumY = Math.max(maximumY, areaTop(castle.throne(),
                        new Size(CastleFileFormat.THRONE_WIDTH, CastleFileFormat.THRONE_HEIGHT)));
                maximumX = Math.max(maximumX, castle.throne().x() + CastleFileFormat.THRONE_WIDTH);
            }
            for (Point cannon : castle.cannons()) {
                minimumY = Math.min(minimumY, cannon.y());
                minimumX = Math.min(minimumX, cannon.x());
                maximumY = Math.max(maximumY, cannon.y() + CastleFileFormat.CANNON_HEIGHT);
                maximumX = Math.max(maximumX, cannon.x() + CastleFileFormat.CANNON_WIDTH);
            }
            if (castle.money().x() != Integer.MIN_VALUE && castle.money().y() != Integer.MIN_VALUE) {
                minimumY = Math.min(minimumY, castle.money().y());
                minimumX = Math.min(minimumX, castle.money().x());
                maximumY = Math.max(maximumY, areaTop(castle.money(), castle.moneyRoom()));
                maximumX = Math.max(maximumX, castle.money().x() + castle.moneyRoom().width());
            }
            if (castle.powder().x() != Integer.MIN_VALUE && castle.powder().y() != Integer.MIN_VALUE) {
                minimumY = Math.min(minimumY, castle.powder().y());
                minimumX = Math.min(minimumX, castle.powder().x());
                maximumY = Math.max(maximumY, areaTop(castle.powder(), castle.powderRoom()));
                maximumX = Math.max(maximumX, castle.powder().x() + castle.powderRoom().width());
            }
            if (castle.balls().x() != Integer.MIN_VALUE && castle.balls().y() != Integer.MIN_VALUE) {
                minimumY = Math.min(minimumY, castle.balls().y());
                minimumX = Math.min(minimumX, castle.balls().x());
                maximumY = Math.max(maximumY, areaTop(castle.balls(), castle.ballRoom()));
                maximumX = Math.max(maximumX, castle.balls().x() + castle.ballRoom().width());
            }
            if (castle.windVane().x() != Integer.MIN_VALUE && castle.windVane().y() != Integer.MIN_VALUE) {
                minimumY = Math.min(minimumY, castle.windVane().y());
                minimumX = Math.min(minimumX, castle.windVane().x());
                maximumY = Math.max(maximumY, castle.windVane().y() + CastleFileFormat.WIND_VANE_HEIGHT);
                maximumX = Math.max(maximumX, castle.windVane().x() + CastleFileFormat.WIND_VANE_WIDTH);
            }
        }

        double scale = BASE_SCALE * zoomFactor;
        // Calculate preferred size based on full span to handle movement in any direction
        // Use castle.width() as minimum baseline for width
        int baseWidth = castle != null ? castle.width() : 100;
        int contentWidth = Math.max(baseWidth, maximumX - minimumX);
        int contentHeight = maximumY - minimumY;
        int preferredWidth = (int) Math.ceil(2 * MARGIN + contentWidth * scale);
        int preferredHeight = (int) Math.ceil(2 * MARGIN + contentHeight * scale);
        setPreferredSize(new java.awt.Dimension(preferredWidth, preferredHeight));
        revalidate();

        // Also revalidate the scroll pane viewport to update scrollbars
        if (getParent() instanceof javax.swing.JViewport viewport) {
            viewport.revalidate();
        }
    }

    private static int calculateMaximumCannonX(Castle castle) {
        int maximumX = 1;
        for (Point cannon : castle.cannons()) {
            maximumX = Math.max(maximumX, cannon.x() + CastleFileFormat.CANNON_WIDTH);
        }
        return maximumX;
    }

    private int calculateMaximumY() {
        int maximumY = 1;
        if (castle != null) {
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
            maximumY = Math.max(maximumY, castle.windVane().y() + CastleFileFormat.WIND_VANE_HEIGHT);
        }
        return maximumY;
    }

    private void centerCastle() {
        if (getParent() instanceof javax.swing.JScrollPane scrollPane) {
            java.awt.Dimension panelSize = getPreferredSize();
            java.awt.Dimension viewportSize = scrollPane.getViewport().getSize();
            
            int scrollX = Math.max(0, (panelSize.width - viewportSize.width) / 2);
            int scrollY = Math.max(0, (panelSize.height - viewportSize.height) / 2);
            
            scrollPane.getViewport().setViewPosition(new java.awt.Point(scrollX, scrollY));
        }
    }

    // Returns rendering parameters for mouse interaction
    double[] getRenderingParameters() {
        int[] bounds = calculateBounds();
        int minimumX = bounds[0];
        int baseMaximumY = calculateMaximumY();
        double scale = BASE_SCALE * zoomFactor;
        // Use fixed origin based on maximumY to keep castle visually fixed
        // This prevents the castle from moving when elements are repositioned
        double left = MARGIN - minimumX * scale;
        double bottom = MARGIN + baseMaximumY * scale;
        return new double[] { left, bottom, scale };
    }

    private int[] calculateBounds() {
        int minimumX = 0;
        int minimumY = 0;
        int maximumY = 1;
        int maximumX = 100;

        if (castle != null) {
            maximumX = castle.width();
            for (DrawingCommand command : castle.drawing()) {
                if (command instanceof Polygon polygon) {
                    for (Point point : polygon.points()) {
                        minimumY = Math.min(minimumY, point.y());
                        minimumX = Math.min(minimumX, point.x());
                        maximumY = Math.max(maximumY, point.y());
                        maximumX = Math.max(maximumX, point.x());
                    }
                }
            }
            if (castle.throne().x() != Integer.MIN_VALUE && castle.throne().y() != Integer.MIN_VALUE) {
                minimumY = Math.min(minimumY, castle.throne().y());
                minimumX = Math.min(minimumX, castle.throne().x());
                maximumY = Math.max(maximumY, areaTop(castle.throne(),
                        new Size(CastleFileFormat.THRONE_WIDTH, CastleFileFormat.THRONE_HEIGHT)));
                maximumX = Math.max(maximumX, castle.throne().x() + CastleFileFormat.THRONE_WIDTH);
            }
            for (Point cannon : castle.cannons()) {
                minimumY = Math.min(minimumY, cannon.y());
                minimumX = Math.min(minimumX, cannon.x());
                maximumY = Math.max(maximumY, cannon.y() + CastleFileFormat.CANNON_HEIGHT);
                maximumX = Math.max(maximumX, cannon.x() + CastleFileFormat.CANNON_WIDTH);
            }
            if (castle.money().x() != Integer.MIN_VALUE && castle.money().y() != Integer.MIN_VALUE) {
                minimumY = Math.min(minimumY, castle.money().y());
                minimumX = Math.min(minimumX, castle.money().x());
                maximumY = Math.max(maximumY, areaTop(castle.money(), castle.moneyRoom()));
                maximumX = Math.max(maximumX, castle.money().x() + castle.moneyRoom().width());
            }
            if (castle.powder().x() != Integer.MIN_VALUE && castle.powder().y() != Integer.MIN_VALUE) {
                minimumY = Math.min(minimumY, castle.powder().y());
                minimumX = Math.min(minimumX, castle.powder().x());
                maximumY = Math.max(maximumY, areaTop(castle.powder(), castle.powderRoom()));
                maximumX = Math.max(maximumX, castle.powder().x() + castle.powderRoom().width());
            }
            if (castle.balls().x() != Integer.MIN_VALUE && castle.balls().y() != Integer.MIN_VALUE) {
                minimumY = Math.min(minimumY, castle.balls().y());
                minimumX = Math.min(minimumX, castle.balls().x());
                maximumY = Math.max(maximumY, areaTop(castle.balls(), castle.ballRoom()));
                maximumX = Math.max(maximumX, castle.balls().x() + castle.ballRoom().width());
            }
            if (castle.windVane().x() != Integer.MIN_VALUE && castle.windVane().y() != Integer.MIN_VALUE) {
                minimumY = Math.min(minimumY, castle.windVane().y());
                minimumX = Math.min(minimumX, castle.windVane().x());
                maximumY = Math.max(maximumY, castle.windVane().y() + CastleFileFormat.WIND_VANE_HEIGHT);
                maximumX = Math.max(maximumX, castle.windVane().x() + CastleFileFormat.WIND_VANE_WIDTH);
            }
        }
        return new int[] { minimumX, minimumY, maximumX, maximumY };
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        if (castle == null && !polygonCreationMode) {
            return;
        }
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (castle != null) {
                drawCastle(g);
            }
            if (polygonCreationMode) {
                drawPolygonCreation(g);
            }
        } finally {
            g.dispose();
        }
    }

    private void drawCastle(Graphics2D g) {
        int[] bounds = calculateBounds();
        int minimumX = bounds[0];
        int baseMaximumY = calculateMaximumY();

        double scale = BASE_SCALE * zoomFactor;
        if (scale <= 0) {
            return;
        }
        double left = MARGIN - minimumX * scale;
        double bottom = MARGIN + baseMaximumY * scale;

        // Start fill style is 2,9 (masonry) according to BALLER.DAT
        Color fill = colorFor(new FillStyle(CastleFileFormat.FILL_STYLE_PATTERN, CastleFileFormat.FILL_INDEX_MASONRY));
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
        drawSprite(g, Sprites.WIND_VANE, castle.windVane(), left, bottom, scale);
        if (selectedWindVane) {
            Rectangle2D windVaneRect = calculateWindVaneRectangle(castle.windVane(), left, bottom, scale);
            g.setColor(COLOR_SELECTED);
            g.setStroke(new BasicStroke(2.0f));
            g.draw(windVaneRect);
            g.setStroke(new BasicStroke(1.0f));
        }
        
        // Draw edge hover indicator for selected polygon
        if (selectedPolygon != null && edgeHoverPosition != null) {
            Point2D hoverPoint = findClosestPointOnEdge(selectedPolygon, edgeHoverPosition, left, bottom, scale);
            if (hoverPoint != null) {
                g.setColor(COLOR_SELECTED);
                g.fill(new Rectangle2D.Double(hoverPoint.getX() - 4, hoverPoint.getY() - 4, 8, 8));
            }
        }
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

    static Rectangle2D.Double calculateWindVaneRectangle(Point windVane, double left, double bottom, double scale) {
        double screenX = left + windVane.x() * scale;
        double screenY = bottom - windVane.y() * scale;
        double screenWidth = CastleFileFormat.WIND_VANE_WIDTH * scale;
        double screenHeight = (CastleFileFormat.WIND_VANE_HEIGHT + 1) * scale;
        return new Rectangle2D.Double(screenX, screenY - (CastleFileFormat.WIND_VANE_HEIGHT + 1) * scale, screenWidth, screenHeight);
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

    void startPolygonCreation(String type) {
        polygonCreationMode = true;
        polygonType = type;
        creationPoints.clear();
        mousePosition = null;
        selectedPolygon = null;
        selectedCannonIndex = -1;
        selectedThrone = false;
        selectedWindVane = false;
        selectedResourceArea = null;
        updatePreferredSize();
        repaint();
    }

    void cancelPolygonCreation() {
        polygonCreationMode = false;
        polygonType = null;
        creationPoints.clear();
        updatePreferredSize();
        repaint();
    }

    boolean isInPolygonCreationMode() {
        return polygonCreationMode;
    }

    String getPolygonType() {
        return polygonType;
    }

    List<Point> getCreationPoints() {
        return new ArrayList<>(creationPoints);
    }

    void addCreationPoint(Point point) {
        creationPoints.add(point);
        repaint();
    }

    private void drawPolygonCreation(Graphics2D g) {
        if (creationPoints.isEmpty() && mousePosition == null) {
            return;
        }

        // Calculate scale and position
        int[] bounds = calculateBounds();
        int minimumX = bounds[0];
        int baseMaximumY = calculateMaximumY();

        double scale = BASE_SCALE * zoomFactor;
        if (scale <= 0) {
            return;
        }
        double left = MARGIN - minimumX * scale;
        double bottom = MARGIN + baseMaximumY * scale;

        // Set color based on polygon type
        Color fillColor = "wall".equals(polygonType) ? COLOR_FILL_MASONRY : COLOR_FILL_ROOF;
        
        // Draw polygon preview with mouse position as last point
        if (creationPoints.size() >= 2 && mousePosition != null) {
            Path2D path = new Path2D.Double();
            Point first = creationPoints.get(0);
            path.moveTo(left + first.x() * scale, bottom - first.y() * scale);
            for (int i = 1; i < creationPoints.size(); i++) {
                Point point = creationPoints.get(i);
                path.lineTo(left + point.x() * scale, bottom - point.y() * scale);
            }
            // Add line to mouse position
            path.lineTo(mousePosition.getX(), mousePosition.getY());
            // Close the polygon
            path.closePath();
            
            g.setColor(fillColor);
            g.fill(path);
            g.setColor(COLOR_OUTLINE);
            g.setStroke(new BasicStroke(1.0f));
            g.draw(path);
        } else if (creationPoints.size() >= 2) {
            // Draw lines between points without mouse position
            Path2D path = new Path2D.Double();
            Point first = creationPoints.get(0);
            path.moveTo(left + first.x() * scale, bottom - first.y() * scale);
            for (int i = 1; i < creationPoints.size(); i++) {
                Point point = creationPoints.get(i);
                path.lineTo(left + point.x() * scale, bottom - point.y() * scale);
            }
            g.setColor(fillColor);
            g.setStroke(new BasicStroke(1.0f));
            g.draw(path);
        }

        // Draw points
        for (Point point : creationPoints) {
            double screenX = left + point.x() * scale;
            double screenY = bottom - point.y() * scale;
            g.setColor(COLOR_SELECTED);
            g.fill(new Rectangle2D.Double(screenX - 4, screenY - 4, 8, 8));
        }

        // Draw yellow square at mouse position
        if (mousePosition != null) {
            g.setColor(Color.YELLOW);
            g.fill(new Rectangle2D.Double(mousePosition.getX() - 4, mousePosition.getY() - 4, 8, 8));
        }
    }

    void finishPolygonCreation() {
        polygonCreationMode = false;
        polygonType = null;
        creationPoints.clear();
        mousePosition = null;
        repaint();
    }

    void setMousePosition(Point2D position) {
        mousePosition = position;
        repaint();
    }
    
    void setEdgeHoverPosition(Point2D position) {
        edgeHoverPosition = position;
        repaint();
    }
    
    int findEdgeIndexForHoverPosition(double left, double bottom, double scale) {
        if (selectedPolygon == null || edgeHoverPosition == null) {
            return -1;
        }
        Point2D closestPoint = findClosestPointOnEdge(selectedPolygon, edgeHoverPosition, left, bottom, scale);
        if (closestPoint == null) {
            return -1;
        }
        
        List<Point> points = selectedPolygon.points();
        final double edgeThreshold = 8.0;
        final double pointThreshold = 10.0;
        
        for (int i = 0; i < points.size(); i++) {
            Point p1 = points.get(i);
            Point p2 = points.get((i + 1) % points.size());
            
            double screenX1 = left + p1.x() * scale;
            double screenY1 = bottom - p1.y() * scale;
            double screenX2 = left + p2.x() * scale;
            double screenY2 = bottom - p2.y() * scale;
            
            double distToP1 = edgeHoverPosition.distance(screenX1, screenY1);
            double distToP2 = edgeHoverPosition.distance(screenX2, screenY2);
            
            if (distToP1 < pointThreshold || distToP2 < pointThreshold) {
                continue;
            }
            
            Point2D closestOnSegment = closestPointOnSegment(
                edgeHoverPosition, screenX1, screenY1, screenX2, screenY2);
            
            double distance = edgeHoverPosition.distance(closestOnSegment);
            
            if (distance < edgeThreshold) {
                return i; // Return the edge index (point index where the edge starts)
            }
        }
        
        return -1;
    }
    
    private Point2D findClosestPointOnEdge(Polygon polygon, Point2D mousePoint, 
                                            double left, double bottom, double scale) {
        final double edgeThreshold = 8.0; // Maximum distance to consider as "near edge"
        final double pointThreshold = 10.0; // Distance to avoid showing near existing points
        
        List<Point> points = polygon.points();
        Point2D closestPoint = null;
        double minDistance = Double.MAX_VALUE;
        
        for (int i = 0; i < points.size(); i++) {
            Point p1 = points.get(i);
            Point p2 = points.get((i + 1) % points.size());
            
            double screenX1 = left + p1.x() * scale;
            double screenY1 = bottom - p1.y() * scale;
            double screenX2 = left + p2.x() * scale;
            double screenY2 = bottom - p2.y() * scale;
            
            // Check if mouse is too close to either endpoint
            double distToP1 = mousePoint.distance(screenX1, screenY1);
            double distToP2 = mousePoint.distance(screenX2, screenY2);
            
            if (distToP1 < pointThreshold || distToP2 < pointThreshold) {
                continue; // Skip this edge, too close to an existing point
            }
            
            // Find closest point on this line segment
            Point2D closestOnSegment = closestPointOnSegment(
                mousePoint, screenX1, screenY1, screenX2, screenY2);
            
            double distance = mousePoint.distance(closestOnSegment);
            
            if (distance < edgeThreshold && distance < minDistance) {
                minDistance = distance;
                closestPoint = closestOnSegment;
            }
        }
        
        return closestPoint;
    }
    
    private Point2D closestPointOnSegment(Point2D point, double x1, double y1, 
                                           double x2, double y2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        
        if (dx == 0 && dy == 0) {
            return new Point2D.Double(x1, y1);
        }
        
        double t = ((point.getX() - x1) * dx + (point.getY() - y1) * dy) / (dx * dx + dy * dy);
        
        // Clamp t to [0, 1] to stay on the segment
        t = Math.max(0, Math.min(1, t));
        
        return new Point2D.Double(x1 + t * dx, y1 + t * dy);
    }
}
