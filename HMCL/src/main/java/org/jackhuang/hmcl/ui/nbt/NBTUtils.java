package org.jackhuang.hmcl.ui.nbt;

import com.github.steveice10.opennbt.SNBTIO;
import com.github.steveice10.opennbt.tag.builtin.Tag;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class NBTUtils {

    private NBTUtils() {
    }

    public static String getSNBT(Tag tag) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            SNBTIO.writeTag(baos, tag, false);
            return baos.toString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
