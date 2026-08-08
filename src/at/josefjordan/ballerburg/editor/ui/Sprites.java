package at.josefjordan.ballerburg.editor.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;

/** Vector sprites transcribed from the original {@code BALLER2.C} source. */
final class Sprites {
    static final int[] KING = {
            -2, 2, 2, 8, 3, 5, 12, 8, 12, 12, 16, 12, 17, 14, 15, 15, 15, 17, 17, 17,
            16, 21, 11, 24, 11, 21, 3, -9, -4, 3, 0, 5, 2, 24, 2, 26, 0, -9,
            19, 4, 18, 9, -9, 10, 4, 11, 9, -9, 14, 4, 15, 4, -9,
            8, 10, 11, 11, -9, 21, 10, 18, 11, -9, 14, 17, 15, 17, -9,
            13, 19, 13, 19, -9, 16, 19, 16, 19, -9,
            12, 22, 12, 21, 15, 21, 14, 22, 17, 21, 17, 22, -9,
            6, 13, 6, 17, 5, 16, 5, 17, 7, 17, 7, 16, -9, -1
    };
    static final int[] CANNON = {
            -2, 1, 1, 1, 0, 3, 3, 5, 0, 3, 3, 3, 11, 2, 11, 2, 9, 4, 9, 4,
            11, 3, 11, 3, 8, 0, 5, 3, 8, 6, 5, 3, 8, 3, 3, -9,
            11, 0, 13, 2, 15, 0, 16, 0, 17, 1, 17, 2, 15, 4, 19, 8, 16, 11, 8, 3, -9, -1
    };
    static final int[] MONEY = {
            -4, 1, 1, 1, 4, 2, 5, 2, 0, 3, 0, 3, 8, 2, 8, 4, 8, 3, 8, 3,
            6, 4, 6, 4, 0, 5, 0, 5, 6, 6, 5, 6, 1, -9, -1
    };
    static final int[] POWDER = {
            -4, 2, 0, 6, 0, 8, 3, 8, 5, 6, 8, 2, 8, 0, 5, 0, 3, 2, 0, 3,
            0, 3, 3, 2, 4, 3, 5, 3, 8, 5, 8, 5, 5, 6, 4, 5, 3, 5, 1, -9, -1
    };
    static final int[] BALL = {
            -2, 1, 1, 1, 0, 0, 1, 0, 3, 1, 4, 3, 4, 4, 3, 4, 1, 3, 0, -9, -1
    };

    private Sprites() {
    }

    static void draw(Graphics2D graphics, int[] data, double x, double bottom, double scale) {
        boolean fill = true;
        int index = 0;
        graphics.setColor(new Color(35, 35, 35));
        graphics.setStroke(new BasicStroke(Math.max(1.0f, (float) (scale * 0.45)),
                BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
        while (data[index] != -1) {
            if (data[index] == -2) {
                index += 3; // The Atari fill pattern has no equivalent in the editor.
            } else if (data[index] == -4) {
                fill = !fill;
                index++;
            } else {
                Path2D path = new Path2D.Double();
                path.moveTo(x + data[index++] * scale, bottom - data[index++] * scale);
                int pointCount = 1;
                while (data[index] != -9) {
                    path.lineTo(x + data[index++] * scale, bottom - data[index++] * scale);
                    pointCount++;
                }
                if (fill && pointCount >= 3) {
                    path.closePath();
                    graphics.fill(path);
                } else if (pointCount == 1) {
                    graphics.fill(new java.awt.geom.Rectangle2D.Double(
                            path.getCurrentPoint().getX(), path.getCurrentPoint().getY(),
                            Math.max(1.0, scale * 0.65), Math.max(1.0, scale * 0.65)));
                } else {
                    graphics.draw(path);
                }
                index++;
            }
        }
    }
}
