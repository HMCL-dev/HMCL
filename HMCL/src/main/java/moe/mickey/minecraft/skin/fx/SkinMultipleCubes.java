package moe.mickey.minecraft.skin.fx;

import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.paint.Color;
import javafx.scene.paint.Material;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;

import java.util.function.BiConsumer;
import java.util.function.Supplier;

public class SkinMultipleCubes extends Group {

    public static class Face extends Group {

        public Face(Image image, int startX, int startY, int width, int height, int interval, boolean reverseX, boolean reverseY,
                    Supplier<Box> supplier, BiConsumer<Box, Point2D> consumer) {
            PixelReader reader = image.getPixelReader();
            for (int x = 0; x < width; x++)
                for (int y = 0; y < height; y++) {
                    int argb;
                    if ((argb = reader.getArgb(startX + (reverseX ? width - x - 1 : x) * interval,
                            startY + (reverseY ? height - y - 1 : y) * interval)) != 0) {
                        Box pixel = supplier.get();
                        consumer.accept(pixel, new Point2D(x, y));
                        pixel.setMaterial(createMaterial(Color.rgb(
                                (argb >> 16) & 0xFF, (argb >> 8) & 0xFF, (argb >> 0) & 0xFF)));
                        getChildren().add(pixel);
                    }
                }
        }

        protected Material createMaterial(Color color) {
            return new PhongMaterial(color);
        }

    }

    protected int width, height, depth;
    protected float startX, startY;
    protected double length, thick;

    public SkinMultipleCubes(int width, int height, int depth, float startX, float startY, double length, double thick) {
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.startX = startX;
        this.startY = startY;
        this.length = length;
        this.thick = thick;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getWidth() {
        return width;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public int getHeight() {
        return height;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }

    public int getDepth() {
        return depth;
    }

    public void setStartX(float startX) {
        this.startX = startX;
    }

    public float getStartX() {
        return startX;
    }

    public void setStartY(float startY) {
        this.startY = startY;
    }

    public float getStartY() {
        return startY;
    }

    public void setLength(double length) {
        this.length = length;
    }

    public double getLength() {
        return length;
    }

    public void setThick(double thick) {
        this.thick = thick;
    }

    public double getThick() {
        return thick;
    }

    public void updateSkin(Image skin) {
        getChildren().clear();
        int start_x = (int) (startX * skin.getWidth()), start_y = (int) (startY * skin.getHeight()),
                interval = (int) Math.max(skin.getWidth() / 64, 1),
                width_interval = width * interval, height_interval = height * interval, depth_interval = depth * interval;
        // FRONT
        getChildren().add(new Face(skin, start_x + depth_interval, start_y + depth_interval, width, height, interval, false, false,
                () -> new Box(length, length, thick), (b, p) -> {
            b.setTranslateX(((width - 1) / 2.0 - p.getX()) * b.getWidth());
            b.setTranslateY(-((height - 1) / 2.0 - p.getY()) * b.getHeight());
            b.setTranslateZ((depth * length + thick) / 2.0);
        }));
        // BACK
        getChildren().add(new Face(skin, start_x + width_interval + depth_interval * 2, start_y + depth_interval, width, height, interval, true, false,
                () -> new Box(length, length, thick), (b, p) -> {
            b.setTranslateX(((width - 1) / 2.0 - p.getX()) * b.getWidth());
            b.setTranslateY(-((height - 1) / 2.0 - p.getY()) * b.getHeight());
            b.setTranslateZ(-(depth * length + thick) / 2.0);
        }));
        // LEFT
        getChildren().add(new Face(skin, start_x + width_interval + depth_interval, start_y + depth_interval, depth, height, interval, false, false,
                () -> new Box(thick, length, length), (b, p) -> {
            b.setTranslateX((width * length + thick) / 2.0);
            b.setTranslateY(-((height - 1) / 2.0 - p.getY()) * b.getHeight());
            b.setTranslateZ(((depth - 1) / 2.0 - p.getX()) * b.getDepth());
        }));
        // RIGHT
        getChildren().add(new Face(skin, start_x, start_y + depth_interval, depth, height, interval, true, false,
                () -> new Box(thick, length, length), (b, p) -> {
            b.setTranslateX(-(width * length + thick) / 2.0);
            b.setTranslateY(-((height - 1) / 2.0 - p.getY()) * b.getHeight());
            b.setTranslateZ(((depth - 1) / 2.0 - p.getX()) * b.getDepth());
        }));
        // TOP
        getChildren().add(new Face(skin, start_x + depth_interval, start_y, width, depth, interval, false, false,
                () -> new Box(length, thick, length), (b, p) -> {
            b.setTranslateX(((width - 1) / 2.0 - p.getX()) * b.getWidth());
            b.setTranslateY(-(height * length + thick) / 2.0);
            b.setTranslateZ(-((depth - 1) / 2.0 - p.getY()) * b.getDepth());
        }));
        // BOTTOM
        getChildren().add(new Face(skin, start_x + width_interval + depth_interval, start_y, width, depth, interval, false, false,
                () -> new Box(length, thick, length), (b, p) -> {
            b.setTranslateX(((width - 1) / 2.0 - p.getX()) * b.getWidth());
            b.setTranslateY((height * length + thick) / 2.0);
            b.setTranslateZ(-((depth - 1) / 2.0 - p.getY()) * b.getDepth());
        }));
    }

}
