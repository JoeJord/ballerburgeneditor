package at.josefjordan.ballerburg.editor.model;

/**
 * Constants defined by the original BALLER.DAT file format.
 */
public final class CastleFileFormat {
    /**
     * Marker following the last castle in a BALLER.DAT file.
     */
    public static final int FILE_TERMINATOR = -999;
    /**
     * Marker ending one castle's drawing-command sequence.
     */
    public static final int CASTLE_TERMINATOR = -1;
    /**
     * Marker introducing a fill-style command and its two arguments.
     */
    public static final int FILL_STYLE_COMMAND = -2;
    /**
     * Marker ending one polygon's coordinate sequence.
     */
    public static final int POLYGON_TERMINATOR = -9;
    /**
     * Pattern fill-style value used by the editor's masonry and roof polygons.
     */
    public static final int FILL_STYLE_PATTERN = 2;
    /**
     * Fill index identifying masonry.
     */
    public static final int FILL_INDEX_MASONRY = 9;
    /**
     * Fill index identifying a roof.
     */
    public static final int FILL_INDEX_ROOF = 11;
    /**
     * File-format coordinate value used in both axes to mark an unused position.
     */
    public static final int UNUSED_CANNON_COORDINATE = -1;
    /**
     * Starting money represented by one money sprite in storage.
     */
    public static final int MONEY_PER_UNIT = 150;
    /**
     * Starting powder represented by one powder sprite in storage.
     */
    public static final int POWDER_PER_UNIT = 30;
    /**
     * Number of cannon-position pairs reserved in each castle record.
     */
    public static final int CANNON_POSITION_COUNT = 10;
    /**
     * Cannon sprite width in castle coordinates.
     */
    public static final int CANNON_WIDTH = 20;
    /**
     * Cannon sprite height in castle coordinates.
     */
    public static final int CANNON_HEIGHT = 12;
    /**
     * Number of reserved integer fields retained in each castle record.
     */
    public static final int RESERVED_VALUE_COUNT = 4;
    /**
     * Number of integer components in one x/y coordinate pair.
     */
    public static final int COORDINATE_COMPONENT_COUNT = 2;
    /**
     * Throne sprite width in castle coordinates.
     */
    public static final int THRONE_WIDTH = 30;
    /**
     * Throne sprite height in castle coordinates.
     */
    public static final int THRONE_HEIGHT = 25;
    /**
     * Width of one cannonball sprite slot in castle coordinates.
     */
    public static final int CANNONBALL_WIDTH = 6;
    /**
     * Height of one cannonball sprite slot in castle coordinates.
     */
    public static final int CANNONBALL_HEIGHT = 6;
    /**
     * Width of one powder sprite slot in castle coordinates.
     */
    public static final int POWDER_WIDTH = 9;
    /**
     * Height of one powder sprite slot in castle coordinates.
     */
    public static final int POWDER_HEIGHT = 9;
    /**
     * Width of one money sprite slot in castle coordinates.
     */
    public static final int MONEY_WIDTH = 7;
    /**
     * Height of one money sprite slot in castle coordinates.
     */
    public static final int MONEY_HEIGHT = 10;
    /**
     * Wind-vane sprite width in castle coordinates.
     */
    public static final int WIND_VANE_WIDTH = 11;
    /**
     * Wind-vane sprite height in castle coordinates.
     */
    public static final int WIND_VANE_HEIGHT = 13;

    /**
     * Prevents instantiation of this constants-only utility class.
     */
    private CastleFileFormat() {
    }
}
