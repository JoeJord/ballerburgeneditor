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

/// The main frame for the editor.
public class MainFrame extends HGBaseFrame {
    private EditorPanel editorPanel;
    private JButton previousCastleButton;
    private JButton nextCastleButton;
    private JLabel castlePositionLabel;

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
        editorPanel.setChangeListener(() -> setChanged(true));
        editorPanel.setZoomListener(this::updateZoomDisplay);
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
            ((StatusBar) getStatusBar()).setZoomLevel(zoomPercent + "%");
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

    public void addMoney() {
        editorPanel.addMoney();
    }

    public void addPowder() {
        editorPanel.addPowder();
    }

    public void addCannonballs() {
        editorPanel.addCannonballs();
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

    public boolean hasSelectedResourceArea() {
        return editorPanel != null && editorPanel.hasSelectedResourceArea();
    }

    public int getCannonCount() {
        return editorPanel != null ? editorPanel.getCannonCount() : 0;
    }

    public boolean hasThrone() {
        return editorPanel != null && editorPanel.hasThrone();
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

    public void removeCannon() {
        editorPanel.removeCannon();
    }

    public void removeThrone() {
        editorPanel.removeThrone();
    }

    public void removeResourceArea() {
        editorPanel.removeResourceArea();
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
        return super.onFileClose();
    }
}
