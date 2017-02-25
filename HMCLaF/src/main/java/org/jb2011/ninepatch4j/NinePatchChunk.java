/*
 * Decompiled with CFR 0_118.
 */
package org.jb2011.ninepatch4j;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/*
 * This class specifies class file version 49.0 but uses Java 6 signatures.  Assumed Java 6.
 */
public class NinePatchChunk implements Serializable {

    private static final long serialVersionUID = -7353439224505296217L;
    private static final int[] sPaddingRect = new int[4];
    private boolean mVerticalStartWithPatch;
    private boolean mHorizontalStartWithPatch;
    private List<Rectangle> mFixed;
    private List<Rectangle> mPatches;
    private List<Rectangle> mHorizontalPatches;
    private List<Rectangle> mVerticalPatches;
    private Pair<Integer> mHorizontalPadding;
    private Pair<Integer> mVerticalPadding;

    public static NinePatchChunk create(BufferedImage image) {
        NinePatchChunk chunk = new NinePatchChunk();
        chunk.findPatches(image);
        return chunk;
    }

    public void draw(BufferedImage image, Graphics2D graphics2D, int x, int y, int scaledWidth, int scaledHeight, int destDensity, int srcDensity) {
        boolean scaling;
        boolean bl = scaling = destDensity != srcDensity && destDensity != 0 && srcDensity != 0;
        if (scaling)
            try {
                graphics2D = (Graphics2D) graphics2D.create();
                float densityScale = (float) destDensity / (float) srcDensity;
                graphics2D.translate(x, y);
                graphics2D.scale(densityScale, densityScale);
                scaledWidth = (int) ((float) scaledWidth / densityScale);
                scaledHeight = (int) ((float) scaledHeight / densityScale);
                y = 0;
                x = 0;
                this.draw(image, graphics2D, x, y, scaledWidth, scaledHeight);
            } finally {
                graphics2D.dispose();
            }
        else
            this.draw(image, graphics2D, x, y, scaledWidth, scaledHeight);
    }

    private void draw(BufferedImage image, Graphics2D graphics2D, int x, int y, int scaledWidth, int scaledHeight) {
        if (scaledWidth <= 1 || scaledHeight <= 1)
            return;
        Graphics2D g = (Graphics2D) graphics2D.create();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        try {
            if (this.mPatches.isEmpty()) {
                g.drawImage(image, x, y, scaledWidth, scaledHeight, null);
                return;
            }
            g.translate(x, y);
            y = 0;
            x = 0;
            DrawingData data = this.computePatches(scaledWidth, scaledHeight);
            int fixedIndex = 0;
            int horizontalIndex = 0;
            int verticalIndex = 0;
            int patchIndex = 0;
            float vWeightSum = 1.0f;
            float vRemainder = data.mRemainderVertical;
            boolean vStretch = this.mVerticalStartWithPatch;
            while (y < scaledHeight - 1) {
                boolean hStretch = this.mHorizontalStartWithPatch;
                int height = 0;
                float vExtra = 0.0f;
                float hWeightSum = 1.0f;
                float hRemainder = data.mRemainderHorizontal;
                while (x < scaledWidth - 1) {
                    float extra;
                    int width;
                    Rectangle r;
                    if (!vStretch) {
                        if (hStretch) {
                            r = this.mHorizontalPatches.get(horizontalIndex++);
                            extra = (float) r.width / data.mHorizontalPatchesSum;
                            width = (int) (extra * hRemainder / hWeightSum);
                            hWeightSum -= extra;
                            hRemainder -= (float) width;
                            g.drawImage(image, x, y, x + width, y + r.height, r.x, r.y, r.x + r.width, r.y + r.height, null);
                            x += width;
                        } else {
                            r = this.mFixed.get(fixedIndex++);
                            g.drawImage(image, x, y, x + r.width, y + r.height, r.x, r.y, r.x + r.width, r.y + r.height, null);
                            x += r.width;
                        }
                        height = r.height;
                    } else if (hStretch) {
                        r = this.mPatches.get(patchIndex++);
                        vExtra = (float) r.height / data.mVerticalPatchesSum;
                        height = (int) (vExtra * vRemainder / vWeightSum);
                        extra = (float) r.width / data.mHorizontalPatchesSum;
                        width = (int) (extra * hRemainder / hWeightSum);
                        hWeightSum -= extra;
                        hRemainder -= (float) width;
                        g.drawImage(image, x, y, x + width, y + height, r.x, r.y, r.x + r.width, r.y + r.height, null);
                        x += width;
                    } else {
                        r = this.mVerticalPatches.get(verticalIndex++);
                        vExtra = (float) r.height / data.mVerticalPatchesSum;
                        height = (int) (vExtra * vRemainder / vWeightSum);
                        g.drawImage(image, x, y, x + r.width, y + height, r.x, r.y, r.x + r.width, r.y + r.height, null);
                        x += r.width;
                    }
                    boolean bl = hStretch = !hStretch;
                }
                x = 0;
                y += height;
                if (vStretch) {
                    vWeightSum -= vExtra;
                    vRemainder -= (float) height;
                }
                boolean bl = vStretch = !vStretch;
            }
        } finally {
            g.dispose();
        }
    }

    public void getPadding(int[] padding) {
        padding[0] = this.mHorizontalPadding.mFirst;
        padding[2] = this.mHorizontalPadding.mSecond;
        padding[1] = this.mVerticalPadding.mFirst;
        padding[3] = this.mVerticalPadding.mSecond;
    }

    public int[] getPadding() {
        this.getPadding(sPaddingRect);
        return sPaddingRect;
    }

    private DrawingData computePatches(int scaledWidth, int scaledHeight) {
        int start;
        DrawingData data = new DrawingData();
        boolean measuredWidth = false;
        boolean endRow = true;
        int remainderHorizontal = 0;
        int remainderVertical = 0;
        if (this.mFixed.size() > 0) {
            start = this.mFixed.get((int) 0).y;
            for (Rectangle rect : this.mFixed) {
                if (rect.y > start) {
                    endRow = true;
                    measuredWidth = true;
                }
                if (!measuredWidth)
                    remainderHorizontal += rect.width;
                if (!endRow)
                    continue;
                remainderVertical += rect.height;
                endRow = false;
                start = rect.y;
            }
        }
        data.mRemainderHorizontal = scaledWidth - remainderHorizontal;
        data.mRemainderVertical = scaledHeight - remainderVertical;
        data.mHorizontalPatchesSum = 0.0f;
        if (this.mHorizontalPatches.size() > 0) {
            start = -1;
            for (Rectangle rect : this.mHorizontalPatches) {
                if (rect.x <= start)
                    continue;
                DrawingData drawingData = data;
                drawingData.mHorizontalPatchesSum = drawingData.mHorizontalPatchesSum + (float) rect.width;
                start = rect.x;
            }
        } else {
            start = -1;
            for (Rectangle rect : this.mPatches) {
                if (rect.x <= start)
                    continue;
                DrawingData drawingData = data;
                drawingData.mHorizontalPatchesSum = drawingData.mHorizontalPatchesSum + (float) rect.width;
                start = rect.x;
            }
        }
        data.mVerticalPatchesSum = 0.0f;
        if (this.mVerticalPatches.size() > 0) {
            start = -1;
            for (Rectangle rect : this.mVerticalPatches) {
                if (rect.y <= start)
                    continue;
                DrawingData drawingData = data;
                drawingData.mVerticalPatchesSum = drawingData.mVerticalPatchesSum + (float) rect.height;
                start = rect.y;
            }
        } else {
            start = -1;
            for (Rectangle rect : this.mPatches) {
                if (rect.y <= start)
                    continue;
                DrawingData drawingData = data;
                drawingData.mVerticalPatchesSum = drawingData.mVerticalPatchesSum + (float) rect.height;
                start = rect.y;
            }
        }
        return data;
    }

    private void findPatches(BufferedImage image) {
        int width = image.getWidth() - 2;
        int height = image.getHeight() - 2;
        int[] row = null;
        int[] column = null;
        row = GraphicsUtilities.getPixels(image, 1, 0, width, 1, row);
        column = GraphicsUtilities.getPixels(image, 0, 1, 1, height, column);
        boolean[] result = new boolean[1];
        Pair<List<Pair<Integer>>> left = this.getPatches(column, result);
        this.mVerticalStartWithPatch = result[0];
        result = new boolean[1];
        Pair<List<Pair<Integer>>> top = this.getPatches(row, result);
        this.mHorizontalStartWithPatch = result[0];
        this.mFixed = this.getRectangles((List) left.mFirst, (List) top.mFirst);
        this.mPatches = this.getRectangles((List) left.mSecond, (List) top.mSecond);
        if (this.mFixed.size() > 0) {
            this.mHorizontalPatches = this.getRectangles((List) left.mFirst, (List) top.mSecond);
            this.mVerticalPatches = this.getRectangles((List) left.mSecond, (List) top.mFirst);
        } else if (((List) top.mFirst).size() > 0) {
            this.mHorizontalPatches = new ArrayList<>(0);
            this.mVerticalPatches = this.getVerticalRectangles(height, (List) top.mFirst);
        } else if (((List) left.mFirst).size() > 0) {
            this.mHorizontalPatches = this.getHorizontalRectangles(width, (List) left.mFirst);
            this.mVerticalPatches = new ArrayList<>(0);
        } else {
            this.mVerticalPatches = new ArrayList<>(0);
            this.mHorizontalPatches = this.mVerticalPatches;
        }
        row = GraphicsUtilities.getPixels(image, 1, height + 1, width, 1, row);
        column = GraphicsUtilities.getPixels(image, width + 1, 1, 1, height, column);
        top = this.getPatches(row, result);
        this.mHorizontalPadding = this.getPadding((List) top.mFirst);
        left = this.getPatches(column, result);
        this.mVerticalPadding = this.getPadding((List) left.mFirst);
    }

    private List<Rectangle> getVerticalRectangles(int imageHeight, List<Pair<Integer>> topPairs) {
        ArrayList<Rectangle> rectangles = new ArrayList<>();
        for (Pair<Integer> top : topPairs) {
            int x = top.mFirst;
            int width = top.mSecond - top.mFirst;
            rectangles.add(new Rectangle(x, 0, width, imageHeight));
        }
        return rectangles;
    }

    private List<Rectangle> getHorizontalRectangles(int imageWidth, List<Pair<Integer>> leftPairs) {
        ArrayList<Rectangle> rectangles = new ArrayList<>();
        for (Pair<Integer> left : leftPairs) {
            int y = left.mFirst;
            int height = left.mSecond - left.mFirst;
            rectangles.add(new Rectangle(0, y, imageWidth, height));
        }
        return rectangles;
    }

    private Pair<Integer> getPadding(List<Pair<Integer>> pairs) {
        if (pairs.isEmpty())
            return new Pair<>(0, 0);
        if (pairs.size() == 1) {
            if (pairs.get((int) 0).mFirst == 0)
                return new Pair<>(pairs.get((int) 0).mSecond - pairs.get((int) 0).mFirst, 0);
            return new Pair<>(0, pairs.get((int) 0).mSecond - pairs.get((int) 0).mFirst);
        }
        int index = pairs.size() - 1;
        return new Pair<>(pairs.get((int) 0).mSecond - pairs.get((int) 0).mFirst, pairs.get((int) index).mSecond - pairs.get((int) index).mFirst);
    }

    private List<Rectangle> getRectangles(List<Pair<Integer>> leftPairs, List<Pair<Integer>> topPairs) {
        ArrayList<Rectangle> rectangles = new ArrayList<>();
        for (Pair<Integer> left : leftPairs) {
            int y = left.mFirst;
            int height = left.mSecond - left.mFirst;
            for (Pair<Integer> top : topPairs) {
                int x = top.mFirst;
                int width = top.mSecond - top.mFirst;
                rectangles.add(new Rectangle(x, y, width, height));
            }
        }
        return rectangles;
    }

    private Pair<List<Pair<Integer>>> getPatches(int[] pixels, boolean[] startWithPatch) {
        int lastIndex = 0;
        int lastPixel = pixels[0];
        boolean first = true;
        List<Pair<Integer>> fixed = new ArrayList<>();
        List<Pair<Integer>> patches = new ArrayList<>();
        int i = 0;
        while (i < pixels.length) {
            int pixel = pixels[i];
            if (pixel != lastPixel) {
                if (lastPixel == -16777216) {
                    if (first)
                        startWithPatch[0] = true;
                    patches.add(new Pair<>(lastIndex, i));
                } else
                    fixed.add(new Pair<>(lastIndex, i));
                first = false;
                lastIndex = i;
                lastPixel = pixel;
            }
            ++i;
        }
        if (lastPixel == -16777216) {
            if (first)
                startWithPatch[0] = true;
            patches.add(new Pair<>(lastIndex, pixels.length));
        } else
            fixed.add(new Pair<>(lastIndex, pixels.length));
        if (patches.isEmpty()) {
            patches.add(new Pair<>(1, pixels.length));
            startWithPatch[0] = true;
            fixed.clear();
        }
        return new Pair<>(fixed, patches);
    }

    static final class DrawingData {

        int mRemainderHorizontal;
        int mRemainderVertical;
        float mHorizontalPatchesSum;
        float mVerticalPatchesSum;
    }

    static class Pair<E> implements Serializable {

        private static final long serialVersionUID = -2204108979541762418L;
        E mFirst;
        E mSecond;

        Pair(E first, E second) {
            this.mFirst = first;
            this.mSecond = second;
        }

        @Override
        public String toString() {
            return "Pair[" + this.mFirst + ", " + this.mSecond + "]";
        }
    }

}
