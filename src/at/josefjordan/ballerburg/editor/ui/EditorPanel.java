package at.josefjordan.ballerburg.editor.ui;

import at.josefjordan.ballerburg.editor.model.BurgDocument;
import hgb.gui.HGBasePanel;

public class EditorPanel extends HGBasePanel {
    private BurgDocument document;

    public BurgDocument getDocument() {
        return document;
    }

    public void setDocument(BurgDocument document) {
        this.document = document;
        repaint();
    }
}
