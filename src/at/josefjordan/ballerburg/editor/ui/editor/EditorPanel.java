package at.josefjordan.ballerburg.editor.ui.editor;

import at.josefjordan.ballerburg.editor.model.CastleDocument;
import at.josefjordan.ballerburg.editor.model.CastleDocument.Castle;
import at.josefjordan.ballerburg.editor.model.CastleDocument.Point;
import at.josefjordan.ballerburg.editor.model.CastleDocument.Resources;
import at.josefjordan.ballerburg.editor.model.CastleDocument.Size;
import at.josefjordan.ballerburg.editor.model.CastleFileFormat;
import hgb.gui.HGBasePanel;
import hgb.lib.HGBaseText;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
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
     * Creates immutable castle snapshots with edited resource-storage data.
     */
    private final ResourceAreaEditor resourceAreaEditor = new ResourceAreaEditor();
    /**
     * The current castle document.
     */
    private transient CastleDocument document;
    /**
     * The index of the currently selected castle.
     */
    private int castleIndex = -1;
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
        castleView.setEditorPanel(this);
        castleView.setZoomListener(() -> {
            if (zoomListener != null) {
                zoomListener.run();
            }
        });
        castleView.setPolygonSelectionListener(this::onPolygonSelectionChanged);
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
        propertyPalette.setResourcesListener(this::updateResources);
        updateSelectedCastle();
    }

    /**
     * Returns the current document, or null when no document is open.
     *
     * @return the current document, or null when no document is open
     */
    public CastleDocument getDocument() {
        return document;
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
        this.document = document;
        castleIndex = document == null || document.castles().isEmpty() ? -1 : 0;
        updateSelectedCastle();
        notifySelectionChanged();
    }

    /**
     * Returns the castle index.
     *
     * @return the selected castle index, or -1 when none is selected
     */
    public int getCastleIndex() {
        return castleIndex;
    }

    /**
     * Returns the castle count.
     *
     * @return the number of castles in the document
     */
    public int getCastleCount() {
        return document == null ? 0 : document.castles().size();
    }

    /**
     * Returns whether any castle in the current document has validation problems.
     *
     * @return {@code true} when a document is open and at least one castle has validation findings
     */
    public boolean hasProblems() {
        return document != null && document.castles().stream()
                .anyMatch(castle -> !castleValidator.validate(castle).isEmpty());
    }

    /**
     * Returns the cannon count.
     *
     * @return the selected castle cannon count, or zero if no castle is selected
     */
    public int getCannonCount() {
        if (castleIndex < 0 || castleIndex >= getCastleCount()) {
            return 0;
        }
        return document.castles().get(castleIndex).cannons().size();
    }

    /**
     * Returns whether the selected castle has a throne.
     *
     * @return {@code true} if the selected castle has a positioned throne
     */
    public boolean hasThrone() {
        if (castleIndex < 0 || castleIndex >= getCastleCount()) {
            return false;
        }
        Castle castle = document.castles().get(castleIndex);
        Point throne = castle.throne();
        return throne.x() != Integer.MIN_VALUE && throne.y() != Integer.MIN_VALUE;
    }

    /**
     * Returns whether the selected castle has a wind vane.
     *
     * @return {@code true} if the selected castle has a positioned wind vane
     */
    public boolean hasWindVane() {
        if (castleIndex < 0 || castleIndex >= getCastleCount()) {
            return false;
        }
        Castle castle = document.castles().get(castleIndex);
        Point windVane = castle.windVane();
        return windVane.x() != Integer.MIN_VALUE && windVane.y() != Integer.MIN_VALUE;
    }

    /**
     * Returns whether the selected castle has a money-storage area.
     *
     * @return {@code true} if the selected castle has a positioned money-storage area
     */
    public boolean hasMoney() {
        if (castleIndex < 0 || castleIndex >= getCastleCount()) {
            return false;
        }
        Castle castle = document.castles().get(castleIndex);
        Point money = castle.money();
        return money.x() != Integer.MIN_VALUE && money.y() != Integer.MIN_VALUE;
    }

    /**
     * Returns whether the selected castle has a powder-storage area.
     *
     * @return {@code true} if the selected castle has a positioned powder-storage area
     */
    public boolean hasPowder() {
        if (castleIndex < 0 || castleIndex >= getCastleCount()) {
            return false;
        }
        Castle castle = document.castles().get(castleIndex);
        Point powder = castle.powder();
        return powder.x() != Integer.MIN_VALUE && powder.y() != Integer.MIN_VALUE;
    }

    /**
     * Returns whether the selected castle has a cannonball-storage area.
     *
     * @return {@code true} if the selected castle has a positioned cannonball-storage area
     */
    public boolean hasBalls() {
        if (castleIndex < 0 || castleIndex >= getCastleCount()) {
            return false;
        }
        Castle castle = document.castles().get(castleIndex);
        Point balls = castle.balls();
        return balls.x() != Integer.MIN_VALUE && balls.y() != Integer.MIN_VALUE;
    }

    /**
     * Inserts a new empty castle after the selected castle, or at the end if none is selected.
     */
    public void addCastle() {
        if (document == null) {
            return;
        }
        List<Castle> castles = new ArrayList<>(document.castles());
        castleIndex = castleIndex < 0 ? castles.size() : castleIndex + 1;
        castles.add(castleIndex, createEmptyCastle());
        document = new CastleDocument(castles);
        documentChanged();
    }

    /**
     * Deletes the selected castle.
     */
    public void deleteCastle() {
        if (document == null || castleIndex < 0 || castleIndex >= getCastleCount()) {
            return;
        }
        List<Castle> castles = new ArrayList<>(document.castles());
        castles.remove(castleIndex);
        document = new CastleDocument(castles);
        castleIndex = Math.min(castleIndex, castles.size() - 1);
        documentChanged();
    }

    /**
     * Adds a cannon at the origin of the selected castle unless its cannon slots are full.
     */
    public void addCannon() {
        if (document == null || castleIndex < 0 || castleIndex >= getCastleCount()) {
            return;
        }
        Castle oldCastle = document.castles().get(castleIndex);
        if (oldCastle.cannons().size() >= CastleFileFormat.CANNON_POSITION_COUNT) {
            return;
        }

        List<Point> newCannons = new ArrayList<>(oldCastle.cannons());
        newCannons.add(new Point(0, 0));

        Castle updatedCastle = new Castle(oldCastle.width(), newCannons, oldCastle.throne(),
                oldCastle.windVane(), oldCastle.money(), oldCastle.powder(), oldCastle.balls(),
                oldCastle.moneyRoom(), oldCastle.powderRoom(), oldCastle.ballRoom(), oldCastle.resources(),
                oldCastle.reserved(), oldCastle.drawing());
        List<Castle> castles = new ArrayList<>(document.castles());
        castles.set(castleIndex, updatedCastle);
        document = new CastleDocument(castles);
        castleView.updateCastle(updatedCastle);
        documentChanged();
    }

    /**
     * Adds a throne at the origin of the selected castle.
     */
    public void addThrone() {
        if (document == null || castleIndex < 0 || castleIndex >= getCastleCount()) {
            return;
        }
        Castle oldCastle = document.castles().get(castleIndex);
        if (hasThrone()) {
            return;
        }

        Point thronePosition = new Point(0, 0);

        Castle updatedCastle = new Castle(oldCastle.width(), oldCastle.cannons(), thronePosition,
                oldCastle.windVane(), oldCastle.money(), oldCastle.powder(), oldCastle.balls(),
                oldCastle.moneyRoom(), oldCastle.powderRoom(), oldCastle.ballRoom(), oldCastle.resources(),
                oldCastle.reserved(), oldCastle.drawing());
        List<Castle> castles = new ArrayList<>(document.castles());
        castles.set(castleIndex, updatedCastle);
        document = new CastleDocument(castles);
        castleView.updateCastle(updatedCastle);
        documentChanged();
    }

    /**
     * Adds a wind vane at the origin of the selected castle.
     */
    public void addWindVane() {
        if (document == null || castleIndex < 0 || castleIndex >= getCastleCount()) {
            return;
        }
        Castle oldCastle = document.castles().get(castleIndex);
        if (hasWindVane()) {
            return;
        }

        Point windVanePosition = new Point(0, 0);

        Castle updatedCastle = new Castle(oldCastle.width(), oldCastle.cannons(), oldCastle.throne(),
                windVanePosition, oldCastle.money(), oldCastle.powder(), oldCastle.balls(),
                oldCastle.moneyRoom(), oldCastle.powderRoom(), oldCastle.ballRoom(), oldCastle.resources(),
                oldCastle.reserved(), oldCastle.drawing());
        List<Castle> castles = new ArrayList<>(document.castles());
        castles.set(castleIndex, updatedCastle);
        document = new CastleDocument(castles);
        castleView.updateCastle(updatedCastle);
        documentChanged();
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
        if (document == null || castleIndex < 0 || castleIndex >= getCastleCount()) {
            return;
        }
        Castle oldCastle = document.castles().get(castleIndex);
        boolean alreadyPositioned = switch (area) {
            case MONEY -> hasMoney();
            case POWDER -> hasPowder();
            case BALLS -> hasBalls();
        };
        if (alreadyPositioned) {
            return;
        }

        Castle updatedCastle = resourceAreaEditor.add(oldCastle, area);
        List<Castle> castles = new ArrayList<>(document.castles());
        castles.set(castleIndex, updatedCastle);
        document = new CastleDocument(castles);
        castleView.updateCastle(updatedCastle);
        documentChanged();
    }

    /**
     * Starts interactive creation of a masonry wall polygon.
     */
    public void addWall() {
        if (document == null || castleIndex < 0 || castleIndex >= getCastleCount()) {
            return;
        }
        castleView.startPolygonCreation("wall");
        setPolygonCreationHint();
    }

    /**
     * Starts interactive creation of a roof polygon.
     */
    public void addRoof() {
        if (document == null || castleIndex < 0 || castleIndex >= getCastleCount()) {
            return;
        }
        castleView.startPolygonCreation("roof");
        setPolygonCreationHint();
    }

    /**
     * Commits a polygon with at least three vertices; otherwise cancels polygon creation.
     *
     * @param points the ordered polygon vertices
     */
    public void finishPolygonCreation(List<Point> points) {
        if (document == null || castleIndex < 0 || castleIndex >= getCastleCount()) {
            castleView.finishPolygonCreation();
            clearPolygonCreationHint();
            return;
        }
        if (points.size() < 3) {
            castleView.finishPolygonCreation();
            clearPolygonCreationHint();
            return;
        }

        Castle updatedCastle = createCastleWithPolygon(points);
        List<Castle> castles = new ArrayList<>(document.castles());
        castles.set(castleIndex, updatedCastle);
        document = new CastleDocument(castles);
        castleView.updateCastle(updatedCastle);
        castleView.finishPolygonCreation();
        clearPolygonCreationHint();
        documentChanged();
        notifySelectionChanged();
    }

    /**
     * Returns a castle copy with the new polygon and its fill-style command appended.
     *
     * @param points the ordered polygon vertices
     * @return castle copy containing the appended polygon drawing commands
     */
    private Castle createCastleWithPolygon(List<Point> points) {
        Castle oldCastle = document.castles().get(castleIndex);
        List<CastleDocument.DrawingCommand> newDrawing = new ArrayList<>(oldCastle.drawing());

        String polygonType = castleView.getPolygonType();
        int fillIndex = "wall".equals(polygonType) ? CastleFileFormat.FILL_INDEX_MASONRY : CastleFileFormat.FILL_INDEX_ROOF;

        // Add FillStyle command before the polygon
        newDrawing.add(new CastleDocument.FillStyle(CastleFileFormat.FILL_STYLE_PATTERN, fillIndex));
        newDrawing.add(new CastleDocument.Polygon(points));

        return new Castle(oldCastle.width(), oldCastle.cannons(), oldCastle.throne(),
                oldCastle.windVane(), oldCastle.money(), oldCastle.powder(), oldCastle.balls(),
                oldCastle.moneyRoom(), oldCastle.powderRoom(), oldCastle.ballRoom(), oldCastle.resources(),
                oldCastle.reserved(), newDrawing);
    }

    /**
     * Cancels polygon creation and clears its status-bar hint.
     */
    public void cancelPolygonCreation() {
        castleView.cancelPolygonCreation();
        clearPolygonCreationHint();
    }

    /**
     * Sets the polygon creation hint.
     */
    private void setPolygonCreationHint() {
        if (statusBarListener != null) {
            statusBarListener.accept(HGBaseText.getText("polygon.hint"));
        }
    }

    /**
     * Clears the polygon-creation status-bar hint.
     */
    private void clearPolygonCreationHint() {
        if (statusBarListener != null) {
            statusBarListener.accept("");
        }
    }

    /**
     * Selects the previous castle when one exists.
     */
    public void showPreviousCastle() {
        setCastleIndex(castleIndex - 1);
    }

    /**
     * Selects the next castle when one exists.
     */
    public void showNextCastle() {
        setCastleIndex(castleIndex + 1);
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
        if (castleIndex < 0 || castleIndex >= getCastleCount()) {
            return;
        }
        Castle oldCastle = document.castles().get(castleIndex);
        List<CastleDocument.DrawingCommand> oldDrawing = oldCastle.drawing();

        int currentPolygonIndex = 0;
        List<CastleDocument.DrawingCommand> newDrawing = new ArrayList<>();
        for (CastleDocument.DrawingCommand command : oldDrawing) {
            if (command instanceof CastleDocument.Polygon polygon) {
                if (currentPolygonIndex == polygonIndex) {
                    List<Point> newPoints = new ArrayList<>(polygon.points());
                    newPoints.set(pointIndex, newPoint);
                    newDrawing.add(new CastleDocument.Polygon(newPoints));
                } else {
                    newDrawing.add(polygon);
                }
                currentPolygonIndex++;
            } else {
                newDrawing.add(command);
            }
        }

        Castle updatedCastle = new Castle(oldCastle.width(), oldCastle.cannons(), oldCastle.throne(),
                oldCastle.windVane(), oldCastle.money(), oldCastle.powder(), oldCastle.balls(),
                oldCastle.moneyRoom(), oldCastle.powderRoom(), oldCastle.ballRoom(), oldCastle.resources(),
                oldCastle.reserved(), newDrawing);
        List<Castle> castles = new ArrayList<>(document.castles());
        castles.set(castleIndex, updatedCastle);
        document = new CastleDocument(castles);
        castleView.updateCastle(updatedCastle);
        updateProblems(updatedCastle);
        if (changeListener != null) {
            changeListener.run();
        }
    }

    /**
     * Replaces all vertices of the specified polygon in the selected castle.
     *
     * @param polygonIndex zero-based index among the selected castle's polygons
     * @param newPoints    the replacement polygon vertices
     */
    public void updatePolygonPoints(int polygonIndex, List<Point> newPoints) {
        if (castleIndex < 0 || castleIndex >= getCastleCount()) {
            return;
        }
        Castle oldCastle = document.castles().get(castleIndex);
        List<CastleDocument.DrawingCommand> oldDrawing = oldCastle.drawing();

        int currentPolygonIndex = 0;
        List<CastleDocument.DrawingCommand> newDrawing = new ArrayList<>();
        for (CastleDocument.DrawingCommand command : oldDrawing) {
            if (command instanceof CastleDocument.Polygon polygon) {
                if (currentPolygonIndex == polygonIndex) {
                    newDrawing.add(new CastleDocument.Polygon(newPoints));
                } else {
                    newDrawing.add(polygon);
                }
                currentPolygonIndex++;
            } else {
                newDrawing.add(command);
            }
        }

        Castle updatedCastle = new Castle(oldCastle.width(), oldCastle.cannons(), oldCastle.throne(),
                oldCastle.windVane(), oldCastle.money(), oldCastle.powder(), oldCastle.balls(),
                oldCastle.moneyRoom(), oldCastle.powderRoom(), oldCastle.ballRoom(), oldCastle.resources(),
                oldCastle.reserved(), newDrawing);
        List<Castle> castles = new ArrayList<>(document.castles());
        castles.set(castleIndex, updatedCastle);
        document = new CastleDocument(castles);
        castleView.updateCastle(updatedCastle);
        updateProblems(updatedCastle);
        if (changeListener != null) {
            changeListener.run();
        }
    }

    /**
     * Updates one cannon's position in the selected castle.
     *
     * @param cannonIndex zero-based index in the castle's cannon list
     * @param newPosition the new position in castle coordinates
     */
    public void updateCannonPosition(int cannonIndex, Point newPosition) {
        if (castleIndex < 0 || castleIndex >= getCastleCount()) {
            return;
        }
        Castle oldCastle = document.castles().get(castleIndex);
        if (cannonIndex < 0 || cannonIndex >= oldCastle.cannons().size()) {
            return;
        }

        List<Point> newCannons = new ArrayList<>(oldCastle.cannons());
        newCannons.set(cannonIndex, newPosition);

        Castle updatedCastle = new Castle(oldCastle.width(), newCannons, oldCastle.throne(),
                oldCastle.windVane(), oldCastle.money(), oldCastle.powder(), oldCastle.balls(),
                oldCastle.moneyRoom(), oldCastle.powderRoom(), oldCastle.ballRoom(), oldCastle.resources(),
                oldCastle.reserved(), oldCastle.drawing());
        List<Castle> castles = new ArrayList<>(document.castles());
        castles.set(castleIndex, updatedCastle);
        document = new CastleDocument(castles);
        castleView.updateCastle(updatedCastle);
        updateProblems(updatedCastle);
        if (changeListener != null) {
            changeListener.run();
        }
    }

    /**
     * Sets the throne's lower-left position in the selected castle.
     *
     * @param newPosition the new position in castle coordinates
     */
    public void updateThronePosition(Point newPosition) {
        if (castleIndex < 0 || castleIndex >= getCastleCount()) {
            return;
        }
        Castle oldCastle = document.castles().get(castleIndex);

        Castle updatedCastle = new Castle(oldCastle.width(), oldCastle.cannons(), newPosition,
                oldCastle.windVane(), oldCastle.money(), oldCastle.powder(), oldCastle.balls(),
                oldCastle.moneyRoom(), oldCastle.powderRoom(), oldCastle.ballRoom(), oldCastle.resources(),
                oldCastle.reserved(), oldCastle.drawing());
        List<Castle> castles = new ArrayList<>(document.castles());
        castles.set(castleIndex, updatedCastle);
        document = new CastleDocument(castles);
        castleView.updateCastle(updatedCastle);
        updateProblems(updatedCastle);
        if (changeListener != null) {
            changeListener.run();
        }
    }

    /**
     * Sets the wind vane's lower-left position in the selected castle.
     *
     * @param newPosition the new position in castle coordinates
     */
    public void updateWindVanePosition(Point newPosition) {
        if (castleIndex < 0 || castleIndex >= getCastleCount()) {
            return;
        }
        Castle oldCastle = document.castles().get(castleIndex);

        Castle updatedCastle = new Castle(oldCastle.width(), oldCastle.cannons(), oldCastle.throne(),
                newPosition, oldCastle.money(), oldCastle.powder(), oldCastle.balls(),
                oldCastle.moneyRoom(), oldCastle.powderRoom(), oldCastle.ballRoom(), oldCastle.resources(),
                oldCastle.reserved(), oldCastle.drawing());
        List<Castle> castles = new ArrayList<>(document.castles());
        castles.set(castleIndex, updatedCastle);
        document = new CastleDocument(castles);
        castleView.updateCastle(updatedCastle);
        updateProblems(updatedCastle);
        if (changeListener != null) {
            changeListener.run();
        }
    }

    /**
     * Sets the lower-left position and dimensions of one storage area.
     *
     * @param resourceArea storage area to resize
     * @param newPosition  the new position in castle coordinates
     * @param newSize      the new resource-area dimensions
     */
    public void updateResourceAreaSize(ResourceArea resourceArea, Point newPosition, Size newSize) {
        if (castleIndex < 0 || castleIndex >= getCastleCount()) {
            return;
        }
        Castle oldCastle = document.castles().get(castleIndex);

        Castle updatedCastle = resourceAreaEditor.resize(oldCastle, resourceArea, newPosition, newSize);

        List<Castle> castles = new ArrayList<>(document.castles());
        castles.set(castleIndex, updatedCastle);
        document = new CastleDocument(castles);
        castleView.updateCastle(updatedCastle);
        propertyPalette.setCastle(updatedCastle);
        updateProblems(updatedCastle);
        if (changeListener != null) {
            changeListener.run();
        }
    }

    /**
     * Moves one storage area without changing its dimensions.
     *
     * @param resourceArea storage area to move
     * @param newPosition  the new position in castle coordinates
     */
    public void updateResourceAreaPosition(ResourceArea resourceArea, Point newPosition) {
        if (castleIndex < 0 || castleIndex >= getCastleCount()) {
            return;
        }
        Castle oldCastle = document.castles().get(castleIndex);

        Castle updatedCastle = resourceAreaEditor.move(oldCastle, resourceArea, newPosition);

        List<Castle> castles = new ArrayList<>(document.castles());
        castles.set(castleIndex, updatedCastle);
        document = new CastleDocument(castles);
        castleView.updateCastle(updatedCastle);
        updateProblems(updatedCastle);
        if (changeListener != null) {
            changeListener.run();
        }
    }

    /**
     * Inserts a vertex immediately after the specified edge's starting vertex.
     *
     * @param edgeIndex zero-based index of the vertex where the edge starts
     * @param newPoint  the replacement point in castle coordinates
     */
    public void addPolygonPoint(int edgeIndex, Point newPoint) {
        CastleDocument.Polygon selectedPolygon = castleView.getSelectedPolygon();
        if (selectedPolygon == null) {
            return;
        }

        int polygonIndex = findPolygonIndex(selectedPolygon);
        if (polygonIndex < 0) {
            return;
        }

        List<Point> newPoints = new ArrayList<>(selectedPolygon.points());
        // Insert the new point after the edge index (edgeIndex is the index of the point where the edge starts)
        newPoints.add(edgeIndex + 1, newPoint);
        updatePolygonPoints(polygonIndex, newPoints);

        // Update the selected polygon reference to the new polygon
        Castle updatedCastle = document.castles().get(castleIndex);
        int currentPolygonIndex = 0;
        for (CastleDocument.DrawingCommand command : updatedCastle.drawing()) {
            if (command instanceof CastleDocument.Polygon polygon) {
                if (currentPolygonIndex == polygonIndex) {
                    castleView.setSelectedPolygon(polygon);
                    break;
                }
                currentPolygonIndex++;
            }
        }

        // Select the newly added point
        castleView.setSelectedPointIndex(edgeIndex + 1);
        onPointSelectionChanged();
    }

    /**
     * Removes the selected polygon vertex when the polygon will retain at least three vertices.
     */
    public void removePolygonPoint() {
        CastleDocument.Polygon selectedPolygon = castleView.getSelectedPolygon();
        if (selectedPolygon == null || selectedPolygon.points().size() <= 3) {
            return;
        }

        int selectedPointIndex = castleView.getSelectedPointIndex();
        if (selectedPointIndex < 0) {
            return;
        }

        int polygonIndex = findPolygonIndex(selectedPolygon);
        if (polygonIndex < 0) {
            return;
        }

        List<Point> newPoints = new ArrayList<>(selectedPolygon.points());
        newPoints.remove(selectedPointIndex);
        updatePolygonPoints(polygonIndex, newPoints);

        // Update the selected polygon reference to the new polygon
        Castle updatedCastle = document.castles().get(castleIndex);
        int currentPolygonIndex = 0;
        for (CastleDocument.DrawingCommand command : updatedCastle.drawing()) {
            if (command instanceof CastleDocument.Polygon polygon) {
                if (currentPolygonIndex == polygonIndex) {
                    castleView.setSelectedPolygon(polygon);
                    break;
                }
                currentPolygonIndex++;
            }
        }

        // Clear the selected point after removal
        castleView.setSelectedPointIndex(-1);
        onPointSelectionChanged();
    }

    /**
     * Finds a polygon's index among the selected castle's polygon drawing commands.
     *
     * @param targetPolygon polygon instance to locate
     * @return zero-based polygon index, or {@code -1} if it is absent
     */
    private int findPolygonIndex(CastleDocument.Polygon targetPolygon) {
        if (castleIndex < 0 || castleIndex >= getCastleCount()) {
            return -1;
        }
        Castle castle = document.castles().get(castleIndex);
        int index = 0;
        for (CastleDocument.DrawingCommand command : castle.drawing()) {
            if (command instanceof CastleDocument.Polygon polygon) {
                if (polygon == targetPolygon) {
                    return index;
                }
                index++;
            }
        }
        return -1;
    }

    /**
     * Returns whether a vertex of the selected polygon is selected.
     *
     * @return {@code true} if a vertex is selected in a polygon that has more than three vertices
     */
    public boolean hasSelectedPolygonPoint() {
        if (castleView.getSelectedPointIndex() < 0) {
            return false;
        }
        CastleDocument.Polygon selectedPolygon = castleView.getSelectedPolygon();
        return selectedPolygon != null && selectedPolygon.points().size() > 3;
    }

    /**
     * Returns whether any polygon vertex is selected.
     *
     * @return {@code true} if a polygon vertex index is selected
     */
    public boolean hasSelectedPoint() {
        return castleView.getSelectedPointIndex() >= 0;
    }

    /**
     * Removes the selected polygon from the current castle drawing.
     */
    public void removePolygon() {
        CastleDocument.Polygon selectedPolygon = castleView.getSelectedPolygon();
        if (selectedPolygon == null) {
            return;
        }

        int polygonIndex = findPolygonIndex(selectedPolygon);
        if (polygonIndex < 0) {
            return;
        }

        Castle oldCastle = document.castles().get(castleIndex);
        List<CastleDocument.DrawingCommand> newDrawing = new ArrayList<>();
        for (CastleDocument.DrawingCommand command : oldCastle.drawing()) {
            if (!(command instanceof CastleDocument.Polygon polygon) || polygon != selectedPolygon) {
                newDrawing.add(command);
            }
        }

        Castle updatedCastle = new Castle(oldCastle.width(), oldCastle.cannons(), oldCastle.throne(),
                oldCastle.windVane(), oldCastle.money(), oldCastle.powder(), oldCastle.balls(),
                oldCastle.moneyRoom(), oldCastle.powderRoom(), oldCastle.ballRoom(), oldCastle.resources(),
                oldCastle.reserved(), newDrawing);
        List<Castle> castles = new ArrayList<>(document.castles());
        castles.set(castleIndex, updatedCastle);
        document = new CastleDocument(castles);
        castleView.updateCastle(updatedCastle);
        castleView.setSelectedPolygon(null);
        updateProblems(updatedCastle);
        if (changeListener != null) {
            changeListener.run();
        }
        notifySelectionChanged();
    }

    /**
     * Returns whether a polygon is selected.
     *
     * @return {@code true} if a polygon is selected in the castle view
     */
    public boolean hasSelectedPolygon() {
        return castleView.getSelectedPolygon() != null;
    }

    /**
     * Returns whether a cannon is selected.
     *
     * @return {@code true} if a cannon index is selected
     */
    public boolean hasSelectedCannon() {
        return castleView.getSelectedCannonIndex() >= 0;
    }

    /**
     * Returns whether the throne is selected.
     *
     * @return {@code true} if the throne is selected
     */
    public boolean hasSelectedThrone() {
        return castleView.isSelectedThrone();
    }

    /**
     * Returns whether the wind vane is selected.
     *
     * @return {@code true} if the wind vane is selected
     */
    public boolean hasSelectedWindVane() {
        return castleView.isSelectedWindVane();
    }

    /**
     * Returns whether a resource-storage area is selected.
     *
     * @return {@code true} if a resource-storage area is selected
     */
    public boolean hasSelectedResourceArea() {
        return castleView.getSelectedResourceArea() != null;
    }

    /**
     * Removes the selected cannon from the current castle.
     */
    public void removeCannon() {
        int selectedCannonIndex = castleView.getSelectedCannonIndex();
        if (selectedCannonIndex < 0) {
            return;
        }

        if (castleIndex < 0 || castleIndex >= getCastleCount()) {
            return;
        }

        Castle oldCastle = document.castles().get(castleIndex);
        if (selectedCannonIndex >= oldCastle.cannons().size()) {
            return;
        }

        List<Point> newCannons = new ArrayList<>(oldCastle.cannons());
        newCannons.remove(selectedCannonIndex);

        Castle updatedCastle = new Castle(oldCastle.width(), newCannons, oldCastle.throne(),
                oldCastle.windVane(), oldCastle.money(), oldCastle.powder(), oldCastle.balls(),
                oldCastle.moneyRoom(), oldCastle.powderRoom(), oldCastle.ballRoom(), oldCastle.resources(),
                oldCastle.reserved(), oldCastle.drawing());
        List<Castle> castles = new ArrayList<>(document.castles());
        castles.set(castleIndex, updatedCastle);
        document = new CastleDocument(castles);
        castleView.updateCastle(updatedCastle);
        castleView.setSelectedCannonIndex(-1);
        updateProblems(updatedCastle);
        if (changeListener != null) {
            changeListener.run();
        }
        notifySelectionChanged();
    }

    /**
     * Marks the selected throne position as unset.
     */
    public void removeThrone() {
        if (!castleView.isSelectedThrone()) {
            return;
        }

        if (castleIndex < 0 || castleIndex >= getCastleCount()) {
            return;
        }

        Castle oldCastle = document.castles().get(castleIndex);
        Point unusedPosition = new Point(Integer.MIN_VALUE, Integer.MIN_VALUE);

        Castle updatedCastle = new Castle(oldCastle.width(), oldCastle.cannons(), unusedPosition,
                oldCastle.windVane(), oldCastle.money(), oldCastle.powder(), oldCastle.balls(),
                oldCastle.moneyRoom(), oldCastle.powderRoom(), oldCastle.ballRoom(), oldCastle.resources(),
                oldCastle.reserved(), oldCastle.drawing());
        List<Castle> castles = new ArrayList<>(document.castles());
        castles.set(castleIndex, updatedCastle);
        document = new CastleDocument(castles);
        castleView.updateCastle(updatedCastle);
        castleView.setSelectedThrone(false);
        updateProblems(updatedCastle);
        if (changeListener != null) {
            changeListener.run();
        }
        notifySelectionChanged();
    }

    /**
     * Marks the selected wind-vane position as unset.
     */
    public void removeWindVane() {
        if (!castleView.isSelectedWindVane()) {
            return;
        }

        if (castleIndex < 0 || castleIndex >= getCastleCount()) {
            return;
        }

        Castle oldCastle = document.castles().get(castleIndex);
        Point unusedPosition = new Point(Integer.MIN_VALUE, Integer.MIN_VALUE);

        Castle updatedCastle = new Castle(oldCastle.width(), oldCastle.cannons(), oldCastle.throne(),
                unusedPosition, oldCastle.money(), oldCastle.powder(), oldCastle.balls(),
                oldCastle.moneyRoom(), oldCastle.powderRoom(), oldCastle.ballRoom(), oldCastle.resources(),
                oldCastle.reserved(), oldCastle.drawing());
        List<Castle> castles = new ArrayList<>(document.castles());
        castles.set(castleIndex, updatedCastle);
        document = new CastleDocument(castles);
        castleView.updateCastle(updatedCastle);
        castleView.setSelectedWindVane(false);
        updateProblems(updatedCastle);
        if (changeListener != null) {
            changeListener.run();
        }
        notifySelectionChanged();
    }

    /**
     * Marks the selected resource-storage position as unset.
     */
    public void removeResourceArea() {
        ResourceArea selectedResourceArea = castleView.getSelectedResourceArea();
        if (selectedResourceArea == null) {
            return;
        }

        if (castleIndex < 0 || castleIndex >= getCastleCount()) {
            return;
        }

        Castle oldCastle = document.castles().get(castleIndex);
        Castle updatedCastle = resourceAreaEditor.remove(oldCastle, selectedResourceArea);

        List<Castle> castles = new ArrayList<>(document.castles());
        castles.set(castleIndex, updatedCastle);
        document = new CastleDocument(castles);
        castleView.updateCastle(updatedCastle);
        propertyPalette.setCastle(updatedCastle);
        castleView.setSelectedResourceArea(null);
        updateProblems(updatedCastle);
        if (changeListener != null) {
            changeListener.run();
        }
        notifySelectionChanged();
    }

    /**
     * Notifies the editor that point selection changed.
     */
    public void onPointSelectionChanged() {
        if (castleView.getSelectedPolygon() != null) {
            setPolygonSelectedHint();
        } else {
            clearPolygonSelectedHint();
        }
        notifySelectionChanged();
    }

    /**
     * Sets the polygon selected hint.
     */
    private void setPolygonSelectedHint() {
        if (statusBarListener != null) {
            statusBarListener.accept(HGBaseText.getText("polygon.selected_hint"));
        }
    }

    /**
     * Clears the polygon-selection hint when creation is not in progress.
     */
    private void clearPolygonSelectedHint() {
        if (statusBarListener != null && !castleView.isInPolygonCreationMode()) {
            statusBarListener.accept("");
        }
    }

    /**
     * Notifies selection listeners that the cannon selection changed.
     */
    public void onCannonSelectionChanged() {
        notifySelectionChanged();
    }

    /**
     * Updates the status-bar hint to match the polygon selection.
     */
    private void onPolygonSelectionChanged() {
        if (castleView.getSelectedPolygon() != null) {
            setPolygonSelectedHint();
        } else {
            clearPolygonSelectedHint();
        }
    }

    /**
     * Selects the requested castle index when it is in range and differs from the current index.
     *
     * @param index the fill index value
     */
    private void setCastleIndex(int index) {
        if (index >= 0 && index < getCastleCount() && index != castleIndex) {
            castleIndex = index;
            updateSelectedCastle();
            notifySelectionChanged();
        }
    }

    /**
     * Updates the selected castle.
     */
    private void updateSelectedCastle() {
        Castle castle = castleIndex >= 0 && castleIndex < getCastleCount()
                ? document.castles().get(castleIndex) : null;
        castleView.setCastle(castle);
        propertyPalette.setCastle(castle);
        updateProblems(castle);
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
     * Updates the selected castle with values from the property palette.
     *
     * @param resources edited starting quantities received from the property palette
     */
    private void updateResources(Resources resources) {
        if (castleIndex < 0 || castleIndex >= getCastleCount()) {
            return;
        }
        Castle oldCastle = document.castles().get(castleIndex);
        if (oldCastle.resources().equals(resources)) {
            return;
        }
        Castle updatedCastle = new Castle(oldCastle.width(), oldCastle.cannons(), oldCastle.throne(),
                oldCastle.windVane(), oldCastle.money(), oldCastle.powder(), oldCastle.balls(),
                oldCastle.moneyRoom(), oldCastle.powderRoom(), oldCastle.ballRoom(), resources,
                oldCastle.reserved(), oldCastle.drawing());
        List<Castle> castles = new ArrayList<>(document.castles());
        castles.set(castleIndex, updatedCastle);
        document = new CastleDocument(castles);
        castleView.updateCastle(updatedCastle);
        updateProblems(updatedCastle);
        if (changeListener != null) {
            changeListener.run();
        }
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
     * Notifies the registered listener that the document changed and refreshes validation problems.
     */
    private void documentChanged() {
        updateSelectedCastle();
        notifySelectionChanged();
        if (changeListener != null) {
            changeListener.run();
        }
    }

    /**
     * Creates a castle with default dimensions, empty geometry, and unset objects.
     *
     * @return the created empty castle
     */
    private Castle createEmptyCastle() {
        Point absent = new Point(Integer.MIN_VALUE, Integer.MIN_VALUE);
        Size emptySize = new Size(0, 0);
        return new Castle(0, List.of(), absent, absent, absent, absent, absent,
                emptySize, emptySize, emptySize, new Resources(0, 0, 0, 0),
                List.of(0, 0, 0, 0), List.of());
    }
}
