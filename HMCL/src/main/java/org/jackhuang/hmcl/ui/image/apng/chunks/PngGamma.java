package org.jackhuang.hmcl.ui.image.apng.chunks;

import java.io.DataInputStream;
import java.io.IOException;

/**
 * A PngGamma object represents data parsed from a ``gAMA`` chunk.
 */
public class PngGamma {
    public final int imageGamma;

    public PngGamma(int imageGamma) {
        this.imageGamma = imageGamma;
    }

    public static PngGamma from(DataInputStream dis) throws IOException {
        return new PngGamma(dis.readInt());
    }
}
