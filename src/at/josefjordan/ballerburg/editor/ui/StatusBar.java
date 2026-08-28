package at.josefjordan.ballerburg.editor.ui;

import hgb.gui.HGBaseStatusBar;

public class StatusBar extends HGBaseStatusBar {
    private static final int PROGRESS_PANEL_WIDTH = 100;
    StatusBar() {
        super(new int[] { 0, PROGRESS_PANEL_WIDTH });
        setProgressPanel(1);
    }
}
