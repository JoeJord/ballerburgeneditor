package at.josefjordan.ballerburg.editor.ui.editor;

import at.josefjordan.ballerburg.editor.model.CastleDocument;
import at.josefjordan.ballerburg.editor.model.CastleDocument.Castle;
import at.josefjordan.ballerburg.editor.model.CastleDocument.Point;
import at.josefjordan.ballerburg.editor.model.CastleDocument.Size;
import at.josefjordan.ballerburg.editor.ui.editor.castle.*;
import at.josefjordan.ballerburg.editor.ui.editor.problems.ProblemsPanel;
import at.josefjordan.ballerburg.editor.ui.editor.properties.PropertyPalettePanel;
import hgb.gui.HGBasePanel;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Editor containing the castle view, property palette, and problems panel.
 */
public class EditorPanel extends HGBasePanel {
    /**
     * Minimum width reserved for the property palette in pixels.
     */
    private static final int PROPERTY_PALETTE_MIN_WIDTH = 200;
    /**
     * Preferred width of the property palette in pixels.
     */
    private static final int PROPERTY_PALETTE_PREFERRED_WIDTH = 230;
    /**
     * Preferred height of the validation-problems panel in pixels.
     */
    private static final int PROBLEMS_PANEL_PREFERRED_HEIGHT = 120;
    /**
     * Canvas that renders and edits the selected castle.
     */
    private final CastleViewPanel castleView = new CastleViewPanel();
    /**
     * Controls for editing the selected castle's starting resource values.
     */
    private final PropertyPalettePanel propertyPalette = new PropertyPalettePanel();
    /**
     * Table showing validation findings for the selected castle.
     */
    private final ProblemsPanel problemsPanel = new ProblemsPanel();
    /**
     * Validates castle structure and placement.
     */
    private final transient CastleValidator castleValidator = new CastleValidator();
    /**
     * Owns the current document, selected castle, and document-level edit coordination.
     */
    private final transient EditorDocumentController documentController;
    /**
     * Coordinates selection queries, selection-dependent edits, and polygon hints.
     */
    private final transient EditorSelectionController selectionController;
    /**
     * Implements castle-element creation and editing operations.
     */
    private final transient EditorCastleActions castleActions;
    /**
     * The listener notified when the selection changes.
     */
    private transient Runnable selectionListener;
    /**
     * The listener notified when the document changes.
     */
    private transient Runnable changeListener;
    /**
     * Callback that starts grouping changes into one history operation.
     */
    private transient Runnable beginHistoryGroupListener;
    /**
     * Callback that ends the current grouped history operation.
     */
    private transient Runnable endHistoryGroupListener;
    /**
     * The listener notified when zoom changes.
     */
    private transient Runnable zoomListener;
    /**
     * The listener used to update the status bar.
     */
    private transient java.util.function.Consumer<String> statusBarListener;

    /**
     * Creates the castle canvas, property palette, and validation panel and connects their callbacks.
     */
    public EditorPanel() {
        super(new BorderLayout());
        documentController = new EditorDocumentController(new EditorDocumentController.Callbacks(
                this::updateSelectedCastle, this::refreshEditedCastle,
                this::notifySelectionChanged, this::notifyDocumentChanged));
        ResourceAreaEditor resourceAreaEditor = new ResourceAreaEditor();
        CastlePolygonEditor polygonEditor = new CastlePolygonEditor();
        CastleElementEditor castleElementEditor = new CastleElementEditor();
        selectionController = new EditorSelectionController(castleView, polygonEditor, castleElementEditor,
                resourceAreaEditor, new EditorSelectionController.Callbacks(this::getSelectedCastle,
                this::commitSelectionEdit, this::notifySelectionChanged, this::setStatusBarText));
        castleActions = new EditorCastleActions(castleView, castleElementEditor, resourceAreaEditor, polygonEditor,
                new EditorCastleActions.Callbacks(this::getSelectedCastle,
                        castle -> commitSelectedCastleChange(castle, true),
                        this::replaceSelectedCastle, this::setStatusBarText));
        castleView.setEditorPanel(this);
        castleView.setZoomListener(() -> {
            if (zoomListener != null) {
                zoomListener.run();
            }
        });
        castleView.setPolygonSelectionListener(selectionController::onPolygonSelectionChanged);
        JScrollPane scrollPane = new JScrollPane(castleView);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, scrollPane, propertyPalette);
        splitPane.setContinuousLayout(true);
        splitPane.setResizeWeight(1.0);
        propertyPalette.setMinimumSize(new Dimension(PROPERTY_PALETTE_MIN_WIDTH, 0));
        propertyPalette.setPreferredSize(new Dimension(PROPERTY_PALETTE_PREFERRED_WIDTH, 0));
        JSplitPane propertySplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, propertyPalette, problemsPanel);
        propertySplitPane.setContinuousLayout(true);
        propertySplitPane.setResizeWeight(0.7);
        problemsPanel.setPreferredSize(new Dimension(0, PROBLEMS_PANEL_PREFERRED_HEIGHT));
        splitPane.setRightComponent(propertySplitPane);
        add(splitPane, BorderLayout.CENTER);
        propertyPalette.setResourcesListener(castleActions::updateResources);
        updateSelectedCastle(null);
    }

    @Override
    public void actualizeText() {
        super.actualizeText();
        propertyPalette.actualizeText();
        problemsPanel.actualizeText();
        updateProblems(getSelectedCastle());
    }

    /**
     * Returns the current document, or null when no document is open.
     *
     * @return the current document, or null when no document is open
     */
    public CastleDocument getDocument() {
        return documentController.getDocument();
    }

    /**
     * Shows or hides the grid over the castle canvas.
     *
     * @param visible whether the grid should be visible
     */
    public void setGridVisible(boolean visible) {
        castleView.setGridVisible(visible);
    }

    /**
     * Returns whether the grid is displayed.
     *
     * @return {@code true} if the castle canvas currently displays the grid
     */
    public boolean isGridVisible() {
        return castleView.isGridVisible();
    }

    /**
     * Replaces the document, selects its first castle when present, and refreshes the view.
     *
     * @param document the document to process
     */
    public void setDocument(CastleDocument document) {
        documentController.setDocument(document);
    }

    /**
     * Returns the castle index.
     *
     * @return the selected castle index, or -1 when none is selected
     */
    public int getCastleIndex() {
        return documentController.getCastleIndex();
    }

    /**
     * Returns the castle count.
     *
     * @return the number of castles in the document
     */
    public int getCastleCount() {
        return documentController.getCastleCount();
    }

    /**
     * Returns whether any castle in the current document has validation problems.
     *
     * @return {@code true} when a document is open and at least one castle has validation findings
     */
    public boolean hasProblems() {
        CastleDocument document = documentController.getDocument();
        return document != null && document.castles().stream()
                .anyMatch(castle -> !castleValidator.validate(castle).isEmpty());
    }

    /**
     * Returns the cannon count.
     *
     * @return the selected castle cannon count, or zero if no castle is selected
     */
    public int getCannonCount() {
        Castle castle = documentController.getSelectedCastle();
        return castle == null ? 0 : castle.cannons().size();
    }

    /**
     * Returns whether the selected castle has a throne.
     *
     * @return {@code true} if the selected castle has a positioned throne
     */
    public boolean hasThrone() {
        Castle castle = documentController.getSelectedCastle();
        return castle != null && isPositioned(castle.throne());
    }

    /**
     * Returns whether the selected castle has a wind vane.
     *
     * @return {@code true} if the selected castle has a positioned wind vane
     */
    public boolean hasWindVane() {
        Castle castle = documentController.getSelectedCastle();
        return castle != null && isPositioned(castle.windVane());
    }

    /**
     * Returns whether the selected castle has a money-storage area.
     *
     * @return {@code true} if the selected castle has a positioned money-storage area
     */
    public boolean hasMoney() {
        Castle castle = documentController.getSelectedCastle();
        return castle != null && isPositioned(castle.money());
    }

    /**
     * Returns whether the selected castle has a powder-storage area.
     *
     * @return {@code true} if the selected castle has a positioned powder-storage area
     */
    public boolean hasPowder() {
        Castle castle = documentController.getSelectedCastle();
        return castle != null && isPositioned(castle.powder());
    }

    /**
     * Returns whether the selected castle has a cannonball-storage area.
     *
     * @return {@code true} if the selected castle has a positioned cannonball-storage area
     */
    public boolean hasBalls() {
        Castle castle = documentController.getSelectedCastle();
        return castle != null && isPositioned(castle.balls());
    }

    /**
     * Inserts a new empty castle after the selected castle, or at the end if none is selected.
     */
    public void addCastle() {
        documentController.addCastle();
    }

    /**
     * Deletes the selected castle.
     */
    public void deleteCastle() {
        documentController.removeSelectedCastle();
    }

    /**
     * Adds a cannon at the origin of the selected castle unless its cannon slots are full.
     */
    public void addCannon() {
        castleActions.addCannon();
    }

    /**
     * Adds a throne at the origin of the selected castle.
     */
    public void addThrone() {
        castleActions.addThrone();
    }

    /**
     * Adds a wind vane at the origin of the selected castle.
     */
    public void addWindVane() {
        castleActions.addWindVane();
    }

    /**
     * Adds money storage at the origin with one money-slot of initial capacity.
     */
    public void addMoney() {
        addResourceArea(ResourceArea.MONEY);
    }

    /**
     * Adds powder storage at the origin with one powder-slot of initial capacity.
     */
    public void addPowder() {
        addResourceArea(ResourceArea.POWDER);
    }

    /**
     * Adds cannonball storage at the origin with one cannonball-slot of initial capacity.
     */
    public void addCannonballs() {
        addResourceArea(ResourceArea.BALLS);
    }

    /**
     * Adds the selected resource area when it is not already positioned.
     *
     * @param area resource area to add
     */
    private void addResourceArea(ResourceArea area) {
        castleActions.addResourceArea(area);
    }

    /**
     * Starts interactive creation of a masonry wall polygon.
     */
    public void addWall() {
        castleActions.addWall();
    }

    /**
     * Starts interactive creation of a roof polygon.
     */
    public void addRoof() {
        castleActions.addRoof();
    }

    /**
     * Commits a polygon with at least three vertices; otherwise cancels polygon creation.
     *
     * @param points the ordered polygon vertices
     */
    public void finishPolygonCreation(List<Point> points) {
        castleActions.finishPolygonCreation(points);
    }

    /**
     * Cancels polygon creation and clears its status-bar hint.
     */
    public void cancelPolygonCreation() {
        castleActions.cancelPolygonCreation();
    }

    /**
     * Selects the previous castle when one exists.
     */
    public void showPreviousCastle() {
        documentController.selectIndex(getCastleIndex() - 1);
    }

    /**
     * Selects the next castle when one exists.
     */
    public void showNextCastle() {
        documentController.selectIndex(getCastleIndex() + 1);
    }

    /**
     * Registers the callback invoked when castle selection changes.
     *
     * @param selectionListener the callback notified when selection changes
     */
    public void setSelectionListener(Runnable selectionListener) {
        this.selectionListener = selectionListener;
    }

    /**
     * Registers the callback invoked when document data changes.
     *
     * @param changeListener the callback notified when the document changes
     */
    public void setChangeListener(Runnable changeListener) {
        this.changeListener = changeListener;
    }

    /**
     * Registers callbacks for the start and end of a grouped edit.
     *
     * @param beginListener the callback for the start of a grouped edit
     * @param endListener   the callback for the end of a grouped edit
     */
    public void setHistoryGroupListeners(Runnable beginListener, Runnable endListener) {
        beginHistoryGroupListener = beginListener;
        endHistoryGroupListener = endListener;
    }

    /**
     * Notifies the history owner that a group of related document edits is beginning.
     */
    public void beginHistoryGroup() {
        if (beginHistoryGroupListener != null) {
            beginHistoryGroupListener.run();
        }
    }

    /**
     * Notifies the history owner that the current group of related edits has ended.
     */
    public void endHistoryGroup() {
        if (endHistoryGroupListener != null) {
            endHistoryGroupListener.run();
        }
    }

    /**
     * Registers the callback invoked when zoom changes.
     *
     * @param zoomListener the callback notified when zoom changes
     */
    public void setZoomListener(Runnable zoomListener) {
        this.zoomListener = zoomListener;
    }

    /**
     * Registers the callback used to display status-bar hints.
     *
     * @param statusBarListener the callback used to display status-bar hints
     */
    public void setStatusBarListener(java.util.function.Consumer<String> statusBarListener) {
        this.statusBarListener = statusBarListener;
    }

    /**
     * Returns the zoom factor.
     *
     * @return the current zoom factor
     */
    public double getZoomFactor() {
        return castleView.getZoomFactor();
    }

    /**
     * Sets zoom percent.
     *
     * @param zoomPercent the desired zoom percentage
     */
    public void setZoomPercent(int zoomPercent) {
        castleView.setZoomFactor(zoomPercent / 100.0);
    }

    /**
     * Replaces one vertex in the specified polygon of the selected castle.
     *
     * @param polygonIndex zero-based index among the selected castle's polygons
     * @param pointIndex   zero-based vertex index within that polygon
     * @param newPoint     the replacement point in castle coordinates
     */
    public void updatePolygonPoint(int polygonIndex, int pointIndex, Point newPoint) {
        castleActions.updatePolygonPoint(polygonIndex, pointIndex, newPoint);
    }

    /**
     * Replaces all vertices of the specified polygon in the selected castle.
     *
     * @param polygonIndex zero-based index among the selected castle's polygons
     * @param newPoints    the replacement polygon vertices
     */
    public void updatePolygonPoints(int polygonIndex, List<Point> newPoints) {
        castleActions.updatePolygonPoints(polygonIndex, newPoints);
    }

    /**
     * Updates one cannon's position in the selected castle.
     *
     * @param cannonIndex zero-based index in the castle's cannon list
     * @param newPosition the new position in castle coordinates
     */
    public void updateCannonPosition(int cannonIndex, Point newPosition) {
        castleActions.updateCannonPosition(cannonIndex, newPosition);
    }

    /**
     * Sets the throne's lower-left position in the selected castle.
     *
     * @param newPosition the new position in castle coordinates
     */
    public void updateThronePosition(Point newPosition) {
        castleActions.updateThronePosition(newPosition);
    }

    /**
     * Sets the wind vane's lower-left position in the selected castle.
     *
     * @param newPosition the new position in castle coordinates
     */
    public void updateWindVanePosition(Point newPosition) {
        castleActions.updateWindVanePosition(newPosition);
    }

    /**
     * Sets the lower-left position and dimensions of one storage area.
     *
     * @param resourceArea storage area to resize
     * @param newPosition  the new position in castle coordinates
     * @param newSize      the new resource-area dimensions
     */
    public void updateResourceAreaSize(ResourceArea resourceArea, Point newPosition, Size newSize) {
        castleActions.updateResourceAreaSize(resourceArea, newPosition, newSize);
    }

    /**
     * Moves one storage area without changing its dimensions.
     *
     * @param resourceArea storage area to move
     * @param newPosition  the new position in castle coordinates
     */
    public void updateResourceAreaPosition(ResourceArea resourceArea, Point newPosition) {
        castleActions.updateResourceAreaPosition(resourceArea, newPosition);
    }

    /**
     * Inserts a vertex immediately after the specified edge's starting vertex.
     *
     * @param edgeIndex zero-based index of the vertex where the edge starts
     * @param newPoint  the replacement point in castle coordinates
     */
    public void addPolygonPoint(int edgeIndex, Point newPoint) {
        selectionController.addPolygonPoint(edgeIndex, newPoint);
    }

    /**
     * Removes the selected polygon vertex when the polygon will retain at least three vertices.
     */
    public void removePolygonPoint() {
        selectionController.removePolygonPoint();
    }

    /**
     * Returns whether a vertex of the selected polygon is selected.
     *
     * @return {@code true} if a vertex is selected in a polygon that has more than three vertices
     */
    public boolean hasSelectedPolygonPoint() {
        return selectionController.hasSelectedPolygonPoint();
    }

    /**
     * Returns whether any polygon vertex is selected.
     *
     * @return {@code true} if a polygon vertex index is selected
     */
    public boolean hasSelectedPoint() {
        return selectionController.hasSelectedPoint();
    }

    /**
     * Removes the selected polygon from the current castle drawing.
     */
    public void removePolygon() {
        selectionController.removePolygon();
    }

    /**
     * Returns whether a polygon is selected.
     *
     * @return {@code true} if a polygon is selected in the castle view
     */
    public boolean hasSelectedPolygon() {
        return selectionController.hasSelectedPolygon();
    }

    /**
     * Returns whether a cannon is selected.
     *
     * @return {@code true} if a cannon index is selected
     */
    public boolean hasSelectedCannon() {
        return selectionController.hasSelectedCannon();
    }

    /**
     * Returns whether the throne is selected.
     *
     * @return {@code true} if the throne is selected
     */
    public boolean hasSelectedThrone() {
        return selectionController.hasSelectedThrone();
    }

    /**
     * Returns whether the wind vane is selected.
     *
     * @return {@code true} if the wind vane is selected
     */
    public boolean hasSelectedWindVane() {
        return selectionController.hasSelectedWindVane();
    }

    /**
     * Returns whether a resource-storage area is selected.
     *
     * @return {@code true} if a resource-storage area is selected
     */
    public boolean hasSelectedResourceArea() {
        return selectionController.hasSelectedResourceArea();
    }

    /**
     * Removes the selected cannon from the current castle.
     */
    public void removeCannon() {
        selectionController.removeCannon();
    }

    /**
     * Marks the selected throne position as unset.
     */
    public void removeThrone() {
        selectionController.removeThrone();
    }

    /**
     * Marks the selected wind-vane position as unset.
     */
    public void removeWindVane() {
        selectionController.removeWindVane();
    }

    /**
     * Marks the selected resource-storage position as unset.
     */
    public void removeResourceArea() {
        selectionController.removeResourceArea();
    }

    /**
     * Notifies the editor that point selection changed.
     */
    public void onPointSelectionChanged() {
        selectionController.onPointSelectionChanged();
    }

    /**
     * Notifies selection listeners that the cannon selection changed.
     */
    public void onCannonSelectionChanged() {
        selectionController.onCannonSelectionChanged();
    }

    /**
     * Updates the selected castle.
     */
    private void updateSelectedCastle(Castle castle) {
        castleView.setCastle(castle);
        propertyPalette.setCastle(castle);
        updateProblems(castle);
    }

    /**
     * Refreshes the views affected by an edit to the selected castle.
     *
     * @param edit details controlling how the updated castle is displayed
     */
    private void refreshEditedCastle(EditorDocumentController.CastleEdit edit) {
        if (edit.resetView()) {
            castleView.setCastle(edit.castle());
        } else {
            castleView.updateCastle(edit.castle());
        }
        if (edit.refreshPropertyPalette()) {
            propertyPalette.setCastle(edit.castle());
        }
        updateProblems(edit.castle());
    }

    /**
     * Replaces the selected castle and refreshes its dependent editor views.
     *
     * @param updatedCastle          replacement castle snapshot
     * @param refreshPropertyPalette whether the property palette should reload the castle
     */
    private void replaceSelectedCastle(Castle updatedCastle, boolean refreshPropertyPalette) {
        documentController.replaceSelectedCastle(updatedCastle, refreshPropertyPalette, false, false);
    }

    /**
     * Commits a selected-castle edit that changes selection-dependent editor state.
     *
     * @param updatedCastle replacement castle snapshot
     * @param resetView     whether to reset the view as for a newly selected castle
     */
    // The commit path currently always resets the view; retain the policy parameter for explicit delegation.
    @SuppressWarnings("SameParameterValue")
    private void commitSelectedCastleChange(Castle updatedCastle, boolean resetView) {
        documentController.replaceSelectedCastle(updatedCastle, true, resetView, true);
    }

    /**
     * Returns the selected castle snapshot when the document and selection are valid.
     *
     * @return the selected castle, or {@code null} when no castle is selected
     */
    private Castle getSelectedCastle() {
        return documentController.getSelectedCastle();
    }

    /**
     * Commits a castle edit initiated by the selection controller.
     *
     * @param updatedCastle          edited castle snapshot
     * @param refreshPropertyPalette whether to reload the property palette from the updated castle
     */
    private void commitSelectionEdit(Castle updatedCastle, boolean refreshPropertyPalette) {
        replaceSelectedCastle(updatedCastle, refreshPropertyPalette);
    }

    /**
     * Displays a status-bar message when a status-bar listener is registered.
     *
     * @param text message to display, or an empty string to clear the status
     */
    private void setStatusBarText(String text) {
        if (statusBarListener != null) {
            statusBarListener.accept(text);
        }
    }

    /**
     * Replaces the validation findings shown for the selected castle.
     *
     * @param castle castle whose findings should be displayed
     */
    private void updateProblems(Castle castle) {
        problemsPanel.setProblems(castleValidator.validate(castle));
    }

    /**
     * Notifies listeners about  selection changed.
     */
    private void notifySelectionChanged() {
        if (selectionListener != null) {
            selectionListener.run();
        }
    }

    /**
     * Notifies the registered listener that document contents changed.
     */
    private void notifyDocumentChanged() {
        if (changeListener != null) {
            changeListener.run();
        }
    }

    /**
     * Checks whether a point differs from the model's unset-position sentinel.
     *
     * @param point position to check
     * @return {@code true} if both coordinates are defined
     */
    private boolean isPositioned(Point point) {
        return point.isSet();
    }
}
