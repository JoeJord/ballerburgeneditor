package at.josefjordan.ballerburg.editor.ui;

import at.josefjordan.ballerburg.editor.model.CastleDocument;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * Tracks document snapshots for undo and redo operations.
 */
final class DocumentHistory {
    /**
     * Earlier document snapshots, with the most recent state at the head.
     */
    private final Deque<Snapshot> undoHistory = new ArrayDeque<>();
    /**
     * Undone document snapshots available to restore, with the next state at the head.
     */
    private final Deque<Snapshot> redoHistory = new ArrayDeque<>();
    /**
     * Callback that installs a document snapshot in the editor.
     */
    private final BiConsumer<CastleDocument, Integer> documentRestorer;
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
     * @param documentRestorer    restores a document snapshot in the editor
     * @param changeMarker        marks the current document as changed
     * @param historyStateUpdater updates UI state that depends on undo/redo availability
     */
    DocumentHistory(BiConsumer<CastleDocument, Integer> documentRestorer, Runnable changeMarker,
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
    void recordChange(CastleDocument document, int castleIndex) {
        if (!restoring && !Objects.equals(document, currentDocument)) {
            if (currentDocument != null && (!groupActive || !groupChanged)) {
                undoHistory.push(new Snapshot(currentDocument, castleIndex));
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
        Snapshot snapshot = undoHistory.pop();
        redoHistory.push(new Snapshot(currentDocument, snapshot.castleIndex()));
        restore(snapshot);
    }

    /**
     * Redoes the most recently undone document change when one is available.
     */
    void redo() {
        if (!canRedo()) {
            return;
        }
        Snapshot snapshot = redoHistory.pop();
        undoHistory.push(new Snapshot(currentDocument, snapshot.castleIndex()));
        restore(snapshot);
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
     * @param snapshot document snapshot and castle selection to restore
     */
    private void restore(Snapshot snapshot) {
        restoring = true;
        try {
            documentRestorer.accept(snapshot.document(), snapshot.castleIndex());
            currentDocument = snapshot.document();
            changeMarker.run();
        } finally {
            restoring = false;
        }
        historyStateUpdater.run();
    }

    /**
     * A document state and the castle selected when that state was recorded.
     *
     * @param document    document snapshot
     * @param castleIndex selected castle index
     */
    private record Snapshot(CastleDocument document, int castleIndex) {
    }
}
