package at.josefjordan.ballerburg.editor.ui.editor.castle;

import at.josefjordan.ballerburg.editor.model.CastleDocument.Castle;
import at.josefjordan.ballerburg.editor.model.CastleDocument.Point;
import at.josefjordan.ballerburg.editor.model.CastleDocument.Resources;

import java.util.ArrayList;
import java.util.List;

/**
 * Creates updated castle snapshots for cannon, throne, wind-vane, and resource-value edits.
 */
public final class CastleElementEditor {
    /**
     * Creates an editor for immutable castle-element updates.
     */
    public CastleElementEditor() {
        // Noting to do.
    }

    /**
     * Adds a cannon at the castle origin.
     *
     * @param castle castle snapshot to update
     * @return updated castle snapshot
     */
    public Castle addCannon(Castle castle) {
        List<Point> cannons = new ArrayList<>(castle.cannons());
        cannons.add(new Point(0, 0));
        return copyWith(castle, cannons, castle.throne(), castle.windVane(), castle.resources());
    }

    /**
     * Removes the cannon at the specified index.
     *
     * @param castle      castle snapshot to update
     * @param cannonIndex index of the cannon to remove
     * @return updated castle snapshot
     */
    public Castle removeCannon(Castle castle, int cannonIndex) {
        List<Point> cannons = new ArrayList<>(castle.cannons());
        cannons.remove(cannonIndex);
        return copyWith(castle, cannons, castle.throne(), castle.windVane(), castle.resources());
    }

    /**
     * Repositions the cannon at the specified index.
     *
     * @param castle      castle snapshot to update
     * @param cannonIndex index of the cannon to move
     * @param position    replacement position in castle coordinates
     * @return updated castle snapshot
     */
    public Castle moveCannon(Castle castle, int cannonIndex, Point position) {
        List<Point> cannons = new ArrayList<>(castle.cannons());
        cannons.set(cannonIndex, position);
        return copyWith(castle, cannons, castle.throne(), castle.windVane(), castle.resources());
    }

    /**
     * Sets the throne's lower-left position.
     *
     * @param castle   castle snapshot to update
     * @param position replacement position in castle coordinates
     * @return updated castle snapshot
     */
    public Castle setThronePosition(Castle castle, Point position) {
        return copyWith(castle, castle.cannons(), position, castle.windVane(), castle.resources());
    }

    /**
     * Sets the wind vane's lower-left position.
     *
     * @param castle   castle snapshot to update
     * @param position replacement position in castle coordinates
     * @return updated castle snapshot
     */
    public Castle setWindVanePosition(Castle castle, Point position) {
        return copyWith(castle, castle.cannons(), castle.throne(), position, castle.resources());
    }

    /**
     * Replaces the starting resource quantities.
     *
     * @param castle    castle snapshot to update
     * @param resources replacement resource values
     * @return updated castle snapshot
     */
    public Castle updateResources(Castle castle, Resources resources) {
        return copyWith(castle, castle.cannons(), castle.throne(), castle.windVane(), resources);
    }

    /**
     * Marks the throne position as unset.
     *
     * @param castle castle snapshot to update
     * @return updated castle snapshot
     */
    public Castle removeThrone(Castle castle) {
        return setThronePosition(castle, unsetPosition());
    }

    /**
     * Marks the wind-vane position as unset.
     *
     * @param castle castle snapshot to update
     * @return updated castle snapshot
     */
    public Castle removeWindVane(Castle castle) {
        return setWindVanePosition(castle, unsetPosition());
    }

    /**
     * Copies a castle while replacing its cannon list, throne, wind vane, or starting resources.
     *
     * @param castle    source castle snapshot supplying all unchanged values
     * @param cannons   replacement cannon positions
     * @param throne    replacement throne position
     * @param windVane  replacement wind-vane position
     * @param resources replacement starting resource quantities
     * @return updated castle snapshot
     */
    private Castle copyWith(Castle castle, List<Point> cannons, Point throne, Point windVane, Resources resources) {
        return new Castle(castle.width(), cannons, throne, windVane, castle.money(), castle.powder(),
                castle.balls(), castle.moneyRoom(), castle.powderRoom(), castle.ballRoom(), resources,
                castle.reserved(), castle.drawing());
    }

    /**
     * Creates the sentinel coordinate used to represent an absent castle element.
     *
     * @return a point whose coordinates are both {@link Integer#MIN_VALUE}
     */
    private Point unsetPosition() {
        return Point.unset();
    }
}
