package at.josefjordan.ballerburg.editor.ui;

import at.josefjordan.ballerburg.editor.model.BurgDocument;
import at.josefjordan.ballerburg.editor.model.BurgDocument.Castle;
import at.josefjordan.ballerburg.editor.model.BurgDocument.Resources;
import hgb.gui.HGBasePanel;

import javax.swing.JSplitPane;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;

/** Editor containing the castle view and the property palette. */
public class EditorPanel extends HGBasePanel {
    private final CastleViewPanel castleView = new CastleViewPanel();
    private final PropertyPalettePanel propertyPalette = new PropertyPalettePanel();
    private BurgDocument document;
    private int castleIndex = -1;
    private Runnable selectionListener;
    private Runnable changeListener;

    public EditorPanel() {
        super(new BorderLayout());
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, castleView, propertyPalette);
        splitPane.setContinuousLayout(true);
        splitPane.setResizeWeight(1.0);
        propertyPalette.setMinimumSize(new Dimension(200, 0));
        propertyPalette.setPreferredSize(new Dimension(230, 0));
        add(splitPane, BorderLayout.CENTER);
        propertyPalette.setResourcesListener(this::updateResources);
        updateSelectedCastle();
    }

    public BurgDocument getDocument() {
        return document;
    }

    public void setDocument(BurgDocument document) {
        this.document = document;
        castleIndex = document == null || document.castles().isEmpty() ? -1 : 0;
        updateSelectedCastle();
        notifySelectionChanged();
    }

    public int getCastleIndex() {
        return castleIndex;
    }

    public int getCastleCount() {
        return document == null ? 0 : document.castles().size();
    }

    public void showPreviousCastle() {
        setCastleIndex(castleIndex - 1);
    }

    public void showNextCastle() {
        setCastleIndex(castleIndex + 1);
    }

    public void setSelectionListener(Runnable selectionListener) {
        this.selectionListener = selectionListener;
    }

    public void setChangeListener(Runnable changeListener) {
        this.changeListener = changeListener;
    }

    private void setCastleIndex(int index) {
        if (index >= 0 && index < getCastleCount() && index != castleIndex) {
            castleIndex = index;
            updateSelectedCastle();
            notifySelectionChanged();
        }
    }

    private void updateSelectedCastle() {
        Castle castle = castleIndex >= 0 && castleIndex < getCastleCount()
                ? document.castles().get(castleIndex) : null;
        castleView.setCastle(castle);
        propertyPalette.setCastle(castle);
    }

    private void updateResources(Resources resources) {
        if (castleIndex < 0 || castleIndex >= getCastleCount()) {
            return;
        }
        Castle oldCastle = document.castles().get(castleIndex);
        if (oldCastle.resources().equals(resources)) {
            return;
        }
        Castle updatedCastle = new Castle(oldCastle.width(), oldCastle.cannons(), oldCastle.throne(),
                oldCastle.flag(), oldCastle.money(), oldCastle.powder(), oldCastle.balls(),
                oldCastle.moneyRoom(), oldCastle.powderRoom(), oldCastle.ballRoom(), resources,
                oldCastle.reserved(), oldCastle.drawing());
        List<Castle> castles = new ArrayList<>(document.castles());
        castles.set(castleIndex, updatedCastle);
        document = new BurgDocument(castles);
        castleView.setCastle(updatedCastle);
        if (changeListener != null) {
            changeListener.run();
        }
    }

    private void notifySelectionChanged() {
        if (selectionListener != null) {
            selectionListener.run();
        }
    }
}
