package at.josefjordan.ballerburg.editor.ui.editor;

import at.josefjordan.ballerburg.editor.model.CastleFileFormat;

import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Vector sprites transcribed from the original {@code BALLER2.C} source.
 */
final class Sprites {
    /**
     * Near-black color used to draw sprite outlines and filled pixels.
     */
    private static final Color INK = new Color(35, 35, 35);
    /**
     * Lazily generated raster images, keyed by the identity of their encoded sprite arrays.
     */
    private static final Map<int[], BufferedImage> RASTER_IMAGES = new IdentityHashMap<>();
    /**
     * Encoded vector data for the king sprite.
     */
    static final int[] KING = {
            -2, 2, 2, 8, 3, 5, 12, 8, 12, 12, 16, 12, 17, 14, 15, 15, 15, 17, 17, 17,
            16, 21, 11, 24, 11, 21, 3, -9, -4, 3, 0, 5, 2, 24, 2, 26, 0, -9,
            19, 4, 18, 9, -9, 10, 4, 11, 9, -9, 14, 4, 15, 4, -9,
            8, 10, 11, 11, -9, 21, 10, 18, 11, -9, 14, 17, 15, 17, -9,
            13, 19, 13, 19, -9, 16, 19, 16, 19, -9,
            12, 22, 12, 21, 15, 21, 14, 22, 17, 21, 17, 22, -9,
            6, 13, 6, 17, 5, 16, 5, 17, 7, 17, 7, 16, -9, -1
    };
    /**
     * Encoded vector data for the cannon sprite.
     */
    static final int[] CANNON = {
            -2, 1, 1, 1, 0, 3, 3, 5, 0, 3, 3, 3, 11, 2, 11, 2, 9, 4, 9, 4,
            11, 3, 11, 3, 8, 0, 5, 3, 8, 6, 5, 3, 8, 3, 3, -9,
            11, 0, 13, 2, 15, 0, 16, 0, 17, 1, 17, 2, 15, 4, 19, 8, 16, 11, 8, 3, -9, -1
    };
    /**
     * Encoded vector data for the money sprite.
     */
    static final int[] MONEY = {
            -4, 1, 1, 1, 4, 2, 5, 2, 0, 3, 0, 3, 8, 2, 8, 4, 8, 3, 8, 3,
            6, 4, 6, 4, 0, 5, 0, 5, 6, 6, 5, 6, 1, -9, -1
    };
    /**
     * Encoded vector data for the powder sprite.
     */
    static final int[] POWDER = {
            -4, 2, 0, 6, 0, 8, 3, 8, 5, 6, 8, 2, 8, 0, 5, 0, 3, 2, 0, 3,
            0, 3, 3, 2, 4, 3, 5, 3, 8, 5, 8, 5, 5, 6, 4, 5, 3, 5, 1, -9, -1
    };
    /**
     * Encoded vector data for the cannonball sprite.
     */
    static final int[] BALL = {
            -2, 1, 1, 1, 0, 0, 1, 0, 3, 1, 4, 3, 4, 4, 3, 4, 1, 3, 0, -9, -1
    };
    /**
     * Encoded vector data for the wind-vane sprite.
     */
    static final int[] WIND_VANE = {
            0, 0, 0, CastleFileFormat.WIND_VANE_HEIGHT, -9,
            0, CastleFileFormat.WIND_VANE_HEIGHT, 0, CastleFileFormat.WIND_VANE_HEIGHT,
            10, CastleFileFormat.WIND_VANE_HEIGHT - 3, 0, CastleFileFormat.WIND_VANE_HEIGHT - 6, -9, -1
    };

    /**
     * Prevents instantiation of this sprite utility class.
     */
    private Sprites() {
    }

    /**
     * Draws a sprite at the supplied position and scale.
     *
     * @param graphics graphics context to draw into
     * @param data     encoded vector sprite
     * @param x        left coordinate in screen space
     * @param bottom   bottom coordinate in screen space
     * @param scale    sprite scale
     */
    static void draw(Graphics2D graphics, int[] data, double x, double bottom, double scale) {
        draw(graphics, data, x, bottom, scale, 1.0f);
    }

    /**
     * Draws a sprite with the supplied opacity.
     *
     * @param graphics graphics context to draw into
     * @param data     encoded vector sprite
     * @param x        left coordinate in screen space
     * @param bottom   bottom coordinate in screen space
     * @param scale    sprite scale
     * @param alpha    opacity, from 0.0 (transparent) to 1.0 (opaque)
     */
    static void draw(Graphics2D graphics, int[] data, double x, double bottom, double scale, float alpha) {
        BufferedImage image = RASTER_IMAGES.computeIfAbsent(data, Sprites::rasterize);
        int logicalWidth = data == BALL ? 5 : image.getWidth();
        int logicalHeight = data == BALL ? 5 : image.getHeight();
        int width = Math.max(1, (int) Math.round(logicalWidth * scale));
        int height = Math.max(1, (int) Math.round(logicalHeight * scale));
        int left = (int) Math.round(x);
        double verticalOffset = data == CANNON ? 0.0 : scale;
        int top = (int) Math.round(bottom - (logicalHeight - 1) * scale - verticalOffset);
        Graphics2D spriteGraphics = (Graphics2D) graphics.create();
        spriteGraphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        if (alpha < 1.0f) {
            spriteGraphics.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, alpha));
        }
        spriteGraphics.drawImage(image, left, top, width, height, null);
        spriteGraphics.dispose();
    }

    /**
     * Rasterizes the encoded vector sprite and returns its image.
     *
     * @param data encoded sprite coordinates
     * @return the raster image
     */
    private static BufferedImage rasterize(int[] data) {
        if (data == BALL) {
            return rasterizeBall();
        }
        int[] size = spriteSize(data);
        BufferedImage image = new BufferedImage(size[0], size[1], BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);
        graphics.translate(0, size[1] - 1);
        graphics.scale(1, -1);

        boolean fill = true;
        int interior = 2;
        int style = 9;
        int index = 0;
        while (data[index] != -1) {
            if (data[index] == CastleFileFormat.FILL_STYLE_COMMAND) {
                interior = data[index + 1];
                style = data[index + 2];
                index += 3;
            } else if (data[index] == -4) {
                fill = !fill;
                index++;
            } else {
                // GEM VDI fills self-intersecting polygons using the odd/even
                // rule. The cannon uses this deliberately for its operator.
                Path2D path = new Path2D.Double(Path2D.WIND_EVEN_ODD);
                path.moveTo(data[index++], data[index++]);
                int pointCount = 1;
                while (data[index] != CastleFileFormat.POLYGON_TERMINATOR) {
                    path.lineTo(data[index++], data[index++]);
                    pointCount++;
                }
                if (fill && pointCount >= 3) {
                    path.closePath();
                    graphics.setPaint(fillPaint(interior, style));
                    graphics.fill(path);
                    // VDI's filled-polygon rasterizer also retains degenerate
                    // edges. Those zero-width edges form the cannon operator.
                    graphics.setColor(INK);
                    graphics.draw(path);
                } else if (pointCount == 1) {
                    graphics.setColor(INK);
                    graphics.fillRect((int) path.getCurrentPoint().getX(),
                            (int) path.getCurrentPoint().getY(), 1, 1);
                } else {
                    graphics.setColor(INK);
                    graphics.draw(path);
                }
                index++;
            }
        }
        graphics.dispose();
        return image;
    }

    /**
     * Returns the paint used for the encoded sprite fill style.
     *
     * @param interior encoded fill interior mode
     * @param style    the fill style value
     * @return the paint corresponding to the sprite fill settings
     */
    private static Paint fillPaint(int interior, int style) {
        if (interior == 1) {
            return INK;
        }
        // GEM pattern 2 has a 25-percent density. Keeping it in the native
        // pixel grid is important for the original king/throne appearance.
        if (interior == 2 && style == 2) {
            BufferedImage pattern = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
            pattern.setRGB(0, 0, INK.getRGB());
            return new TexturePaint(pattern, new Rectangle2D.Double(0, 0, 2, 2));
        }
        return INK;
    }

    /**
     * Calculates the pixel dimensions needed for the encoded sprite.
     *
     * @param data the encoded sprite coordinates
     * @return the width and height required to rasterize the sprite
     */
    private static int[] spriteSize(int[] data) {
        int maximumX = 0;
        int maximumY = 0;
        int index = 0;
        while (data[index] != -1) {
            if (data[index] == CastleFileFormat.FILL_STYLE_COMMAND) {
                index += 3;
            } else if (data[index] == -4) {
                index++;
            } else {
                while (data[index] != CastleFileFormat.POLYGON_TERMINATOR) {
                    maximumX = Math.max(maximumX, data[index++]);
                    maximumY = Math.max(maximumY, data[index++]);
                }
                index++;
            }
        }
        return new int[]{maximumX + 1, maximumY + 1};
    }

    /**
     * Creates the fixed raster image used for the cannonball sprite.
     *
     * @return the raster image of the cannonball sprite
     */
    @SuppressWarnings("java:S1192") // Repeated rows encode the ball's raster shape.
    private static BufferedImage rasterizeBall() {
        BufferedImage image = new BufferedImage(7, 7, BufferedImage.TYPE_INT_ARGB);
        String[] rows = {
                "..###..",
                ".#####.",
                "#######",
                "#######",
                "#######",
                ".#####.",
                "..###.."
        };
        for (int y = 0; y < rows.length; y++) {
            for (int x = 0; x < rows[y].length(); x++) {
                if (rows[y].charAt(x) == '#') {
                    image.setRGB(x, y, INK.getRGB());
                }
            }
        }
        return image;
    }
}
