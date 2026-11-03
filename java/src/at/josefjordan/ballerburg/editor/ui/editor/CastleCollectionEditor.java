package at.josefjordan.ballerburg.editor.ui.editor;

import at.josefjordan.ballerburg.editor.model.CastleDocument;
import at.josefjordan.ballerburg.editor.model.CastleDocument.Castle;
import at.josefjordan.ballerburg.editor.model.CastleDocument.Point;
import at.josefjordan.ballerburg.editor.model.CastleDocument.Resources;
import at.josefjordan.ballerburg.editor.model.CastleDocument.Size;

import java.util.ArrayList;
import java.util.List;

/**
 * Applies collection-level operations to immutable castle documents.
 */
final class CastleCollectionEditor {
    /**
     * Inserts a new default castle after the current selection, or at the end when nothing is selected.
     *
     * @param document      document being edited
     * @param selectedIndex index of the selected castle, or {@code -1} when none is selected
     * @return the new document and index of the inserted castle
     */
    CollectionEdit add(CastleDocument document, int selectedIndex) {
        List<Castle> castles = new ArrayList<>(document.castles());
        int newIndex = selectedIndex < 0 ? castles.size() : selectedIndex + 1;
        castles.add(newIndex, createEmptyCastle());
        return new CollectionEdit(new CastleDocument(castles), newIndex);
    }

    /**
     * Removes the selected castle and selects the nearest remaining castle.
     *
     * @param document      document being edited
     * @param selectedIndex index of the castle to remove
     * @return the new document and selected index, or {@code -1} when the document is empty
     */
    CollectionEdit remove(CastleDocument document, int selectedIndex) {
        List<Castle> castles = new ArrayList<>(document.castles());
        castles.remove(selectedIndex);
        int newIndex = Math.min(selectedIndex, castles.size() - 1);
        return new CollectionEdit(new CastleDocument(castles), newIndex);
    }

    /**
     * Selects an index only when it is within the current castle list.
     *
     * @param requestedIndex requested castle index
     * @param castleCount    number of castles in the document
     * @param currentIndex   currently selected castle index
     * @return the requested index when valid and different, otherwise the current index
     */
    int selectIndex(int requestedIndex, int castleCount, int currentIndex) {
        return requestedIndex >= 0 && requestedIndex < castleCount ? requestedIndex : currentIndex;
    }

    /**
     * Creates a castle with no elements, unset positions, and zero-sized resource areas.
     *
     * @return a new empty castle initialized with default values
     */
    private Castle createEmptyCastle() {
        Point absent = Point.unset();
        Size emptySize = new Size(0, 0);
        return new Castle(0, List.of(), absent, absent, absent, absent, absent,
                emptySize, emptySize, emptySize, new Resources(0, 0, 0, 0),
                List.of(0, 0, 0, 0), List.of());
    }

    /**
     * Result of a castle-list edit.
     *
     * @param document      updated document
     * @param selectedIndex index to select after the edit
     */
    record CollectionEdit(CastleDocument document, int selectedIndex) {
    }
}
