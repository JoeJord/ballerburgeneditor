package at.josefjordan.ballerburg.editor.ui.editor.properties;

import at.josefjordan.ballerburg.editor.model.CastleDocument.Castle;
import at.josefjordan.ballerburg.editor.model.CastleDocument.Resources;
import at.josefjordan.ballerburg.editor.model.CastleDocument.Size;
import at.josefjordan.ballerburg.editor.model.CastleFileFormat;
import hgb.gui.HGBasePanel;
import hgb.lib.HGBaseText;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

/**
 * Property palette for the editable values of the selected castle.
 */
public final class PropertyPalettePanel extends HGBasePanel {
    /**
     * Localized heading for the selected castle's editable properties.
     */
    private final JLabel titleLabel = new JLabel();
    /**
     * Label for the starting-money spinner.
     */
    private final JLabel moneyLabel = new JLabel();
    /**
     * Label for the starting-powder spinner.
     */
    private final JLabel powderLabel = new JLabel();
    /**
     * Label for the starting-cannonball spinner.
     */
    private final JLabel ballsLabel = new JLabel();
    /**
     * Label for the starting-people spinner.
     */
    private final JLabel peopleLabel = new JLabel();
    /**
     * The money spinner.
     */
    private final JSpinner moneySpinner = createSpinner();
    /**
     * The powder spinner.
     */
    private final JSpinner powderSpinner = createSpinner();
    /**
     * The balls spinner.
     */
    private final JSpinner ballsSpinner = createSpinner();
    /**
     * The people spinner.
     */
    private final JSpinner peopleSpinner = createSpinner();
    /**
     * The listener notified when resource values change.
     */
    private transient Consumer<Resources> resourcesListener;
    /**
     * Whether spinner changes are being applied programmatically.
     */
    private boolean updating;

    /**
     * Creates the localized resource controls and wires their change notifications.
     */
    public PropertyPalettePanel() {
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

        moneySpinner.addChangeListener(_ -> fireResourcesChanged());
        powderSpinner.addChangeListener(_ -> fireResourcesChanged());
        ballsSpinner.addChangeListener(_ -> fireResourcesChanged());
        peopleSpinner.addChangeListener(_ -> fireResourcesChanged());
        actualizeText();
        setCastle(null);
    }

    /**
     * Registers the callback invoked when resource values change.
     *
     * @param resourcesListener the callback notified when resource values change
     */
    public void setResourcesListener(Consumer<Resources> resourcesListener) {
        this.resourcesListener = resourcesListener;
    }

    /**
     * Displays the castle's resource values and enables the controls when a castle is present.
     *
     * @param castle castle whose resource values and storage capacities to display, or {@code null} to clear
     */
    public void setCastle(Castle castle) {
        updating = true;
        try {
            Resources resources = castle == null ? new Resources(0, 0, 0, 0) : castle.resources();
            setResourceMaximums(castle);
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

    /**
     * Creates a non-negative integer spinner with unit increments.
     *
     * @return the configured resource-value spinner
     */
    private static JSpinner createSpinner() {
        return new JSpinner(new SpinnerNumberModel(0, 0, Integer.MAX_VALUE, 1));
    }

    /**
     * Sets each resource spinner maximum from its storage-area capacity.
     *
     * @param castle castle whose storage areas define the spinner limits, or {@code null} for zero capacity
     */
    private void setResourceMaximums(Castle castle) {
        Size moneyRoom = castle == null ? new Size(0, 0) : castle.moneyRoom();
        Size powderRoom = castle == null ? new Size(0, 0) : castle.powderRoom();
        Size ballRoom = castle == null ? new Size(0, 0) : castle.ballRoom();
        ((SpinnerNumberModel) moneySpinner.getModel()).setMaximum(
                resourceValueCapacity(moneyRoom, CastleFileFormat.MONEY_WIDTH, CastleFileFormat.MONEY_HEIGHT,
                        CastleFileFormat.MONEY_PER_UNIT));
        ((SpinnerNumberModel) powderSpinner.getModel()).setMaximum(
                resourceValueCapacity(powderRoom, CastleFileFormat.POWDER_WIDTH, CastleFileFormat.POWDER_HEIGHT,
                        CastleFileFormat.POWDER_PER_UNIT));
        ((SpinnerNumberModel) ballsSpinner.getModel()).setMaximum(
                resourceValueCapacity(ballRoom, CastleFileFormat.CANNONBALL_WIDTH, CastleFileFormat.CANNONBALL_HEIGHT,
                        1));
    }

    /**
     * Calculates the maximum resource value that fits in the storage area.
     *
     * @param room         storage-area dimensions in castle coordinates
     * @param slotWidth    width of one resource slot
     * @param slotHeight   height of one resource slot
     * @param valuePerSlot resource quantity represented by one slot
     * @return the maximum quantity that fits in the area's complete slots
     */
    private int resourceValueCapacity(Size room, int slotWidth, int slotHeight, int valuePerSlot) {
        long slots = (long) (room.width() / slotWidth) * (room.height() / slotHeight);
        return (int) Math.min(Integer.MAX_VALUE, slots * valuePerSlot);
    }

    /**
     * Adds one label-and-spinner row using and advancing the shared grid constraints.
     *
     * @param label       descriptive label for the resource value
     * @param spinner     control for editing that value
     * @param constraints layout constraints shared by the property rows
     */
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

    /**
     * Enables or disables all resource-value spinners.
     *
     * @param enabled whether the component should be enabled
     */
    private void setFieldsEnabled(boolean enabled) {
        moneySpinner.setEnabled(enabled);
        powderSpinner.setEnabled(enabled);
        ballsSpinner.setEnabled(enabled);
        peopleSpinner.setEnabled(enabled);
    }

    /**
     * Sends the current spinner values to the registered listener when values change outside refreshes.
     */
    private void fireResourcesChanged() {
        if (!updating && resourcesListener != null) {
            resourcesListener.accept(new Resources((Integer) moneySpinner.getValue(),
                    (Integer) powderSpinner.getValue(), (Integer) ballsSpinner.getValue(),
                    (Integer) peopleSpinner.getValue()));
        }
    }
}
