package at.josefjordan.ballerburg.editor.ui.editor.castle;

import at.josefjordan.ballerburg.editor.model.CastleDocument.Point;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Holds the transient state of a polygon being created in the castle view.
 */
final class CastlePolygonCreation {
    /**
     * Category of the polygon being created, or {@code null} when creation is inactive.
     */
    private CastlePolygonType type;
    /**
     * Vertices entered so far, in click order.
     */
    private final List<Point> points = new ArrayList<>();
    /**
     * Screen-space pointer used to render the in-progress polygon preview.
     */
    private Point2D mousePosition;

    /**
     * Checks whether polygon creation is active.
     *
     * @return {@code true} when a polygon category has been selected
     */
    boolean isActive() {
        return type != null;
    }

    /**
     * Returns the active polygon category.
     *
     * @return polygon category, or {@code null} when creation is inactive
     */
    CastlePolygonType type() {
        return type;
    }

    /**
     * Returns an immutable snapshot of the entered vertices.
     *
     * @return entered vertices in click order
     */
    List<Point> points() {
        return List.copyOf(points);
    }

    /**
     * Returns the screen-space pointer position for the preview.
     *
     * @return preview position, or {@code null} when no preview position is set
     */
    Point2D mousePosition() {
        return mousePosition;
    }

    /**
     * Starts a new polygon creation and clears any prior vertices and preview position.
     *
     * @param type polygon category to create
     * @throws NullPointerException if {@code type} is {@code null}
     */
    void start(CastlePolygonType type) {
        this.type = Objects.requireNonNull(type);
        points.clear();
        mousePosition = null;
    }

    /**
     * Cancels polygon creation and discards the entered vertices.
     */
    void cancel() {
        type = null;
        points.clear();
    }

    /**
     * Finishes polygon creation and clears all creation state.
     */
    void finish() {
        type = null;
        points.clear();
        mousePosition = null;
    }

    /**
     * Clears the active category and vertices when another castle is selected.
     */
    void clearForCastleSelection() {
        type = null;
        points.clear();
    }

    /**
     * Appends a vertex to the polygon being created.
     *
     * @param point new vertex in castle coordinates
     */
    void addPoint(Point point) {
        points.add(point);
    }

    /**
     * Sets the pointer position used for the in-progress polygon preview.
     *
     * @param mousePosition screen-space preview position, or {@code null} to clear the preview
     */
    void setMousePosition(Point2D mousePosition) {
        this.mousePosition = mousePosition;
    }
}
