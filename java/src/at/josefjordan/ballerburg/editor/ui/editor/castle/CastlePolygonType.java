package at.josefjordan.ballerburg.editor.ui.editor.castle;

import at.josefjordan.ballerburg.editor.model.CastleFileFormat;

/**
 * Fill category used when creating a castle polygon.
 */
public enum CastlePolygonType {
    /**
     * Masonry wall polygon.
     */
    WALL(CastleFileFormat.FILL_INDEX_MASONRY),
    /**
     * Roof polygon.
     */
    ROOF(CastleFileFormat.FILL_INDEX_ROOF);

    /**
     * BALLER.DAT fill index associated with this polygon category.
     */
    private final int fillIndex;

    /**
     * Associates this polygon category with its BALLER.DAT fill index.
     *
     * @param fillIndex file-format fill index
     */
    CastlePolygonType(int fillIndex) {
        this.fillIndex = fillIndex;
    }

    /**
     * Returns the BALLER.DAT fill index for this polygon category.
     *
     * @return corresponding file-format fill index
     */
    public int fillIndex() {
        return fillIndex;
    }
}
