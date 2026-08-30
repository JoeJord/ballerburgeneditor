package at.josefjordan.ballerburg.editor.ui;

import at.josefjordan.ballerburg.editor.ui.menu.AddCastleAction;
import at.josefjordan.ballerburg.editor.ui.menu.DeleteCastleAction;
import at.josefjordan.ballerburg.editor.ui.menu.RemoveAction;
import hgb.gui.HGBaseGuiTools;
import hgb.gui.menu.HGBaseMenu;

import java.io.File;

public class MainMenu extends HGBaseMenu {
    private static final String ADD_CASTLE = "edit.add_castle";
    private static final String DELETE_CASTLE = "edit.delete_castle";
    private static final String REMOVE = "edit.remove";

    public MainMenu(MainFrame frame) {
        super(frame);
        registerAction(ADD_CASTLE, new AddCastleAction(frame));
        registerAction(DELETE_CASTLE, new DeleteCastleAction(frame));
        registerAction(REMOVE, new RemoveAction(frame));
        setFileMenuStates();
    }

    @Override
    public void setFileMenuStates() {
        super.setFileMenuStates();
        MainFrame frame = (MainFrame) getMainFrame();
        boolean hasOpenCastleFile = frame != null && frame.getFile() != null;
        boolean hasCastle = hasOpenCastleFile && frame.hasCastle();
        boolean hasSelectedPolygon = hasCastle && frame.hasSelectedPolygon();
        boolean hasSelectedPolygonPoint = hasCastle && frame.hasSelectedPolygonPoint();
        boolean hasSelectedCannon = hasCastle && frame.hasSelectedCannon();
        boolean hasSelectedThrone = hasCastle && frame.hasSelectedThrone();
        boolean canRemove = hasSelectedPolygonPoint || (hasSelectedPolygon && !frame.hasSelectedPoint()) || hasSelectedCannon || hasSelectedThrone;
        HGBaseGuiTools.setEnabledNullSafe(getMenuItem(ADD_CASTLE), hasOpenCastleFile);
        HGBaseGuiTools.setEnabledNullSafe(getToolbarItem(ADD_CASTLE), hasOpenCastleFile);
        HGBaseGuiTools.setEnabledNullSafe(getMenuItem(DELETE_CASTLE), hasCastle);
        HGBaseGuiTools.setEnabledNullSafe(getToolbarItem(DELETE_CASTLE), hasCastle);
        HGBaseGuiTools.setEnabledNullSafe(getMenuItem(REMOVE), canRemove);
        HGBaseGuiTools.setEnabledNullSafe(getToolbarItem(REMOVE), canRemove);
    }

    @Override
    public boolean onFileOpen(File file) {
        boolean opened = super.onFileOpen(file);
        setFileMenuStates();
        return opened;
    }

    @Override
    public boolean onFileClose() {
        boolean closed = super.onFileClose();
        setFileMenuStates();
        return closed;
    }
}
