package at.josefjordan.ballerburg.editor.ui.menu;

import at.josefjordan.ballerburg.editor.ui.MainFrame;
import hgb.gui.menu.IMenuAction;

import java.awt.event.ActionEvent;

/**
 * Removes the selected polygon vertex, polygon, or castle object.
 */
public class RemoveAction implements IMenuAction {
    /**
     * The main application frame.
     */
    private final MainFrame frame;

    /**
     * Creates an action that removes the current selection through the application frame.
     *
     * @param frame application frame whose selected castle is edited
     */
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
            } else if (frame.hasSelectedThrone()) {
                frame.removeThrone();
            } else if (frame.hasSelectedWindVane()) {
                frame.removeWindVane();
            } else if (frame.hasSelectedResourceArea()) {
                frame.removeResourceArea();
            }
        }
    }
}
