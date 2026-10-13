package at.josefjordan.ballerburg.editor.ui;

import at.josefjordan.ballerburg.editor.model.CastleDocument;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Tracks document snapshots for undo and redo operations.
 */
final class DocumentHistory {
    /**
     * Earlier document snapshots, with the most recent state at the head.
     */
    private final Deque<CastleDocument> undoHistory = new ArrayDeque<>();
    /**
     * Undone document snapshots available to restore, with the next state at the head.
     */
    private final Deque<CastleDocument> redoHistory = new ArrayDeque<>();
    /**
     * Callback that installs a document snapshot in the editor.
     */
    private final Consumer<CastleDocument> documentRestorer;
    /**
     * Callback that marks the current document as modified.
     */
    private final Runnable changeMarker;
    /**
     * Callback that refreshes UI state dependent on undo/redo availability.
     */
    private final Runnable historyStateUpdater;
    /**
     * Current document snapshot used as the baseline for recording edits.
     */
    private CastleDocument currentDocument;
    /**
     * Whether a history snapshot is currently being restored.
     */
    private boolean restoring;
    /**
     * Whether edits are currently grouped into one undoable operation.
     */
    private boolean groupActive;
    /**
     * Whether the active edit group has recorded a document change.
     */
    private boolean groupChanged;

    /**
     * Creates a history manager using callbacks for restoring documents and updating frame state.
     *
     * @param documentRestorer     restores a document snapshot in the editor
     * @param changeMarker         marks the current document as changed
     * @param historyStateUpdater  updates UI state that depends on undo/redo availability
     */
    DocumentHistory(Consumer<CastleDocument> documentRestorer, Runnable changeMarker,
                    Runnable historyStateUpdater) {
        this.documentRestorer = documentRestorer;
        this.changeMarker = changeMarker;
        this.historyStateUpdater = historyStateUpdater;
    }

    /**
     * Returns whether an earlier document snapshot is available.
     *
     * @return {@code true} if an undo operation is available
     */
    boolean canUndo() {
        return !undoHistory.isEmpty();
    }

    /**
     * Returns whether a later document snapshot is available.
     *
     * @return {@code true} if a redo operation is available
     */
    boolean canRedo() {
        return !redoHistory.isEmpty();
    }

    /**
     * Records the current document, coalescing changes within an active group.
     *
     * @param document current document snapshot
     */
    void recordChange(CastleDocument document) {
        if (!restoring && !Objects.equals(document, currentDocument)) {
            if (currentDocument != null && (!groupActive || !groupChanged)) {
                undoHistory.push(currentDocument);
            }
            if (!groupActive || !groupChanged) {
                redoHistory.clear();
            }
            currentDocument = document;
            groupChanged = true;
        }
        changeMarker.run();
    }

    /**
     * Undoes the most recent document change when one is available.
     */
    void undo() {
        if (!canUndo()) {
            return;
        }
        redoHistory.push(currentDocument);
        restore(undoHistory.pop());
    }

    /**
     * Redoes the most recently undone document change when one is available.
     */
    void redo() {
        if (!canRedo()) {
            return;
        }
        undoHistory.push(currentDocument);
        restore(redoHistory.pop());
    }

    /**
     * Begins coalescing changes into one undoable operation.
     */
    void beginGroup() {
        groupActive = true;
        groupChanged = false;
    }

    /**
     * Ends the current grouped operation.
     */
    void endGroup() {
        groupActive = false;
        groupChanged = false;
    }

    /**
     * Clears undo/redo state and establishes a new document baseline.
     *
     * @param document document snapshot to use as the baseline, or {@code null} when closed
     */
    void reset(CastleDocument document) {
        undoHistory.clear();
        redoHistory.clear();
        currentDocument = document;
        groupActive = false;
        groupChanged = false;
    }

    /**
     * Installs a snapshot from the history and refreshes the dependent frame state.
     *
     * @param document document snapshot to restore
     */
    private void restore(CastleDocument document) {
        restoring = true;
        try {
            documentRestorer.accept(document);
            currentDocument = document;
            changeMarker.run();
        } finally {
            restoring = false;
        }
        historyStateUpdater.run();
    }
}
