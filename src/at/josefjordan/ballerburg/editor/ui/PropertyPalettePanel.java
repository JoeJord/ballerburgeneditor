package at.josefjordan.ballerburg.editor.ui;

import at.josefjordan.ballerburg.editor.model.BurgDocument.Castle;
import at.josefjordan.ballerburg.editor.model.BurgDocument.Resources;
import hgb.gui.HGBasePanel;
import hgb.lib.HGBaseText;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.function.Consumer;

/** Property palette for the editable values of the selected castle. */
final class PropertyPalettePanel extends HGBasePanel {
    private final JLabel titleLabel = new JLabel();
    private final JLabel moneyLabel = new JLabel();
    private final JLabel powderLabel = new JLabel();
    private final JLabel ballsLabel = new JLabel();
    private final JLabel peopleLabel = new JLabel();
    private final JSpinner moneySpinner = createSpinner();
    private final JSpinner powderSpinner = createSpinner();
    private final JSpinner ballsSpinner = createSpinner();
    private final JSpinner peopleSpinner = createSpinner();
    private Consumer<Resources> resourcesListener;
    private boolean updating;

    PropertyPalettePanel() {
        super(new GridBagLayout());
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.gridwidth = 2;
        constraints.anchor = GridBagConstraints.LINE_START;
        constraints.insets = new Insets(0, 0, 12, 0);
        add(titleLabel, constraints);

        constraints.gridwidth = 1;
        constraints.insets = new Insets(3, 0, 3, 8);
        addRow(moneyLabel, moneySpinner, constraints);
        addRow(powderLabel, powderSpinner, constraints);
        addRow(ballsLabel, ballsSpinner, constraints);
        addRow(peopleLabel, peopleSpinner, constraints);
        constraints.gridy++;
        constraints.weighty = 1.0;
        add(new JLabel(), constraints);

        moneySpinner.addChangeListener(event -> fireResourcesChanged());
        powderSpinner.addChangeListener(event -> fireResourcesChanged());
        ballsSpinner.addChangeListener(event -> fireResourcesChanged());
        peopleSpinner.addChangeListener(event -> fireResourcesChanged());
        actualizeText();
        setCastle(null);
    }

    void setResourcesListener(Consumer<Resources> resourcesListener) {
        this.resourcesListener = resourcesListener;
    }

    void setCastle(Castle castle) {
        updating = true;
        try {
            Resources resources = castle == null ? new Resources(0, 0, 0, 0) : castle.resources();
            moneySpinner.setValue(resources.money());
            powderSpinner.setValue(resources.powder());
            ballsSpinner.setValue(resources.balls());
            peopleSpinner.setValue(resources.people());
            setFieldsEnabled(castle != null);
        } finally {
            updating = false;
        }
    }

    @Override
    public void actualizeText() {
        titleLabel.setText(HGBaseText.getText("properties.title"));
        moneyLabel.setText(HGBaseText.getText("properties.money"));
        powderLabel.setText(HGBaseText.getText("properties.powder"));
        ballsLabel.setText(HGBaseText.getText("properties.balls"));
        peopleLabel.setText(HGBaseText.getText("properties.people"));
    }

    private static JSpinner createSpinner() {
        return new JSpinner(new SpinnerNumberModel(0, 0, Integer.MAX_VALUE, 1));
    }

    private void addRow(JLabel label, JSpinner spinner, GridBagConstraints constraints) {
        constraints.gridx = 0;
        constraints.gridy++;
        constraints.weightx = 0.0;
        constraints.fill = GridBagConstraints.NONE;
        add(label, constraints);
        constraints.gridx = 1;
        constraints.weightx = 1.0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        add(spinner, constraints);
    }

    private void setFieldsEnabled(boolean enabled) {
        moneySpinner.setEnabled(enabled);
        powderSpinner.setEnabled(enabled);
        ballsSpinner.setEnabled(enabled);
        peopleSpinner.setEnabled(enabled);
    }

    private void fireResourcesChanged() {
        if (!updating && resourcesListener != null) {
            resourcesListener.accept(new Resources((Integer) moneySpinner.getValue(),
                    (Integer) powderSpinner.getValue(), (Integer) ballsSpinner.getValue(),
                    (Integer) peopleSpinner.getValue()));
        }
    }
}
