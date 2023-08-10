package org.jackhuang.hmcl.mod.modinfo;

import com.google.gson.JsonParseException;
import org.jackhuang.hmcl.mod.LocalModFile;
import org.jackhuang.hmcl.mod.ModManager;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Path;

public interface IModMetadataReader {
    LocalModFile fromFile(ModManager modManager, Path modFile, FileSystem fs) throws IOException, JsonParseException;
}
