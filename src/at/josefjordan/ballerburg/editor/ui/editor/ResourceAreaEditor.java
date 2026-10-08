package at.josefjordan.ballerburg.editor.ui.editor;

import at.josefjordan.ballerburg.editor.model.CastleDocument.Castle;
import at.josefjordan.ballerburg.editor.model.CastleDocument.Point;
import at.josefjordan.ballerburg.editor.model.CastleDocument.Resources;
import at.josefjordan.ballerburg.editor.model.CastleDocument.Size;
import at.josefjordan.ballerburg.editor.model.CastleFileFormat;

/**
 * Creates immutable castle snapshots with updated resource-storage data.
 */
final class ResourceAreaEditor {
    /**
     * Sentinel lower-left position used to mark an absent resource area.
     */
    private static final Point UNSET_POSITION = new Point(Integer.MIN_VALUE, Integer.MIN_VALUE);
    /**
     * Zero-sized dimensions used when a resource area is removed.
     */
    private static final Size EMPTY_SIZE = new Size(0, 0);

    /**
     * Adds a resource area at the origin with one slot of initial capacity.
     *
     * @param castle castle to update
     * @param area   resource area to create
     * @return updated castle snapshot
     */
    Castle add(Castle castle, ResourceArea area) {
        Point origin = new Point(0, 0);
        return withArea(castle, area, origin, slotSize(area), castle.resources());
    }

    /**
     * Repositions a resource area without changing its dimensions or resource quantity.
     *
     * @param castle   castle to update
     * @param area     resource area to move
     * @param position new lower-left position
     * @return updated castle snapshot
     */
    Castle move(Castle castle, ResourceArea area, Point position) {
        return withArea(castle, area, position, areaSize(castle, area), castle.resources());
    }

    /**
     * Repositions and resizes a resource area without changing its resource quantity.
     *
     * @param castle   castle to update
     * @param area     resource area to resize
     * @param position new lower-left position
     * @param size     new dimensions
     * @return updated castle snapshot
     */
    Castle resize(Castle castle, ResourceArea area, Point position, Size size) {
        return withArea(castle, area, position, size, castle.resources());
    }

    /**
     * Removes a resource area, clears its stored quantity, and resets its geometry.
     *
     * @param castle castle to update
     * @param area   resource area to remove
     * @return updated castle snapshot
     */
    Castle remove(Castle castle, ResourceArea area) {
        Resources oldResources = castle.resources();
        Resources newResources = switch (area) {
            case MONEY -> new Resources(0, oldResources.powder(), oldResources.balls(), oldResources.people());
            case POWDER -> new Resources(oldResources.money(), 0, oldResources.balls(), oldResources.people());
            case BALLS -> new Resources(oldResources.money(), oldResources.powder(), 0, oldResources.people());
        };
        return withArea(castle, area, UNSET_POSITION, EMPTY_SIZE, newResources);
    }

    /**
     * Copies the castle with only the selected resource area's position, dimensions, or resources replaced.
     *
     * @param castle    source castle snapshot
     * @param area      resource area to replace
     * @param position  replacement lower-left position
     * @param size      replacement dimensions
     * @param resources replacement starting resource values
     * @return updated castle snapshot
     */
    private Castle withArea(Castle castle, ResourceArea area, Point position, Size size, Resources resources) {
        return switch (area) {
            case MONEY -> new Castle(castle.width(), castle.cannons(), castle.throne(), castle.windVane(),
                    position, castle.powder(), castle.balls(), size, castle.powderRoom(), castle.ballRoom(),
                    resources, castle.reserved(), castle.drawing());
            case POWDER -> new Castle(castle.width(), castle.cannons(), castle.throne(), castle.windVane(),
                    castle.money(), position, castle.balls(), castle.moneyRoom(), size, castle.ballRoom(),
                    resources, castle.reserved(), castle.drawing());
            case BALLS -> new Castle(castle.width(), castle.cannons(), castle.throne(), castle.windVane(),
                    castle.money(), castle.powder(), position, castle.moneyRoom(), castle.powderRoom(), size,
                    resources, castle.reserved(), castle.drawing());
        };
    }

    /**
     * Returns the dimensions currently stored for a resource area.
     *
     * @param castle castle containing the resource area
     * @param area   resource area whose dimensions are requested
     * @return the area's dimensions in castle coordinates
     */
    private Size areaSize(Castle castle, ResourceArea area) {
        return switch (area) {
            case MONEY -> castle.moneyRoom();
            case POWDER -> castle.powderRoom();
            case BALLS -> castle.ballRoom();
        };
    }

    /**
     * Returns the dimensions of one storage slot for the selected resource type.
     *
     * @param area resource area whose slot dimensions are requested
     * @return slot dimensions in castle coordinates
     */
    private Size slotSize(ResourceArea area) {
        return switch (area) {
            case MONEY -> new Size(CastleFileFormat.MONEY_WIDTH, CastleFileFormat.MONEY_HEIGHT);
            case POWDER -> new Size(CastleFileFormat.POWDER_WIDTH, CastleFileFormat.POWDER_HEIGHT);
            case BALLS -> new Size(CastleFileFormat.CANNONBALL_WIDTH, CastleFileFormat.CANNONBALL_HEIGHT);
        };
    }
}
