package at.josefjordan.ballerburg.editor.ui;

import at.josefjordan.ballerburg.editor.io.BurgenReader;
import at.josefjordan.ballerburg.editor.io.BurgenWriter;
import at.josefjordan.ballerburg.editor.model.BurgDocument;
import hgb.gui.HGBaseDialog;
import hgb.gui.HGBaseFrame;
import hgb.gui.menu.HGBaseMenu;
import hgb.lib.HGBaseTools;

import java.io.File;
import java.io.IOException;

/// The main frame for the editor.
public class MainFrame extends HGBaseFrame {
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
        mainFrame.setMenu(new HGBaseMenu(mainFrame));
        mainFrame.setPanel(new EditorPanel());
    }

    @Override
    public int onFileOpen(File file) {
        try {
            BurgDocument document = new BurgenReader().read(file.toPath());
            ((EditorPanel) getPanel()).setDocument(document);
            return 0;
        } catch (IOException | IllegalArgumentException exception) {
            HGBaseDialog.printError(exception, this);
            return HGBaseTools.INVALID;
        }
    }

    @Override
    public int onFileSave(File file) {
        BurgDocument document = ((EditorPanel) getPanel()).getDocument();
        if (document == null) {
            return HGBaseTools.INVALID;
        }
        try {
            new BurgenWriter().write(file.toPath(), document);
            return 0;
        } catch (IOException exception) {
            HGBaseDialog.printError(exception, this);
            return HGBaseTools.INVALID;
        }
    }
}
