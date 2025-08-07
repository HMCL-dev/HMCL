// Copy from https://github.com/aellerton/japng
// Licensed under the Apache License, Version 2.0.

package org.jackhuang.hmcl.ui.image.apng.argb8888;

import org.jackhuang.hmcl.ui.image.apng.PngConstants;
import org.jackhuang.hmcl.ui.image.apng.PngScanlineBuffer;
import org.jackhuang.hmcl.ui.image.apng.chunks.PngHeader;
import org.jackhuang.hmcl.ui.image.apng.error.PngException;
import org.jackhuang.hmcl.ui.image.apng.error.PngFeatureException;
import org.jackhuang.hmcl.ui.image.apng.error.PngIntegrityException;

/**
 * A series of scanline processor implementations for different input pixel formats,
 * each able to transform the input into ARGB8888 output pixels.
 */
public class Argb8888Processors {

    /**
     * Determine from the header the concrete Argb8888Processor implementation the
     * caller needs to transform the incoming pixels into the ARGB8888 pixel format.
     *
     * @param header         of the image being loaded
     * @param scanlineReader the scanline buffer being used
     * @param bitmap         the destination bitmap
     * @return a concrete Argb8888Processor to transform source pixels into the specific bitmap.
     * @throws PngException if there is a feature not supported or some specification break with the file.
     */
    public static Argb8888ScanlineProcessor from(PngHeader header, PngScanlineBuffer scanlineReader, Argb8888Bitmap bitmap) throws PngException {

        int bytesPerScanline = header.bytesPerRow;
        switch (header.colourType) {
            case PNG_GREYSCALE:
                switch (header.bitDepth) {
                    case 1:
                        return new IndexedColourBits(bytesPerScanline, bitmap, 7, 0x01, PngConstants.SHIFTS_1, Argb8888Palette.forGreyscale(1));
                    case 2:
                        return new IndexedColourBits(bytesPerScanline, bitmap, 3, 0x03, PngConstants.SHIFTS_2, Argb8888Palette.forGreyscale(2));
                    case 4:
                        return new IndexedColourBits(bytesPerScanline, bitmap, 1, 0x0F, PngConstants.SHIFTS_4, Argb8888Palette.forGreyscale(4));
                    case 8:
                        return new Greyscale8(bytesPerScanline, bitmap);
                    case 16:
                        throw new PngFeatureException("Greyscale supports 1, 2, 4, 8 but not 16.");
                    default:
                        throw new PngIntegrityException(String.format("Invalid greyscale bit-depth: %d", header.bitDepth)); // TODO: should be in header parse.
                }

            case PNG_GREYSCALE_WITH_ALPHA:
                switch (header.bitDepth) {
                    case 4:
                        return new Greyscale4Alpha(bytesPerScanline, bitmap);
                    case 8:
                        return new Greyscale8Alpha(bytesPerScanline, bitmap);
                    case 16:
                        return new Greyscale16Alpha(bytesPerScanline, bitmap);
                    default:
                        throw new PngIntegrityException(String.format("Invalid greyscale-with-alpha bit-depth: %d", header.bitDepth)); // TODO: should be in header parse.
                }

            case PNG_INDEXED_COLOUR:
                switch (header.bitDepth) {
                    case 1:
                        return new IndexedColourBits(bytesPerScanline, bitmap, 7, 0x01, PngConstants.SHIFTS_1);
                    case 2:
                        return new IndexedColourBits(bytesPerScanline, bitmap, 3, 0x03, PngConstants.SHIFTS_2);
                    case 4:
                        return new IndexedColourBits(bytesPerScanline, bitmap, 1, 0x0F, PngConstants.SHIFTS_4);
                    case 8:
                        return new IndexedColour8(bytesPerScanline, bitmap);
                    default:
                        throw new PngIntegrityException(String.format("Invalid indexed colour bit-depth: %d", header.bitDepth)); // TODO: should be in header parse.
                }

            case PNG_TRUECOLOUR:
                switch (header.bitDepth) {
                    case 8:
                        return new Truecolour8(bytesPerScanline, bitmap);
                    case 16:
                        return new Truecolour16(bytesPerScanline, bitmap);
                    default:
                        throw new PngIntegrityException(String.format("Invalid truecolour bit-depth: %d", header.bitDepth)); // TODO: should be in header parse.

                }

            case PNG_TRUECOLOUR_WITH_ALPHA:
                switch (header.bitDepth) {
                    case 8:
                        return new Truecolour8Alpha(bytesPerScanline, bitmap);
                    case 16:
                        return new Truecolour16Alpha(bytesPerScanline, bitmap);
                    default:
                        throw new PngIntegrityException(String.format("Invalid truecolour with alpha bit-depth: %d", header.bitDepth)); // TODO: should be in header parse.

                }

            default:
                throw new PngFeatureException("ARGB8888 doesn't support PNG mode " + header.colourType.name());
        }
    }

    /**
     * Transforms 1-, 2-, 4-bit indexed colour source pixels to ARGB8888 pixels.
     */
    public static class IndexedColourBits extends Argb8888ScanlineProcessor {

        private int highBit;
        private int mask;
        private byte[] shifts;

        public IndexedColourBits(int bytesPerScanline, Argb8888Bitmap bitmap, int highBit, int mask, byte[] shifts) {
            this(bytesPerScanline, bitmap, highBit, mask, shifts, null);
        }

        public IndexedColourBits(int bytesPerScanline, Argb8888Bitmap bitmap, int highBit, int mask, byte[] shifts, Argb8888Palette palette) {
            super(bytesPerScanline, bitmap);
            this.highBit = highBit;
            this.mask = mask;
            this.shifts = shifts;
            this.palette = palette;
        }

        @Override
        public void processScanline(byte[] srcBytes, int srcPosition) {
            final int[] destArray = this.bitmap.array;
            final int width = this.bitmap.width;
            int writePosition = this.y * width;
            int lastWritePosition = writePosition + width;
            int bit = highBit;

            while (writePosition < lastWritePosition) {
                //final int index=(srcBytes[srcPosition+x] & masks[bit]) >> shifts[bit];
                //final int index = (srcBytes[srcPosition])
                //final int v = srcBytes[srcPosition];
                final int index = mask & (srcBytes[srcPosition] >> shifts[bit]);

                int dest;
                if (palette != null) {
                    dest = palette.argbArray[index];
                } else {
                    dest = 0;
                }
                destArray[writePosition++] = dest;
                if (bit == 0) {
                    srcPosition++;
                    bit = highBit;
                } else {
                    bit--;
                }
            }
            this.y++;
        }

        @Override
        public Argb8888ScanlineProcessor clone(int bytesPerRow, Argb8888Bitmap bitmap) {
            return new IndexedColourBits(bytesPerRow, bitmap, highBit, mask, shifts, palette);
        }
    }

    /**
     * Special case implementation to transform 8-bit indexed colour source pixels to ARGB8888 pixels.
     */
    public static class IndexedColour8 extends Argb8888ScanlineProcessor {

        public IndexedColour8(int bytesPerScanline, Argb8888Bitmap bitmap) {
            super(bytesPerScanline, bitmap);
        }

        @Override
        public void processScanline(byte[] srcBytes, int srcPosition) {
            final int[] destArray = this.bitmap.array;
            final int width = this.bitmap.width;
            int writePosition = this.y * width;
            for (int x = 0; x < width; x++) {

                final int index = 0xff & srcBytes[srcPosition++]; // TODO: need to use transparency and background chunks

                int dest;
                if (palette != null) {
                    dest = palette.argbArray[index];
                } else {
                    dest = 0;
                }
                destArray[writePosition++] = dest;
            }
            this.y++;
        }

        @Override
        public Argb8888ScanlineProcessor clone(int bytesPerRow, Argb8888Bitmap bitmap) {
            return new IndexedColour8(bytesPerRow, bitmap);
        }
    }

    /**
     * Transforms 4-bit greyscale with alpha source pixels to ARGB8888 pixels.
     */
    public static class Greyscale4Alpha extends Argb8888ScanlineProcessor {

        public Greyscale4Alpha(int bytesPerScanline, Argb8888Bitmap bitmap) {
            super(bytesPerScanline, bitmap);
        }

        @Override
        public void processScanline(byte[] srcBytes, int srcPosition) {
            final int[] destArray = this.bitmap.array;
            final int width = this.bitmap.width;
            int writePosition = this.y * width;
            for (int x = 0; x < width; x++) {
                final int v = srcBytes[srcPosition++];
                final int k = PngConstants.GREY_PALETTE_16[0x0f & (v >> 4)];
                final int a = PngConstants.GREY_PALETTE_16[0x0f & v];
                final int c = a << 24 | k << 16 | k << 8 | k;
                destArray[writePosition++] = c;
            }
            this.y++;
        }

        @Override
        public Argb8888ScanlineProcessor clone(int bytesPerRow, Argb8888Bitmap bitmap) {
            return new Greyscale4Alpha(bytesPerRow, bitmap);
        }
    }

    /**
     * Transforms 8-bit greyscale source pixels to ARGB8888 pixels.
     */
    public static class Greyscale8 extends Argb8888ScanlineProcessor {

        boolean haveTransparent = false;
        int transparentSample = 0;

        public Greyscale8(int bytesPerScanline, Argb8888Bitmap bitmap) {
            super(bytesPerScanline, bitmap);
        }

        @Override
        public void processScanline(byte[] srcBytes, int srcPosition) {
            final int[] destArray = this.bitmap.array;
            final int width = this.bitmap.width;
            final int alpha = 0xff000000; // No alpha in the image means every pixel must be fully opaque
            int writePosition = this.y * width;
            for (int x = 0; x < width; x++) {
                final int sample = 0xff & srcBytes[srcPosition++];
                final int k = (haveTransparent && sample == transparentSample) ? transparentSample : sample;
                final int c = alpha | k << 16 | k << 8 | k;
                destArray[writePosition++] = c;
            }
            this.y++;
        }

        @Override
        public void processTransparentGreyscale(byte k1, byte k0) {
            // According to below, when image is less than 16-bits per pixel, use least significant byte
            // http://www.w3.org/TR/PNG/#11transinfo
            haveTransparent = true;
            transparentSample = 0xff & k0; // NOT k1 according to http://www.w3.org/TR/PNG/#11transinfo
        }

        @Override
        public Argb8888ScanlineProcessor clone(int bytesPerRow, Argb8888Bitmap bitmap) {
            return new Greyscale8(bytesPerRow, bitmap);
        }
    }

    /**
     * Transforms 8-bit greyscale with alpha source pixels to ARGB8888 pixels.
     */
    public static class Greyscale8Alpha extends Argb8888ScanlineProcessor {

        public Greyscale8Alpha(int bytesPerScanline, Argb8888Bitmap bitmap) {
            super(bytesPerScanline, bitmap);
        }

        @Override
        public void processScanline(byte[] srcBytes, int srcPosition) {
            final int[] destArray = this.bitmap.array;
            final int width = this.bitmap.width;
            int writePosition = this.y * width;
            for (int x = 0; x < width; x++) {
                final int k = 0xff & srcBytes[srcPosition++];
                final int a = 0xff & srcBytes[srcPosition++];
                final int c = a << 24 | k << 16 | k << 8 | k;
                destArray[writePosition++] = c;
            }
            this.y++;
        }

        @Override
        public Argb8888ScanlineProcessor clone(int bytesPerRow, Argb8888Bitmap bitmap) {
            return new Greyscale8Alpha(bytesPerRow, bitmap);
        }
    }

    /**
     * Transforms 16-bit greyscale with alpha source pixels to ARGB8888 pixels.
     */
    public static class Greyscale16Alpha extends Argb8888ScanlineProcessor {

        public Greyscale16Alpha(int bytesPerScanline, Argb8888Bitmap bitmap) {
            super(bytesPerScanline, bitmap);
        }

        @Override
        public void processScanline(byte[] srcBytes, int srcPosition) {
            final int[] destArray = this.bitmap.array;
            final int width = this.bitmap.width;
            int writePosition = this.y * width;
            for (int x = 0; x < width; x++) {
                final int k = 0xff & srcBytes[srcPosition++];
                srcPosition++; // skip the least-significant byte of the grey
                final int a = 0xff & srcBytes[srcPosition++];
                srcPosition++; // skip the least-significant byte of the alpha
                final int c = a << 24 | k << 16 | k << 8 | k;
                destArray[writePosition++] = c;
            }
            this.y++;
        }

        @Override
        public Argb8888ScanlineProcessor clone(int bytesPerRow, Argb8888Bitmap bitmap) {
            return new Greyscale16Alpha(bytesPerRow, bitmap);
        }
    }

    /**
     * Transforms true-colour (RGB) 8-bit source pixels to ARGB8888 pixels.
     */
    public static class Truecolour8 extends Argb8888ScanlineProcessor {
        public Truecolour8(int bytesPerScanline, Argb8888Bitmap bitmap) {
            super(bytesPerScanline, bitmap);
        }

        @Override
        public void processScanline(byte[] srcBytes, int srcPosition) {
            final int[] destArray = this.bitmap.array;
            final int width = this.bitmap.width;
            final int alpha = 0xff000000; // No alpha in the image means every pixel must be fully opaque
            int writePosition = this.y * width;
            for (int x = 0; x < width; x++) {
                final int r = 0xff & srcBytes[srcPosition++];
                final int g = 0xff & srcBytes[srcPosition++];
                final int b = 0xff & srcBytes[srcPosition++];
                final int c = alpha | r << 16 | g << 8 | b;
                destArray[writePosition++] = c;
            }
            this.y++;
        }

        @Override
        public Argb8888ScanlineProcessor clone(int bytesPerRow, Argb8888Bitmap bitmap) {
            return new Truecolour8(bytesPerRow, bitmap);
        }
    }

    /**
     * Transforms true-colour with alpha (RGBA) 8-bit source pixels to ARGB8888 pixels.
     */
    public static class Truecolour8Alpha extends Argb8888ScanlineProcessor {

        public Truecolour8Alpha(int bytesPerScanline, Argb8888Bitmap bitmap) {
            super(bytesPerScanline, bitmap);
        }

        @Override
        public void processScanline(byte[] srcBytes, int srcPosition) {
            final int[] destArray = this.bitmap.array;
            final int width = this.bitmap.width;
            int writePosition = this.y * width;
            //srcPosition++; // skip filter byte
            for (int x = 0; x < width; x++) {
                final int r = 0xff & srcBytes[srcPosition++];
                final int g = 0xff & srcBytes[srcPosition++];
                final int b = 0xff & srcBytes[srcPosition++];
                final int a = 0xff & srcBytes[srcPosition++];
                final int c = a << 24 | r << 16 | g << 8 | b;
                destArray[writePosition++] = c;
            }
            this.y++;
        }

        @Override
        public Argb8888ScanlineProcessor clone(int bytesPerRow, Argb8888Bitmap bitmap) {
            return new Truecolour8Alpha(bytesPerRow, bitmap);
        }
    }

    /**
     * Transforms true-colour (RGB) 16-bit source pixels to ARGB8888 pixels.
     */
    public static class Truecolour16 extends Argb8888ScanlineProcessor {
        public Truecolour16(int bytesPerScanline, Argb8888Bitmap bitmap) {
            super(bytesPerScanline, bitmap);
        }

        @Override
        public void processScanline(byte[] srcBytes, int srcPosition) {
            final int[] destArray = this.bitmap.array;
            final int width = this.bitmap.width;
            final int alpha = 0xff000000; // No alpha in the image means every pixel must be fully opaque
            int writePosition = this.y * width;
            //srcPosition++; // skip filter byte
            for (int x = 0; x < width; x++) {
                final int r = 0xff & srcBytes[srcPosition];
                srcPosition += 2; // skip the byte just read and the least significant byte of the next
                final int g = 0xff & srcBytes[srcPosition];
                srcPosition += 2; // ditto
                final int b = 0xff & srcBytes[srcPosition];
                srcPosition += 2; // ditto again
                final int c = alpha | r << 16 | g << 8 | b;
                destArray[writePosition++] = c;
            }
            this.y++;
        }

        @Override
        public Argb8888ScanlineProcessor clone(int bytesPerRow, Argb8888Bitmap bitmap) {
            return new Truecolour16(bytesPerRow, bitmap);
        }
    }

    /**
     * Transforms true-colour with alpha (RGBA) 16-bit source pixels to ARGB8888 pixels.
     * <p>
     * Note that the simpler method of resampling the colour is done, namely discard the LSB.
     */
    public static class Truecolour16Alpha extends Argb8888ScanlineProcessor {
        public Truecolour16Alpha(int bytesPerScanline, Argb8888Bitmap bitmap) {
            super(bytesPerScanline, bitmap);
        }

        @Override
        public void processScanline(byte[] srcBytes, int srcPosition) {
            final int[] destArray = this.bitmap.array;
            final int width = this.bitmap.width;
            //final int alpha = 0xff000000; // No alpha in the image means every pixel must be fully opaque
            int writePosition = this.y * width;
            //srcPosition++; // skip filter byte
            for (int x = 0; x < width; x++) {
                final int r = 0xff & srcBytes[srcPosition];
                srcPosition += 2; // skip the byte just read and the least significant byte of the next
                final int g = 0xff & srcBytes[srcPosition];
                srcPosition += 2; // ditto
                final int b = 0xff & srcBytes[srcPosition];
                srcPosition += 2; // ditto again
                final int alpha = 0xff & srcBytes[srcPosition];
                srcPosition += 2; // skip the byte just read and the least significant byte of the next
                final int c = alpha << 24 | r << 16 | g << 8 | b;
                destArray[writePosition++] = c;
            }
            this.y++;
        }

        @Override
        public Argb8888ScanlineProcessor clone(int bytesPerRow, Argb8888Bitmap bitmap) {
            return new Truecolour16Alpha(bytesPerRow, bitmap);
        }
    }

    private Argb8888Processors() {
    }
}
