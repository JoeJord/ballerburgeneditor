package at.josefjordan.ballerburg.editor.ui.editor;

import hgb.gui.HGBasePanel;
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
    private final ProblemTableModel tableModel = new ProblemTableModel();
    private final transient Icon errorIcon = UIManager.getIcon("OptionPane.errorIcon");
    private final transient Icon warningIcon = UIManager.getIcon("OptionPane.warningIcon");

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

    private static final class ProblemTableRowSorter extends TableRowSorter<ProblemTableModel> {
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

    private static final class ProblemTableModel extends DefaultTableModel {
        ProblemTableModel() {
            super(new Object[][]{}, new Object[]{
                    HGBaseText.getText("problems.type"),
                    HGBaseText.getText("problems.description")
            });
        }

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

    enum ProblemSeverity {
        ERROR,
        WARNING
    }

    record Problem(ProblemSeverity severity, String text) {
    }
}
