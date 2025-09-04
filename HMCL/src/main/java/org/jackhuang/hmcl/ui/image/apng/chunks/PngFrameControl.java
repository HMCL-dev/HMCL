// Copy from https://github.com/aellerton/japng
// Licensed under the Apache License, Version 2.0.

package org.jackhuang.hmcl.ui.image.apng.chunks;

import org.jackhuang.hmcl.ui.image.apng.map.PngChunkMap;

import java.util.ArrayList;
import java.util.List;

/**
 * A PngFrameControl object contains data parsed from the ``fcTL`` chunk data
 * in an animated PNG File.
 * <p>
 * See https://wiki.mozilla.org/APNG_Specification#.60fcTL.60:_The_Frame_Control_Chunk
 * <pre>
 *     0    sequence_number       (unsigned int)   Sequence number of the animation chunk, starting from 0
 *     4    width                 (unsigned int)   Width of the following frame
 *     8    height                (unsigned int)   Height of the following frame
 *    12    x_offset              (unsigned int)   X position at which to render the following frame
 *    16    y_offset              (unsigned int)   Y position at which to render the following frame
 *    20    delay_num             (unsigned short) Frame delay fraction numerator
 *    22    delay_den             (unsigned short) Frame delay fraction denominator
 *    24    dispose_op            (byte)           Type of frame area disposal to be done after rendering this frame
 *    25    blend_op              (byte)           Type of frame area rendering for this frame
 * </pre>
 * <p>
 * Delay denominator: from spec, "if denominator is zero it should be treated as 100ths of second".
 * <pre>
 * dispose op:
 *    value
 *   0           APNG_DISPOSE_OP_NONE
 *   1           APNG_DISPOSE_OP_BACKGROUND
 *   2           APNG_DISPOSE_OP_PREVIOUS
 *
 * blend op:
 *  value
 *   0       APNG_BLEND_OP_SOURCE
 *   1       APNG_BLEND_OP_OVER
 * </pre>
 */
public class PngFrameControl {
    public final int sequenceNumber;
    public final int width;
    public final int height;
    public final int xOffset;
    public final int yOffset;
    public final short delayNumerator;
    public final short delayDenominator;
    public final byte disposeOp;
    public final byte blendOp;
    List<PngChunkMap> imageChunks = new ArrayList<>(1); // TODO: this may be removed

    public PngFrameControl(int sequenceNumber, int width, int height, int xOffset, int yOffset, short delayNumerator, short delayDenominator, byte disposeOp, byte blendOp) {
        this.sequenceNumber = sequenceNumber;
        this.width = width;
        this.height = height;
        this.xOffset = xOffset;
        this.yOffset = yOffset;
        this.delayNumerator = delayNumerator;
        this.delayDenominator = delayDenominator == 0 ? 100 : delayDenominator; // APNG spec says zero === 100.
        this.disposeOp = disposeOp;
        this.blendOp = blendOp;
    }

    /**
     * @return number of milliseconds to show this frame for
     */
    public int getDelayMilliseconds() {
        if (delayDenominator == 1000) {
            return delayNumerator;
        } else {
            // if denom is 100 then need to multiple by 10
            float f = 1000 / delayDenominator; // 1000/100 -> 10
            return (int) (delayNumerator * f);
        }
    }

    // TODO: can this be removed?
    public void appendImageData(PngChunkMap chunkMap) {
        imageChunks.add(chunkMap);
    }

    // TODO: this may be removed
    public List<PngChunkMap> getImageChunks() {
        return imageChunks;
    }

    @Override
    public String toString() {
        return "PngFrameControl{" +
                "sequenceNumber=" + sequenceNumber +
                ", width=" + width +
                ", height=" + height +
                ", xOffset=" + xOffset +
                ", yOffset=" + yOffset +
                ", delayNumerator=" + delayNumerator +
                ", delayDenominator=" + delayDenominator +
                ", disposeOp=" + disposeOp +
                ", blendOp=" + blendOp +
                '}';
    }

    // mainly for ease in unit testing
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        PngFrameControl that = (PngFrameControl) o;

        if (sequenceNumber != that.sequenceNumber) return false;
        if (width != that.width) return false;
        if (height != that.height) return false;
        if (xOffset != that.xOffset) return false;
        if (yOffset != that.yOffset) return false;
        if (delayNumerator != that.delayNumerator) return false;
        if (delayDenominator != that.delayDenominator) return false;
        if (disposeOp != that.disposeOp) return false;
        return blendOp == that.blendOp;

    }

    @Override
    public int hashCode() {
        int result = sequenceNumber;
        result = 31 * result + width;
        result = 31 * result + height;
        result = 31 * result + xOffset;
        result = 31 * result + yOffset;
        result = 31 * result + (int) delayNumerator;
        result = 31 * result + (int) delayDenominator;
        result = 31 * result + (int) disposeOp;
        result = 31 * result + (int) blendOp;
        return result;
    }
}
