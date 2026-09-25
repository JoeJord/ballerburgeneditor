package at.josefjordan.ballerburg.editor.ui.menu;

import at.josefjordan.ballerburg.editor.ui.MainFrame;
import hgb.gui.menu.IMenuAction;

import javax.swing.AbstractButton;
import java.awt.event.ActionEvent;

public class ToggleGridAction implements IMenuAction {
    private final MainFrame frame;

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
