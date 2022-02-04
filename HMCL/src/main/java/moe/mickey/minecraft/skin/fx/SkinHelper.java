package moe.mickey.minecraft.skin.fx;

import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;

public final class SkinHelper {

    private SkinHelper() {
    }

    public static class PixelCopier {

        protected Image srcImage;
        protected WritableImage newImage;

        public PixelCopier(Image srcImage, WritableImage newImage) {
            this.srcImage = srcImage;
            this.newImage = newImage;
        }

        public void copy(int srcX, int srcY, int width, int height) {
            copy(srcX, srcY, srcX, srcY, width, height);
        }

        public void copy(int srcX, int srcY, int toX, int toY, int width, int height) {
            copy(srcX, srcY, toX, toY, width, height, false, false);
        }

        public void copy(int srcX, int srcY, int toX, int toY, int width, int height, boolean reversalX, boolean reversalY) {
            PixelReader reader = srcImage.getPixelReader();
            PixelWriter writer = newImage.getPixelWriter();
            for (int x = 0; x < width; x++)
                for (int y = 0; y < height; y++)
                    writer.setArgb(toX + x, toY + y,
                            reader.getArgb(srcX + (reversalX ? width - x - 1 : x), srcY + (reversalY ? height - y - 1 : y)));
        }

        public void copy(float srcX, float srcY, float toX, float toY, float width, float height) {
            copy(srcX, srcY, toX, toY, width, height, false, false);
        }

        public void copy(float srcX, float srcY, float toX, float toY, float width, float height, boolean reversalX, boolean reversalY) {
            PixelReader reader = srcImage.getPixelReader();
            PixelWriter writer = newImage.getPixelWriter();
            int srcScaleX = (int) srcImage.getWidth();
            int srcScaleY = (int) srcImage.getHeight();
            int newScaleX = (int) newImage.getWidth();
            int newScaleY = (int) newImage.getHeight();
            int srcWidth = (int) (width * srcScaleX);
            int srcHeight = (int) (height * srcScaleY);

            for (int x = 0; x < srcWidth; x++)
                for (int y = 0; y < srcHeight; y++)
                    writer.setArgb((int) (toX * newScaleX + x), (int) (toY * newScaleY + y),
                            reader.getArgb((int) (srcX * srcScaleX + (reversalX ? srcWidth - x - 1 : x)),
                                    (int) (srcY * srcScaleY + (reversalY ? srcHeight - y - 1 : y))));
        }

    }

    public static boolean isNoRequest(Image image) {
        return image.getRequestedWidth() == 0 && image.getRequestedHeight() == 0;
    }

    public static boolean isSkin(Image image) {
        return image.getWidth() % 64 == 0 && image.getWidth() / 64 > 0 &&
                (image.getHeight() == image.getWidth() / 2 || image.getHeight() == image.getWidth());
    }

    public static Image x32Tox64(Image srcSkin) {
        if (srcSkin.getHeight() == srcSkin.getWidth())
            return srcSkin;

        WritableImage newSkin = new WritableImage((int) srcSkin.getWidth(), (int) srcSkin.getHeight() * 2);
        PixelCopier copier = new PixelCopier(srcSkin, newSkin);
        // HEAD & HAT
        copier.copy(0 / 64F, 0 / 32F, 0 / 64F, 0 / 64F, 64 / 64F, 16 / 32F);
        // LEFT-LEG
        x32Tox64(copier, 0 / 64F, 16 / 32F, 16 / 64F, 48 / 64F, 4 / 64F, 12 / 32F, 4 / 64F);
        // RIGHT-LEG
        copier.copy(0 / 64F, 16 / 32F, 0 / 64F, 16 / 64F, 16 / 64F, 16 / 32F);
        // BODY
        copier.copy(16 / 64F, 16 / 32F, 16 / 64F, 16 / 64F, 24 / 64F, 16 / 32F);
        // LEFT-ARM
        x32Tox64(copier, 40 / 64F, 16 / 32F, 32 / 64F, 48 / 64F, 4 / 64F, 12 / 32F, 4 / 64F);
        // RIGHT-ARM
        copier.copy(40 / 64F, 16 / 32F, 40 / 64F, 16 / 64F, 16 / 64F, 16 / 32F);

        return newSkin;
    }

    static void x32Tox64(PixelCopier copier, float srcX, float srcY, float toX, float toY, float width, float height, float depth) {
        // TOP
        copier.copy(srcX + depth, srcY, toX + depth, toY, width, depth * 2, true, false);
        // BOTTOM
        copier.copy(srcX + depth + width, srcY, toX + depth + width, toY, width, depth * 2, true, false);
        // INS
        copier.copy(srcX, srcY + depth * 2, toX + width + depth, toY + depth, depth, height, true, false);
        // OUTS
        copier.copy(srcX + width + depth, srcY + depth * 2, toX, toY + depth, depth, height, true, false);
        // FRONT
        copier.copy(srcX + depth, srcY + depth * 2, toX + depth, toY + depth, width, height, true, false);
        // BACK
        copier.copy(srcX + width + depth * 2, srcY + depth * 2, toX + width + depth * 2, toY + depth, width, height, true, false);
    }

    public static Image enlarge(Image srcSkin, int multipleX, int multipleY) {
        WritableImage newSkin = new WritableImage((int) srcSkin.getWidth() * multipleX, (int) srcSkin.getHeight() * multipleY);
        PixelReader reader = srcSkin.getPixelReader();
        PixelWriter writer = newSkin.getPixelWriter();

        for (int x = 0, lenX = (int) srcSkin.getWidth(); x < lenX; x++)
            for (int y = 0, lenY = (int) srcSkin.getHeight(); y < lenY; y++)
                for (int mx = 0; mx < multipleX; mx++)
                    for (int my = 0; my < multipleY; my++) {
                        int argb = reader.getArgb(x, y);
                        writer.setArgb(x * multipleX + mx, y * multipleY + my, argb);
                    }

        return newSkin;
    }

}
