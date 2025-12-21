package org.jackhuang.hmcl.mod;

import org.jackhuang.hmcl.game.GameRepository;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

public abstract class LocalFileManager<T extends ILocalFile> {

    public static final String DISABLED_EXTENSION = ".disabled";
    public static final String OLD_EXTENSION = ".old";

    public static String getLocalFileName(Path file) {
        return StringUtils.removeSuffix(FileUtils.getName(file), DISABLED_EXTENSION, OLD_EXTENSION);
    }

    protected final Set<T> localFiles = new LinkedHashSet<>();

    protected final GameRepository repository;
    protected final String id;

    public LocalFileManager(GameRepository gameRepository, String versionId) {
        this.repository = gameRepository;
        this.id = versionId;
    }

    public GameRepository getRepository() {
        return repository;
    }

    public String getInstanceId() {
        return id;
    }

    public abstract Path getDirectory();

    public abstract void refresh() throws IOException;

    public @Unmodifiable List<T> getLocalFiles() throws IOException {
        return List.copyOf(localFiles);
    }

    public Path setOld(T modFile, boolean old) throws IOException {
        Path newPath;
        if (old) {
            newPath = backupFile(modFile.getFile());
            localFiles.remove(modFile);
        } else {
            newPath = restoreFile(modFile.getFile());
            localFiles.add(modFile);
        }
        return newPath;
    }

    private Path backupFile(Path file) throws IOException {
        Path newPath = file.resolveSibling(
                StringUtils.addSuffix(
                        StringUtils.removeSuffix(FileUtils.getName(file), DISABLED_EXTENSION),
                        OLD_EXTENSION
                )
        );
        if (Files.exists(file)) {
            Files.move(file, newPath, StandardCopyOption.REPLACE_EXISTING);
        }
        return newPath;
    }

    private Path restoreFile(Path file) throws IOException {
        Path newPath = file.resolveSibling(
                StringUtils.removeSuffix(FileUtils.getName(file), OLD_EXTENSION)
        );
        if (Files.exists(file)) {
            Files.move(file, newPath, StandardCopyOption.REPLACE_EXISTING);
        }
        return newPath;
    }
}
