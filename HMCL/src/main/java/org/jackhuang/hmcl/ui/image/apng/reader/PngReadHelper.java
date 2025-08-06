// Copy from https://github.com/aellerton/japng
// Licensed under the Apache License, Version 2.0.

package org.jackhuang.hmcl.ui.image.apng.reader;

import org.jackhuang.hmcl.ui.image.apng.PngColourType;
import org.jackhuang.hmcl.ui.image.apng.PngConstants;
import org.jackhuang.hmcl.ui.image.apng.error.PngException;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

/**
 * PNG reading support functions.
 */
public class PngReadHelper {

    /**
     * Return true if the next 8 bytes of the InputStream match the
     * standard 8 byte PNG Signature, and false if they do not match.
     * <p>
     * If the stream ends before the signature is read an EOFExceptoin is thrown.
     * </p><p>
     * Note that no temporary buffer is allocated.
     * </p>
     *
     * @param is Stream to read the 8-byte PNG signature from
     * @throws IOException in the case of any IO exception, including EOF.
     */
    public static boolean readSignature(InputStream is) throws IOException {
        for (int i = 0; i < PngConstants.LENGTH_SIGNATURE; i++) {
            int b = is.read();
            if (b < 0) {
                throw new EOFException();
            }
            if ((byte) b != PngConstants.BYTES_SIGNATURE[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Reads a given InputStream using the PngReader to process all chunks until the file is
     * finished, then returning the result from the PngReader.
     *
     * @param is        stream to read
     * @param reader    reads and delegates processing of all chunks
     * @param <ResultT> result of the processing
     * @return result of the processing of the InputStream.
     * @throws PngException
     */
    public static <ResultT> ResultT read(InputStream is, PngReader<ResultT> reader) throws PngException {
        try {
            if (!PngReadHelper.readSignature(is)) {
                throw new PngException(PngConstants.ERROR_NOT_PNG, "Failed to read PNG signature");
            }

//            PngAtOnceSource source = PngAtOnceSource.from(is);//, sourceName);
            PngSource source = new PngStreamSource(is);
            boolean finished = false;

            while (!finished) {
                int length = source.readInt();
                int code = source.readInt();
                finished = reader.readChunk(source, code, length);
            }

            if (source.available() > 0) { // Should trailing data after IEND always be error or can configure as warning?
                throw new PngException(PngConstants.ERROR_EOF_EXPECTED, String.format("Completed IEND but %d byte(s) remain", source.available()));
            }

            reader.finishedChunks(source);

            return reader.getResult();

        } catch (EOFException e) {
            throw new PngException(PngConstants.ERROR_EOF, "Unexpected EOF", e);
        } catch (IOException e) {
            throw new PngException(PngConstants.ERROR_UNKNOWN_IO_FAILURE, e.getMessage(), e);
        }

    }

    /**
     * Number of bytes per row is key to processing scanlines.
     * <p>
     * TODO: should this by on the header?
     */
    public static int calculateBytesPerRow(int pixelsPerRow, byte bitDepth, PngColourType colourType, byte interlaceMethod) {
        if (interlaceMethod != 0) {
            throw new IllegalStateException("Interlaced images not yet supported");

        } else {
            int numComponentsPerPixel = colourType.componentsPerPixel; // or "channels". e.g. "gray and alpha" means two components.
            int bitsPerComponent = bitDepth; // e.g. "4" means 4 bits for gray, 4 bits for alpha
            int bitsPerPixel = bitsPerComponent * numComponentsPerPixel;  // e.g. total of 8 bits per pixel
            int bitsPerRow = bitsPerPixel * pixelsPerRow;

            //?? use that (bitDepth+7)>>3 ... thing?
            //   unsigned int bpp = (row_info->pixel_depth + 7) >> 3; // libpng

            // If there are less than 8 bits per pixel, then ensure the last byte of the row is padded.
            int bytesPerRow = bitsPerRow / 8 + ((0 == (bitsPerRow % 8)) ? 0 : (8 - bitsPerRow % 8));
            return 1 + bytesPerRow; // need 1 byte for filter code
        }
    }

    private PngReadHelper() {
    }
}
