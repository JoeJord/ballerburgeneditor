package at.josefjordan.ballerburg.editor.ui.editor;

import at.josefjordan.ballerburg.editor.model.CastleDocument;
import at.josefjordan.ballerburg.editor.model.CastleDocument.Castle;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Owns the editor's document and selected-castle state and coordinates document edits.
 */
final class EditorDocumentController {
    /**
     * Applies immutable operations to the document's castle list.
     */
    private final CastleCollectionEditor castleCollectionEditor = new CastleCollectionEditor();
    /**
     * Connects document state changes to the editor's views and listeners.
     */
    private final Callbacks callbacks;
    /**
     * Current castle document, or {@code null} when no document is open.
     */
    private CastleDocument document;
    /**
     * Index of the selected castle, or {@code -1} when no castle is selected.
     */
    private int castleIndex = -1;

    /**
     * UI updates and notifications triggered by document or selection changes.
     *
     * @param selectionUpdater  refreshes all views for a selected castle
     * @param editUpdater       refreshes views affected by an in-place castle edit
     * @param selectionNotifier notifies listeners that the selected castle changed
     * @param documentNotifier  notifies listeners that document contents changed
     */
    record Callbacks(Consumer<Castle> selectionUpdater,
                     Consumer<CastleEdit> editUpdater,
                     Runnable selectionNotifier,
                     Runnable documentNotifier) {
    }

    /**
     * Describes how the UI should refresh after replacing the selected castle.
     *
     * @param castle                 replacement castle
     * @param refreshPropertyPalette whether to reload the property palette
     * @param resetView              whether to reset the canvas view
     */
    record CastleEdit(Castle castle, boolean refreshPropertyPalette, boolean resetView) {
    }

    /**
     * Creates a controller that reports document and selection changes through the supplied callbacks.
     *
     * @param callbacks view-update and notification callbacks
     */
    EditorDocumentController(Callbacks callbacks) {
        this.callbacks = callbacks;
    }

    /**
     * Returns the current document.
     *
     * @return the current document, or {@code null} when none is open
     */
    CastleDocument getDocument() {
        return document;
    }

    /**
     * Returns the selected castle's index.
     *
     * @return the selected index, or {@code -1} when no castle is selected
     */
    int getCastleIndex() {
        return castleIndex;
    }

    /**
     * Returns the number of castles in the current document.
     *
     * @return the castle count, or zero when no document is open
     */
    int getCastleCount() {
        return document == null ? 0 : document.castles().size();
    }

    /**
     * Returns the selected castle when the document and selection are valid.
     *
     * @return selected castle, or {@code null} when none is selected
     */
    Castle getSelectedCastle() {
        return document == null || castleIndex < 0 || castleIndex >= getCastleCount()
                ? null : document.castles().get(castleIndex);
    }

    /**
     * Replaces the document, selects its first castle if present, and refreshes selection-dependent views.
     *
     * @param document replacement document, or {@code null} to clear the editor
     */
    void setDocument(CastleDocument document) {
        this.document = document;
        castleIndex = document == null || document.castles().isEmpty() ? -1 : 0;
        callbacks.selectionUpdater().accept(getSelectedCastle());
        callbacks.selectionNotifier().run();
    }

    /**
     * Adds an empty castle after the selection, or at the end if no castle is selected.
     */
    void addCastle() {
        if (document == null) {
            return;
        }
        CastleCollectionEditor.CollectionEdit edit = castleCollectionEditor.add(document, castleIndex);
        applyCollectionEdit(edit);
    }

    /**
     * Removes the selected castle and selects the nearest remaining castle.
     */
    void removeSelectedCastle() {
        if (document == null || getSelectedCastle() == null) {
            return;
        }
        CastleCollectionEditor.CollectionEdit edit = castleCollectionEditor.remove(document, castleIndex);
        applyCollectionEdit(edit);
    }

    /**
     * Selects a castle when the requested index is in range and differs from the current selection.
     *
     * @param index requested castle index
     */
    void selectIndex(int index) {
        int selectedIndex = castleCollectionEditor.selectIndex(index, getCastleCount(), castleIndex);
        if (selectedIndex == castleIndex) {
            return;
        }
        castleIndex = selectedIndex;
        callbacks.selectionUpdater().accept(getSelectedCastle());
        callbacks.selectionNotifier().run();
    }

    /**
     * Replaces the selected castle snapshot and updates dependent views and listeners.
     *
     * @param updatedCastle          replacement castle snapshot
     * @param refreshPropertyPalette whether the property palette should reload the replacement
     * @param resetView              whether the canvas should reset its view for the replacement
     * @param notifySelection        whether to notify listeners that selection-dependent state changed
     */
    void replaceSelectedCastle(Castle updatedCastle, boolean refreshPropertyPalette, boolean resetView, boolean notifySelection) {
        if (getSelectedCastle() == null) {
            return;
        }
        List<Castle> castles = new ArrayList<>(document.castles());
        castles.set(castleIndex, updatedCastle);
        document = new CastleDocument(castles);
        callbacks.editUpdater().accept(new CastleEdit(updatedCastle, refreshPropertyPalette, resetView));
        if (notifySelection) {
            callbacks.selectionNotifier().run();
        }
        callbacks.documentNotifier().run();
    }

    /**
     * Stores the result of a castle-collection operation and refreshes selection and document listeners.
     *
     * @param edit result containing the updated document and selection index
     */
    private void applyCollectionEdit(CastleCollectionEditor.CollectionEdit edit) {
        document = edit.document();
        castleIndex = edit.selectedIndex();
        callbacks.selectionUpdater().accept(getSelectedCastle());
        callbacks.selectionNotifier().run();
        callbacks.documentNotifier().run();
    }
}
