package at.josefjordan.ballerburg.editor.ui.editor;

import at.josefjordan.ballerburg.editor.model.CastleDocument.Castle;
import at.josefjordan.ballerburg.editor.model.CastleDocument.Point;
import at.josefjordan.ballerburg.editor.model.CastleDocument.Resources;
import at.josefjordan.ballerburg.editor.model.CastleDocument.Size;
import at.josefjordan.ballerburg.editor.model.CastleFileFormat;
import at.josefjordan.ballerburg.editor.ui.editor.castle.CastleElementEditor;
import at.josefjordan.ballerburg.editor.ui.editor.castle.CastlePolygonEditor;
import at.josefjordan.ballerburg.editor.ui.editor.castle.CastlePolygonType;
import at.josefjordan.ballerburg.editor.ui.editor.castle.CastleViewPanel;
import at.josefjordan.ballerburg.editor.ui.editor.castle.ResourceArea;
import at.josefjordan.ballerburg.editor.ui.editor.castle.ResourceAreaEditor;
import hgb.lib.HGBaseText;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Implements castle-element creation and editing actions for the editor panel.
 */
final class EditorCastleActions {
    /**
     * Castle canvas whose interactive polygon-creation mode is controlled by these actions.
     */
    private final CastleViewPanel castleView;
    /**
     * Creates immutable snapshots for cannon, throne, wind-vane, and resource edits.
     */
    private final CastleElementEditor castleElementEditor;
    /**
     * Creates immutable snapshots for resource-storage edits.
     */
    private final ResourceAreaEditor resourceAreaEditor;
    /**
     * Creates immutable snapshots for polygon edits.
     */
    private final CastlePolygonEditor polygonEditor;
    /**
     * Supplies the current castle and handles commits and status-bar updates.
     */
    private final Callbacks callbacks;

    /**
     * Connections back to document state and editor refresh behavior.
     *
     * @param selectedCastleSupplier supplies the selected castle or {@code null}
     * @param creationCommitter      commits a creation edit and applies creation-specific view updates
     * @param editCommitter          commits a continuous edit; second argument requests property-palette refresh
     * @param statusBarUpdater       updates the status-bar message
     */
    record Callbacks(Supplier<Castle> selectedCastleSupplier,
                     Consumer<Castle> creationCommitter,
                     BiConsumer<Castle, Boolean> editCommitter,
                     Consumer<String> statusBarUpdater) {
    }

    /**
     * Creates the action coordinator and connects its model editors and callbacks.
     *
     * @param castleView          castle canvas used for polygon creation
     * @param castleElementEditor editor for non-resource castle elements
     * @param resourceAreaEditor  editor for resource storage areas
     * @param polygonEditor       editor for castle polygons
     * @param callbacks           suppliers and handlers for selected-castle operations
     */
    EditorCastleActions(CastleViewPanel castleView, CastleElementEditor castleElementEditor, ResourceAreaEditor resourceAreaEditor, CastlePolygonEditor polygonEditor, Callbacks callbacks) {
        this.castleView = castleView;
        this.castleElementEditor = castleElementEditor;
        this.resourceAreaEditor = resourceAreaEditor;
        this.polygonEditor = polygonEditor;
        this.callbacks = callbacks;
    }

    /**
     * Adds a cannon at the origin unless the castle has no selection or all cannon slots are occupied.
     */
    void addCannon() {
        Castle castle = selectedCastle();
        if (castle == null || castle.cannons().size() >= CastleFileFormat.CANNON_POSITION_COUNT) {
            return;
        }
        callbacks.creationCommitter().accept(castleElementEditor.addCannon(castle));
    }

    /**
     * Adds a throne at the origin if the selected castle does not already have one.
     */
    void addThrone() {
        Castle castle = selectedCastle();
        if (castle == null || isPositioned(castle.throne())) {
            return;
        }
        callbacks.creationCommitter().accept(castleElementEditor.setThronePosition(castle, new Point(0, 0)));
    }

    /**
     * Adds a wind vane at the origin if the selected castle does not already have one.
     */
    void addWindVane() {
        Castle castle = selectedCastle();
        if (castle == null || isPositioned(castle.windVane())) {
            return;
        }
        callbacks.creationCommitter().accept(castleElementEditor.setWindVanePosition(castle, new Point(0, 0)));
    }

    /**
     * Adds a resource-storage area at the origin if it is not already positioned.
     *
     * @param area resource area to add
     */
    void addResourceArea(ResourceArea area) {
        Castle castle = selectedCastle();
        if (castle == null || isResourceAreaPositioned(castle, area)) {
            return;
        }
        callbacks.creationCommitter().accept(resourceAreaEditor.add(castle, area));
    }

    /**
     * Starts interactive creation of a wall polygon for the selected castle.
     */
    void addWall() {
        startPolygonCreation(CastlePolygonType.WALL);
    }

    /**
     * Starts interactive creation of a roof polygon for the selected castle.
     */
    void addRoof() {
        startPolygonCreation(CastlePolygonType.ROOF);
    }

    /**
     * Commits a polygon with at least three points, or ends creation without committing it.
     *
     * @param points ordered polygon vertices in castle coordinates
     */
    void finishPolygonCreation(List<Point> points) {
        Castle castle = selectedCastle();
        if (castle == null || points.size() < 3) {
            castleView.finishPolygonCreation();
            callbacks.statusBarUpdater().accept("");
            return;
        }
        Castle updatedCastle = polygonEditor.add(castle, points, castleView.getPolygonType());
        castleView.finishPolygonCreation();
        callbacks.statusBarUpdater().accept("");
        callbacks.creationCommitter().accept(updatedCastle);
    }

    /**
     * Cancels interactive polygon creation and clears its status-bar hint.
     */
    void cancelPolygonCreation() {
        castleView.cancelPolygonCreation();
        callbacks.statusBarUpdater().accept("");
    }

    /**
     * Replaces one vertex of a polygon in the selected castle.
     *
     * @param polygonIndex zero-based polygon index
     * @param pointIndex   zero-based vertex index within the polygon
     * @param newPoint     replacement vertex in castle coordinates
     */
    void updatePolygonPoint(int polygonIndex, int pointIndex, Point newPoint) {
        updateCastle(castle -> polygonEditor.updatePoint(castle, polygonIndex, pointIndex, newPoint), false);
    }

    /**
     * Replaces all vertices of a polygon in the selected castle.
     *
     * @param polygonIndex zero-based polygon index
     * @param newPoints    replacement vertices in order
     */
    void updatePolygonPoints(int polygonIndex, List<Point> newPoints) {
        updateCastle(castle -> polygonEditor.updatePoints(castle, polygonIndex, newPoints), false);
    }

    /**
     * Moves a cannon to a new position in the selected castle.
     *
     * @param cannonIndex zero-based cannon index
     * @param newPosition destination in castle coordinates
     */
    void updateCannonPosition(int cannonIndex, Point newPosition) {
        Castle castle = selectedCastle();
        if (castle == null || cannonIndex < 0 || cannonIndex >= castle.cannons().size()) {
            return;
        }
        callbacks.editCommitter().accept(castleElementEditor.moveCannon(castle, cannonIndex, newPosition), false);
    }

    /**
     * Sets the selected castle's throne position.
     *
     * @param newPosition destination in castle coordinates
     */
    void updateThronePosition(Point newPosition) {
        updateCastle(castle -> castleElementEditor.setThronePosition(castle, newPosition), false);
    }

    /**
     * Sets the selected castle's wind-vane position.
     *
     * @param newPosition destination in castle coordinates
     */
    void updateWindVanePosition(Point newPosition) {
        updateCastle(castle -> castleElementEditor.setWindVanePosition(castle, newPosition), false);
    }

    /**
     * Changes the position and size of a resource-storage area.
     *
     * @param area        resource area to resize
     * @param newPosition new lower-left position in castle coordinates
     * @param newSize     new area dimensions
     */
    void updateResourceAreaSize(ResourceArea area, Point newPosition, Size newSize) {
        updateCastle(castle -> resourceAreaEditor.resize(castle, area, newPosition, newSize), true);
    }

    /**
     * Moves a resource-storage area without changing its size.
     *
     * @param area        resource area to move
     * @param newPosition destination in castle coordinates
     */
    void updateResourceAreaPosition(ResourceArea area, Point newPosition) {
        updateCastle(castle -> resourceAreaEditor.move(castle, area, newPosition), false);
    }

    /**
     * Updates the selected castle's starting resource quantities.
     *
     * @param resources replacement resource quantities
     */
    void updateResources(Resources resources) {
        Castle castle = selectedCastle();
        if (castle == null || castle.resources().equals(resources)) {
            return;
        }
        callbacks.editCommitter().accept(castleElementEditor.updateResources(castle, resources), false);
    }

    /**
     * Returns the castle currently selected by the editor.
     *
     * @return selected castle, or {@code null} when no castle is selected
     */
    private Castle selectedCastle() {
        return callbacks.selectedCastleSupplier().get();
    }

    /**
     * Applies an immutable castle transformation and commits it when a castle is selected.
     *
     * @param edit                   transformation producing the updated castle
     * @param refreshPropertyPalette whether the edit requires refreshing the property palette
     */
    private void updateCastle(java.util.function.UnaryOperator<Castle> edit, boolean refreshPropertyPalette) {
        Castle castle = selectedCastle();
        if (castle != null) {
            callbacks.editCommitter().accept(edit.apply(castle), refreshPropertyPalette);
        }
    }

    /**
     * Enters polygon-creation mode when a castle is selected and displays its hint.
     *
     * @param polygonType polygon type understood by the castle canvas
     */
    private void startPolygonCreation(CastlePolygonType polygonType) {
        if (selectedCastle() == null) {
            return;
        }
        castleView.startPolygonCreation(polygonType);
        callbacks.statusBarUpdater().accept(HGBaseText.getText("polygon.hint"));
    }

    /**
     * Checks whether the specified resource area has already been placed.
     *
     * @param castle castle whose resource-area position is checked
     * @param area   resource area to check
     * @return {@code true} if the area has a defined position
     */
    private boolean isResourceAreaPositioned(Castle castle, ResourceArea area) {
        return switch (area) {
            case MONEY -> isPositioned(castle.money());
            case POWDER -> isPositioned(castle.powder());
            case BALLS -> isPositioned(castle.balls());
        };
    }

    /**
     * Checks whether a point is different from the model's unpositioned sentinel.
     *
     * @param point position to check
     * @return {@code true} if both coordinates are defined
     */
    private boolean isPositioned(Point point) {
        return point.isSet();
    }
}
