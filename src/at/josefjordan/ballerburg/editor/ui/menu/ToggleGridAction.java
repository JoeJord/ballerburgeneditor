package at.josefjordan.ballerburg.editor.ui.menu;

import at.josefjordan.ballerburg.editor.ui.MainFrame;
import hgb.gui.menu.IMenuAction;

import javax.swing.*;
import java.awt.event.ActionEvent;

/**
 * Sets grid visibility from the menu button state, or toggles it for other action sources.
 */
public class ToggleGridAction implements IMenuAction {
    /**
     * The main application frame.
     */
    private final MainFrame frame;

    /**
     * Creates an action that changes grid visibility in the application frame.
     *
     * @param frame frame whose castle view and grid menu state are updated
     */
    public ToggleGridAction(MainFrame frame) {
        this.frame = frame;
    }

    @Override
    public void perform(String id, ActionEvent e) {
        if (e.getSource() instanceof AbstractButton button) {
            frame.setGridVisible(button.isSelected());
        } else {
            frame.setGridVisible(!frame.isGridVisible());
        }
    }
}
