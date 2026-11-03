package at.josefjordan.ballerburg.editor.ui;

import at.josefjordan.ballerburg.editor.io.CastleReader;
import at.josefjordan.ballerburg.editor.io.CastleWriter;
import at.josefjordan.ballerburg.editor.model.CastleDocument;
import at.josefjordan.ballerburg.editor.ui.editor.EditorPanel;
import hgb.gui.HGBaseDialog;
import hgb.gui.HGBaseFrame;
import hgb.lib.HGBaseConfig;
import hgb.lib.HGBaseText;
import hgb.lib.HGBaseTools;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * The main frame for the editor.
 */
public class MainFrame extends HGBaseFrame {
    /**
     * Main editor component displayed in the frame.
     */
    private EditorPanel editorPanel;
    /**
     * Toolbar control for selecting the preceding castle.
     */
    private JButton previousCastleButton;
    /**
     * Toolbar control for selecting the following castle.
     */
    private JButton nextCastleButton;
    /**
     * Toolbar readout showing the selected castle's position in the document.
     */
    private JLabel castlePositionLabel;
    /**
     * Tracks document snapshots for undo and redo.
     */
    private final transient DocumentHistory documentHistory;

    /**
     * Creates the main editor frame using the specified settings file.
     *
     * @param settingsFile path of the settings file consumed by the base frame
     */
    public MainFrame(String settingsFile) {
        super(settingsFile);
        documentHistory = new DocumentHistory(
                (document, castleIndex) -> editorPanel.restoreDocument(document, castleIndex),
                () -> setChanged(true),
                this::updateFileMenuStates);
    }

    /**
     * Creates and displays the editor, then processes any file argument.
     *
     * @param args command-line arguments
     */
    @SuppressWarnings("java:S9149") // Keep the application entry point required by the run configuration.
    public static void main(String[] args) {
        MainFrame mainFrame = new MainFrame("/xml/settings.xml");
        mainFrame.setMenu(new MainMenu(mainFrame));
        mainFrame.setStatusBar(new StatusBar());
        mainFrame.initializeEditor();
        mainFrame.testCommandLineForFile(args);
    }

    /**
     * Initializes the editor and connects its change, selection, history, zoom, and status-bar callbacks.
     */
    private void initializeEditor() {
        editorPanel = new EditorPanel();
        editorPanel.setSelectionListener(this::updateCastleNavigation);
        editorPanel.setChangeListener(castleIndex -> documentHistory.recordChange(editorPanel.getDocument(), castleIndex));
        editorPanel.setHistoryGroupListeners(this::beginHistoryGroup, this::endHistoryGroup);
        editorPanel.setZoomListener(this::updateZoomDisplay);
        if (getStatusBar() instanceof StatusBar statusBar) {
            statusBar.setZoomListener(zoomPercent -> editorPanel.setZoomPercent(zoomPercent));
        }
        editorPanel.setStatusBarListener(hint -> {
            if (getStatusBar() instanceof StatusBar statusBar) {
                statusBar.setHint(hint);
            }
        });
        setGridVisible(HGBaseConfig.getBoolean("grid"));
        addCastleNavigationToToolbar();
        setPanel(editorPanel);
        updateCastleNavigation();
        updateZoomDisplay();
    }

    /**
     * Inserts previous/next controls and a position readout into the toolbar.
     */
    private void addCastleNavigationToToolbar() {
        JToolBar toolbar = getToolbar();
        int insertionIndex = Math.max(0, toolbar.getComponentCount() - 1);
        toolbar.add(new JSeparator(SwingConstants.VERTICAL), insertionIndex++);

        previousCastleButton = new JButton("◀");
        previousCastleButton.setFocusable(false);
        previousCastleButton.addActionListener(_ -> editorPanel.showPreviousCastle());
        toolbar.add(previousCastleButton, insertionIndex++);

        castlePositionLabel = new JLabel();
        castlePositionLabel.setHorizontalAlignment(SwingConstants.CENTER);
        castlePositionLabel.setPreferredSize(new Dimension(90, 24));
        toolbar.add(castlePositionLabel, insertionIndex++);

        nextCastleButton = new JButton("▶");
        nextCastleButton.setFocusable(false);
        nextCastleButton.addActionListener(_ -> editorPanel.showNextCastle());
        toolbar.add(nextCastleButton, insertionIndex);
        actualizeCastleNavigationText();
    }

    /**
     * Updates navigation availability and the selected-castle position readout.
     */
    private void updateCastleNavigation() {
        if (previousCastleButton == null) {
            return;
        }
        int index = editorPanel.getCastleIndex();
        int count = editorPanel.getCastleCount();
        previousCastleButton.setEnabled(index > 0);
        nextCastleButton.setEnabled(index >= 0 && index + 1 < count);
        castlePositionLabel.setText(count == 0
                ? HGBaseText.getText("castle.none")
                : HGBaseText.getText("castle.position", String.valueOf(index + 1), String.valueOf(count)));
        if (getMenu() != null) {
            getMenu().setFileMenuStates();
        }
    }

    /**
     * Refreshes the castle-navigation tooltips and position label.
     */
    private void actualizeCastleNavigationText() {
        if (previousCastleButton != null) {
            previousCastleButton.setToolTipText(HGBaseText.getText("castle.previous"));
            nextCastleButton.setToolTipText(HGBaseText.getText("castle.next"));
            updateCastleNavigation();
        }
    }

    /**
     * Updates the zoom display.
     */
    private void updateZoomDisplay() {
        if (getStatusBar() != null) {
            double zoomFactor = editorPanel.getZoomFactor();
            int zoomPercent = (int) Math.round(zoomFactor * 100);
            ((StatusBar) getStatusBar()).setZoomLevel(zoomPercent);
        }
    }

    /**
     * Adds a castle to the current document.
     */
    public void addCastle() {
        editorPanel.addCastle();
    }

    /**
     * Adds a cannon to the selected castle.
     */
    public void addCannon() {
        editorPanel.addCannon();
    }

    /**
     * Adds a throne to the selected castle.
     */
    public void addThrone() {
        editorPanel.addThrone();
    }

    /**
     * Adds a wind vane to the selected castle.
     */
    public void addWindVane() {
        editorPanel.addWindVane();
    }

    /**
     * Adds money storage to the selected castle.
     */
    public void addMoney() {
        editorPanel.addMoney();
    }

    /**
     * Adds powder storage to the selected castle.
     */
    public void addPowder() {
        editorPanel.addPowder();
    }

    /**
     * Adds cannonball storage to the selected castle.
     */
    public void addCannonballs() {
        editorPanel.addCannonballs();
    }

    /**
     * Starts creation of a wall polygon in the selected castle.
     */
    public void addWall() {
        editorPanel.addWall();
    }

    /**
     * Starts creation of a roof polygon in the selected castle.
     */
    public void addRoof() {
        editorPanel.addRoof();
    }

    /**
     * Deletes the selected castle.
     */
    public void deleteCastle() {
        editorPanel.deleteCastle();
    }

    /**
     * Removes the selected vertex when its polygon has more than three vertices.
     */
    public void removePolygonPoint() {
        editorPanel.removePolygonPoint();
    }

    /**
     * Removes the selected polygon from the selected castle.
     */
    public void removePolygon() {
        editorPanel.removePolygon();
    }

    /**
     * Returns whether the current document contains at least one castle.
     *
     * @return {@code true} if the open document contains at least one castle
     */
    public boolean hasCastle() {
        return editorPanel != null && editorPanel.getCastleCount() > 0;
    }

    /**
     * Returns whether a vertex of the selected polygon is selected.
     *
     * @return {@code true} if a vertex of the selected polygon is selected and removable
     */
    public boolean hasSelectedPolygonPoint() {
        return editorPanel != null && editorPanel.hasSelectedPolygonPoint();
    }

    /**
     * Returns whether a polygon is selected.
     *
     * @return {@code true} if a polygon is selected
     */
    public boolean hasSelectedPolygon() {
        return editorPanel != null && editorPanel.hasSelectedPolygon();
    }

    /**
     * Returns whether any polygon vertex is selected.
     *
     * @return {@code true} if a polygon vertex is selected
     */
    public boolean hasSelectedPoint() {
        return editorPanel != null && editorPanel.hasSelectedPoint();
    }

    /**
     * Returns whether a cannon is selected.
     *
     * @return {@code true} if a cannon is selected
     */
    public boolean hasSelectedCannon() {
        return editorPanel != null && editorPanel.hasSelectedCannon();
    }

    /**
     * Returns whether the throne is selected.
     *
     * @return {@code true} if the throne is selected
     */
    public boolean hasSelectedThrone() {
        return editorPanel != null && editorPanel.hasSelectedThrone();
    }

    /**
     * Returns whether the wind vane is selected.
     *
     * @return {@code true} if the wind vane is selected
     */
    public boolean hasSelectedWindVane() {
        return editorPanel != null && editorPanel.hasSelectedWindVane();
    }

    /**
     * Returns whether a resource-storage area is selected.
     *
     * @return {@code true} if a resource-storage area is selected
     */
    public boolean hasSelectedResourceArea() {
        return editorPanel != null && editorPanel.hasSelectedResourceArea();
    }

    /**
     * Returns the cannon count.
     *
     * @return the selected castle cannon count, or zero if no castle is selected
     */
    public int getCannonCount() {
        return editorPanel != null ? editorPanel.getCannonCount() : 0;
    }

    /**
     * Returns whether the selected castle has a throne.
     *
     * @return {@code true} if the selected castle contains a throne
     */
    public boolean hasThrone() {
        return editorPanel != null && editorPanel.hasThrone();
    }

    /**
     * Returns whether the selected castle has a wind vane.
     *
     * @return {@code true} if the selected castle contains a wind vane
     */
    public boolean hasWindVane() {
        return editorPanel != null && editorPanel.hasWindVane();
    }

    /**
     * Returns whether the selected castle has a money-storage area.
     *
     * @return {@code true} if the selected castle has money storage
     */
    public boolean hasMoney() {
        return editorPanel != null && editorPanel.hasMoney();
    }

    /**
     * Returns whether the selected castle has a powder-storage area.
     *
     * @return {@code true} if the selected castle has powder storage
     */
    public boolean hasPowder() {
        return editorPanel != null && editorPanel.hasPowder();
    }

    /**
     * Returns whether the selected castle has a cannonball-storage area.
     *
     * @return {@code true} if the selected castle has cannonball storage
     */
    public boolean hasBalls() {
        return editorPanel != null && editorPanel.hasBalls();
    }

    /**
     * Removes the selected cannon.
     */
    public void removeCannon() {
        editorPanel.removeCannon();
    }

    /**
     * Removes the throne when it is selected.
     */
    public void removeThrone() {
        editorPanel.removeThrone();
    }

    /**
     * Removes the wind vane when it is selected.
     */
    public void removeWindVane() {
        editorPanel.removeWindVane();
    }

    /**
     * Removes the selected resource-storage area.
     */
    public void removeResourceArea() {
        editorPanel.removeResourceArea();
    }

    /**
     * Sets whether the castle grid is displayed.
     *
     * @param visible whether the grid should be visible
     */
    public void setGridVisible(boolean visible) {
        editorPanel.setGridVisible(visible);
        if (getMenu() instanceof MainMenu mainMenu) {
            mainMenu.setGridSelected(visible);
        }
    }

    /**
     * Returns whether the grid is displayed.
     *
     * @return {@code true} if the editor currently displays the grid
     */
    public boolean isGridVisible() {
        return editorPanel.isGridVisible();
    }

    /**
     * Returns whether an earlier document state is available to restore.
     *
     * @return {@code true} if an earlier document snapshot is available to undo
     */
    public boolean canUndo() {
        return documentHistory.canUndo();
    }

    /**
     * Returns whether a later document state is available to restore.
     *
     * @return {@code true} if an undone document snapshot is available to redo
     */
    public boolean canRedo() {
        return documentHistory.canRedo();
    }

    /**
     * Undoes the most recent document change when one is available.
     */
    public void undo() {
        documentHistory.undo();
        clearStatusBarHint();
    }

    /**
     * Redoes the most recently undone document change when one is available.
     */
    public void redo() {
        documentHistory.redo();
        clearStatusBarHint();
    }

    /**
     * Clears the status-bar hint text.
     */
    private void clearStatusBarHint() {
        if (getStatusBar() instanceof StatusBar statusBar) {
            statusBar.setHint("");
        }
    }

    /**
     * Begins a group of related document edits.
     */
    public void beginHistoryGroup() {
        documentHistory.beginGroup();
    }

    /**
     * Ends the current group of related document edits.
     */
    public void endHistoryGroup() {
        documentHistory.endGroup();
    }

    @Override
    public void actualizeText() {
        super.actualizeText();
        actualizeCastleNavigationText();
    }

    @Override
    public int onFileNew() {
        CastleDocument document = new CastleDocument(List.of());
        editorPanel.setDocument(document);
        editorPanel.addCastle();
        documentHistory.reset(editorPanel.getDocument());
        return 0;
    }

    @Override
    public int onFileOpen(File file) {
        try {
            CastleDocument document = new CastleReader().read(file.toPath());
            editorPanel.setDocument(document);
            documentHistory.reset(document);
            return 0;
        } catch (IOException | IllegalArgumentException exception) {
            HGBaseDialog.printError(exception, this);
            return HGBaseTools.INVALID;
        }
    }

    @Override
    public int onFileSave(File file) {
        CastleDocument document = editorPanel.getDocument();
        if (document == null) {
            return HGBaseTools.INVALID;
        }
        if (editorPanel.hasProblems()) {
            int result = JOptionPane.showConfirmDialog(this,
                    HGBaseText.getText("problems.save_confirmation"),
                    HGBaseText.getText("problems.title"),
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (result != JOptionPane.YES_OPTION) {
                return HGBaseTools.INVALID;
            }
        }
        try {
            new CastleWriter().write(file.toPath(), document);
            return 0;
        } catch (IOException exception) {
            HGBaseDialog.printError(exception, this);
            return HGBaseTools.INVALID;
        }
    }

    @Override
    public int onFileClose() {
        editorPanel.setDocument(null);
        documentHistory.reset(null);
        clearStatusBarHint();
        return super.onFileClose();
    }

    private void updateFileMenuStates() {
        if (getMenu() != null) {
            getMenu().setFileMenuStates();
        }
    }
}
