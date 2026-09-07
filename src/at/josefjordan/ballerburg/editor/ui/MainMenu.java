package at.josefjordan.ballerburg.editor.ui;

import at.josefjordan.ballerburg.editor.model.CastleFileFormat;
import at.josefjordan.ballerburg.editor.ui.menu.AddCannonAction;
import at.josefjordan.ballerburg.editor.ui.menu.AddCannonballsAction;
import at.josefjordan.ballerburg.editor.ui.menu.AddCastleAction;
import at.josefjordan.ballerburg.editor.ui.menu.AddWindVaneAction;
import at.josefjordan.ballerburg.editor.ui.menu.AddMoneyAction;
import at.josefjordan.ballerburg.editor.ui.menu.AddPowderAction;
import at.josefjordan.ballerburg.editor.ui.menu.AddThroneAction;
import at.josefjordan.ballerburg.editor.ui.menu.DeleteCastleAction;
import at.josefjordan.ballerburg.editor.ui.menu.RemoveAction;
import hgb.gui.HGBaseGuiTools;
import hgb.gui.menu.HGBaseMenu;

import java.io.File;

public class MainMenu extends HGBaseMenu {
    private static final String ADD_CASTLE = "edit.add_castle";
    private static final String ADD_CANNON = "edit.add_cannon";
    private static final String ADD_THRONE = "edit.add_throne";
    private static final String ADD_WIND_VANE = "edit.add_windvane";
    private static final String ADD_MONEY = "edit.add_money";
    private static final String ADD_POWDER = "edit.add_powder";
    private static final String ADD_CANNONBALLS = "edit.add_cannonballs";
    private static final String DELETE_CASTLE = "edit.delete_castle";
    private static final String REMOVE = "edit.remove";

    public MainMenu(MainFrame frame) {
        super(frame);
        registerAction(ADD_CASTLE, new AddCastleAction(frame));
        registerAction(ADD_CANNON, new AddCannonAction(frame));
        registerAction(ADD_THRONE, new AddThroneAction(frame));
        registerAction(ADD_WIND_VANE, new AddWindVaneAction(frame));
        registerAction(ADD_MONEY, new AddMoneyAction(frame));
        registerAction(ADD_POWDER, new AddPowderAction(frame));
        registerAction(ADD_CANNONBALLS, new AddCannonballsAction(frame));
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
        boolean hasSelectedWindVane = hasCastle && frame.hasSelectedWindVane();
        boolean hasSelectedResourceArea = hasCastle && frame.hasSelectedResourceArea();
        boolean canRemove = hasSelectedPolygonPoint || (hasSelectedPolygon && !frame.hasSelectedPoint()) || hasSelectedCannon || hasSelectedThrone || hasSelectedWindVane || hasSelectedResourceArea;
        boolean canAddCannon = hasCastle && frame.getCannonCount() < CastleFileFormat.CANNON_POSITION_COUNT;
        boolean canAddThrone = hasCastle && !frame.hasThrone();
        boolean canAddWindVane = hasCastle && !frame.hasWindVane();
        boolean canAddMoney = hasCastle && !frame.hasMoney();
        boolean canAddPowder = hasCastle && !frame.hasPowder();
        boolean canAddCannonballs = hasCastle && !frame.hasBalls();
        HGBaseGuiTools.setEnabledNullSafe(getMenuItem(ADD_CASTLE), hasOpenCastleFile);
        HGBaseGuiTools.setEnabledNullSafe(getToolbarItem(ADD_CASTLE), hasOpenCastleFile);
        HGBaseGuiTools.setEnabledNullSafe(getMenuItem(ADD_CANNON), canAddCannon);
        HGBaseGuiTools.setEnabledNullSafe(getToolbarItem(ADD_CANNON), canAddCannon);
        HGBaseGuiTools.setEnabledNullSafe(getMenuItem(ADD_THRONE), canAddThrone);
        HGBaseGuiTools.setEnabledNullSafe(getToolbarItem(ADD_THRONE), canAddThrone);
        HGBaseGuiTools.setEnabledNullSafe(getMenuItem(ADD_WIND_VANE), canAddWindVane);
        HGBaseGuiTools.setEnabledNullSafe(getToolbarItem(ADD_WIND_VANE), canAddWindVane);
        HGBaseGuiTools.setEnabledNullSafe(getMenuItem(ADD_MONEY), canAddMoney);
        HGBaseGuiTools.setEnabledNullSafe(getToolbarItem(ADD_MONEY), canAddMoney);
        HGBaseGuiTools.setEnabledNullSafe(getMenuItem(ADD_POWDER), canAddPowder);
        HGBaseGuiTools.setEnabledNullSafe(getToolbarItem(ADD_POWDER), canAddPowder);
        HGBaseGuiTools.setEnabledNullSafe(getMenuItem(ADD_CANNONBALLS), canAddCannonballs);
        HGBaseGuiTools.setEnabledNullSafe(getToolbarItem(ADD_CANNONBALLS), canAddCannonballs);
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
