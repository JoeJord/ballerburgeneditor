package at.josefjordan.ballerburg.editor.model;

/** Constants defined by the original BALLER.DAT file format. */
public final class CastleFileFormat {
    public static final int FILE_TERMINATOR = -999;
    public static final int CASTLE_TERMINATOR = -1;
    public static final int FILL_STYLE_COMMAND = -2;
    public static final int POLYGON_TERMINATOR = -9;

    public static final int FILL_STYLE_PATTERN = 2;
    public static final int FILL_INDEX_MASONRY = 9;
    public static final int FILL_INDEX_ROOF = 11;

    public static final int UNUSED_CANNON_COORDINATE = -1;

    public static final int MONEY_PER_UNIT = 150;
    public static final int POWDER_PER_UNIT = 30;

    public static final int CANNON_POSITION_COUNT = 10;
    public static final int CANNON_WIDTH = 20;
    public static final int CANNON_HEIGHT = 12;
    public static final int RESERVED_VALUE_COUNT = 4;
    public static final int COORDINATE_COMPONENT_COUNT = 2;
    public static final int THRONE_WIDTH = 30;
    public static final int THRONE_HEIGHT = 25;
    public static final int CANNONBALL_WIDTH = 6;
    public static final int CANNONBALL_HEIGHT = 6;
    public static final int POWDER_WIDTH = 9;
    public static final int POWDER_HEIGHT = 9;
    public static final int MONEY_WIDTH = 7;
    public static final int MONEY_HEIGHT = 10;
    public static final int FLAG_WIDTH = 30;
    public static final int FLAG_HEIGHT = 15;
    public static final int FLAG_POLE_LENGTH = 13;

    private CastleFileFormat() {
    }
}
