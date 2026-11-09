// Copy from https://github.com/aellerton/japng
// Licensed under the Apache License, Version 2.0.

package org.jackhuang.hmcl.ui.image.apng;

import org.jackhuang.hmcl.ui.image.apng.argb8888.*;
import org.jackhuang.hmcl.ui.image.apng.error.PngException;
import org.jackhuang.hmcl.ui.image.apng.map.PngMap;
import org.jackhuang.hmcl.ui.image.apng.map.PngMapReader;
import org.jackhuang.hmcl.ui.image.apng.reader.DefaultPngChunkReader;
import org.jackhuang.hmcl.ui.image.apng.reader.PngChunkProcessor;
import org.jackhuang.hmcl.ui.image.apng.reader.PngReadHelper;
import org.jackhuang.hmcl.ui.image.apng.reader.PngReader;
import org.jackhuang.hmcl.ui.image.apng.util.PngContainer;
import org.jackhuang.hmcl.ui.image.apng.util.PngContainerBuilder;

import java.io.InputStream;

/**
 * Convenient one liners for loading PNG images.
 */
public class Png {

    /**
     * Read the provided stream and produce a PngMap of the data.
     *
     * @param is         stream to read from
     * @param sourceName optional name, mainly for debugging.
     * @return PngMap of the data.
     * @throws PngException
     */
    public static PngMap readMap(InputStream is, String sourceName) throws PngException {
        return PngReadHelper.read(is, new PngMapReader(sourceName));
    }

    public static PngContainer readContainer(InputStream is) throws PngException {
        return PngReadHelper.read(is, new DefaultPngChunkReader<>(new PngContainerBuilder()));
    }

    public static <ResultT> ResultT read(InputStream is, PngReader<ResultT> reader) throws PngException {
        return PngReadHelper.read(is, reader);
    }

    public static <ResultT> ResultT read(InputStream is, PngChunkProcessor<ResultT> processor) throws PngException {
        return PngReadHelper.read(is, new DefaultPngChunkReader<>(processor));
    }

    public static Argb8888Bitmap readArgb8888Bitmap(InputStream is) throws PngException {
        Argb8888Processor<Argb8888Bitmap> processor = new Argb8888Processor<>(new DefaultImageArgb8888Director());
        return PngReadHelper.read(is, new DefaultPngChunkReader<>(processor));
    }

    public static Argb8888BitmapSequence readArgb8888BitmapSequence(InputStream is) throws PngException {
        Argb8888Processor<Argb8888BitmapSequence> processor = new Argb8888Processor<>(new Argb8888BitmapSequenceDirector());
        return PngReadHelper.read(is, new DefaultPngChunkReader<>(processor));
    }
}
