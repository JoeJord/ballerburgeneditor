package at.josefjordan.ballerburg.editor.model;

/** Constants defined by the original BALLER.DAT file format. */
public final class BurgenFileFormat {
    public static final int FILE_TERMINATOR = -999;
    public static final int CASTLE_TERMINATOR = -1;
    public static final int FILL_STYLE_COMMAND = -2;
    public static final int POLYGON_TERMINATOR = -9;

    public static final int CANNON_POSITION_COUNT = 10;
    public static final int RESERVED_VALUE_COUNT = 4;
    public static final int COORDINATE_COMPONENT_COUNT = 2;
    public static final int THRONE_WIDTH = 30;
    public static final int THRONE_HEIGHT = 25;

    private BurgenFileFormat() {
    }
}
