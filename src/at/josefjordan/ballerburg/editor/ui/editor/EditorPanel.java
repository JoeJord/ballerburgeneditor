package at.josefjordan.ballerburg.editor.ui.editor;

import at.josefjordan.ballerburg.editor.model.CastleDocument;
import at.josefjordan.ballerburg.editor.model.CastleDocument.Castle;
import at.josefjordan.ballerburg.editor.model.CastleDocument.Point;
import at.josefjordan.ballerburg.editor.model.CastleDocument.Resources;
import at.josefjordan.ballerburg.editor.model.CastleDocument.Size;
import at.josefjordan.ballerburg.editor.model.CastleFileFormat;
import hgb.gui.HGBasePanel;
import hgb.lib.HGBaseText;

import javax.swing.JSplitPane;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.Area;
import java.util.ArrayList;
import java.util.List;

/** Editor containing the castle view, property palette, and problems panel. */
public class EditorPanel extends HGBasePanel {
    private static final int PROPERTY_PALETTE_MIN_WIDTH = 200;
    private static final int PROPERTY_PALETTE_PREFERRED_WIDTH = 230;
    private static final int PROBLEMS_PANEL_PREFERRED_HEIGHT = 120;

    private final CastleViewPanel castleView = new CastleViewPanel();
    private final PropertyPalettePanel propertyPalette = new PropertyPalettePanel();
    private final ProblemsPanel problemsPanel = new ProblemsPanel();
    private CastleDocument document;
    private int castleIndex = -1;
    private Runnable selectionListener;
    private Runnable changeListener;
    private Runnable beginHistoryGroupListener;
    private Runnable endHistoryGroupListener;
    private Runnable zoomListener;
    private java.util.function.Consumer<String> statusBarListener;

    public EditorPanel() {
        super(new BorderLayout());
        castleView.setEditorPanel(this);
        castleView.setZoomListener(() -> {
            if (zoomListener != null) {
                zoomListener.run();
            }
        });
        castleView.setPolygonSelectionListener(this::onPolygonSelectionChanged);
        JScrollPane scrollPane = new JScrollPane(castleView);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, scrollPane, propertyPalette);
        splitPane.setContinuousLayout(true);
        splitPane.setResizeWeight(1.0);
        propertyPalette.setMinimumSize(new Dimension(PROPERTY_PALETTE_MIN_WIDTH, 0));
        propertyPalette.setPreferredSize(new Dimension(PROPERTY_PALETTE_PREFERRED_WIDTH, 0));
        JSplitPane propertySplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, propertyPalette, problemsPanel);
        propertySplitPane.setContinuousLayout(true);
        propertySplitPane.setResizeWeight(0.7);
        problemsPanel.setPreferredSize(new Dimension(0, PROBLEMS_PANEL_PREFERRED_HEIGHT));
        splitPane.setRightComponent(propertySplitPane);
        add(splitPane, BorderLayout.CENTER);
        propertyPalette.setResourcesListener(this::updateResources);
        updateSelectedCastle();
    }

    public CastleDocument getDocument() {
        return document;
    }

    public void setDocument(CastleDocument document) {
        this.document = document;
        castleIndex = document == null || document.castles().isEmpty() ? -1 : 0;
        updateSelectedCastle();
        notifySelectionChanged();
    }

    public int getCastleIndex() {
        return castleIndex;
    }

    public int getCastleCount() {
        return document == null ? 0 : document.castles().size();
    }

    public boolean hasProblems() {
        return document != null && document.castles().stream()
                .anyMatch(castle -> !createProblems(castle).isEmpty());
    }

    public int getCannonCount() {
        if (castleIndex < 0 || castleIndex >= getCastleCount()) {
            return 0;
        }
        return document.castles().get(castleIndex).cannons().size();
    }

    public boolean hasThrone() {
        if (castleIndex < 0 || castleIndex >= getCastleCount()) {
            return false;
        }
        Castle castle = document.castles().get(castleIndex);
        Point throne = castle.throne();
        return throne.x() != Integer.MIN_VALUE && throne.y() != Integer.MIN_VALUE;
    }

    public boolean hasWindVane() {
        if (castleIndex < 0 || castleIndex >= getCastleCount()) {
            return false;
        }
        Castle castle = document.castles().get(castleIndex);
        Point windVane = castle.windVane();
        return windVane.x() != Integer.MIN_VALUE && windVane.y() != Integer.MIN_VALUE;
    }

    public boolean hasMoney() {
        if (castleIndex < 0 || castleIndex >= getCastleCount()) {
            return false;
        }
        Castle castle = document.castles().get(castleIndex);
        Point money = castle.money();
        return money.x() != Integer.MIN_VALUE && money.y() != Integer.MIN_VALUE;
    }

    public boolean hasPowder() {
        if (castleIndex < 0 || castleIndex >= getCastleCount()) {
            return false;
        }
        Castle castle = document.castles().get(castleIndex);
        Point powder = castle.powder();
        return powder.x() != Integer.MIN_VALUE && powder.y() != Integer.MIN_VALUE;
    }

    public boolean hasBalls() {
        if (castleIndex < 0 || castleIndex >= getCastleCount()) {
            return false;
        }
        Castle castle = document.castles().get(castleIndex);
        Point balls = castle.balls();
        return balls.x() != Integer.MIN_VALUE && balls.y() != Integer.MIN_VALUE;
    }

    public boolean hasWall() {
        if (castleIndex < 0 || castleIndex >= getCastleCount()) {
            return false;
        }
        return hasWall(document.castles().get(castleIndex));
    }

    private boolean hasWall(Castle castle) {
        // Start fill style is 2,9 (masonry) according to BALLER.DAT
        int fillStyle = CastleFileFormat.FILL_STYLE_PATTERN;
        int fillIndex = CastleFileFormat.FILL_INDEX_MASONRY;
        for (CastleDocument.DrawingCommand command : castle.drawing()) {
            if (command instanceof CastleDocument.FillStyle(int style, int index)) {
                fillStyle = style;
                fillIndex = index;
            } else if (command instanceof CastleDocument.Polygon) {
                if (fillStyle == CastleFileFormat.FILL_STYLE_PATTERN && fillIndex == CastleFileFormat.FILL_INDEX_MASONRY) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean addCastle() {
        if (document == null) {
            return false;
        }
        List<Castle> castles = new ArrayList<>(document.castles());
        castleIndex = castleIndex < 0 ? castles.size() : castleIndex + 1;
        castles.add(castleIndex, createEmptyCastle());
        document = new CastleDocument(castles);
        documentChanged();
        return true;
    }

    public boolean deleteCastle() {
        if (document == null || castleIndex < 0 || castleIndex >= getCastleCount()) {
            return false;
        }
        List<Castle> castles = new ArrayList<>(document.castles());
        castles.remove(castleIndex);
        document = new CastleDocument(castles);
        castleIndex = Math.min(castleIndex, castles.size() - 1);
        documentChanged();
        return true;
    }

    public boolean addCannon() {
        if (document == null || castleIndex < 0 || castleIndex >= getCastleCount()) {
            return false;
        }
        Castle oldCastle = document.castles().get(castleIndex);
        if (oldCastle.cannons().size() >= CastleFileFormat.CANNON_POSITION_COUNT) {
            return false;
        }

        List<Point> newCannons = new ArrayList<>(oldCastle.cannons());
        newCannons.add(new Point(0, 0));

        Castle updatedCastle = new Castle(oldCastle.width(), newCannons, oldCastle.throne(),
                oldCastle.windVane(), oldCastle.money(), oldCastle.powder(), oldCastle.balls(),
                oldCastle.moneyRoom(), oldCastle.powderRoom(), oldCastle.ballRoom(), oldCastle.resources(),
                oldCastle.reserved(), oldCastle.drawing());
        List<Castle> castles = new ArrayList<>(document.castles());
        castles.set(castleIndex, updatedCastle);
        document = new CastleDocument(castles);
        castleView.updateCastle(updatedCastle);
        documentChanged();
        return true;
    }

    public boolean addThrone() {
        if (document == null || castleIndex < 0 || castleIndex >= getCastleCount()) {
            return false;
        }
        Castle oldCastle = document.castles().get(castleIndex);
        if (hasThrone()) {
            return false;
        }

        Point thronePosition = new Point(0, 0);

        Castle updatedCastle = new Castle(oldCastle.width(), oldCastle.cannons(), thronePosition,
                oldCastle.windVane(), oldCastle.money(), oldCastle.powder(), oldCastle.balls(),
                oldCastle.moneyRoom(), oldCastle.powderRoom(), oldCastle.ballRoom(), oldCastle.resources(),
                oldCastle.reserved(), oldCastle.drawing());
        List<Castle> castles = new ArrayList<>(document.castles());
        castles.set(castleIndex, updatedCastle);
        document = new CastleDocument(castles);
        castleView.updateCastle(updatedCastle);
        documentChanged();
        return true;
    }

    public boolean addWindVane() {
        if (document == null || castleIndex < 0 || castleIndex >= getCastleCount()) {
            return false;
        }
        Castle oldCastle = document.castles().get(castleIndex);
        if (hasWindVane()) {
            return false;
        }

        Point windVanePosition = new Point(0, 0);

        Castle updatedCastle = new Castle(oldCastle.width(), oldCastle.cannons(), oldCastle.throne(),
                windVanePosition, oldCastle.money(), oldCastle.powder(), oldCastle.balls(),
                oldCastle.moneyRoom(), oldCastle.powderRoom(), oldCastle.ballRoom(), oldCastle.resources(),
                oldCastle.reserved(), oldCastle.drawing());
        List<Castle> castles = new ArrayList<>(document.castles());
        castles.set(castleIndex, updatedCastle);
        document = new CastleDocument(castles);
        castleView.updateCastle(updatedCastle);
        documentChanged();
        return true;
    }

    public boolean addMoney() {
        if (document == null || castleIndex < 0 || castleIndex >= getCastleCount()) {
            return false;
        }
        Castle oldCastle = document.castles().get(castleIndex);
        if (hasMoney()) {
            return false;
        }

        Point moneyPosition = new Point(0, 0);
        Size moneyRoomSize = new Size(CastleFileFormat.MONEY_WIDTH, CastleFileFormat.MONEY_HEIGHT);

        Castle updatedCastle = new Castle(oldCastle.width(), oldCastle.cannons(), oldCastle.throne(),
                oldCastle.windVane(), moneyPosition, oldCastle.powder(), oldCastle.balls(),
                moneyRoomSize, oldCastle.powderRoom(), oldCastle.ballRoom(), oldCastle.resources(),
                oldCastle.reserved(), oldCastle.drawing());
        List<Castle> castles = new ArrayList<>(document.castles());
        castles.set(castleIndex, updatedCastle);
        document = new CastleDocument(castles);
        castleView.updateCastle(updatedCastle);
        documentChanged();
        return true;
    }

    public boolean addPowder() {
        if (document == null || castleIndex < 0 || castleIndex >= getCastleCount()) {
            return false;
        }
        Castle oldCastle = document.castles().get(castleIndex);
        if (hasPowder()) {
            return false;
        }

        Point powderPosition = new Point(0, 0);
        Size powderRoomSize = new Size(CastleFileFormat.POWDER_WIDTH, CastleFileFormat.POWDER_HEIGHT);

        Castle updatedCastle = new Castle(oldCastle.width(), oldCastle.cannons(), oldCastle.throne(),
                oldCastle.windVane(), oldCastle.money(), powderPosition, oldCastle.balls(),
                oldCastle.moneyRoom(), powderRoomSize, oldCastle.ballRoom(), oldCastle.resources(),
                oldCastle.reserved(), oldCastle.drawing());
        List<Castle> castles = new ArrayList<>(document.castles());
        castles.set(castleIndex, updatedCastle);
        document = new CastleDocument(castles);
        castleView.updateCastle(updatedCastle);
        documentChanged();
        return true;
    }

    public boolean addCannonballs() {
        if (document == null || castleIndex < 0 || castleIndex >= getCastleCount()) {
            return false;
        }
        Castle oldCastle = document.castles().get(castleIndex);
        if (hasBalls()) {
            return false;
        }

        Point ballsPosition = new Point(0, 0);
        Size ballRoomSize = new Size(CastleFileFormat.CANNONBALL_WIDTH, CastleFileFormat.CANNONBALL_HEIGHT);

        Castle updatedCastle = new Castle(oldCastle.width(), oldCastle.cannons(), oldCastle.throne(),
                oldCastle.windVane(), oldCastle.money(), oldCastle.powder(), ballsPosition,
                oldCastle.moneyRoom(), oldCastle.powderRoom(), ballRoomSize, oldCastle.resources(),
                oldCastle.reserved(), oldCastle.drawing());
        List<Castle> castles = new ArrayList<>(document.castles());
        castles.set(castleIndex, updatedCastle);
        document = new CastleDocument(castles);
        castleView.updateCastle(updatedCastle);
        documentChanged();
        return true;
    }

    public void addWall() {
        if (document == null || castleIndex < 0 || castleIndex >= getCastleCount()) {
            return;
        }
        if (hasWall()) {
            return;
        }
        castleView.startPolygonCreation("wall");
        setPolygonCreationHint();
    }

    public void addRoof() {
        if (document == null || castleIndex < 0 || castleIndex >= getCastleCount()) {
            return;
        }
        castleView.startPolygonCreation("roof");
        setPolygonCreationHint();
    }

    public void finishPolygonCreation(List<Point> points) {
        if (document == null || castleIndex < 0 || castleIndex >= getCastleCount()) {
            castleView.finishPolygonCreation();
            clearPolygonCreationHint();
            return;
        }
        if (points.size() < 3) {
            castleView.finishPolygonCreation();
            clearPolygonCreationHint();
            return;
        }

        Castle oldCastle = document.castles().get(castleIndex);
        List<CastleDocument.DrawingCommand> newDrawing = new ArrayList<>(oldCastle.drawing());

        String polygonType = castleView.getPolygonType();
        int fillIndex = "wall".equals(polygonType) ? CastleFileFormat.FILL_INDEX_MASONRY : CastleFileFormat.FILL_INDEX_ROOF;

        // Add FillStyle command before the polygon
        newDrawing.add(new CastleDocument.FillStyle(CastleFileFormat.FILL_STYLE_PATTERN, fillIndex));
        newDrawing.add(new CastleDocument.Polygon(points));

        Castle updatedCastle = new Castle(oldCastle.width(), oldCastle.cannons(), oldCastle.throne(),
                oldCastle.windVane(), oldCastle.money(), oldCastle.powder(), oldCastle.balls(),
                oldCastle.moneyRoom(), oldCastle.powderRoom(), oldCastle.ballRoom(), oldCastle.resources(),
                oldCastle.reserved(), newDrawing);
        List<Castle> castles = new ArrayList<>(document.castles());
        castles.set(castleIndex, updatedCastle);
        document = new CastleDocument(castles);
        castleView.updateCastle(updatedCastle);
        castleView.finishPolygonCreation();
        clearPolygonCreationHint();
        documentChanged();
        notifySelectionChanged();
    }

    public void cancelPolygonCreation() {
        castleView.cancelPolygonCreation();
        clearPolygonCreationHint();
    }

    private void setPolygonCreationHint() {
        if (statusBarListener != null) {
            statusBarListener.accept(HGBaseText.getText("polygon.hint"));
        }
    }

    private void clearPolygonCreationHint() {
        if (statusBarListener != null) {
            statusBarListener.accept("");
        }
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

    public void setChangeListener(Runnable changeListener) {
        this.changeListener = changeListener;
    }

    public void setHistoryGroupListeners(Runnable beginListener, Runnable endListener) {
        beginHistoryGroupListener = beginListener;
        endHistoryGroupListener = endListener;
    }

    public void beginHistoryGroup() {
        if (beginHistoryGroupListener != null) {
            beginHistoryGroupListener.run();
        }
    }

    public void endHistoryGroup() {
        if (endHistoryGroupListener != null) {
            endHistoryGroupListener.run();
        }
    }

    public void setZoomListener(Runnable zoomListener) {
        this.zoomListener = zoomListener;
    }

    public void setStatusBarListener(java.util.function.Consumer<String> statusBarListener) {
        this.statusBarListener = statusBarListener;
    }

    public double getZoomFactor() {
        return castleView.getZoomFactor();
    }

    public void setZoomPercent(int zoomPercent) {
        castleView.setZoomFactor(zoomPercent / 100.0);
    }

    public void updatePolygonPoint(int polygonIndex, int pointIndex, Point newPoint) {
        if (castleIndex < 0 || castleIndex >= getCastleCount()) {
            return;
        }
        Castle oldCastle = document.castles().get(castleIndex);
        List<CastleDocument.DrawingCommand> oldDrawing = oldCastle.drawing();
        
        int currentPolygonIndex = 0;
        List<CastleDocument.DrawingCommand> newDrawing = new ArrayList<>();
        for (CastleDocument.DrawingCommand command : oldDrawing) {
            if (command instanceof CastleDocument.Polygon polygon) {
                if (currentPolygonIndex == polygonIndex) {
                    List<Point> newPoints = new ArrayList<>(polygon.points());
                    newPoints.set(pointIndex, newPoint);
                    newDrawing.add(new CastleDocument.Polygon(newPoints));
                } else {
                    newDrawing.add(polygon);
                }
                currentPolygonIndex++;
            } else {
                newDrawing.add(command);
            }
        }
        
        Castle updatedCastle = new Castle(oldCastle.width(), oldCastle.cannons(), oldCastle.throne(),
                oldCastle.windVane(), oldCastle.money(), oldCastle.powder(), oldCastle.balls(),
                oldCastle.moneyRoom(), oldCastle.powderRoom(), oldCastle.ballRoom(), oldCastle.resources(),
                oldCastle.reserved(), newDrawing);
        List<Castle> castles = new ArrayList<>(document.castles());
        castles.set(castleIndex, updatedCastle);
        document = new CastleDocument(castles);
        castleView.updateCastle(updatedCastle);
        updateProblems(updatedCastle);
        if (changeListener != null) {
            changeListener.run();
        }
    }

    public void updatePolygonPoints(int polygonIndex, List<Point> newPoints) {
        if (castleIndex < 0 || castleIndex >= getCastleCount()) {
            return;
        }
        Castle oldCastle = document.castles().get(castleIndex);
        List<CastleDocument.DrawingCommand> oldDrawing = oldCastle.drawing();

        int currentPolygonIndex = 0;
        List<CastleDocument.DrawingCommand> newDrawing = new ArrayList<>();
        for (CastleDocument.DrawingCommand command : oldDrawing) {
            if (command instanceof CastleDocument.Polygon polygon) {
                if (currentPolygonIndex == polygonIndex) {
                    newDrawing.add(new CastleDocument.Polygon(newPoints));
                } else {
                    newDrawing.add(polygon);
                }
                currentPolygonIndex++;
            } else {
                newDrawing.add(command);
            }
        }

        Castle updatedCastle = new Castle(oldCastle.width(), oldCastle.cannons(), oldCastle.throne(),
                oldCastle.windVane(), oldCastle.money(), oldCastle.powder(), oldCastle.balls(),
                oldCastle.moneyRoom(), oldCastle.powderRoom(), oldCastle.ballRoom(), oldCastle.resources(),
                oldCastle.reserved(), newDrawing);
        List<Castle> castles = new ArrayList<>(document.castles());
        castles.set(castleIndex, updatedCastle);
        document = new CastleDocument(castles);
        castleView.updateCastle(updatedCastle);
        updateProblems(updatedCastle);
        if (changeListener != null) {
            changeListener.run();
        }
    }

    public void updateCannonPosition(int cannonIndex, Point newPosition) {
        if (castleIndex < 0 || castleIndex >= getCastleCount()) {
            return;
        }
        Castle oldCastle = document.castles().get(castleIndex);
        if (cannonIndex < 0 || cannonIndex >= oldCastle.cannons().size()) {
            return;
        }

        List<Point> newCannons = new ArrayList<>(oldCastle.cannons());
        newCannons.set(cannonIndex, newPosition);

        Castle updatedCastle = new Castle(oldCastle.width(), newCannons, oldCastle.throne(),
                oldCastle.windVane(), oldCastle.money(), oldCastle.powder(), oldCastle.balls(),
                oldCastle.moneyRoom(), oldCastle.powderRoom(), oldCastle.ballRoom(), oldCastle.resources(),
                oldCastle.reserved(), oldCastle.drawing());
        List<Castle> castles = new ArrayList<>(document.castles());
        castles.set(castleIndex, updatedCastle);
        document = new CastleDocument(castles);
        castleView.updateCastle(updatedCastle);
        updateProblems(updatedCastle);
        if (changeListener != null) {
            changeListener.run();
        }
    }

    public void updateThronePosition(Point newPosition) {
        if (castleIndex < 0 || castleIndex >= getCastleCount()) {
            return;
        }
        Castle oldCastle = document.castles().get(castleIndex);

        Castle updatedCastle = new Castle(oldCastle.width(), oldCastle.cannons(), newPosition,
                oldCastle.windVane(), oldCastle.money(), oldCastle.powder(), oldCastle.balls(),
                oldCastle.moneyRoom(), oldCastle.powderRoom(), oldCastle.ballRoom(), oldCastle.resources(),
                oldCastle.reserved(), oldCastle.drawing());
        List<Castle> castles = new ArrayList<>(document.castles());
        castles.set(castleIndex, updatedCastle);
        document = new CastleDocument(castles);
        castleView.updateCastle(updatedCastle);
        updateProblems(updatedCastle);
        if (changeListener != null) {
            changeListener.run();
        }
    }

    public void updateWindVanePosition(Point newPosition) {
        if (castleIndex < 0 || castleIndex >= getCastleCount()) {
            return;
        }
        Castle oldCastle = document.castles().get(castleIndex);

        Castle updatedCastle = new Castle(oldCastle.width(), oldCastle.cannons(), oldCastle.throne(),
                newPosition, oldCastle.money(), oldCastle.powder(), oldCastle.balls(),
                oldCastle.moneyRoom(), oldCastle.powderRoom(), oldCastle.ballRoom(), oldCastle.resources(),
                oldCastle.reserved(), oldCastle.drawing());
        List<Castle> castles = new ArrayList<>(document.castles());
        castles.set(castleIndex, updatedCastle);
        document = new CastleDocument(castles);
        castleView.updateCastle(updatedCastle);
        updateProblems(updatedCastle);
        if (changeListener != null) {
            changeListener.run();
        }
    }

    public void updateResourceAreaSize(String resourceArea, Point newPosition, Size newSize) {
        if (castleIndex < 0 || castleIndex >= getCastleCount()) {
            return;
        }
        Castle oldCastle = document.castles().get(castleIndex);

        Castle updatedCastle;
        switch (resourceArea) {
            case "money":
                updatedCastle = new Castle(oldCastle.width(), oldCastle.cannons(), oldCastle.throne(),
                        oldCastle.windVane(), newPosition, oldCastle.powder(), oldCastle.balls(),
                        newSize, oldCastle.powderRoom(), oldCastle.ballRoom(), oldCastle.resources(),
                        oldCastle.reserved(), oldCastle.drawing());
                break;
            case "powder":
                updatedCastle = new Castle(oldCastle.width(), oldCastle.cannons(), oldCastle.throne(),
                        oldCastle.windVane(), oldCastle.money(), newPosition, oldCastle.balls(),
                        oldCastle.moneyRoom(), newSize, oldCastle.ballRoom(), oldCastle.resources(),
                        oldCastle.reserved(), oldCastle.drawing());
                break;
            case "balls":
                updatedCastle = new Castle(oldCastle.width(), oldCastle.cannons(), oldCastle.throne(),
                        oldCastle.windVane(), oldCastle.money(), oldCastle.powder(), newPosition,
                        oldCastle.moneyRoom(), oldCastle.powderRoom(), newSize, oldCastle.resources(),
                        oldCastle.reserved(), oldCastle.drawing());
                break;
            default:
                return;
        }

        List<Castle> castles = new ArrayList<>(document.castles());
        castles.set(castleIndex, updatedCastle);
        document = new CastleDocument(castles);
        castleView.updateCastle(updatedCastle);
        propertyPalette.setCastle(updatedCastle);
        updateProblems(updatedCastle);
        if (changeListener != null) {
            changeListener.run();
        }
    }

    public void updateResourceAreaPosition(String resourceArea, Point newPosition) {
        if (castleIndex < 0 || castleIndex >= getCastleCount()) {
            return;
        }
        Castle oldCastle = document.castles().get(castleIndex);

        Castle updatedCastle;
        switch (resourceArea) {
            case "money":
                updatedCastle = new Castle(oldCastle.width(), oldCastle.cannons(), oldCastle.throne(),
                        oldCastle.windVane(), newPosition, oldCastle.powder(), oldCastle.balls(),
                        oldCastle.moneyRoom(), oldCastle.powderRoom(), oldCastle.ballRoom(), oldCastle.resources(),
                        oldCastle.reserved(), oldCastle.drawing());
                break;
            case "powder":
                updatedCastle = new Castle(oldCastle.width(), oldCastle.cannons(), oldCastle.throne(),
                        oldCastle.windVane(), oldCastle.money(), newPosition, oldCastle.balls(),
                        oldCastle.moneyRoom(), oldCastle.powderRoom(), oldCastle.ballRoom(), oldCastle.resources(),
                        oldCastle.reserved(), oldCastle.drawing());
                break;
            case "balls":
                updatedCastle = new Castle(oldCastle.width(), oldCastle.cannons(), oldCastle.throne(),
                        oldCastle.windVane(), oldCastle.money(), oldCastle.powder(), newPosition,
                        oldCastle.moneyRoom(), oldCastle.powderRoom(), oldCastle.ballRoom(), oldCastle.resources(),
                        oldCastle.reserved(), oldCastle.drawing());
                break;
            default:
                return;
        }

        List<Castle> castles = new ArrayList<>(document.castles());
        castles.set(castleIndex, updatedCastle);
        document = new CastleDocument(castles);
        castleView.updateCastle(updatedCastle);
        updateProblems(updatedCastle);
        if (changeListener != null) {
            changeListener.run();
        }
    }

    public boolean addPolygonPoint(int edgeIndex, Point newPoint) {
        CastleDocument.Polygon selectedPolygon = castleView.getSelectedPolygon();
        if (selectedPolygon == null) {
            return false;
        }
        
        int polygonIndex = findPolygonIndex(selectedPolygon);
        if (polygonIndex < 0) {
            return false;
        }
        
        List<Point> newPoints = new ArrayList<>(selectedPolygon.points());
        // Insert the new point after the edge index (edgeIndex is the index of the point where the edge starts)
        newPoints.add(edgeIndex + 1, newPoint);
        updatePolygonPoints(polygonIndex, newPoints);
        
        // Update the selected polygon reference to the new polygon
        Castle updatedCastle = document.castles().get(castleIndex);
        int currentPolygonIndex = 0;
        for (CastleDocument.DrawingCommand command : updatedCastle.drawing()) {
            if (command instanceof CastleDocument.Polygon polygon) {
                if (currentPolygonIndex == polygonIndex) {
                    castleView.setSelectedPolygon(polygon);
                    break;
                }
                currentPolygonIndex++;
            }
        }
        
        // Select the newly added point
        castleView.setSelectedPointIndex(edgeIndex + 1);
        onPointSelectionChanged();
        return true;
    }

    public boolean removePolygonPoint() {
        CastleDocument.Polygon selectedPolygon = castleView.getSelectedPolygon();
        if (selectedPolygon == null || selectedPolygon.points().size() <= 3) {
            return false;
        }
        
        int selectedPointIndex = castleView.getSelectedPointIndex();
        if (selectedPointIndex < 0) {
            return false;
        }
        
        int polygonIndex = findPolygonIndex(selectedPolygon);
        if (polygonIndex < 0) {
            return false;
        }
        
        List<Point> newPoints = new ArrayList<>(selectedPolygon.points());
        newPoints.remove(selectedPointIndex);
        updatePolygonPoints(polygonIndex, newPoints);
        
        // Update the selected polygon reference to the new polygon
        Castle updatedCastle = document.castles().get(castleIndex);
        int currentPolygonIndex = 0;
        for (CastleDocument.DrawingCommand command : updatedCastle.drawing()) {
            if (command instanceof CastleDocument.Polygon polygon) {
                if (currentPolygonIndex == polygonIndex) {
                    castleView.setSelectedPolygon(polygon);
                    break;
                }
                currentPolygonIndex++;
            }
        }
        
        // Clear the selected point after removal
        castleView.setSelectedPointIndex(-1);
        onPointSelectionChanged();
        return true;
    }

    private int findPolygonIndex(CastleDocument.Polygon targetPolygon) {
        if (castleIndex < 0 || castleIndex >= getCastleCount()) {
            return -1;
        }
        Castle castle = document.castles().get(castleIndex);
        int index = 0;
        for (CastleDocument.DrawingCommand command : castle.drawing()) {
            if (command instanceof CastleDocument.Polygon polygon) {
                if (polygon == targetPolygon) {
                    return index;
                }
                index++;
            }
        }
        return -1;
    }

    public boolean hasSelectedPolygonPoint() {
        if (castleView.getSelectedPointIndex() < 0) {
            return false;
        }
        CastleDocument.Polygon selectedPolygon = castleView.getSelectedPolygon();
        return selectedPolygon != null && selectedPolygon.points().size() > 3;
    }

    public boolean hasSelectedPoint() {
        return castleView.getSelectedPointIndex() >= 0;
    }

    public boolean removePolygon() {
        CastleDocument.Polygon selectedPolygon = castleView.getSelectedPolygon();
        if (selectedPolygon == null) {
            return false;
        }

        int polygonIndex = findPolygonIndex(selectedPolygon);
        if (polygonIndex < 0) {
            return false;
        }

        Castle oldCastle = document.castles().get(castleIndex);
        List<CastleDocument.DrawingCommand> newDrawing = new ArrayList<>();
        for (CastleDocument.DrawingCommand command : oldCastle.drawing()) {
            if (!(command instanceof CastleDocument.Polygon polygon) || polygon != selectedPolygon) {
                newDrawing.add(command);
            }
        }

        Castle updatedCastle = new Castle(oldCastle.width(), oldCastle.cannons(), oldCastle.throne(),
                oldCastle.windVane(), oldCastle.money(), oldCastle.powder(), oldCastle.balls(),
                oldCastle.moneyRoom(), oldCastle.powderRoom(), oldCastle.ballRoom(), oldCastle.resources(),
                oldCastle.reserved(), newDrawing);
        List<Castle> castles = new ArrayList<>(document.castles());
        castles.set(castleIndex, updatedCastle);
        document = new CastleDocument(castles);
        castleView.updateCastle(updatedCastle);
        castleView.setSelectedPolygon(null);
        updateProblems(updatedCastle);
        if (changeListener != null) {
            changeListener.run();
        }
        notifySelectionChanged();
        return true;
    }

    public boolean hasSelectedPolygon() {
        return castleView.getSelectedPolygon() != null;
    }

    public boolean hasSelectedCannon() {
        return castleView.getSelectedCannonIndex() >= 0;
    }

    public boolean hasSelectedThrone() {
        return castleView.isSelectedThrone();
    }

    public boolean hasSelectedWindVane() {
        return castleView.isSelectedWindVane();
    }

    public boolean hasSelectedResourceArea() {
        return castleView.getSelectedResourceArea() != null;
    }

    public String getSelectedResourceArea() {
        return castleView.getSelectedResourceArea();
    }

    public boolean removeCannon() {
        int selectedCannonIndex = castleView.getSelectedCannonIndex();
        if (selectedCannonIndex < 0) {
            return false;
        }

        if (castleIndex < 0 || castleIndex >= getCastleCount()) {
            return false;
        }

        Castle oldCastle = document.castles().get(castleIndex);
        if (selectedCannonIndex < 0 || selectedCannonIndex >= oldCastle.cannons().size()) {
            return false;
        }

        List<Point> newCannons = new ArrayList<>(oldCastle.cannons());
        newCannons.remove(selectedCannonIndex);

        Castle updatedCastle = new Castle(oldCastle.width(), newCannons, oldCastle.throne(),
                oldCastle.windVane(), oldCastle.money(), oldCastle.powder(), oldCastle.balls(),
                oldCastle.moneyRoom(), oldCastle.powderRoom(), oldCastle.ballRoom(), oldCastle.resources(),
                oldCastle.reserved(), oldCastle.drawing());
        List<Castle> castles = new ArrayList<>(document.castles());
        castles.set(castleIndex, updatedCastle);
        document = new CastleDocument(castles);
        castleView.updateCastle(updatedCastle);
        castleView.setSelectedCannonIndex(-1);
        updateProblems(updatedCastle);
        if (changeListener != null) {
            changeListener.run();
        }
        notifySelectionChanged();
        return true;
    }

    public boolean removeThrone() {
        if (!castleView.isSelectedThrone()) {
            return false;
        }

        if (castleIndex < 0 || castleIndex >= getCastleCount()) {
            return false;
        }

        Castle oldCastle = document.castles().get(castleIndex);
        Point unusedPosition = new Point(Integer.MIN_VALUE, Integer.MIN_VALUE);

        Castle updatedCastle = new Castle(oldCastle.width(), oldCastle.cannons(), unusedPosition,
                oldCastle.windVane(), oldCastle.money(), oldCastle.powder(), oldCastle.balls(),
                oldCastle.moneyRoom(), oldCastle.powderRoom(), oldCastle.ballRoom(), oldCastle.resources(),
                oldCastle.reserved(), oldCastle.drawing());
        List<Castle> castles = new ArrayList<>(document.castles());
        castles.set(castleIndex, updatedCastle);
        document = new CastleDocument(castles);
        castleView.updateCastle(updatedCastle);
        castleView.setSelectedThrone(false);
        updateProblems(updatedCastle);
        if (changeListener != null) {
            changeListener.run();
        }
        notifySelectionChanged();
        return true;
    }

    public boolean removeWindVane() {
        if (!castleView.isSelectedWindVane()) {
            return false;
        }

        if (castleIndex < 0 || castleIndex >= getCastleCount()) {
            return false;
        }

        Castle oldCastle = document.castles().get(castleIndex);
        Point unusedPosition = new Point(Integer.MIN_VALUE, Integer.MIN_VALUE);

        Castle updatedCastle = new Castle(oldCastle.width(), oldCastle.cannons(), oldCastle.throne(),
                unusedPosition, oldCastle.money(), oldCastle.powder(), oldCastle.balls(),
                oldCastle.moneyRoom(), oldCastle.powderRoom(), oldCastle.ballRoom(), oldCastle.resources(),
                oldCastle.reserved(), oldCastle.drawing());
        List<Castle> castles = new ArrayList<>(document.castles());
        castles.set(castleIndex, updatedCastle);
        document = new CastleDocument(castles);
        castleView.updateCastle(updatedCastle);
        castleView.setSelectedWindVane(false);
        updateProblems(updatedCastle);
        if (changeListener != null) {
            changeListener.run();
        }
        notifySelectionChanged();
        return true;
    }

    public boolean removeResourceArea() {
        String selectedResourceArea = castleView.getSelectedResourceArea();
        if (selectedResourceArea == null) {
            return false;
        }

        if (castleIndex < 0 || castleIndex >= getCastleCount()) {
            return false;
        }

        Castle oldCastle = document.castles().get(castleIndex);
        Point unusedPosition = new Point(Integer.MIN_VALUE, Integer.MIN_VALUE);
        Size emptySize = new Size(0, 0);
        Resources oldResources = oldCastle.resources();

        Castle updatedCastle;
        if ("money".equals(selectedResourceArea)) {
            Resources newResources = new Resources(0, oldResources.powder(), oldResources.balls(), oldResources.people());
            updatedCastle = new Castle(oldCastle.width(), oldCastle.cannons(), oldCastle.throne(),
                    oldCastle.windVane(), unusedPosition, oldCastle.powder(), oldCastle.balls(),
                    emptySize, oldCastle.powderRoom(), oldCastle.ballRoom(), newResources,
                    oldCastle.reserved(), oldCastle.drawing());
        } else if ("powder".equals(selectedResourceArea)) {
            Resources newResources = new Resources(oldResources.money(), 0, oldResources.balls(), oldResources.people());
            updatedCastle = new Castle(oldCastle.width(), oldCastle.cannons(), oldCastle.throne(),
                    oldCastle.windVane(), oldCastle.money(), unusedPosition, oldCastle.balls(),
                    oldCastle.moneyRoom(), emptySize, oldCastle.ballRoom(), newResources,
                    oldCastle.reserved(), oldCastle.drawing());
        } else if ("balls".equals(selectedResourceArea)) {
            Resources newResources = new Resources(oldResources.money(), oldResources.powder(), 0, oldResources.people());
            updatedCastle = new Castle(oldCastle.width(), oldCastle.cannons(), oldCastle.throne(),
                    oldCastle.windVane(), oldCastle.money(), oldCastle.powder(), unusedPosition,
                    oldCastle.moneyRoom(), oldCastle.powderRoom(), emptySize, newResources,
                    oldCastle.reserved(), oldCastle.drawing());
        } else {
            return false;
        }

        List<Castle> castles = new ArrayList<>(document.castles());
        castles.set(castleIndex, updatedCastle);
        document = new CastleDocument(castles);
        castleView.updateCastle(updatedCastle);
        propertyPalette.setCastle(updatedCastle);
        castleView.setSelectedResourceArea(null);
        updateProblems(updatedCastle);
        if (changeListener != null) {
            changeListener.run();
        }
        notifySelectionChanged();
        return true;
    }

    public void onPointSelectionChanged() {
        if (castleView.getSelectedPolygon() != null) {
            setPolygonSelectedHint();
        } else {
            clearPolygonSelectedHint();
        }
        notifySelectionChanged();
    }
    
    private void setPolygonSelectedHint() {
        if (statusBarListener != null) {
            statusBarListener.accept(HGBaseText.getText("polygon.selected_hint"));
        }
    }
    
    private void clearPolygonSelectedHint() {
        if (statusBarListener != null && !castleView.isInPolygonCreationMode()) {
            statusBarListener.accept("");
        }
    }

    public void onCannonSelectionChanged() {
        notifySelectionChanged();
    }
    
    private void onPolygonSelectionChanged() {
        if (castleView.getSelectedPolygon() != null) {
            setPolygonSelectedHint();
        } else {
            clearPolygonSelectedHint();
        }
    }

    private void setCastleIndex(int index) {
        if (index >= 0 && index < getCastleCount() && index != castleIndex) {
            castleIndex = index;
            updateSelectedCastle();
            notifySelectionChanged();
        }
    }

    private void updateSelectedCastle() {
        Castle castle = castleIndex >= 0 && castleIndex < getCastleCount()
                ? document.castles().get(castleIndex) : null;
        castleView.setCastle(castle);
        propertyPalette.setCastle(castle);
        updateProblems(castle);
    }

    private void updateProblems(Castle castle) {
        problemsPanel.setProblems(createProblems(castle));
    }

    private List<ProblemsPanel.Problem> createProblems(Castle castle) {
        List<ProblemsPanel.Problem> problems = new ArrayList<>();
        if (castle != null) {
            boolean wallPresent = hasWall(castle);
            addMissingProblem(problems, hasThrone(castle), "problems.missing_throne");
            addMissingProblem(problems, hasMoney(castle), "problems.missing_money");
            addMissingProblem(problems, hasBalls(castle), "problems.missing_balls");
            addMissingProblem(problems, hasPowder(castle), "problems.missing_powder");
            addMissingProblem(problems, hasWindVane(castle), "problems.missing_windvane");
            addMissingProblem(problems, wallPresent, "problems.missing_wall");
            if (wallPresent) {
                addMissingProblem(problems, isInsideWall(castle, castle.throne(),
                        new Size(CastleFileFormat.THRONE_WIDTH, CastleFileFormat.THRONE_HEIGHT)),
                        "problems.throne_outside_wall");
                addMissingProblem(problems, isInsideWall(castle, castle.money(), castle.moneyRoom()),
                        "problems.money_outside_wall");
                addMissingProblem(problems, isInsideWall(castle, castle.balls(), castle.ballRoom()),
                        "problems.balls_outside_wall");
                addMissingProblem(problems, isInsideWall(castle, castle.powder(), castle.powderRoom()),
                        "problems.powder_outside_wall");
            }
            addOverlapProblem(problems, castle.throne(),
                    new Size(CastleFileFormat.THRONE_WIDTH, CastleFileFormat.THRONE_HEIGHT),
                    castle.money(), castle.moneyRoom(), "problems.overlap_throne_money");
            addOverlapProblem(problems, castle.throne(),
                    new Size(CastleFileFormat.THRONE_WIDTH, CastleFileFormat.THRONE_HEIGHT),
                    castle.balls(), castle.ballRoom(), "problems.overlap_throne_balls");
            addOverlapProblem(problems, castle.throne(),
                    new Size(CastleFileFormat.THRONE_WIDTH, CastleFileFormat.THRONE_HEIGHT),
                    castle.powder(), castle.powderRoom(), "problems.overlap_throne_powder");
            addOverlapProblem(problems, castle.money(), castle.moneyRoom(),
                    castle.balls(), castle.ballRoom(), "problems.overlap_money_balls");
            addOverlapProblem(problems, castle.money(), castle.moneyRoom(),
                    castle.powder(), castle.powderRoom(), "problems.overlap_money_powder");
            addOverlapProblem(problems, castle.balls(), castle.ballRoom(),
                    castle.powder(), castle.powderRoom(), "problems.overlap_balls_powder");
            addCannonOverlapProblems(problems, castle);
            addWindVaneOverlapProblems(problems, castle);
            addPolygonOverlapProblems(problems, castle);
            addZeroWarning(problems, hasMoney(castle), castle.resources().money(), "problems.zero_money");
            addZeroWarning(problems, hasPowder(castle), castle.resources().powder(), "problems.zero_powder");
            addZeroWarning(problems, hasBalls(castle), castle.resources().balls(), "problems.zero_balls");
            addZeroWarning(problems, hasPeople(castle), castle.resources().people(), "problems.zero_people");
            if (castle.cannons().isEmpty()) {
                problems.add(new ProblemsPanel.Problem(ProblemsPanel.ProblemSeverity.WARNING,
                        HGBaseText.getText("problems.no_cannons")));
            }
        }
        return problems;
    }

    private void addMissingProblem(List<ProblemsPanel.Problem> problems, boolean present, String textKey) {
        if (!present) {
            problems.add(new ProblemsPanel.Problem(ProblemsPanel.ProblemSeverity.ERROR,
                    HGBaseText.getText(textKey)));
        }
    }

    private void addZeroWarning(List<ProblemsPanel.Problem> problems, boolean areaPresent, int value,
                                String textKey) {
        if (areaPresent && value == 0) {
            problems.add(new ProblemsPanel.Problem(ProblemsPanel.ProblemSeverity.WARNING,
                    HGBaseText.getText(textKey)));
        }
    }

    private boolean hasThrone(Castle castle) {
        return hasPoint(castle.throne());
    }

    private boolean hasWindVane(Castle castle) {
        return hasPoint(castle.windVane());
    }

    private boolean hasMoney(Castle castle) {
        return hasPoint(castle.money());
    }

    private boolean hasPowder(Castle castle) {
        return hasPoint(castle.powder());
    }

    private boolean hasBalls(Castle castle) {
        return hasPoint(castle.balls());
    }

    private boolean hasPeople(Castle castle) {
        return castle.resources().people() >= 0;
    }

    private boolean hasPoint(Point point) {
        return point.x() != Integer.MIN_VALUE && point.y() != Integer.MIN_VALUE;
    }

    private void addOverlapProblem(List<ProblemsPanel.Problem> problems, Point firstPosition, Size firstSize,
                                   Point secondPosition, Size secondSize, String textKey) {
        if (hasPoint(firstPosition) && hasPoint(secondPosition)
                && rectanglesOverlap(firstPosition, firstSize, secondPosition, secondSize)) {
            problems.add(new ProblemsPanel.Problem(ProblemsPanel.ProblemSeverity.ERROR,
                    HGBaseText.getText(textKey)));
        }
    }

    private void addCannonOverlapProblems(List<ProblemsPanel.Problem> problems, Castle castle) {
        Size cannonSize = new Size(CastleFileFormat.CANNON_WIDTH, CastleFileFormat.CANNON_HEIGHT);
        for (int index = 0; index < castle.cannons().size(); index++) {
            Point cannon = castle.cannons().get(index);
            addOverlapProblem(problems, cannon, cannonSize, castle.throne(),
                    new Size(CastleFileFormat.THRONE_WIDTH, CastleFileFormat.THRONE_HEIGHT),
                    "problems.overlap_cannon_throne");
            addOverlapProblem(problems, cannon, cannonSize, castle.money(), castle.moneyRoom(),
                    "problems.overlap_cannon_money");
            addOverlapProblem(problems, cannon, cannonSize, castle.balls(), castle.ballRoom(),
                    "problems.overlap_cannon_balls");
            addOverlapProblem(problems, cannon, cannonSize, castle.powder(), castle.powderRoom(),
                    "problems.overlap_cannon_powder");
            addPolygonOverlapProblems(problems, cannon, cannonSize, castle,
                    "problems.overlap_cannon_wall", "problems.overlap_cannon_roof");
            for (int otherIndex = index + 1; otherIndex < castle.cannons().size(); otherIndex++) {
                addOverlapProblem(problems, cannon, cannonSize, castle.cannons().get(otherIndex), cannonSize,
                        "problems.overlap_cannon_cannon");
            }
        }
    }

    private void addWindVaneOverlapProblems(List<ProblemsPanel.Problem> problems, Castle castle) {
        Point windVane = castle.windVane();
        Size windVaneSize = new Size(CastleFileFormat.WIND_VANE_WIDTH, CastleFileFormat.WIND_VANE_HEIGHT);
        addOverlapProblem(problems, windVane, windVaneSize, castle.throne(),
                new Size(CastleFileFormat.THRONE_WIDTH, CastleFileFormat.THRONE_HEIGHT),
                "problems.overlap_windvane_throne");
        addOverlapProblem(problems, windVane, windVaneSize, castle.money(), castle.moneyRoom(),
                "problems.overlap_windvane_money");
        addOverlapProblem(problems, windVane, windVaneSize, castle.balls(), castle.ballRoom(),
                "problems.overlap_windvane_balls");
        addOverlapProblem(problems, windVane, windVaneSize, castle.powder(), castle.powderRoom(),
                "problems.overlap_windvane_powder");
        for (Point cannon : castle.cannons()) {
            addOverlapProblem(problems, windVane, windVaneSize, cannon,
                    new Size(CastleFileFormat.CANNON_WIDTH, CastleFileFormat.CANNON_HEIGHT),
                    "problems.overlap_windvane_cannon");
        }
        addPolygonOverlapProblems(problems, windVane, windVaneSize, castle,
                "problems.overlap_windvane_wall", "problems.overlap_windvane_roof");
    }

    private void addPolygonOverlapProblems(List<ProblemsPanel.Problem> problems, Castle castle) {
        List<PolygonInfo> polygons = polygonInfos(castle);
        for (int firstIndex = 0; firstIndex < polygons.size(); firstIndex++) {
            for (int secondIndex = firstIndex + 1; secondIndex < polygons.size(); secondIndex++) {
                if (polygonsOverlap(polygons.get(firstIndex), polygons.get(secondIndex))) {
                    problems.add(new ProblemsPanel.Problem(ProblemsPanel.ProblemSeverity.ERROR,
                            HGBaseText.getText(polygonOverlapTextKey(
                                    polygons.get(firstIndex), polygons.get(secondIndex)))));
                }
            }
        }
    }

    private List<PolygonInfo> polygonInfos(Castle castle) {
        List<PolygonInfo> polygons = new ArrayList<>();
        int fillStyle = CastleFileFormat.FILL_STYLE_PATTERN;
        int fillIndex = CastleFileFormat.FILL_INDEX_MASONRY;
        for (CastleDocument.DrawingCommand command : castle.drawing()) {
            if (command instanceof CastleDocument.FillStyle(int style, int index)) {
                fillStyle = style;
                fillIndex = index;
            } else if (command instanceof CastleDocument.Polygon polygon) {
                boolean wall = fillStyle == CastleFileFormat.FILL_STYLE_PATTERN
                        && fillIndex == CastleFileFormat.FILL_INDEX_MASONRY;
                polygons.add(new PolygonInfo(polygon, wall));
            }
        }
        return polygons;
    }

    private String polygonOverlapTextKey(PolygonInfo first, PolygonInfo second) {
        if (first.wall() && second.wall()) {
            return "problems.overlap_wall_wall";
        }
        if (!first.wall() && !second.wall()) {
            return "problems.overlap_roof_roof";
        }
        return "problems.overlap_wall_roof";
    }

    private void addPolygonOverlapProblems(List<ProblemsPanel.Problem> problems, Point position, Size size,
                                           Castle castle, String wallTextKey, String roofTextKey) {
        if (!hasPoint(position)) {
            return;
        }
        Rectangle2D rectangle = new Rectangle2D.Double(position.x(), position.y(), size.width(), size.height());
        int fillStyle = CastleFileFormat.FILL_STYLE_PATTERN;
        int fillIndex = CastleFileFormat.FILL_INDEX_MASONRY;
        for (CastleDocument.DrawingCommand command : castle.drawing()) {
            if (command instanceof CastleDocument.FillStyle(int style, int index)) {
                fillStyle = style;
                fillIndex = index;
            } else if (command instanceof CastleDocument.Polygon polygon
                    && polygonIntersects(polygon, rectangle)) {
                String textKey = fillStyle == CastleFileFormat.FILL_STYLE_PATTERN
                        && fillIndex == CastleFileFormat.FILL_INDEX_MASONRY
                        ? wallTextKey : roofTextKey;
                problems.add(new ProblemsPanel.Problem(ProblemsPanel.ProblemSeverity.ERROR,
                        HGBaseText.getText(textKey)));
            }
        }
    }

    private boolean rectanglesOverlap(Point firstPosition, Size firstSize,
                                      Point secondPosition, Size secondSize) {
        return new Rectangle2D.Double(firstPosition.x(), firstPosition.y(),
                firstSize.width(), firstSize.height()).intersects(
                secondPosition.x(), secondPosition.y(), secondSize.width(), secondSize.height());
    }

    private boolean isInsideWall(Castle castle, Point position, Size size) {
        if (!hasPoint(position)) {
            return true;
        }
        Rectangle2D area = new Rectangle2D.Double(position.x(), position.y(), size.width(), size.height());
        int fillStyle = CastleFileFormat.FILL_STYLE_PATTERN;
        int fillIndex = CastleFileFormat.FILL_INDEX_MASONRY;
        for (CastleDocument.DrawingCommand command : castle.drawing()) {
            if (command instanceof CastleDocument.FillStyle(int style, int index)) {
                fillStyle = style;
                fillIndex = index;
            } else if (command instanceof CastleDocument.Polygon polygon) {
                if (fillStyle == CastleFileFormat.FILL_STYLE_PATTERN
                        && fillIndex == CastleFileFormat.FILL_INDEX_MASONRY
                        && polygonContains(polygon, area)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean polygonContains(CastleDocument.Polygon polygon, Rectangle2D area) {
        Path2D path = new Path2D.Double();
        Point first = polygon.points().get(0);
        path.moveTo(first.x(), first.y());
        for (int index = 1; index < polygon.points().size(); index++) {
            Point point = polygon.points().get(index);
            path.lineTo(point.x(), point.y());
        }
        path.closePath();
        return path.contains(area);
    }

    private boolean polygonIntersects(CastleDocument.Polygon polygon, Rectangle2D rectangle) {
        Path2D path = new Path2D.Double();
        Point first = polygon.points().get(0);
        path.moveTo(first.x(), first.y());
        for (int index = 1; index < polygon.points().size(); index++) {
            Point point = polygon.points().get(index);
            path.lineTo(point.x(), point.y());
        }
        path.closePath();
        return path.intersects(rectangle);
    }

    private boolean polygonsOverlap(PolygonInfo first, PolygonInfo second) {
        Area intersection = polygonArea(first.polygon());
        intersection.intersect(polygonArea(second.polygon()));
        return !intersection.isEmpty();
    }

    private Area polygonArea(CastleDocument.Polygon polygon) {
        Path2D path = new Path2D.Double();
        Point first = polygon.points().get(0);
        path.moveTo(first.x(), first.y());
        for (int index = 1; index < polygon.points().size(); index++) {
            Point point = polygon.points().get(index);
            path.lineTo(point.x(), point.y());
        }
        path.closePath();
        return new Area(path);
    }

    private record PolygonInfo(CastleDocument.Polygon polygon, boolean wall) {
    }

    private void updateResources(Resources resources) {
        if (castleIndex < 0 || castleIndex >= getCastleCount()) {
            return;
        }
        Castle oldCastle = document.castles().get(castleIndex);
        if (oldCastle.resources().equals(resources)) {
            return;
        }
        Castle updatedCastle = new Castle(oldCastle.width(), oldCastle.cannons(), oldCastle.throne(),
                oldCastle.windVane(), oldCastle.money(), oldCastle.powder(), oldCastle.balls(),
                oldCastle.moneyRoom(), oldCastle.powderRoom(), oldCastle.ballRoom(), resources,
                oldCastle.reserved(), oldCastle.drawing());
        List<Castle> castles = new ArrayList<>(document.castles());
        castles.set(castleIndex, updatedCastle);
        document = new CastleDocument(castles);
        castleView.updateCastle(updatedCastle);
        updateProblems(updatedCastle);
        if (changeListener != null) {
            changeListener.run();
        }
    }

    private void notifySelectionChanged() {
        if (selectionListener != null) {
            selectionListener.run();
        }
    }

    private void documentChanged() {
        updateSelectedCastle();
        notifySelectionChanged();
        if (changeListener != null) {
            changeListener.run();
        }
    }

    private Castle createEmptyCastle() {
        Point origin = new Point(0, 0);
        Size emptySize = new Size(0, 0);
        return new Castle(0, List.of(), origin, origin, origin, origin, origin,
                emptySize, emptySize, emptySize, new Resources(0, 0, 0, 0),
                List.of(0, 0, 0, 0), List.of());
    }
}
