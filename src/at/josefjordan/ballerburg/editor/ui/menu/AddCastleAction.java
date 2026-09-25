package at.josefjordan.ballerburg.editor.ui.menu;

import at.josefjordan.ballerburg.editor.ui.MainFrame;
import hgb.gui.menu.IMenuAction;

import java.awt.event.ActionEvent;

/**
 * Adds an empty castle to the open document when invoked.
 */
public class AddCastleAction implements IMenuAction {
    /**
     * The main application frame.
     */
    private final MainFrame frame;

    /**
     * Creates an action that inserts a castle through the application frame.
     *
     * @param frame application frame whose document receives the castle
     */
    public AddCastleAction(MainFrame frame) {
        this.frame = frame;
    }

    @Override
    public void perform(String id, ActionEvent e) {
        if (frame.getFile() != null) {
            frame.addCastle();
        }
    }
}
