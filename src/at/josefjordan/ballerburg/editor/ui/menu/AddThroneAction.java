package at.josefjordan.ballerburg.editor.ui.menu;

import at.josefjordan.ballerburg.editor.ui.MainFrame;
import hgb.gui.menu.IMenuAction;

import java.awt.event.ActionEvent;

public class AddThroneAction implements IMenuAction {
    private final MainFrame frame;

    public AddThroneAction(MainFrame frame) {
        this.frame = frame;
    }

    @Override
    public void perform(String id, ActionEvent e) {
        if (frame.getFile() != null) {
            frame.addThrone();
        }
    }
}
