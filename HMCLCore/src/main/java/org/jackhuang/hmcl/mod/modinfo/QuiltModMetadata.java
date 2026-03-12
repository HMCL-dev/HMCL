package org.jackhuang.hmcl.mod.modinfo;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import kala.compress.archivers.zip.ZipArchiveEntry;
import org.jackhuang.hmcl.mod.LocalModFile;
import org.jackhuang.hmcl.mod.ModLoaderType;
import org.jackhuang.hmcl.mod.ModManager;
import org.jackhuang.hmcl.util.Immutable;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.tree.ZipFileTree;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Collectors;

@Immutable
public final class QuiltModMetadata {
    private static final class QuiltLoader {
        private static final class Metadata {
            private final String name;
            private final String description;
            private final JsonObject contributors;
            private final String icon;
            private final JsonObject contact;

            public Metadata(String name, String description, JsonObject contributors, String icon, JsonObject contact) {
                this.name = name;
                this.description = description;
                this.contributors = contributors;
                this.icon = icon;
                this.contact = contact;
            }
        }

        private final String id;
        private final String version;
        private final Metadata metadata;

        public QuiltLoader(String id, String version, Metadata metadata) {
            this.id = id;
            this.version = version;
            this.metadata = metadata;
        }
    }

    private final int schema_version;
    private final QuiltLoader quilt_loader;

    public QuiltModMetadata(int schemaVersion, QuiltLoader quiltLoader) {
        this.schema_version = schemaVersion;
        this.quilt_loader = quiltLoader;
    }

    public static LocalModFile fromFile(ModManager modManager, Path modFile, ZipFileTree tree) throws IOException, JsonParseException {
        ZipArchiveEntry path = tree.getEntry("quilt.mod.json");
        if (path == null) {
            throw new IOException("File " + modFile + " is not a Quilt mod.");
        }

        QuiltModMetadata root = JsonUtils.fromNonNullJsonFully(tree.getInputStream(path), QuiltModMetadata.class);
        if (root.schema_version != 1) {
            throw new IOException("File " + modFile + " is not a supported Quilt mod.");
        }

        return new LocalModFile(
                modManager,
                modManager.getLocalMod(root.quilt_loader.id, ModLoaderType.QUILT),
                modFile,
                root.quilt_loader.metadata.name,
                new LocalModFile.Description(root.quilt_loader.metadata.description),
                root.quilt_loader.metadata.contributors.entrySet().stream().map(entry -> String.format("%s (%s)", entry.getKey(), entry.getValue().getAsJsonPrimitive().getAsString())).collect(Collectors.joining(", ")),
                root.quilt_loader.version,
                "",
                Optional.ofNullable(root.quilt_loader.metadata.contact.get("homepage")).map(jsonElement -> jsonElement.getAsJsonPrimitive().getAsString()).orElse(""),
                root.quilt_loader.metadata.icon
        );
    }
}
