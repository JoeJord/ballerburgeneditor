package at.josefjordan.ballerburg.editor.ui;

import hgb.gui.HGBaseStatusBar;

public class StatusBar extends HGBaseStatusBar {
    private static final int ZOOM_PANEL_INDEX = 1;
    private static final int PROGRESS_PANEL_INDEX = 2;
    private static final int ZOOM_LEVEL_WIDTH = 40;
    private static final int PROGRESS_PANEL_WIDTH = 100;

    StatusBar() {
        super(new int[] { 0, ZOOM_LEVEL_WIDTH, PROGRESS_PANEL_WIDTH });
        setProgressPanel(PROGRESS_PANEL_INDEX);
    }

    void setZoomLevel(String zoomLevel) {
        setText(ZOOM_PANEL_INDEX, zoomLevel);
    }

    void setHint(String hint) {
        setText(0, hint);
    }
}
