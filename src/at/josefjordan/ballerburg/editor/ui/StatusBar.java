package at.josefjordan.ballerburg.editor.ui;

import hgb.gui.HGBaseStatusBar;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.IntConsumer;

public class StatusBar extends HGBaseStatusBar {
    private static final int ZOOM_PANEL_INDEX = 1;
    private static final int PROGRESS_PANEL_INDEX = 2;
    private static final int ZOOM_LEVEL_WIDTH = 150;
    private static final int PROGRESS_PANEL_WIDTH = 100;
    private static final int MIN_ZOOM_PERCENT = 10;
    private static final int MAX_ZOOM_PERCENT = 1000;

    private final JSlider zoomSlider = new JSlider(SwingConstants.HORIZONTAL,
            MIN_ZOOM_PERCENT, MAX_ZOOM_PERCENT, 100);
    private final JLabel zoomLabel = new JLabel("100%");
    private final JLabel zoomResetLabel = new JLabel("1:1");
    private IntConsumer zoomListener;

    StatusBar() {
        super(new int[] { 0, ZOOM_LEVEL_WIDTH, PROGRESS_PANEL_WIDTH });
        getPanel(ZOOM_PANEL_INDEX).remove(getLabel(ZOOM_PANEL_INDEX));
        zoomSlider.setFocusable(false);
        zoomSlider.setToolTipText("Zoom");
        zoomSlider.addChangeListener(event -> {
            int zoomPercent = zoomSlider.getValue();
            zoomLabel.setText(zoomPercent + "%");
            if (zoomListener != null) {
                zoomListener.accept(zoomPercent);
            }
        });
        zoomLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        zoomResetLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        zoomResetLabel.setToolTipText("Zoom auf 100% zurücksetzen");
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

    void setZoomLevel(int zoomPercent) {
        int boundedZoomPercent = Math.max(MIN_ZOOM_PERCENT, Math.min(MAX_ZOOM_PERCENT, zoomPercent));
        zoomSlider.setValue(boundedZoomPercent);
        zoomLabel.setText(boundedZoomPercent + "%");
    }

    void setZoomListener(IntConsumer zoomListener) {
        this.zoomListener = zoomListener;
    }

    void setHint(String hint) {
        setText(0, hint);
    }
}
