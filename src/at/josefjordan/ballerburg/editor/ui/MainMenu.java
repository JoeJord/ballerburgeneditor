package at.josefjordan.ballerburg.editor.ui;

import at.josefjordan.ballerburg.editor.model.CastleFileFormat;
import at.josefjordan.ballerburg.editor.ui.menu.*;
import hgb.gui.HGBaseGuiTools;
import hgb.gui.menu.HGBaseMenu;
import hgb.gui.menu.HGBaseMenuTools;

import java.io.File;

public class MainMenu extends HGBaseMenu {
    private static final String ADD_CASTLE = "edit.add_castle";
    private static final String ADD_CANNON = "edit.add_cannon";
    private static final String ADD_THRONE = "edit.add_throne";
    private static final String ADD_WIND_VANE = "edit.add_windvane";
    private static final String ADD_MONEY = "edit.add_money";
    private static final String ADD_POWDER = "edit.add_powder";
    private static final String ADD_CANNONBALLS = "edit.add_cannonballs";
    private static final String ADD_WALL = "edit.add_wall";
    private static final String ADD_ROOF = "edit.add_roof";
    private static final String DELETE_CASTLE = "edit.delete_castle";
    private static final String REMOVE = "edit.remove";
    private static final String UNDO = "edit.undo";
    private static final String REDO = "edit.redo";
    private static final String GRID = "view.grid";

    public MainMenu(MainFrame frame) {
        super(frame);
        registerAction(ADD_CASTLE, new AddCastleAction(frame));
        registerAction(ADD_CANNON, new AddCannonAction(frame));
        registerAction(ADD_THRONE, new AddThroneAction(frame));
        registerAction(ADD_WIND_VANE, new AddWindVaneAction(frame));
        registerAction(ADD_MONEY, new AddMoneyAction(frame));
        registerAction(ADD_POWDER, new AddPowderAction(frame));
        registerAction(ADD_CANNONBALLS, new AddCannonballsAction(frame));
        registerAction(ADD_WALL, new AddWallAction(frame));
        registerAction(ADD_ROOF, new AddRoofAction(frame));
        registerAction(DELETE_CASTLE, new DeleteCastleAction(frame));
        registerAction(REMOVE, new RemoveAction(frame));
        registerAction(UNDO, new UndoAction(frame));
        registerAction(REDO, new RedoAction(frame));
        registerAction(GRID, new ToggleGridAction(frame));
        setFileMenuStates();
    }

    void setGridSelected(boolean selected) {
        HGBaseMenuTools.setMenuItemSelected(getMainFrame(), GRID, selected);
    }

    @Override
    public void setFileMenuStates() {
        super.setFileMenuStates();
        MainFrame frame = (MainFrame) getMainFrame();
        if (frame == null) {
            return;
        }
        boolean hasOpenCastleFile = frame.getFile() != null;
        boolean canUndo = frame.canUndo();
        boolean canRedo = frame.canRedo();
        boolean hasCastle = hasOpenCastleFile && frame.hasCastle();
        boolean canRemove = hasCastle && canRemoveSelection(frame);
        boolean canAddCannon = canAddCannon(frame, hasCastle);
        setEnabled(UNDO, canUndo);
        setEnabled(REDO, canRedo);
        setEnabled(ADD_CASTLE, hasOpenCastleFile);
        setEnabled(ADD_CANNON, canAddCannon);
        setEnabled(ADD_THRONE, hasCastle && !frame.hasThrone());
        setEnabled(ADD_WIND_VANE, hasCastle && !frame.hasWindVane());
        setEnabled(ADD_MONEY, hasCastle && !frame.hasMoney());
        setEnabled(ADD_POWDER, hasCastle && !frame.hasPowder());
        setEnabled(ADD_CANNONBALLS, hasCastle && !frame.hasBalls());
        setEnabled(ADD_WALL, hasCastle);
        setEnabled(ADD_ROOF, hasCastle);
        setEnabled(DELETE_CASTLE, hasCastle);
        setEnabled(REMOVE, canRemove);
    }

    private boolean canRemoveSelection(MainFrame frame) {
        boolean hasPolygon = frame.hasSelectedPolygon();
        boolean hasPolygonPoint = frame.hasSelectedPolygonPoint();
        return hasPolygonPoint || hasPolygon && !frame.hasSelectedPoint()
                || frame.hasSelectedCannon() || frame.hasSelectedThrone()
                || frame.hasSelectedWindVane() || frame.hasSelectedResourceArea();
    }

    private boolean canAddCannon(MainFrame frame, boolean hasCastle) {
        return hasCastle && frame.getCannonCount() < CastleFileFormat.CANNON_POSITION_COUNT;
    }

    private void setEnabled(String action, boolean enabled) {
        HGBaseGuiTools.setEnabledNullSafe(getMenuItem(action), enabled);
        HGBaseGuiTools.setEnabledNullSafe(getToolbarItem(action), enabled);
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
