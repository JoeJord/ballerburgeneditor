package at.josefjordan.ballerburg.editor.ui;

import at.josefjordan.ballerburg.editor.model.CastleFileFormat;
import at.josefjordan.ballerburg.editor.ui.menu.*;
import hgb.gui.HGBaseGuiTools;
import hgb.gui.menu.HGBaseMenu;
import hgb.gui.menu.HGBaseMenuTools;

import java.io.File;

/**
 * The application menu and toolbar actions.
 */
public class MainMenu extends HGBaseMenu {
    /**
     * Identifier for the Add Castle menu and toolbar action.
     */
    private static final String ADD_CASTLE = "edit.add_castle";
    /**
     * Identifier for the Add Cannon menu and toolbar action.
     */
    private static final String ADD_CANNON = "edit.add_cannon";
    /**
     * Identifier for the Add Throne menu and toolbar action.
     */
    private static final String ADD_THRONE = "edit.add_throne";
    /**
     * Identifier for the Add Wind Vane menu and toolbar action.
     */
    private static final String ADD_WIND_VANE = "edit.add_windvane";
    /**
     * Identifier for the Add Money menu and toolbar action.
     */
    private static final String ADD_MONEY = "edit.add_money";
    /**
     * Identifier for the Add Powder menu and toolbar action.
     */
    private static final String ADD_POWDER = "edit.add_powder";
    /**
     * Identifier for the Add Cannonballs menu and toolbar action.
     */
    private static final String ADD_CANNONBALLS = "edit.add_cannonballs";
    /**
     * Identifier for the Add Wall menu and toolbar action.
     */
    private static final String ADD_WALL = "edit.add_wall";
    /**
     * Identifier for the Add Roof menu and toolbar action.
     */
    private static final String ADD_ROOF = "edit.add_roof";
    /**
     * Identifier for the Delete Castle menu and toolbar action.
     */
    private static final String DELETE_CASTLE = "edit.delete_castle";
    /**
     * Identifier for the Remove menu and toolbar action.
     */
    private static final String REMOVE = "edit.remove";
    /**
     * Identifier for the Undo menu and toolbar action.
     */
    private static final String UNDO = "edit.undo";
    /**
     * Identifier for the Redo menu and toolbar action.
     */
    private static final String REDO = "edit.redo";
    /**
     * Identifier for the grid visibility menu and toolbar action.
     */
    private static final String GRID = "view.grid";

    /**
     * Registers the editor actions and initializes file-dependent menu states.
     *
     * @param frame application frame whose document and editor state the actions use
     */
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

    /**
     * Sets the grid menu item to match the current grid visibility.
     *
     * @param selected whether the item should be selected
     */
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

    /**
     * Returns whether the current selection has a removable item.
     *
     * @param frame the owning application frame
     * @return {@code true} if a polygon vertex, polygon, cannon, throne, wind vane, or resource area can be removed
     */
    private boolean canRemoveSelection(MainFrame frame) {
        boolean hasPolygon = frame.hasSelectedPolygon();
        boolean hasPolygonPoint = frame.hasSelectedPolygonPoint();
        return hasPolygonPoint || hasPolygon && !frame.hasSelectedPoint()
                || frame.hasSelectedCannon() || frame.hasSelectedThrone()
                || frame.hasSelectedWindVane() || frame.hasSelectedResourceArea();
    }

    /**
     * Returns whether the cannon limit permits adding another cannon.
     *
     * @param frame     the owning application frame
     * @param hasCastle whether a castle is selected in the open document
     * @return {@code true} if a castle is selected and the cannon-position limit has not been reached
     */
    private boolean canAddCannon(MainFrame frame, boolean hasCastle) {
        return hasCastle && frame.getCannonCount() < CastleFileFormat.CANNON_POSITION_COUNT;
    }

    /**
     * Enables or disables the matching menu and toolbar items.
     *
     * @param action  the registered action identifier
     * @param enabled whether the component should be enabled
     */
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
