package org.jackhuang.hmcl.ui.nbt;

import java.io.File;

public final class NBTHelper {
    public static boolean isNBTFileByExtension(File file) {
        return NBTFileType.ofFile(file) != null;
    }
}
