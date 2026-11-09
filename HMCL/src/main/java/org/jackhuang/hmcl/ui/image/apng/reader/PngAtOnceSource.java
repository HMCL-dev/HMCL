// Copy from https://github.com/aellerton/japng
// Licensed under the Apache License, Version 2.0.

package org.jackhuang.hmcl.ui.image.apng.reader;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * A simple way to process a PNG file is to read the entire thing into memory.
 * <p>
 * Very bad for very large images (500Mb?)? Yes.
 * But simple for smaller (say <10Mb?) images? Also yes.
 * I'd certainly like to make a lean alternative that processes bytes without reading
 * the whole thing into memory, but it can come later. Patches welcome.
 * <p>
 * WARNING: I'm not sure I'll keep this. I may remove it and do everything with
 * a BufferedInputStream + DataInputStream.
 * </p>
 */
public class PngAtOnceSource implements PngSource {

    final byte[] bytes;
    //private String sourceDescription;
    private final ByteArrayInputStream bis;
    private final DataInputStream dis;

    public PngAtOnceSource(byte[] bytes) { //}, String sourceDescription) {
        this.bytes = bytes;
        //this.sourceDescription = sourceDescription;
        this.bis = new ByteArrayInputStream(this.bytes); // never closed because nothing to do for ByteArrayInputStream
        this.dis = new DataInputStream(this.bis); // never closed because underlying stream doesn't need to be closed.
    }

    @Override
    public int getLength() {
        return bytes == null ? 0 : bytes.length;
    }

    @Override
    public boolean supportsByteAccess() {
        return true;
    }

    @Override
    public byte[] getBytes() throws IOException {
        return bytes;
    }

    @Override
    public byte readByte() throws IOException {
        return dis.readByte();
    }

    @Override
    public short readUnsignedShort() throws IOException {
        return (short) dis.readUnsignedShort();
    }

    @Override
    public int readInt() throws IOException {
        return dis.readInt();
    }

    @Override
    public long skip(int chunkLength) throws IOException {
        return dis.skip(chunkLength);
    }

    @Override
    public int tell() {
        return bytes.length - bis.available();
    }

    @Override
    public int available() {
        return bis.available();
    }

//    @Override
//    public String getSourceDescription() {
//        return sourceDescription;
//    }

//    @Override
//    public ByteArrayInputStream getBis() {
//        return bis;
//    }

    @Override
    public DataInputStream getDis() {
        return dis;
    }

    @Override
    public InputStream slice(int dataLength) throws IOException {
        // The below would be fine but in this case we have the full byte stream anyway...
        //return ByteStreams.limit(bis, dataLength);
        InputStream slice = new ByteArrayInputStream(bytes, tell(), dataLength);
        this.skip(dataLength);
        return slice;
    }

    public static PngAtOnceSource from(InputStream is) throws IOException {
        return new PngAtOnceSource(is.readAllBytes());
    }
}
