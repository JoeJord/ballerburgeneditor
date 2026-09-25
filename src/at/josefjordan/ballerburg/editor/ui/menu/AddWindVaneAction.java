package at.josefjordan.ballerburg.editor.ui.menu;

import at.josefjordan.ballerburg.editor.ui.MainFrame;
import hgb.gui.menu.IMenuAction;

import java.awt.event.ActionEvent;

/**
 * Adds a wind vane to the selected castle when a document is open.
 */
public class AddWindVaneAction implements IMenuAction {
    /**
     * The main application frame.
     */
    private final MainFrame frame;

    /**
     * Creates an action that adds a wind vane through the application frame.
     *
     * @param frame application frame whose selected castle is edited
     */
    public AddWindVaneAction(MainFrame frame) {
        this.frame = frame;
    }

    @Override
    public void perform(String id, ActionEvent e) {
        if (frame.getFile() != null) {
            frame.addWindVane();
        }
    }
}
