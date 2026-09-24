package at.josefjordan.ballerburg.editor.ui;

import at.josefjordan.ballerburg.editor.io.CastleReader;
import at.josefjordan.ballerburg.editor.io.CastleWriter;
import at.josefjordan.ballerburg.editor.model.CastleDocument;
import at.josefjordan.ballerburg.editor.ui.editor.EditorPanel;
import hgb.gui.HGBaseDialog;
import hgb.gui.HGBaseFrame;
import hgb.lib.HGBaseTools;
import hgb.lib.HGBaseText;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JSeparator;
import javax.swing.JToolBar;
import javax.swing.SwingConstants;
import java.awt.Dimension;
import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/// The main frame for the editor.
public class MainFrame extends HGBaseFrame {
    private EditorPanel editorPanel;
    private JButton previousCastleButton;
    private JButton nextCastleButton;
    private JLabel castlePositionLabel;
    private final Deque<CastleDocument> undoHistory = new ArrayDeque<>();
    private final Deque<CastleDocument> redoHistory = new ArrayDeque<>();
    private CastleDocument historyDocument;
    private boolean restoringHistory;
    private boolean historyGroupActive;
    private boolean historyGroupChanged;

    /**
     * Constructs a new instance.
     *
     * @param settingsFile The name of the settings file.
     */
    public MainFrame(String settingsFile) {
        super(settingsFile);
    }

    /**
     * The main method.
     *
     * @param args The command line arguments.
     */
    public static void main(String[] args) {
        MainFrame mainFrame = new MainFrame("/xml/settings.xml");
        mainFrame.setMenu(new MainMenu(mainFrame));
        mainFrame.setStatusBar(new StatusBar());
        mainFrame.initializeEditor();
        mainFrame.testCommandLineForFile(args);
    }

    private void initializeEditor() {
        editorPanel = new EditorPanel();
        editorPanel.setSelectionListener(this::updateCastleNavigation);
        editorPanel.setChangeListener(this::recordDocumentChange);
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
        addCastleNavigationToToolbar();
        setPanel(editorPanel);
        updateCastleNavigation();
        updateZoomDisplay();
    }

    private void addCastleNavigationToToolbar() {
        JToolBar toolbar = getToolbar();
        int insertionIndex = Math.max(0, toolbar.getComponentCount() - 1);
        toolbar.add(new JSeparator(SwingConstants.VERTICAL), insertionIndex++);

        previousCastleButton = new JButton("\u25c0");
        previousCastleButton.setFocusable(false);
        previousCastleButton.addActionListener(event -> editorPanel.showPreviousCastle());
        toolbar.add(previousCastleButton, insertionIndex++);

        castlePositionLabel = new JLabel();
        castlePositionLabel.setHorizontalAlignment(SwingConstants.CENTER);
        castlePositionLabel.setPreferredSize(new Dimension(90, 24));
        toolbar.add(castlePositionLabel, insertionIndex++);

        nextCastleButton = new JButton("\u25b6");
        nextCastleButton.setFocusable(false);
        nextCastleButton.addActionListener(event -> editorPanel.showNextCastle());
        toolbar.add(nextCastleButton, insertionIndex);
        actualizeCastleNavigationText();
    }

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

    private void actualizeCastleNavigationText() {
        if (previousCastleButton != null) {
            previousCastleButton.setToolTipText(HGBaseText.getText("castle.previous"));
            nextCastleButton.setToolTipText(HGBaseText.getText("castle.next"));
            updateCastleNavigation();
        }
    }

    private void updateZoomDisplay() {
        if (getStatusBar() != null) {
            double zoomFactor = editorPanel.getZoomFactor();
            int zoomPercent = (int) Math.round(zoomFactor * 100);
            ((StatusBar) getStatusBar()).setZoomLevel(zoomPercent);
        }
    }

    public void addCastle() {
        editorPanel.addCastle();
    }

    public void addCannon() {
        editorPanel.addCannon();
    }

    public void addThrone() {
        editorPanel.addThrone();
    }

    public void addWindVane() {
        editorPanel.addWindVane();
    }

    public void addMoney() {
        editorPanel.addMoney();
    }

    public void addPowder() {
        editorPanel.addPowder();
    }

    public void addCannonballs() {
        editorPanel.addCannonballs();
    }

    public void addWall() {
        editorPanel.addWall();
    }

    public void addRoof() {
        editorPanel.addRoof();
    }

    public void deleteCastle() {
        editorPanel.deleteCastle();
    }

    public void removePolygonPoint() {
        editorPanel.removePolygonPoint();
    }

    public void removePolygon() {
        editorPanel.removePolygon();
    }

    public boolean hasCastle() {
        return editorPanel != null && editorPanel.getCastleCount() > 0;
    }

    public boolean hasSelectedPolygonPoint() {
        return editorPanel != null && editorPanel.hasSelectedPolygonPoint();
    }

    public boolean hasSelectedPolygon() {
        return editorPanel != null && editorPanel.hasSelectedPolygon();
    }

    public boolean hasSelectedPoint() {
        return editorPanel != null && editorPanel.hasSelectedPoint();
    }

    public boolean hasSelectedCannon() {
        return editorPanel != null && editorPanel.hasSelectedCannon();
    }

    public boolean hasSelectedThrone() {
        return editorPanel != null && editorPanel.hasSelectedThrone();
    }

    public boolean hasSelectedWindVane() {
        return editorPanel != null && editorPanel.hasSelectedWindVane();
    }

    public boolean hasSelectedResourceArea() {
        return editorPanel != null && editorPanel.hasSelectedResourceArea();
    }

    public int getCannonCount() {
        return editorPanel != null ? editorPanel.getCannonCount() : 0;
    }

    public boolean hasThrone() {
        return editorPanel != null && editorPanel.hasThrone();
    }

    public boolean hasWindVane() {
        return editorPanel != null && editorPanel.hasWindVane();
    }

    public boolean hasMoney() {
        return editorPanel != null && editorPanel.hasMoney();
    }

    public boolean hasPowder() {
        return editorPanel != null && editorPanel.hasPowder();
    }

    public boolean hasBalls() {
        return editorPanel != null && editorPanel.hasBalls();
    }

    public boolean hasWall(){
        return editorPanel != null && editorPanel.hasWall();
    }

    public void removeCannon() {
        editorPanel.removeCannon();
    }

    public void removeThrone() {
        editorPanel.removeThrone();
    }

    public void removeWindVane() {
        editorPanel.removeWindVane();
    }

    public void removeResourceArea() {
        editorPanel.removeResourceArea();
    }

    public boolean canUndo() {
        return !undoHistory.isEmpty();
    }

    public boolean canRedo() {
        return !redoHistory.isEmpty();
    }

    public void undo() {
        if (!canUndo()) {
            return;
        }
        redoHistory.push(historyDocument);
        restoreHistoryState(undoHistory.pop());
    }

    public void redo() {
        if (!canRedo()) {
            return;
        }
        undoHistory.push(historyDocument);
        restoreHistoryState(redoHistory.pop());
    }

    public void beginHistoryGroup() {
        historyGroupActive = true;
        historyGroupChanged = false;
    }

    public void endHistoryGroup() {
        historyGroupActive = false;
        historyGroupChanged = false;
    }

    @Override
    public void actualizeText() {
        super.actualizeText();
        actualizeCastleNavigationText();
    }

    @Override
    public int onFileOpen(File file) {
        try {
            CastleDocument document = new CastleReader().read(file.toPath());
            editorPanel.setDocument(document);
            resetHistory(document);
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
        resetHistory(null);
        return super.onFileClose();
    }

    private void recordDocumentChange() {
        CastleDocument currentDocument = editorPanel.getDocument();
        if (!restoringHistory && !Objects.equals(currentDocument, historyDocument)) {
            if (historyDocument != null && (!historyGroupActive || !historyGroupChanged)) {
                undoHistory.push(historyDocument);
            }
            if (!historyGroupActive || !historyGroupChanged) {
                redoHistory.clear();
            }
            historyDocument = currentDocument;
            historyGroupChanged = true;
        }
        setChanged(true);
    }

    private void restoreHistoryState(CastleDocument document) {
        restoringHistory = true;
        try {
            editorPanel.setDocument(document);
            historyDocument = document;
            setChanged(true);
        } finally {
            restoringHistory = false;
        }
        if (getMenu() != null) {
            getMenu().setFileMenuStates();
        }
    }

    private void resetHistory(CastleDocument document) {
        undoHistory.clear();
        redoHistory.clear();
        historyDocument = document;
        historyGroupActive = false;
        historyGroupChanged = false;
    }
}
