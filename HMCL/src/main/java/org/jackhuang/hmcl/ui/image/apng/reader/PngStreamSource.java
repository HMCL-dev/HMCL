// Copy from https://github.com/aellerton/japng
// Licensed under the Apache License, Version 2.0.

package org.jackhuang.hmcl.ui.image.apng.reader;

import org.jackhuang.hmcl.ui.image.apng.util.InputStreamSlice;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Created by aellerton on 13/06/2015.
 */
public class PngStreamSource implements PngSource {
    final InputStream src;
    //private final ByteArrayInputStream bis;
    private final DataInputStream dis;

    public PngStreamSource(InputStream src) {
        this.src = src;
        //this.bis = new ByteArrayInputStream(this.bytes); // never closed because nothing to do for ByteArrayInputStream
        this.dis = new DataInputStream(this.src); // never closed because underlying stream doesn't need to be closed.

    }

    @Override
    public int getLength() {
        return 0; // TODO?
    }

    @Override
    public boolean supportsByteAccess() {
        return false;
    }

    @Override
    public byte[] getBytes() throws IOException {
        return null;
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
        return 0; // TODO
    }

    @Override
    public int available() {
        try {
            return dis.available(); // TODO: adequate?
        } catch (IOException e) {
            return 0; // TODO
        }
    }

//    @Override
//    public ByteArrayInputStream getBis() {
//        return null;
//    }

    @Override
    public DataInputStream getDis() {
        return dis;
    }

    @Override
    public InputStream slice(int dataLength) {
        return new InputStreamSlice(src, dataLength);
    }

}
