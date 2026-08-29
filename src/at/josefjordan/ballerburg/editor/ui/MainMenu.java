package at.josefjordan.ballerburg.editor.ui;

import at.josefjordan.ballerburg.editor.ui.menu.AddCastleAction;
import at.josefjordan.ballerburg.editor.ui.menu.DeleteCastleAction;
import at.josefjordan.ballerburg.editor.ui.menu.RemovePolygonPointAction;
import hgb.gui.HGBaseGuiTools;
import hgb.gui.menu.HGBaseMenu;

import java.io.File;

public class MainMenu extends HGBaseMenu {
    private static final String ADD_CASTLE = "edit.add_castle";
    private static final String DELETE_CASTLE = "edit.delete_castle";
    private static final String REMOVE_POLYGON_POINT = "edit.remove_polygon_point";

    public MainMenu(MainFrame frame) {
        super(frame);
        registerAction(ADD_CASTLE, new AddCastleAction(frame));
        registerAction(DELETE_CASTLE, new DeleteCastleAction(frame));
        registerAction(REMOVE_POLYGON_POINT, new RemovePolygonPointAction(frame));
        setFileMenuStates();
    }

    @Override
    public void setFileMenuStates() {
        super.setFileMenuStates();
        MainFrame frame = (MainFrame) getMainFrame();
        boolean hasOpenCastleFile = frame != null && frame.getFile() != null;
        boolean hasCastle = hasOpenCastleFile && frame.hasCastle();
        boolean hasSelectedPoint = hasCastle && frame.hasSelectedPolygonPoint();
        HGBaseGuiTools.setEnabledNullSafe(getMenuItem(ADD_CASTLE), hasOpenCastleFile);
        HGBaseGuiTools.setEnabledNullSafe(getToolbarItem(ADD_CASTLE), hasOpenCastleFile);
        HGBaseGuiTools.setEnabledNullSafe(getMenuItem(DELETE_CASTLE), hasCastle);
        HGBaseGuiTools.setEnabledNullSafe(getToolbarItem(DELETE_CASTLE), hasCastle);
        HGBaseGuiTools.setEnabledNullSafe(getMenuItem(REMOVE_POLYGON_POINT), hasSelectedPoint);
        HGBaseGuiTools.setEnabledNullSafe(getToolbarItem(REMOVE_POLYGON_POINT), hasSelectedPoint);
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
