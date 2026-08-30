package at.josefjordan.ballerburg.editor.ui.menu;

import at.josefjordan.ballerburg.editor.ui.MainFrame;
import hgb.gui.menu.IMenuAction;

import java.awt.event.ActionEvent;

public class RemoveAction implements IMenuAction {
    private final MainFrame frame;

    public RemoveAction(MainFrame frame) {
        this.frame = frame;
    }

    @Override
    public void perform(String id, ActionEvent e) {
        if (frame.getFile() != null) {
            if (frame.hasSelectedPolygonPoint()) {
                frame.removePolygonPoint();
            } else if (frame.hasSelectedPolygon()) {
                frame.removePolygon();
            } else if (frame.hasSelectedCannon()) {
                frame.removeCannon();
            }
        }
    }
}
