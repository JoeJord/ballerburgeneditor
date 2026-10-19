package at.josefjordan.ballerburg.editor.ui;

import hgb.gui.HGBaseStatusBar;
import hgb.lib.HGBaseText;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.IntConsumer;

/**
 * The application status bar, including zoom controls.
 */
public class StatusBar extends HGBaseStatusBar {
    /**
     * Index of the status-bar panel that contains the zoom controls.
     */
    private static final int ZOOM_PANEL_INDEX = 1;
    /**
     * Index of the status-bar panel used for progress display.
     */
    private static final int PROGRESS_PANEL_INDEX = 2;
    /**
     * Preferred width of the zoom-controls panel in pixels.
     */
    private static final int ZOOM_LEVEL_WIDTH = 150;
    /**
     * Preferred width of the progress panel in pixels.
     */
    private static final int PROGRESS_PANEL_WIDTH = 100;
    /**
     * Lowest zoom percentage accepted by the slider.
     */
    private static final int MIN_ZOOM_PERCENT = 10;
    /**
     * Highest zoom percentage accepted by the slider.
     */
    private static final int MAX_ZOOM_PERCENT = 1000;

    /**
     * Slider used to set the editor zoom percentage.
     */
    private final JSlider zoomSlider = new JSlider(SwingConstants.HORIZONTAL, MIN_ZOOM_PERCENT, MAX_ZOOM_PERCENT, 100);
    /**
     * Displays the current zoom percentage.
     */
    private final JLabel zoomLabel = new JLabel("100%");
    /**
     * Clickable control that restores zoom to 100%.
     */
    private final JLabel zoomResetLabel = new JLabel("1:1");
    /**
     * Localization key for the currently displayed hint, or an empty string when there is none.
     */
    private String hintKey = "";
    /**
     * The listener notified when zoom changes.
     */
    private transient IntConsumer zoomListener;

    /**
     * Creates the status bar's zoom slider, readout, reset control, and progress panel.
     */
    StatusBar() {
        super(new int[]{0, ZOOM_LEVEL_WIDTH, PROGRESS_PANEL_WIDTH});
        getPanel(ZOOM_PANEL_INDEX).remove(getLabel(ZOOM_PANEL_INDEX));
        zoomSlider.setFocusable(false);
        zoomSlider.setToolTipText("Zoom");
        zoomSlider.addChangeListener(_ -> {
            int zoomPercent = zoomSlider.getValue();
            zoomLabel.setText(zoomPercent + "%");
            if (zoomListener != null) {
                zoomListener.accept(zoomPercent);
            }
        });
        zoomLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        zoomResetLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        actualizeText();
        zoomResetLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        zoomResetLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                zoomSlider.setValue(100);
            }
        });
        JPanel zoomDisplayPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        zoomDisplayPanel.setOpaque(false);
        zoomDisplayPanel.add(zoomLabel);
        zoomDisplayPanel.add(zoomResetLabel);
        getPanel(ZOOM_PANEL_INDEX).setLayout(new BorderLayout(4, 0));
        getPanel(ZOOM_PANEL_INDEX).add(zoomSlider, BorderLayout.CENTER);
        getPanel(ZOOM_PANEL_INDEX).add(zoomDisplayPanel, BorderLayout.EAST);
        setProgressPanel(PROGRESS_PANEL_INDEX);
    }

    @Override
    public void actualizeText() {
        zoomResetLabel.setToolTipText(HGBaseText.getText("zoom.reset_tooltip"));
        setHint(hintKey);
    }

    /**
     * Clamps and displays the requested zoom percentage.
     *
     * @param zoomPercent the desired zoom percentage
     */
    void setZoomLevel(int zoomPercent) {
        int boundedZoomPercent = Math.clamp(zoomPercent, MIN_ZOOM_PERCENT, MAX_ZOOM_PERCENT);
        zoomSlider.setValue(boundedZoomPercent);
        zoomLabel.setText(boundedZoomPercent + "%");
    }

    /**
     * Registers the callback invoked when zoom changes.
     *
     * @param zoomListener the callback notified when zoom changes
     */
    void setZoomListener(IntConsumer zoomListener) {
        this.zoomListener = zoomListener;
    }

    /**
     * Displays a hint in the status bar.
     *
     * @param hintKey localization key for the status-bar message, or an empty string to clear it
     */
    void setHint(String hintKey) {
        this.hintKey = hintKey;
        setText(0, hintKey.isEmpty() ? "" : HGBaseText.getText(hintKey));
    }
}
