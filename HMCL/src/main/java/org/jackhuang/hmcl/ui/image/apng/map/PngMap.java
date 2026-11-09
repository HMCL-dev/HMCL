// Copy from https://github.com/aellerton/japng
// Licensed under the Apache License, Version 2.0.

package org.jackhuang.hmcl.ui.image.apng.map;

import java.util.List;

/**
 * A PngMap represents a map over an entire single PNG file.
 * <p>
 * WARNING: not sure if this API will remain.
 * </p>
 */
public class PngMap {
    public String source;
    public List<PngChunkMap> chunks;
}
