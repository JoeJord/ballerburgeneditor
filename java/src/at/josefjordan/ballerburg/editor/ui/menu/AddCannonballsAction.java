package at.josefjordan.ballerburg.editor.ui.menu;

import at.josefjordan.ballerburg.editor.ui.MainFrame;
import hgb.gui.menu.IMenuAction;

import java.awt.event.ActionEvent;

/**
 * Adds cannonball storage to the selected castle when a document is open.
 */
public class AddCannonballsAction implements IMenuAction {
    /**
     * The main application frame.
     */
    private final MainFrame frame;

    /**
     * Creates an action that adds cannonball storage through the application frame.
     *
     * @param frame application frame whose selected castle is edited
     */
    public AddCannonballsAction(MainFrame frame) {
        this.frame = frame;
    }

    @Override
    public void perform(String id, ActionEvent e) {
        if (frame.getFile() != null) {
            frame.addCannonballs();
        }
    }
}
