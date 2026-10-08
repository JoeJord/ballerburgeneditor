package at.josefjordan.ballerburg.editor.ui.editor;

import hgb.gui.HGBasePanel;
import hgb.gui.HGBaseGuiTools;
import hgb.lib.HGBaseText;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.util.List;

/**
 * Displays validation problems for the current castle.
 */
final class ProblemsPanel extends HGBasePanel {
    /**
     * Model holding the severity and localized description for each validation finding.
     */
    private final ProblemTableModel tableModel = new ProblemTableModel();
    /**
     * Icon shown for blocking validation errors.
     */
    private final transient Icon errorIcon = HGBaseGuiTools.loadImage("/gfx/error.png");
    /**
     * Icon shown for non-blocking warnings.
     */
    private final transient Icon warningIcon = HGBaseGuiTools.loadImage("/gfx/warning.png");

    /**
     * Builds the sortable validation table with severity icons and localized headings.
     */
    ProblemsPanel() {
        super(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder(HGBaseText.getText("problems.title")));
        JTable table = new JTable(tableModel);
        table.setShowGrid(false);
        table.setRowHeight(Math.max(table.getRowHeight(), 22));
        table.setFillsViewportHeight(true);
        table.setRowSorter(new ProblemTableRowSorter(tableModel));
        table.getColumnModel().getColumn(0).setPreferredWidth(28);
        table.getColumnModel().getColumn(0).setMaxWidth(28);
        table.getColumnModel().getColumn(0).setCellRenderer(new SeverityCellRenderer());
        add(new JScrollPane(table), BorderLayout.CENTER);
    }

    /**
     * Replaces the validation problems shown in the table.
     *
     * @param problems the problems to display
     */
    void setProblems(List<Problem> problems) {
        tableModel.setProblems(problems);
    }

    @Override
    public void actualizeText() {
        setBorder(BorderFactory.createTitledBorder(HGBaseText.getText("problems.title")));
        tableModel.setColumnIdentifiers(new Object[]{
                HGBaseText.getText("problems.type"),
                HGBaseText.getText("problems.description")
        });
    }

    /**
     * Renders a problem severity as an icon and tooltip.
     */
    private final class SeverityCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focused, int row, int column) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(
                    table, value, selected, focused, row, column);
            label.setHorizontalAlignment(CENTER);
            label.setIcon(value == ProblemSeverity.ERROR ? errorIcon : warningIcon);
            label.setText("");
            label.setToolTipText(HGBaseText.getText(value == ProblemSeverity.ERROR
                    ? "problems.error"
                    : "problems.warning"));
            return label;
        }
    }

    /**
     * Sorts problem rows and cycles back to the unsorted order.
     */
    private static final class ProblemTableRowSorter extends TableRowSorter<ProblemTableModel> {
        /**
         * Creates a sorter for the validation-problem rows.
         *
         * @param model table model whose rows are sorted
         */
        ProblemTableRowSorter(ProblemTableModel model) {
            super(model);
        }

        @Override
        public void toggleSortOrder(int column) {
            List<? extends SortKey> sortKeys = getSortKeys();
            if (!sortKeys.isEmpty()
                    && sortKeys.getFirst().getColumn() == column
                    && sortKeys.getFirst().getSortOrder() == SortOrder.DESCENDING) {
                setSortKeys(List.of());
            } else {
                super.toggleSortOrder(column);
            }
        }
    }

    /**
     * Stores the severity and text of validation problems.
     */
    private static final class ProblemTableModel extends DefaultTableModel {
        /**
         * Creates an initially empty table model with localized columns.
         */
        ProblemTableModel() {
            super(new Object[][]{}, new Object[]{
                    HGBaseText.getText("problems.type"),
                    HGBaseText.getText("problems.description")
            });
        }

        /**
         * Replaces the validation problems shown in the table.
         *
         * @param problems the problems to display
         */
        void setProblems(List<Problem> problems) {
            setRowCount(0);
            for (Problem problem : problems) {
                addRow(new Object[]{problem.severity(), problem.text()});
            }
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    }

    /**
     * The severity levels assigned to validation problems.
     */
    enum ProblemSeverity {
        /**
         * A problem that prevents a castle from being saved without confirmation.
         */
        ERROR,
        /**
         * A problem that is informational or does not prevent saving.
         */
        WARNING
    }

    /**
     * A validation problem and its severity.
     *
     * @param severity severity of the problem
     * @param text     user-visible problem description
     */
    record Problem(ProblemSeverity severity, String text) {
    }
}
