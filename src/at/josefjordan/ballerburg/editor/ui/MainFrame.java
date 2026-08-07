package at.josefjordan.ballerburg.editor.ui;

import hgb.gui.HGBaseFrame;
import hgb.gui.menu.HGBaseMenu;

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
}
