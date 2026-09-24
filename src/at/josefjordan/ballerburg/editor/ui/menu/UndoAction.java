package at.josefjordan.ballerburg.editor.ui.menu;

import at.josefjordan.ballerburg.editor.ui.MainFrame;
import hgb.gui.menu.IMenuAction;

import java.awt.event.ActionEvent;

public class UndoAction implements IMenuAction {
    private final MainFrame frame;

    public UndoAction(MainFrame frame) {
        this.frame = frame;
    }

    @Override
    public void perform(String id, ActionEvent e) {
        frame.undo();
    }
}
