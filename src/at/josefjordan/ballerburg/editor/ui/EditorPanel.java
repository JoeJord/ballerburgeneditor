package at.josefjordan.ballerburg.editor.ui;

import at.josefjordan.ballerburg.editor.model.BurgDocument;
import at.josefjordan.ballerburg.editor.model.BurgDocument.Castle;
import at.josefjordan.ballerburg.editor.model.BurgDocument.Point;
import at.josefjordan.ballerburg.editor.model.BurgDocument.Resources;
import at.josefjordan.ballerburg.editor.model.BurgDocument.Size;
import hgb.gui.HGBasePanel;

import javax.swing.JSplitPane;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;

/** Editor containing the castle view and the property palette. */
public class EditorPanel extends HGBasePanel {
    private final CastleViewPanel castleView = new CastleViewPanel();
    private final PropertyPalettePanel propertyPalette = new PropertyPalettePanel();
    private BurgDocument document;
    private int castleIndex = -1;
    private Runnable selectionListener;
    private Runnable changeListener;

    public EditorPanel() {
        super(new BorderLayout());
        castleView.setEditorPanel(this);
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, castleView, propertyPalette);
        splitPane.setContinuousLayout(true);
        splitPane.setResizeWeight(1.0);
        propertyPalette.setMinimumSize(new Dimension(200, 0));
        propertyPalette.setPreferredSize(new Dimension(230, 0));
        add(splitPane, BorderLayout.CENTER);
        propertyPalette.setResourcesListener(this::updateResources);
        updateSelectedCastle();
    }

    public BurgDocument getDocument() {
        return document;
    }

    public void setDocument(BurgDocument document) {
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

    public boolean addCastle() {
        if (document == null) {
            return false;
        }
        List<Castle> castles = new ArrayList<>(document.castles());
        castleIndex = castleIndex < 0 ? castles.size() : castleIndex + 1;
        castles.add(castleIndex, createEmptyCastle());
        document = new BurgDocument(castles);
        documentChanged();
        return true;
    }

    public boolean deleteCastle() {
        if (document == null || castleIndex < 0 || castleIndex >= getCastleCount()) {
            return false;
        }
        List<Castle> castles = new ArrayList<>(document.castles());
        castles.remove(castleIndex);
        document = new BurgDocument(castles);
        castleIndex = Math.min(castleIndex, castles.size() - 1);
        documentChanged();
        return true;
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

    public void updatePolygonPoint(int polygonIndex, int pointIndex, Point newPoint) {
        if (castleIndex < 0 || castleIndex >= getCastleCount()) {
            return;
        }
        Castle oldCastle = document.castles().get(castleIndex);
        List<BurgDocument.DrawingCommand> oldDrawing = oldCastle.drawing();
        
        int currentPolygonIndex = 0;
        List<BurgDocument.DrawingCommand> newDrawing = new ArrayList<>();
        for (BurgDocument.DrawingCommand command : oldDrawing) {
            if (command instanceof BurgDocument.Polygon polygon) {
                if (currentPolygonIndex == polygonIndex) {
                    List<Point> newPoints = new ArrayList<>(polygon.points());
                    newPoints.set(pointIndex, newPoint);
                    newDrawing.add(new BurgDocument.Polygon(newPoints));
                } else {
                    newDrawing.add(polygon);
                }
                currentPolygonIndex++;
            } else {
                newDrawing.add(command);
            }
        }
        
        Castle updatedCastle = new Castle(oldCastle.width(), oldCastle.cannons(), oldCastle.throne(),
                oldCastle.flag(), oldCastle.money(), oldCastle.powder(), oldCastle.balls(),
                oldCastle.moneyRoom(), oldCastle.powderRoom(), oldCastle.ballRoom(), oldCastle.resources(),
                oldCastle.reserved(), newDrawing);
        List<Castle> castles = new ArrayList<>(document.castles());
        castles.set(castleIndex, updatedCastle);
        document = new BurgDocument(castles);
        castleView.setCastle(updatedCastle);
        if (changeListener != null) {
            changeListener.run();
        }
    }

    public void updatePolygonPoints(int polygonIndex, List<Point> newPoints) {
        if (castleIndex < 0 || castleIndex >= getCastleCount()) {
            return;
        }
        Castle oldCastle = document.castles().get(castleIndex);
        List<BurgDocument.DrawingCommand> oldDrawing = oldCastle.drawing();
        
        int currentPolygonIndex = 0;
        List<BurgDocument.DrawingCommand> newDrawing = new ArrayList<>();
        for (BurgDocument.DrawingCommand command : oldDrawing) {
            if (command instanceof BurgDocument.Polygon polygon) {
                if (currentPolygonIndex == polygonIndex) {
                    newDrawing.add(new BurgDocument.Polygon(newPoints));
                } else {
                    newDrawing.add(polygon);
                }
                currentPolygonIndex++;
            } else {
                newDrawing.add(command);
            }
        }
        
        Castle updatedCastle = new Castle(oldCastle.width(), oldCastle.cannons(), oldCastle.throne(),
                oldCastle.flag(), oldCastle.money(), oldCastle.powder(), oldCastle.balls(),
                oldCastle.moneyRoom(), oldCastle.powderRoom(), oldCastle.ballRoom(), oldCastle.resources(),
                oldCastle.reserved(), newDrawing);
        List<Castle> castles = new ArrayList<>(document.castles());
        castles.set(castleIndex, updatedCastle);
        document = new BurgDocument(castles);
        castleView.setCastle(updatedCastle);
        if (changeListener != null) {
            changeListener.run();
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
                oldCastle.flag(), oldCastle.money(), oldCastle.powder(), oldCastle.balls(),
                oldCastle.moneyRoom(), oldCastle.powderRoom(), oldCastle.ballRoom(), resources,
                oldCastle.reserved(), oldCastle.drawing());
        List<Castle> castles = new ArrayList<>(document.castles());
        castles.set(castleIndex, updatedCastle);
        document = new BurgDocument(castles);
        castleView.setCastle(updatedCastle);
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
