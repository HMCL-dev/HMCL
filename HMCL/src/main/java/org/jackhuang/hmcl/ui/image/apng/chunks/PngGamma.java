// Copy from https://github.com/aellerton/japng
// Licensed under the Apache License, Version 2.0.

package org.jackhuang.hmcl.ui.image.apng.chunks;

import java.io.DataInputStream;
import java.io.IOException;

/**
 * A PngGamma object represents data parsed from a ``gAMA`` chunk.
 */
public record PngGamma(int imageGamma) {

    public static PngGamma from(DataInputStream dis) throws IOException {
        return new PngGamma(dis.readInt());
    }
}
