package at.josefjordan.ballerburg.editor.ui.menu;

import at.josefjordan.ballerburg.editor.ui.MainFrame;
import hgb.gui.menu.IMenuAction;

import java.awt.event.ActionEvent;

/**
 * Reapplies the most recently undone document change.
 */
public class RedoAction implements IMenuAction {
    /**
     * The main application frame.
     */
    private final MainFrame frame;

    /**
     * Creates an action that reapplies the latest undone change.
     *
     * @param frame application frame whose redo history is used
     */
    public RedoAction(MainFrame frame) {
        this.frame = frame;
    }

    @Override
    public void perform(String id, ActionEvent e) {
        frame.redo();
    }
}
