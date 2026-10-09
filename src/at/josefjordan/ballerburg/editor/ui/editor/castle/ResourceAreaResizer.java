package at.josefjordan.ballerburg.editor.ui.editor.castle;

import at.josefjordan.ballerburg.editor.model.CastleDocument.Castle;
import at.josefjordan.ballerburg.editor.model.CastleDocument.Point;
import at.josefjordan.ballerburg.editor.model.CastleDocument.Size;
import at.josefjordan.ballerburg.editor.model.CastleFileFormat;

/**
 * Calculates slot-aligned resource-storage dimensions and positions during corner resizing.
 */
final class ResourceAreaResizer {
    /**
     * Calculates the resized area while keeping the corner opposite the dragged handle fixed.
     *
     * @param castle           castle whose resource count determines the minimum storage capacity
     * @param resourceArea     storage area being resized
     * @param originalPosition lower-left position captured when resizing started
     * @param originalSize     dimensions captured when resizing started
     * @param cornerIndex      active handle (bottom-left 0, bottom-right 1, top-left 2, top-right 3)
     * @param deltaX           horizontal pointer movement in castle coordinates
     * @param deltaY           vertical pointer movement in castle coordinates
     * @return resized dimensions and corresponding lower-left position
     */
    ResizeResult resize(Castle castle, ResourceArea resourceArea, Point originalPosition, Size originalSize,
                        int cornerIndex, double deltaX, double deltaY) {
        int stepX = getStepX(resourceArea);
        int stepY = getStepY(resourceArea);
        int resourceCount = getResourceCount(castle, resourceArea);
        Size minimumSize = calculateMinimumSize(resourceCount, stepX, stepY);
        ResizeConstraints constraints = new ResizeConstraints(minimumSize, resourceCount, stepX, stepY);
        int roundedDeltaX = (int) Math.round(deltaX / stepX) * stepX;
        int roundedDeltaY = (int) Math.round(deltaY / stepY) * stepY;
        Size newSize = calculateNewSize(originalSize, cornerIndex, roundedDeltaX, roundedDeltaY, constraints);
        Point newPosition = calculateNewPosition(originalPosition, originalSize, newSize, cornerIndex);
        return new ResizeResult(newPosition, newSize);
    }

    /**
     * Positions the resized area so that the corner opposite the dragged handle remains fixed.
     *
     * @param originalPosition lower-left position captured when resizing started
     * @param originalSize     dimensions captured when resizing started
     * @param newSize          resized dimensions
     * @param cornerIndex      active handle (bottom-left 0, bottom-right 1, top-left 2, top-right 3)
     * @return the resized area's lower-left position
     */
    private Point calculateNewPosition(Point originalPosition, Size originalSize, Size newSize, int cornerIndex) {
        int newWidth = newSize.width();
        int newHeight = newSize.height();
        int rightAlignedX = originalPosition.x() + originalSize.width() - newWidth;
        int topAlignedY = originalPosition.y() + originalSize.height() - newHeight;
        return switch (cornerIndex) {
            case 0 -> new Point(rightAlignedX, topAlignedY);
            case 1 -> new Point(originalPosition.x(), topAlignedY);
            case 2 -> new Point(rightAlignedX, originalPosition.y());
            default -> originalPosition;
        };
    }

    /**
     * Applies the drag deltas and adjusts the result to grid-aligned dimensions that fit the resources.
     *
     * @param originalSize dimensions captured when resizing started
     * @param cornerIndex  active handle (bottom-left 0, bottom-right 1, top-left 2, top-right 3)
     * @param deltaX       rounded horizontal pointer movement in castle coordinates
     * @param deltaY       rounded vertical pointer movement in castle coordinates
     * @param constraints  minimum dimensions, resource count, and grid steps for the selected area
     * @return the new dimensions in castle coordinates
     */
    private Size calculateNewSize(Size originalSize, int cornerIndex, int deltaX, int deltaY,
                                  ResizeConstraints constraints) {
        Size resizedSize = switch (cornerIndex) {
            case 0 -> new Size(originalSize.width() - deltaX, originalSize.height() + deltaY);
            case 1 -> new Size(originalSize.width() + deltaX, originalSize.height() + deltaY);
            case 2 -> new Size(originalSize.width() - deltaX, originalSize.height() - deltaY);
            case 3 -> new Size(originalSize.width() + deltaX, originalSize.height() - deltaY);
            default -> originalSize;
        };

        int width = Math.max(constraints.minimumSize().width(),
                (resizedSize.width() / constraints.stepX()) * constraints.stepX());
        int height = Math.max(constraints.minimumSize().height(),
                (resizedSize.height() / constraints.stepY()) * constraints.stepY());
        if (!canFitResources(width, height, constraints)) {
            Size validSize = findNearestValidSize(width, height, constraints);
            width = validSize.width();
            height = validSize.height();
        }
        return new Size(width, height);
    }

    /**
     * Returns the horizontal grid step for a resource-storage area.
     *
     * @param resourceArea storage area whose grid step is requested
     * @return horizontal slot width in castle coordinates
     */
    private int getStepX(ResourceArea resourceArea) {
        return switch (resourceArea) {
            case MONEY -> CastleFileFormat.MONEY_WIDTH;
            case POWDER -> CastleFileFormat.POWDER_WIDTH;
            case BALLS -> CastleFileFormat.CANNONBALL_WIDTH;
        };
    }

    /**
     * Returns the vertical grid step for a resource-storage area.
     *
     * @param resourceArea storage area whose grid step is requested
     * @return vertical slot height in castle coordinates
     */
    private int getStepY(ResourceArea resourceArea) {
        return switch (resourceArea) {
            case MONEY -> CastleFileFormat.MONEY_HEIGHT;
            case POWDER -> CastleFileFormat.POWDER_HEIGHT;
            case BALLS -> CastleFileFormat.CANNONBALL_HEIGHT;
        };
    }

    /**
     * Counts the occupied storage slots required by the castle's current resource quantity.
     *
     * @param castle       castle containing the resource quantities
     * @param resourceArea storage area whose resource count is requested
     * @return number of slots required, rounded up where resources occupy fractional units
     */
    private int getResourceCount(Castle castle, ResourceArea resourceArea) {
        return switch (resourceArea) {
            case MONEY -> divideRoundedUp(castle.resources().money(), CastleFileFormat.MONEY_PER_UNIT);
            case POWDER -> divideRoundedUp(castle.resources().powder(), CastleFileFormat.POWDER_PER_UNIT);
            case BALLS -> castle.resources().balls();
        };
    }

    /**
     * Divides a positive quantity by a unit size, rounding any remainder up to another unit.
     *
     * @param value   quantity to divide
     * @param divisor size of one unit
     * @return zero for non-positive quantities, otherwise the quotient rounded up
     */
    private int divideRoundedUp(int value, int divisor) {
        return value <= 0 ? 0 : (value + divisor - 1) / divisor;
    }

    /**
     * Calculates the smallest independently possible width and height for the required slot count.
     *
     * @param resourceCount number of slots that must fit
     * @param stepX         horizontal slot width in castle coordinates
     * @param stepY         vertical slot height in castle coordinates
     * @return minimum dimensions aligned to the slot grid
     */
    private Size calculateMinimumSize(int resourceCount, int stepX, int stepY) {
        if (resourceCount <= 0) {
            return new Size(stepX, stepY);
        }
        int minWidth = Integer.MAX_VALUE;
        int minHeight = Integer.MAX_VALUE;
        for (int columns = 1; columns <= resourceCount; columns++) {
            int rows = (int) Math.ceil((double) resourceCount / columns);
            minWidth = Math.min(minWidth, columns * stepX);
            minHeight = Math.min(minHeight, rows * stepY);
        }
        return new Size(minWidth, minHeight);
    }

    /**
     * Checks whether dimensions aligned to the slot grid can hold the required resources.
     *
     * @param width       candidate width in castle coordinates
     * @param height      candidate height in castle coordinates
     * @param constraints grid steps and required resource count
     * @return {@code true} if the candidate has enough slots
     */
    private boolean canFitResources(int width, int height, ResizeConstraints constraints) {
        if (constraints.resourceCount() <= 0) {
            return true;
        }
        int columns = width / constraints.stepX();
        int rows = height / constraints.stepY();
        return columns > 0 && rows > 0 && columns * rows >= constraints.resourceCount();
    }

    /**
     * Finds the smallest candidate dimensions near the target that can hold all required resources.
     *
     * @param targetWidth  desired width in castle coordinates
     * @param targetHeight desired height in castle coordinates
     * @param constraints  grid steps and required resource count
     * @return valid dimensions with the lower total deviation from the target
     */
    private Size findNearestValidSize(int targetWidth, int targetHeight, ResizeConstraints constraints) {
        if (constraints.resourceCount() <= 0) {
            return new Size(Math.max(constraints.stepX(), targetWidth),
                    Math.max(constraints.stepY(), targetHeight));
        }

        targetWidth = Math.max(constraints.stepX(), targetWidth);
        targetHeight = Math.max(constraints.stepY(), targetHeight);

        int columns = Math.max(1, targetWidth / constraints.stepX());
        int rows = (int) Math.ceil((double) constraints.resourceCount() / columns);
        Size sizeWithFixedWidth = new Size(columns * constraints.stepX(), rows * constraints.stepY());

        rows = Math.max(1, targetHeight / constraints.stepY());
        columns = (int) Math.ceil((double) constraints.resourceCount() / rows);
        Size sizeWithFixedHeight = new Size(columns * constraints.stepX(), rows * constraints.stepY());

        double diffWithFixedWidth = (double) Math.abs(sizeWithFixedWidth.width() - targetWidth)
                + Math.abs(sizeWithFixedWidth.height() - targetHeight);
        double diffWithFixedHeight = (double) Math.abs(sizeWithFixedHeight.width() - targetWidth)
                + Math.abs(sizeWithFixedHeight.height() - targetHeight);
        return diffWithFixedWidth <= diffWithFixedHeight ? sizeWithFixedWidth : sizeWithFixedHeight;
    }

    /**
     * Resized storage-area geometry.
     *
     * @param position lower-left position in castle coordinates
     * @param size     dimensions in castle coordinates
     */
    record ResizeResult(Point position, Size size) {
    }

    /**
     * Grid and capacity constraints used while calculating resized dimensions.
     *
     * @param minimumSize   minimum dimensions for the resource quantity
     * @param resourceCount number of storage slots required
     * @param stepX         horizontal slot width in castle coordinates
     * @param stepY         vertical slot height in castle coordinates
     */
    private record ResizeConstraints(Size minimumSize, int resourceCount, int stepX, int stepY) {
    }
}
