package at.josefjordan.ballerburg.editor.ui.editor;

import at.josefjordan.ballerburg.editor.model.CastleDocument.Castle;
import at.josefjordan.ballerburg.editor.model.CastleDocument.Point;
import at.josefjordan.ballerburg.editor.model.CastleDocument.Polygon;
import at.josefjordan.ballerburg.editor.ui.editor.castle.CastleElementEditor;
import at.josefjordan.ballerburg.editor.ui.editor.castle.CastlePolygonEditor;
import at.josefjordan.ballerburg.editor.ui.editor.castle.CastleViewPanel;
import at.josefjordan.ballerburg.editor.ui.editor.castle.ResourceArea;
import at.josefjordan.ballerburg.editor.ui.editor.castle.ResourceAreaEditor;
import hgb.lib.HGBaseText;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Coordinates selection queries, selection-dependent edits, and polygon-selection hints.
 */
final class EditorSelectionController {
    /**
     * Castle view whose current selection is queried and updated.
     */
    private final CastleViewPanel castleView;
    /**
     * Applies immutable edits to polygon drawing commands.
     */
    private final CastlePolygonEditor polygonEditor;
    /**
     * Creates updated snapshots when selected non-polygon elements are removed.
     */
    private final CastleElementEditor castleElementEditor;
    /**
     * Creates updated snapshots when selected resource areas are removed.
     */
    private final ResourceAreaEditor resourceAreaEditor;
    /**
     * Supplies callbacks into the owning editor panel.
     */
    private final Callbacks callbacks;

    /**
     * Collaboration callbacks required by selection-dependent editor actions.
     *
     * @param selectedCastleSupplier  supplies the selected castle, or {@code null} when none is selected
     * @param castleEditCommitter     commits an updated castle; its second argument requests palette refresh
     * @param selectionChangeNotifier notifies listeners that selection changed
     * @param statusBarUpdater        updates status-bar messages
     */
    record Callbacks(Supplier<Castle> selectedCastleSupplier,
                     BiConsumer<Castle, Boolean> castleEditCommitter,
                     Runnable selectionChangeNotifier,
                     Consumer<String> statusBarUpdater) {
    }

    /**
     * Creates a controller for selection-dependent editor behavior.
     *
     * @param castleView          view that owns selection state
     * @param polygonEditor       helper that transforms polygon drawing data
     * @param castleElementEditor helper that transforms castle-element data
     * @param resourceAreaEditor  helper that transforms resource-area data
     * @param callbacks           callbacks into the owning editor panel
     */
    EditorSelectionController(CastleViewPanel castleView, CastlePolygonEditor polygonEditor, CastleElementEditor castleElementEditor, ResourceAreaEditor resourceAreaEditor, Callbacks callbacks) {
        this.castleView = castleView;
        this.polygonEditor = polygonEditor;
        this.castleElementEditor = castleElementEditor;
        this.resourceAreaEditor = resourceAreaEditor;
        this.callbacks = callbacks;
    }

    /**
     * Returns whether a removable vertex is selected on a polygon with more than three vertices.
     *
     * @return {@code true} if the selected vertex can be removed without invalidating the polygon
     */
    boolean hasSelectedPolygonPoint() {
        if (castleView.getSelectedPointIndex() < 0) {
            return false;
        }
        Polygon selectedPolygon = castleView.getSelectedPolygon();
        return selectedPolygon != null && selectedPolygon.points().size() > 3;
    }

    /**
     * Returns whether any vertex index is selected.
     *
     * @return {@code true} if a polygon vertex is selected
     */
    boolean hasSelectedPoint() {
        return castleView.getSelectedPointIndex() >= 0;
    }

    /**
     * Returns whether a polygon is selected.
     *
     * @return {@code true} if the view has a selected polygon
     */
    boolean hasSelectedPolygon() {
        return castleView.getSelectedPolygon() != null;
    }

    /**
     * Returns whether a cannon is selected.
     *
     * @return {@code true} if the view has a selected cannon index
     */
    boolean hasSelectedCannon() {
        return castleView.getSelectedCannonIndex() >= 0;
    }

    /**
     * Returns whether the throne is selected.
     *
     * @return {@code true} if the throne is selected
     */
    boolean hasSelectedThrone() {
        return castleView.isSelectedThrone();
    }

    /**
     * Returns whether the wind vane is selected.
     *
     * @return {@code true} if the wind vane is selected
     */
    boolean hasSelectedWindVane() {
        return castleView.isSelectedWindVane();
    }

    /**
     * Returns whether a resource area is selected.
     *
     * @return {@code true} if the view has a selected resource area
     */
    boolean hasSelectedResourceArea() {
        return castleView.getSelectedResourceArea() != null;
    }

    /**
     * Inserts a vertex after the specified edge and selects the new vertex.
     *
     * @param edgeIndex index of the edge's starting vertex
     * @param newPoint  inserted vertex in castle coordinates
     */
    void addPolygonPoint(int edgeIndex, Point newPoint) {
        Polygon selectedPolygon = castleView.getSelectedPolygon();
        Castle castle = callbacks.selectedCastleSupplier().get();
        if (selectedPolygon == null || castle == null) {
            return;
        }
        int polygonIndex = polygonEditor.indexOf(castle, selectedPolygon);
        if (polygonIndex < 0) {
            return;
        }

        List<Point> points = new ArrayList<>(selectedPolygon.points());
        points.add(edgeIndex + 1, newPoint);
        callbacks.castleEditCommitter().accept(polygonEditor.updatePoints(castle, polygonIndex, points), false);

        Castle updatedCastle = callbacks.selectedCastleSupplier().get();
        castleView.setSelectedPolygon(polygonEditor.polygonAt(updatedCastle, polygonIndex));
        castleView.setSelectedPointIndex(edgeIndex + 1);
        onPointSelectionChanged();
    }

    /**
     * Removes the selected polygon vertex when at least three vertices will remain.
     */
    void removePolygonPoint() {
        Polygon selectedPolygon = castleView.getSelectedPolygon();
        if (selectedPolygon == null || selectedPolygon.points().size() <= 3) {
            return;
        }
        int selectedPointIndex = castleView.getSelectedPointIndex();
        if (selectedPointIndex < 0) {
            return;
        }

        Castle castle = callbacks.selectedCastleSupplier().get();
        if (castle == null) {
            return;
        }
        int polygonIndex = polygonEditor.indexOf(castle, selectedPolygon);
        if (polygonIndex < 0) {
            return;
        }

        List<Point> points = new ArrayList<>(selectedPolygon.points());
        points.remove(selectedPointIndex);
        callbacks.castleEditCommitter().accept(polygonEditor.updatePoints(castle, polygonIndex, points), false);

        Castle updatedCastle = callbacks.selectedCastleSupplier().get();
        castleView.setSelectedPolygon(polygonEditor.polygonAt(updatedCastle, polygonIndex));
        castleView.setSelectedPointIndex(-1);
        onPointSelectionChanged();
    }

    /**
     * Removes the selected polygon from the current castle.
     */
    void removePolygon() {
        Polygon selectedPolygon = castleView.getSelectedPolygon();
        Castle castle = callbacks.selectedCastleSupplier().get();
        if (selectedPolygon == null || castle == null || polygonEditor.indexOf(castle, selectedPolygon) < 0) {
            return;
        }
        callbacks.castleEditCommitter().accept(polygonEditor.remove(castle, selectedPolygon), false);
        castleView.setSelectedPolygon(null);
        callbacks.selectionChangeNotifier().run();
    }

    /**
     * Removes the selected cannon from the current castle.
     */
    void removeCannon() {
        int cannonIndex = castleView.getSelectedCannonIndex();
        Castle castle = callbacks.selectedCastleSupplier().get();
        if (cannonIndex < 0 || castle == null || cannonIndex >= castle.cannons().size()) {
            return;
        }
        Castle updatedCastle = castleElementEditor.removeCannon(castle, cannonIndex);
        castleView.setSelectedCannonIndex(-1);
        callbacks.castleEditCommitter().accept(updatedCastle, false);
        callbacks.selectionChangeNotifier().run();
    }

    /**
     * Marks the selected throne position as unset.
     */
    void removeThrone() {
        Castle castle = callbacks.selectedCastleSupplier().get();
        if (!castleView.isSelectedThrone() || castle == null) {
            return;
        }
        Castle updatedCastle = castleElementEditor.removeThrone(castle);
        castleView.setSelectedThrone(false);
        callbacks.castleEditCommitter().accept(updatedCastle, false);
        callbacks.selectionChangeNotifier().run();
    }

    /**
     * Marks the selected wind-vane position as unset.
     */
    void removeWindVane() {
        Castle castle = callbacks.selectedCastleSupplier().get();
        if (!castleView.isSelectedWindVane() || castle == null) {
            return;
        }
        Castle updatedCastle = castleElementEditor.removeWindVane(castle);
        castleView.setSelectedWindVane(false);
        callbacks.castleEditCommitter().accept(updatedCastle, false);
        callbacks.selectionChangeNotifier().run();
    }

    /**
     * Removes the selected resource area and refreshes the property palette.
     */
    void removeResourceArea() {
        ResourceArea selectedArea = castleView.getSelectedResourceArea();
        Castle castle = callbacks.selectedCastleSupplier().get();
        if (selectedArea == null || castle == null) {
            return;
        }
        Castle updatedCastle = resourceAreaEditor.remove(castle, selectedArea);
        castleView.setSelectedResourceArea(null);
        callbacks.castleEditCommitter().accept(updatedCastle, true);
        callbacks.selectionChangeNotifier().run();
    }

    /**
     * Updates the polygon-selection hint and notifies selection listeners.
     */
    void onPointSelectionChanged() {
        if (castleView.getSelectedPolygon() != null) {
            callbacks.statusBarUpdater().accept(HGBaseText.getText("polygon.selected_hint"));
        } else if (!castleView.isInPolygonCreationMode()) {
            callbacks.statusBarUpdater().accept("");
        }
        callbacks.selectionChangeNotifier().run();
    }

    /**
     * Notifies selection listeners that the cannon selection changed.
     */
    void onCannonSelectionChanged() {
        callbacks.selectionChangeNotifier().run();
    }

    /**
     * Updates the status-bar hint after polygon selection changes.
     */
    void onPolygonSelectionChanged() {
        if (castleView.getSelectedPolygon() != null) {
            callbacks.statusBarUpdater().accept(HGBaseText.getText("polygon.selected_hint"));
        } else if (!castleView.isInPolygonCreationMode()) {
            callbacks.statusBarUpdater().accept("");
        }
    }
}
