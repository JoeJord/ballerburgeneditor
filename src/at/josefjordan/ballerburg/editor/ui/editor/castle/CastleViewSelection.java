package at.josefjordan.ballerburg.editor.ui.editor.castle;

import at.josefjordan.ballerburg.editor.model.CastleDocument.Polygon;

import java.awt.geom.Point2D;
import java.util.Objects;

/**
 * Holds the selection and edge-hover state of a castle view.
 */
final class CastleViewSelection {
    /**
     * Currently selected polygon, or {@code null} when no polygon is selected.
     */
    private Polygon selectedPolygon;
    /**
     * Selected vertex index in the selected polygon, or {@code -1}.
     */
    private int selectedPointIndex = -1;
    /**
     * Selected cannon index, or {@code -1}.
     */
    private int selectedCannonIndex = -1;
    /**
     * Whether the throne is selected.
     */
    private boolean selectedThrone;
    /**
     * Whether the wind vane is selected.
     */
    private boolean selectedWindVane;
    /**
     * Selected resource area, or {@code null} when none is selected.
     */
    private ResourceArea selectedResourceArea;
    /**
     * Selected resource-area corner being resized, or {@code -1} when none.
     */
    private int draggingResourceCorner = -1;
    /**
     * Current screen-space pointer position for selected-polygon edge highlighting.
     */
    private Point2D edgeHoverPosition;

    /**
     * Returns the selected polygon, if any.
     */
    Polygon selectedPolygon() {
        return selectedPolygon;
    }

    /**
     * Returns the selected vertex index, or {@code -1}.
     */
    int selectedPointIndex() {
        return selectedPointIndex;
    }

    /**
     * Returns the selected cannon index, or {@code -1}.
     */
    int selectedCannonIndex() {
        return selectedCannonIndex;
    }

    /**
     * Returns whether the throne is selected.
     */
    boolean selectedThrone() {
        return selectedThrone;
    }

    /**
     * Returns whether the wind vane is selected.
     */
    boolean selectedWindVane() {
        return selectedWindVane;
    }

    /**
     * Returns the selected resource area, if any.
     */
    ResourceArea selectedResourceArea() {
        return selectedResourceArea;
    }

    /**
     * Returns the active resource-area resize corner, or {@code -1}.
     */
    int draggingResourceCorner() {
        return draggingResourceCorner;
    }

    /**
     * Returns the pointer position used for edge highlighting, if set.
     */
    Point2D edgeHoverPosition() {
        return edgeHoverPosition;
    }

    /**
     * Selects a polygon and clears the edge-hover position when the selection changes.
     *
     * @param polygon polygon to select, or {@code null} to clear selection
     * @return {@code true} if the selected polygon changed
     */
    boolean setSelectedPolygon(Polygon polygon) {
        if (polygon == selectedPolygon) {
            return false;
        }
        selectedPolygon = polygon;
        edgeHoverPosition = null;
        return true;
    }

    /**
     * Updates the selected polygon vertex index.
     *
     * @param index vertex index, or {@code -1} to clear selection
     * @return {@code true} if the index changed
     */
    boolean setSelectedPointIndex(int index) {
        if (selectedPointIndex == index) {
            return false;
        }
        selectedPointIndex = index;
        return true;
    }

    /**
     * Updates the selected cannon index.
     *
     * @param index cannon index, or {@code -1} to clear selection
     * @return {@code true} if the index changed
     */
    boolean setSelectedCannonIndex(int index) {
        if (selectedCannonIndex == index) {
            return false;
        }
        selectedCannonIndex = index;
        return true;
    }

    /**
     * Updates whether the throne is selected.
     *
     * @param selected whether the throne should be selected
     * @return {@code true} if the selection changed
     */
    boolean setSelectedThrone(boolean selected) {
        if (selectedThrone == selected) {
            return false;
        }
        selectedThrone = selected;
        return true;
    }

    /**
     * Updates whether the wind vane is selected.
     *
     * @param selected whether the wind vane should be selected
     * @return {@code true} if the selection changed
     */
    boolean setSelectedWindVane(boolean selected) {
        if (selectedWindVane == selected) {
            return false;
        }
        selectedWindVane = selected;
        return true;
    }

    /**
     * Selects a resource area and clears any active resize-corner selection if it changes.
     *
     * @param resourceArea resource area to select, or {@code null} to clear selection
     * @return {@code true} if the resource-area selection changed
     */
    boolean setSelectedResourceArea(ResourceArea resourceArea) {
        if (Objects.equals(selectedResourceArea, resourceArea)) {
            return false;
        }
        selectedResourceArea = resourceArea;
        draggingResourceCorner = -1;
        return true;
    }

    /**
     * Updates the selected resize corner.
     *
     * @param cornerIndex corner index, or {@code -1} when no corner is being resized
     * @return {@code true} if the corner changed
     */
    boolean setDraggingResourceCorner(int cornerIndex) {
        if (draggingResourceCorner == cornerIndex) {
            return false;
        }
        draggingResourceCorner = cornerIndex;
        return true;
    }

    /**
     * Updates the pointer location used for edge hover highlighting.
     *
     * @param position screen-space pointer position, or {@code null} to clear it
     * @return {@code true} if the pointer position changed
     */
    boolean setEdgeHoverPosition(Point2D position) {
        if (Objects.equals(edgeHoverPosition, position)) {
            return false;
        }
        edgeHoverPosition = position;
        return true;
    }

    /**
     * Clears selection associated with the previous castle.
     */
    void clearForCastleSelection() {
        selectedPolygon = null;
        selectedCannonIndex = -1;
        selectedThrone = false;
        selectedWindVane = false;
        selectedResourceArea = null;
        edgeHoverPosition = null;
    }

    /**
     * Clears selectable elements when polygon creation starts.
     */
    void clearForPolygonCreation() {
        selectedPolygon = null;
        selectedCannonIndex = -1;
        selectedThrone = false;
        selectedWindVane = false;
        selectedResourceArea = null;
    }
}
