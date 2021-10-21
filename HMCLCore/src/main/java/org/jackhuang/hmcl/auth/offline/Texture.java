/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.auth.offline;

import javax.imageio.IIOException;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

import static java.util.Objects.requireNonNull;

public class Texture {
    private final String hash;
    private final byte[] data;

    public Texture(String hash, byte[] data) {
        this.hash = requireNonNull(hash);
        this.data = requireNonNull(data);
    }

    public byte[] getData() {
        return data;
    }

    public String getHash() {
        return hash;
    }

    public InputStream getInputStream() {
        return new ByteArrayInputStream(data);
    }

    public int getLength() {
        return data.length;
    }

    private static final Map<String, Texture> textures = new HashMap<>();

    public static boolean hasTexture(String hash) {
        return textures.containsKey(hash);
    }

    public static Texture getTexture(String hash) {
        return textures.get(hash);
    }

    private static String computeTextureHash(BufferedImage img) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        int width = img.getWidth();
        int height = img.getHeight();
        byte[] buf = new byte[4096];

        putInt(buf, 0, width);
        putInt(buf, 4, height);
        int pos = 8;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                putInt(buf, pos, img.getRGB(x, y));
                if (buf[pos + 0] == 0) {
                    buf[pos + 1] = buf[pos + 2] = buf[pos + 3] = 0;
                }
                pos += 4;
                if (pos == buf.length) {
                    pos = 0;
                    digest.update(buf, 0, buf.length);
                }
            }
        }
        if (pos > 0) {
            digest.update(buf, 0, pos);
        }

        byte[] sha256 = digest.digest();
        return String.format("%0" + (sha256.length << 1) + "x", new BigInteger(1, sha256));
    }

    private static void putInt(byte[] array, int offset, int x) {
        array[offset + 0] = (byte) (x >> 24 & 0xff);
        array[offset + 1] = (byte) (x >> 16 & 0xff);
        array[offset + 2] = (byte) (x >> 8 & 0xff);
        array[offset + 3] = (byte) (x >> 0 & 0xff);
    }

    public static Texture loadTexture(InputStream in) throws IOException {
        if (in == null) return null;
        BufferedImage img = ImageIO.read(in);
        if (img == null) {
            throw new IIOException("No image found");
        }

        String hash = computeTextureHash(img);

        Texture existent = textures.get(hash);
        if (existent != null) {
            return existent;
        }

        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        ImageIO.write(img, "png", buf);
        Texture texture = new Texture(hash, buf.toByteArray());

        existent = textures.putIfAbsent(hash, texture);

        if (existent != null) {
            return existent;
        }
        return texture;
    }

    public static Texture loadTexture(String url) throws IOException {
        if (url == null) return null;
        return loadTexture(new URL(url).openStream());
    }

}
