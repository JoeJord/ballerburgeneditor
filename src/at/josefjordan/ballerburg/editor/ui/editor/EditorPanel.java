package at.josefjordan.ballerburg.editor.ui.editor;

import at.josefjordan.ballerburg.editor.model.CastleDocument;
import at.josefjordan.ballerburg.editor.model.CastleDocument.Castle;
import at.josefjordan.ballerburg.editor.model.CastleDocument.Point;
import at.josefjordan.ballerburg.editor.model.CastleDocument.Resources;
import at.josefjordan.ballerburg.editor.model.CastleDocument.Size;
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
    private CastleDocument document;
    private int castleIndex = -1;
    private Runnable selectionListener;
    private Runnable changeListener;
    private Runnable zoomListener;

    public EditorPanel() {
        super(new BorderLayout());
        castleView.setEditorPanel(this);
        castleView.setZoomListener(() -> {
            if (zoomListener != null) {
                zoomListener.run();
            }
        });
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, castleView, propertyPalette);
        splitPane.setContinuousLayout(true);
        splitPane.setResizeWeight(1.0);
        propertyPalette.setMinimumSize(new Dimension(200, 0));
        propertyPalette.setPreferredSize(new Dimension(230, 0));
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

    public void setZoomListener(Runnable zoomListener) {
        this.zoomListener = zoomListener;
    }

    public double getZoomFactor() {
        return castleView.getZoomFactor();
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
                oldCastle.flag(), oldCastle.money(), oldCastle.powder(), oldCastle.balls(),
                oldCastle.moneyRoom(), oldCastle.powderRoom(), oldCastle.ballRoom(), oldCastle.resources(),
                oldCastle.reserved(), newDrawing);
        List<Castle> castles = new ArrayList<>(document.castles());
        castles.set(castleIndex, updatedCastle);
        document = new CastleDocument(castles);
        castleView.updateCastle(updatedCastle);
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
                oldCastle.flag(), oldCastle.money(), oldCastle.powder(), oldCastle.balls(),
                oldCastle.moneyRoom(), oldCastle.powderRoom(), oldCastle.ballRoom(), oldCastle.resources(),
                oldCastle.reserved(), newDrawing);
        List<Castle> castles = new ArrayList<>(document.castles());
        castles.set(castleIndex, updatedCastle);
        document = new CastleDocument(castles);
        castleView.updateCastle(updatedCastle);
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
                oldCastle.flag(), oldCastle.money(), oldCastle.powder(), oldCastle.balls(),
                oldCastle.moneyRoom(), oldCastle.powderRoom(), oldCastle.ballRoom(), oldCastle.resources(),
                oldCastle.reserved(), oldCastle.drawing());
        List<Castle> castles = new ArrayList<>(document.castles());
        castles.set(castleIndex, updatedCastle);
        document = new CastleDocument(castles);
        castleView.updateCastle(updatedCastle);
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
                oldCastle.flag(), oldCastle.money(), oldCastle.powder(), oldCastle.balls(),
                oldCastle.moneyRoom(), oldCastle.powderRoom(), oldCastle.ballRoom(), oldCastle.resources(),
                oldCastle.reserved(), oldCastle.drawing());
        List<Castle> castles = new ArrayList<>(document.castles());
        castles.set(castleIndex, updatedCastle);
        document = new CastleDocument(castles);
        castleView.updateCastle(updatedCastle);
        if (changeListener != null) {
            changeListener.run();
        }
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
                oldCastle.flag(), oldCastle.money(), oldCastle.powder(), oldCastle.balls(),
                oldCastle.moneyRoom(), oldCastle.powderRoom(), oldCastle.ballRoom(), oldCastle.resources(),
                oldCastle.reserved(), newDrawing);
        List<Castle> castles = new ArrayList<>(document.castles());
        castles.set(castleIndex, updatedCastle);
        document = new CastleDocument(castles);
        castleView.updateCastle(updatedCastle);
        castleView.setSelectedPolygon(null);
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
                oldCastle.flag(), oldCastle.money(), oldCastle.powder(), oldCastle.balls(),
                oldCastle.moneyRoom(), oldCastle.powderRoom(), oldCastle.ballRoom(), oldCastle.resources(),
                oldCastle.reserved(), oldCastle.drawing());
        List<Castle> castles = new ArrayList<>(document.castles());
        castles.set(castleIndex, updatedCastle);
        document = new CastleDocument(castles);
        castleView.updateCastle(updatedCastle);
        castleView.setSelectedCannonIndex(-1);
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
                oldCastle.flag(), oldCastle.money(), oldCastle.powder(), oldCastle.balls(),
                oldCastle.moneyRoom(), oldCastle.powderRoom(), oldCastle.ballRoom(), oldCastle.resources(),
                oldCastle.reserved(), oldCastle.drawing());
        List<Castle> castles = new ArrayList<>(document.castles());
        castles.set(castleIndex, updatedCastle);
        document = new CastleDocument(castles);
        castleView.updateCastle(updatedCastle);
        castleView.setSelectedThrone(false);
        if (changeListener != null) {
            changeListener.run();
        }
        notifySelectionChanged();
        return true;
    }

    public void onPointSelectionChanged() {
        notifySelectionChanged();
    }

    public void onCannonSelectionChanged() {
        notifySelectionChanged();
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
        document = new CastleDocument(castles);
        castleView.updateCastle(updatedCastle);
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
