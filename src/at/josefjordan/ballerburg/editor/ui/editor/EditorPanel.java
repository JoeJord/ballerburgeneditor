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
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Editor containing the castle view, property palette, and problems panel.
 */
public class EditorPanel extends HGBasePanel {
    /**
     * Largest permitted horizontal span of positioned castle elements, in castle coordinates.
     */
    private static final int MAX_CASTLE_ELEMENT_WIDTH = 200;
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
                .anyMatch(castle -> !createProblems(castle).isEmpty());
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
     * Returns whether the drawing contains at least one polygon under the masonry fill style.
     *
     * @param castle castle whose drawing commands are examined
     * @return {@code true} if a polygon is drawn with the masonry fill style
     */
    private boolean hasWall(Castle castle) {
        // Start fill style is 2,9 (masonry) according to BALLER.DAT
        int fillStyle = CastleFileFormat.FILL_STYLE_PATTERN;
        int fillIndex = CastleFileFormat.FILL_INDEX_MASONRY;
        for (CastleDocument.DrawingCommand command : castle.drawing()) {
            if (command instanceof CastleDocument.FillStyle(int style, int index)) {
                fillStyle = style;
                fillIndex = index;
            } else if (command instanceof CastleDocument.Polygon && fillStyle == CastleFileFormat.FILL_STYLE_PATTERN && fillIndex == CastleFileFormat.FILL_INDEX_MASONRY) {
                return true;
            }

        }
        return false;
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
        if (document == null || castleIndex < 0 || castleIndex >= getCastleCount()) {
            return;
        }
        Castle oldCastle = document.castles().get(castleIndex);
        if (hasMoney()) {
            return;
        }

        Point moneyPosition = new Point(0, 0);
        Size moneyRoomSize = new Size(CastleFileFormat.MONEY_WIDTH, CastleFileFormat.MONEY_HEIGHT);

        Castle updatedCastle = new Castle(oldCastle.width(), oldCastle.cannons(), oldCastle.throne(),
                oldCastle.windVane(), moneyPosition, oldCastle.powder(), oldCastle.balls(),
                moneyRoomSize, oldCastle.powderRoom(), oldCastle.ballRoom(), oldCastle.resources(),
                oldCastle.reserved(), oldCastle.drawing());
        List<Castle> castles = new ArrayList<>(document.castles());
        castles.set(castleIndex, updatedCastle);
        document = new CastleDocument(castles);
        castleView.updateCastle(updatedCastle);
        documentChanged();
    }

    /**
     * Adds powder storage at the origin with one powder-slot of initial capacity.
     */
    public void addPowder() {
        if (document == null || castleIndex < 0 || castleIndex >= getCastleCount()) {
            return;
        }
        Castle oldCastle = document.castles().get(castleIndex);
        if (hasPowder()) {
            return;
        }

        Point powderPosition = new Point(0, 0);
        Size powderRoomSize = new Size(CastleFileFormat.POWDER_WIDTH, CastleFileFormat.POWDER_HEIGHT);

        Castle updatedCastle = new Castle(oldCastle.width(), oldCastle.cannons(), oldCastle.throne(),
                oldCastle.windVane(), oldCastle.money(), powderPosition, oldCastle.balls(),
                oldCastle.moneyRoom(), powderRoomSize, oldCastle.ballRoom(), oldCastle.resources(),
                oldCastle.reserved(), oldCastle.drawing());
        List<Castle> castles = new ArrayList<>(document.castles());
        castles.set(castleIndex, updatedCastle);
        document = new CastleDocument(castles);
        castleView.updateCastle(updatedCastle);
        documentChanged();
    }

    /**
     * Adds cannonball storage at the origin with one cannonball-slot of initial capacity.
     */
    public void addCannonballs() {
        if (document == null || castleIndex < 0 || castleIndex >= getCastleCount()) {
            return;
        }
        Castle oldCastle = document.castles().get(castleIndex);
        if (hasBalls()) {
            return;
        }

        Castle updatedCastle = addCannonballsToCastle(oldCastle);
        List<Castle> castles = new ArrayList<>(document.castles());
        castles.set(castleIndex, updatedCastle);
        document = new CastleDocument(castles);
        castleView.updateCastle(updatedCastle);
        documentChanged();
    }

    /**
     * Returns a copy of the castle with cannonball storage initialized.
     *
     * @param castle castle to copy and initialize
     * @return a copy of the castle with cannonball storage initialized
     */
    private Castle addCannonballsToCastle(Castle castle) {
        Point ballsPosition = new Point(0, 0);
        Size ballRoomSize = new Size(CastleFileFormat.CANNONBALL_WIDTH, CastleFileFormat.CANNONBALL_HEIGHT);
        return new Castle(castle.width(), castle.cannons(), castle.throne(),
                castle.windVane(), castle.money(), castle.powder(), ballsPosition,
                castle.moneyRoom(), castle.powderRoom(), ballRoomSize, castle.resources(),
                castle.reserved(), castle.drawing());
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

        Castle updatedCastle;
        switch (resourceArea) {
            case MONEY:
                updatedCastle = new Castle(oldCastle.width(), oldCastle.cannons(), oldCastle.throne(),
                        oldCastle.windVane(), newPosition, oldCastle.powder(), oldCastle.balls(),
                        newSize, oldCastle.powderRoom(), oldCastle.ballRoom(), oldCastle.resources(),
                        oldCastle.reserved(), oldCastle.drawing());
                break;
            case POWDER:
                updatedCastle = new Castle(oldCastle.width(), oldCastle.cannons(), oldCastle.throne(),
                        oldCastle.windVane(), oldCastle.money(), newPosition, oldCastle.balls(),
                        oldCastle.moneyRoom(), newSize, oldCastle.ballRoom(), oldCastle.resources(),
                        oldCastle.reserved(), oldCastle.drawing());
                break;
            case BALLS:
                updatedCastle = new Castle(oldCastle.width(), oldCastle.cannons(), oldCastle.throne(),
                        oldCastle.windVane(), oldCastle.money(), oldCastle.powder(), newPosition,
                        oldCastle.moneyRoom(), oldCastle.powderRoom(), newSize, oldCastle.resources(),
                        oldCastle.reserved(), oldCastle.drawing());
                break;
            default:
                return;
        }

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

        Castle updatedCastle;
        switch (resourceArea) {
            case MONEY:
                updatedCastle = new Castle(oldCastle.width(), oldCastle.cannons(), oldCastle.throne(),
                        oldCastle.windVane(), newPosition, oldCastle.powder(), oldCastle.balls(),
                        oldCastle.moneyRoom(), oldCastle.powderRoom(), oldCastle.ballRoom(), oldCastle.resources(),
                        oldCastle.reserved(), oldCastle.drawing());
                break;
            case POWDER:
                updatedCastle = new Castle(oldCastle.width(), oldCastle.cannons(), oldCastle.throne(),
                        oldCastle.windVane(), oldCastle.money(), newPosition, oldCastle.balls(),
                        oldCastle.moneyRoom(), oldCastle.powderRoom(), oldCastle.ballRoom(), oldCastle.resources(),
                        oldCastle.reserved(), oldCastle.drawing());
                break;
            case BALLS:
                updatedCastle = new Castle(oldCastle.width(), oldCastle.cannons(), oldCastle.throne(),
                        oldCastle.windVane(), oldCastle.money(), oldCastle.powder(), newPosition,
                        oldCastle.moneyRoom(), oldCastle.powderRoom(), oldCastle.ballRoom(), oldCastle.resources(),
                        oldCastle.reserved(), oldCastle.drawing());
                break;
            default:
                return;
        }

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
        Point unusedPosition = new Point(Integer.MIN_VALUE, Integer.MIN_VALUE);
        Size emptySize = new Size(0, 0);
        Resources oldResources = oldCastle.resources();

        Castle updatedCastle;
        if (selectedResourceArea == ResourceArea.MONEY) {
            Resources newResources = new Resources(0, oldResources.powder(), oldResources.balls(), oldResources.people());
            updatedCastle = new Castle(oldCastle.width(), oldCastle.cannons(), oldCastle.throne(),
                    oldCastle.windVane(), unusedPosition, oldCastle.powder(), oldCastle.balls(),
                    emptySize, oldCastle.powderRoom(), oldCastle.ballRoom(), newResources,
                    oldCastle.reserved(), oldCastle.drawing());
        } else if (selectedResourceArea == ResourceArea.POWDER) {
            Resources newResources = new Resources(oldResources.money(), 0, oldResources.balls(), oldResources.people());
            updatedCastle = new Castle(oldCastle.width(), oldCastle.cannons(), oldCastle.throne(),
                    oldCastle.windVane(), oldCastle.money(), unusedPosition, oldCastle.balls(),
                    oldCastle.moneyRoom(), emptySize, oldCastle.ballRoom(), newResources,
                    oldCastle.reserved(), oldCastle.drawing());
        } else if (selectedResourceArea == ResourceArea.BALLS) {
            Resources newResources = new Resources(oldResources.money(), oldResources.powder(), 0, oldResources.people());
            updatedCastle = new Castle(oldCastle.width(), oldCastle.cannons(), oldCastle.throne(),
                    oldCastle.windVane(), oldCastle.money(), oldCastle.powder(), unusedPosition,
                    oldCastle.moneyRoom(), oldCastle.powderRoom(), emptySize, newResources,
                    oldCastle.reserved(), oldCastle.drawing());
        } else {
            return;
        }

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
        problemsPanel.setProblems(createProblems(castle));
    }

    /**
     * Builds the validation problems for the supplied castle.
     *
     * @param castle castle to check for missing elements, overlaps, and other validation findings
     * @return the validation problems found in the supplied castle
     */
    private List<ProblemsPanel.Problem> createProblems(Castle castle) {
        List<ProblemsPanel.Problem> problems = new ArrayList<>();
        if (castle != null) {
            boolean wallPresent = hasWall(castle);
            addMissingProblem(problems, hasThrone(castle), "problems.missing_throne");
            addMissingProblem(problems, hasMoney(castle), "problems.missing_money");
            addMissingProblem(problems, hasBalls(castle), "problems.missing_balls");
            addMissingProblem(problems, hasPowder(castle), "problems.missing_powder");
            addMissingProblem(problems, hasWindVane(castle), "problems.missing_windvane");
            addMissingProblem(problems, wallPresent, "problems.missing_wall");
            addMissingProblem(problems, hasElementWidth(castle), "problems.elements_too_wide");
            if (wallPresent) {
                addMissingProblem(problems, isInsideWall(castle, castle.throne(),
                                new Size(CastleFileFormat.THRONE_WIDTH, CastleFileFormat.THRONE_HEIGHT)),
                        "problems.throne_outside_wall");
                addMissingProblem(problems, isInsideWall(castle, castle.money(), castle.moneyRoom()),
                        "problems.money_outside_wall");
                addMissingProblem(problems, isInsideWall(castle, castle.balls(), castle.ballRoom()),
                        "problems.balls_outside_wall");
                addMissingProblem(problems, isInsideWall(castle, castle.powder(), castle.powderRoom()),
                        "problems.powder_outside_wall");
            }
            addOverlapProblem(problems, castle.throne(),
                    new Size(CastleFileFormat.THRONE_WIDTH, CastleFileFormat.THRONE_HEIGHT),
                    castle.money(), castle.moneyRoom(), "problems.overlap_throne_money");
            addOverlapProblem(problems, castle.throne(),
                    new Size(CastleFileFormat.THRONE_WIDTH, CastleFileFormat.THRONE_HEIGHT),
                    castle.balls(), castle.ballRoom(), "problems.overlap_throne_balls");
            addOverlapProblem(problems, castle.throne(),
                    new Size(CastleFileFormat.THRONE_WIDTH, CastleFileFormat.THRONE_HEIGHT),
                    castle.powder(), castle.powderRoom(), "problems.overlap_throne_powder");
            addOverlapProblem(problems, castle.money(), castle.moneyRoom(),
                    castle.balls(), castle.ballRoom(), "problems.overlap_money_balls");
            addOverlapProblem(problems, castle.money(), castle.moneyRoom(),
                    castle.powder(), castle.powderRoom(), "problems.overlap_money_powder");
            addOverlapProblem(problems, castle.balls(), castle.ballRoom(),
                    castle.powder(), castle.powderRoom(), "problems.overlap_balls_powder");
            addCannonOverlapProblems(problems, castle);
            addWindVaneOverlapProblems(problems, castle);
            addZeroWarning(problems, hasMoney(castle), castle.resources().money(), "problems.zero_money");
            addZeroWarning(problems, hasPowder(castle), castle.resources().powder(), "problems.zero_powder");
            addZeroWarning(problems, hasBalls(castle), castle.resources().balls(), "problems.zero_balls");
            addZeroWarning(problems, hasPeople(castle), castle.resources().people(), "problems.zero_people");
            if (castle.cannons().isEmpty()) {
                problems.add(new ProblemsPanel.Problem(ProblemsPanel.ProblemSeverity.WARNING,
                        HGBaseText.getText("problems.no_cannons")));
            }
        }
        return problems;
    }

    /**
     * Adds a validation problem when the required item is missing.
     *
     * @param problems the problems to display
     * @param present  whether the required castle item is present
     * @param textKey  localization key for the missing-item message
     */
    private void addMissingProblem(List<ProblemsPanel.Problem> problems, boolean present, String textKey) {
        if (!present) {
            problems.add(new ProblemsPanel.Problem(ProblemsPanel.ProblemSeverity.ERROR,
                    HGBaseText.getText(textKey)));
        }
    }

    /**
     * Adds a warning when an available resource has a zero starting value.
     *
     * @param problems    the problems to display
     * @param areaPresent whether the resource-storage area exists
     * @param value       the resource's starting quantity
     * @param textKey     localization key for the zero-value warning
     */
    private void addZeroWarning(List<ProblemsPanel.Problem> problems, boolean areaPresent, int value,
                                String textKey) {
        if (areaPresent && value == 0) {
            problems.add(new ProblemsPanel.Problem(ProblemsPanel.ProblemSeverity.WARNING,
                    HGBaseText.getText(textKey)));
        }
    }

    /**
     * Returns whether the castle has a positioned throne.
     *
     * @param castle castle whose throne position is checked
     * @return {@code true} if the castle contains a throne position
     */
    private boolean hasThrone(Castle castle) {
        return hasPoint(castle.throne());
    }

    /**
     * Returns whether the castle has a positioned wind vane.
     *
     * @param castle castle whose wind-vane position is checked
     * @return {@code true} if the castle contains a wind-vane position
     */
    private boolean hasWindVane(Castle castle) {
        return hasPoint(castle.windVane());
    }

    /**
     * Returns whether the castle has a positioned money-storage area.
     *
     * @param castle castle whose money-storage position is checked
     * @return {@code true} if the castle contains a money-storage position
     */
    private boolean hasMoney(Castle castle) {
        return hasPoint(castle.money());
    }

    /**
     * Returns whether the castle has a positioned powder-storage area.
     *
     * @param castle castle whose powder-storage position is checked
     * @return {@code true} if the castle contains a powder-storage position
     */
    private boolean hasPowder(Castle castle) {
        return hasPoint(castle.powder());
    }

    /**
     * Returns whether the castle has a positioned cannonball-storage area.
     *
     * @param castle castle whose cannonball-storage position is checked
     * @return {@code true} if the castle contains a cannonball-storage position
     */
    private boolean hasBalls(Castle castle) {
        return hasPoint(castle.balls());
    }

    /**
     * Returns whether the castle's starting people value is nonnegative.
     *
     * @param castle castle whose starting people quantity is checked
     * @return {@code true} if the starting people value is zero or greater
     */
    private boolean hasPeople(Castle castle) {
        return castle.resources().people() >= 0;
    }

    /**
     * Returns whether both coordinates identify a set castle point.
     *
     * @param point castle coordinate pair to test for the unset-point sentinel
     * @return true if both coordinates are set; false otherwise
     */
    private boolean hasPoint(Point point) {
        return point.x() != Integer.MIN_VALUE && point.y() != Integer.MIN_VALUE;
    }

    /**
     * Returns whether every castle element fits within the allowed width.
     *
     * @param castle castle whose elements' horizontal extent is measured
     * @return {@code true} if the castle has no positioned elements or their horizontal span is within the limit
     */
    private boolean hasElementWidth(Castle castle) {
        int minimumX = Integer.MAX_VALUE;
        int maximumX = Integer.MIN_VALUE;
        for (CastleDocument.DrawingCommand command : castle.drawing()) {
            if (command instanceof CastleDocument.Polygon(List<Point> points)) {
                for (Point point : points) {
                    minimumX = Math.min(minimumX, point.x());
                    maximumX = Math.max(maximumX, point.x());
                }
            }
        }
        if (hasPoint(castle.throne())) {
            minimumX = Math.min(minimumX, castle.throne().x());
            maximumX = Math.max(maximumX, castle.throne().x() + CastleFileFormat.THRONE_WIDTH);
        }
        if (hasPoint(castle.money())) {
            minimumX = Math.min(minimumX, castle.money().x());
            maximumX = Math.max(maximumX, castle.money().x() + castle.moneyRoom().width());
        }
        if (hasPoint(castle.powder())) {
            minimumX = Math.min(minimumX, castle.powder().x());
            maximumX = Math.max(maximumX, castle.powder().x() + castle.powderRoom().width());
        }
        if (hasPoint(castle.balls())) {
            minimumX = Math.min(minimumX, castle.balls().x());
            maximumX = Math.max(maximumX, castle.balls().x() + castle.ballRoom().width());
        }
        if (hasPoint(castle.windVane())) {
            minimumX = Math.min(minimumX, castle.windVane().x());
            maximumX = Math.max(maximumX, castle.windVane().x() + CastleFileFormat.WIND_VANE_WIDTH);
        }
        for (Point cannon : castle.cannons()) {
            minimumX = Math.min(minimumX, cannon.x());
            maximumX = Math.max(maximumX, cannon.x() + CastleFileFormat.CANNON_WIDTH);
        }
        return minimumX == Integer.MAX_VALUE
                || maximumX - minimumX <= MAX_CASTLE_ELEMENT_WIDTH;
    }

    /**
     * Adds a problem when two positioned areas overlap.
     *
     * @param problems       the problems to display
     * @param firstPosition  lower-left position of the first area
     * @param firstSize      dimensions of the first area
     * @param secondPosition lower-left position of the second area
     * @param secondSize     dimensions of the second area
     * @param textKey        localization key for the overlap message
     */
    private void addOverlapProblem(List<ProblemsPanel.Problem> problems, Point firstPosition, Size firstSize,
                                   Point secondPosition, Size secondSize, String textKey) {
        if (hasPoint(firstPosition) && hasPoint(secondPosition)
                && rectanglesOverlap(firstPosition, firstSize, secondPosition, secondSize)) {
            problems.add(new ProblemsPanel.Problem(ProblemsPanel.ProblemSeverity.ERROR,
                    HGBaseText.getText(textKey)));
        }
    }

    /**
     * Checks the selected castle objects for overlaps with each cannon.
     *
     * @param problems validation findings to append to
     * @param castle   castle whose cannons are checked against other elements
     */
    private void addCannonOverlapProblems(List<ProblemsPanel.Problem> problems, Castle castle) {
        Size cannonSize = new Size(CastleFileFormat.CANNON_WIDTH, CastleFileFormat.CANNON_HEIGHT);
        for (int index = 0; index < castle.cannons().size(); index++) {
            Point cannon = castle.cannons().get(index);
            addOverlapProblem(problems, cannon, cannonSize, castle.throne(),
                    new Size(CastleFileFormat.THRONE_WIDTH, CastleFileFormat.THRONE_HEIGHT),
                    "problems.overlap_cannon_throne");
            addOverlapProblem(problems, cannon, cannonSize, castle.money(), castle.moneyRoom(),
                    "problems.overlap_cannon_money");
            addOverlapProblem(problems, cannon, cannonSize, castle.balls(), castle.ballRoom(),
                    "problems.overlap_cannon_balls");
            addOverlapProblem(problems, cannon, cannonSize, castle.powder(), castle.powderRoom(),
                    "problems.overlap_cannon_powder");
            addPolygonOverlapProblems(problems, cannon, cannonSize, castle,
                    "problems.overlap_cannon_wall", "problems.overlap_cannon_roof");
            for (int otherIndex = index + 1; otherIndex < castle.cannons().size(); otherIndex++) {
                addOverlapProblem(problems, cannon, cannonSize, castle.cannons().get(otherIndex), cannonSize,
                        "problems.overlap_cannon_cannon");
            }
        }
    }

    /**
     * Checks the selected castle objects for overlaps with the wind vane.
     *
     * @param problems validation findings to append to
     * @param castle   castle whose wind vane is checked against other elements
     */
    private void addWindVaneOverlapProblems(List<ProblemsPanel.Problem> problems, Castle castle) {
        Point windVane = castle.windVane();
        Size windVaneSize = new Size(CastleFileFormat.WIND_VANE_WIDTH, CastleFileFormat.WIND_VANE_HEIGHT);
        addOverlapProblem(problems, windVane, windVaneSize, castle.throne(),
                new Size(CastleFileFormat.THRONE_WIDTH, CastleFileFormat.THRONE_HEIGHT),
                "problems.overlap_windvane_throne");
        addOverlapProblem(problems, windVane, windVaneSize, castle.money(), castle.moneyRoom(),
                "problems.overlap_windvane_money");
        addOverlapProblem(problems, windVane, windVaneSize, castle.balls(), castle.ballRoom(),
                "problems.overlap_windvane_balls");
        addOverlapProblem(problems, windVane, windVaneSize, castle.powder(), castle.powderRoom(),
                "problems.overlap_windvane_powder");
        for (Point cannon : castle.cannons()) {
            addOverlapProblem(problems, windVane, windVaneSize, cannon,
                    new Size(CastleFileFormat.CANNON_WIDTH, CastleFileFormat.CANNON_HEIGHT),
                    "problems.overlap_windvane_cannon");
        }
        addPolygonOverlapProblems(problems, windVane, windVaneSize, castle,
                "problems.overlap_windvane_wall", "problems.overlap_windvane_roof");
    }

    /**
     * Adds overlap problems for a rectangle intersecting castle polygons.
     *
     * @param problems    the problems to display
     * @param position    lower-left position of the object rectangle
     * @param size        width and height of the object rectangle
     * @param castle      castle drawing whose polygons are checked for intersection
     * @param wallTextKey localization key for overlap with masonry
     * @param roofTextKey localization key for overlap with a non-masonry polygon
     */
    private void addPolygonOverlapProblems(List<ProblemsPanel.Problem> problems, Point position, Size size,
                                           Castle castle, String wallTextKey, String roofTextKey) {
        if (!hasPoint(position)) {
            return;
        }
        Rectangle2D rectangle = new Rectangle2D.Double(position.x(), position.y(), size.width(), size.height());
        int fillStyle = CastleFileFormat.FILL_STYLE_PATTERN;
        int fillIndex = CastleFileFormat.FILL_INDEX_MASONRY;
        for (CastleDocument.DrawingCommand command : castle.drawing()) {
            if (command instanceof CastleDocument.FillStyle(int style, int index)) {
                fillStyle = style;
                fillIndex = index;
            } else if (command instanceof CastleDocument.Polygon polygon
                    && polygonIntersects(polygon, rectangle)) {
                String textKey = fillStyle == CastleFileFormat.FILL_STYLE_PATTERN
                        && fillIndex == CastleFileFormat.FILL_INDEX_MASONRY
                        ? wallTextKey : roofTextKey;
                problems.add(new ProblemsPanel.Problem(ProblemsPanel.ProblemSeverity.ERROR,
                        HGBaseText.getText(textKey)));
            }
        }
    }

    /**
     * Returns whether the two rectangles overlap.
     *
     * @param firstPosition  lower-left position of the first rectangle
     * @param firstSize      width and height of the first rectangle
     * @param secondPosition lower-left position of the second rectangle
     * @param secondSize     width and height of the second rectangle
     * @return {@code true} if the rectangles have a nonempty intersection
     */
    private boolean rectanglesOverlap(Point firstPosition, Size firstSize,
                                      Point secondPosition, Size secondSize) {
        return new Rectangle2D.Double(firstPosition.x(), firstPosition.y(),
                firstSize.width(), firstSize.height()).intersects(
                secondPosition.x(), secondPosition.y(), secondSize.width(), secondSize.height());
    }

    /**
     * Returns whether the complete area lies within the wall polygon.
     *
     * @param castle   castle whose masonry polygons are checked
     * @param position lower-left position of the area
     * @param size     width and height of the area
     * @return {@code true} if the area is unset or lies entirely inside a masonry polygon
     */
    private boolean isInsideWall(Castle castle, Point position, Size size) {
        if (!hasPoint(position)) {
            return true;
        }
        Rectangle2D area = new Rectangle2D.Double(position.x(), position.y(), size.width(), size.height());
        int fillStyle = CastleFileFormat.FILL_STYLE_PATTERN;
        int fillIndex = CastleFileFormat.FILL_INDEX_MASONRY;
        for (CastleDocument.DrawingCommand command : castle.drawing()) {
            if (command instanceof CastleDocument.FillStyle(int style, int index)) {
                fillStyle = style;
                fillIndex = index;
            } else if (command instanceof CastleDocument.Polygon polygon && fillStyle == CastleFileFormat.FILL_STYLE_PATTERN
                    && fillIndex == CastleFileFormat.FILL_INDEX_MASONRY
                    && polygonContains(polygon, area)) {
                return true;
            }

        }
        return false;
    }

    /**
     * Returns whether the polygon contains the supplied rectangle.
     *
     * @param polygon polygon to test
     * @param area    rectangle that must be contained by the polygon
     * @return {@code true} if the polygon contains the entire rectangle
     */
    private boolean polygonContains(CastleDocument.Polygon polygon, Rectangle2D area) {
        Path2D path = new Path2D.Double();
        Point first = polygon.points().getFirst();
        path.moveTo(first.x(), first.y());
        for (int index = 1; index < polygon.points().size(); index++) {
            Point point = polygon.points().get(index);
            path.lineTo(point.x(), point.y());
        }
        path.closePath();
        return path.contains(area);
    }

    /**
     * Returns whether the polygon intersects the supplied rectangle.
     *
     * @param polygon   polygon to test
     * @param rectangle rectangle to test for intersection
     * @return {@code true} if the polygon intersects the rectangle
     */
    private boolean polygonIntersects(CastleDocument.Polygon polygon, Rectangle2D rectangle) {
        Path2D path = new Path2D.Double();
        Point first = polygon.points().getFirst();
        path.moveTo(first.x(), first.y());
        for (int index = 1; index < polygon.points().size(); index++) {
            Point point = polygon.points().get(index);
            path.lineTo(point.x(), point.y());
        }
        path.closePath();
        return path.intersects(rectangle);
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
