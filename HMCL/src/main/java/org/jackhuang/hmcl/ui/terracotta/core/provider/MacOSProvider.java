package org.jackhuang.hmcl.ui.terracotta.core.provider;

import javafx.beans.property.DoubleProperty;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.terracotta.core.TerracottaMetadata;
import org.jackhuang.hmcl.util.platform.Architecture;
import org.jackhuang.hmcl.util.platform.ManagedProcess;
import org.jackhuang.hmcl.util.platform.SystemUtils;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Set;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class MacOSProvider implements ITerracottaProvider {
    public static final List<URI> INSTALLER, BINARY;

    static {
        if (Architecture.SYSTEM_ARCH == Architecture.X86_64) {
            INSTALLER = TerracottaMetadata.MACOS_INSTALLER_X86_64;
            BINARY = TerracottaMetadata.MACOS_BIN_X86_64;
        } else if (Architecture.SYSTEM_ARCH == Architecture.ARM64) {
            INSTALLER = TerracottaMetadata.MACOS_INSTALLER_ARM64;
            BINARY = TerracottaMetadata.MACOS_BIN_ARM64;
        } else {
            INSTALLER = null;
            BINARY = null;
        }
    }

    private static final Path PATH = BINARY != null ? Metadata.DEPENDENCIES_DIRECTORY.resolve(String.format(
            "terracota/%s/%s", TerracottaMetadata.VERSION, TerracottaMetadata.getFileName(BINARY.get(0))
    )).toAbsolutePath() : null;

    @Override
    public boolean exist() {
        return Files.exists(Path.of("/Applications/terracotta.app")) && Files.exists(PATH);
    }

    @Override
    public Task<?> install(DoubleProperty progress) throws IOException {
        Path installer = Files.createTempFile("hmcl-terracotta-installer-", ".pkg").toAbsolutePath();
        Task<?> installerTask = new FileDownloadTask(INSTALLER, installer);

        Path binary = PATH.resolveSibling(PATH.getFileName() + ".tmp");
        Task<?> binaryTask = new FileDownloadTask(BINARY, binary);

        progress.bind(installerTask.progressProperty().add(binaryTask.progressProperty()).multiply(0.3));

        installerTask = installerTask.thenComposeAsync(() -> {
            ManagedProcess process = new ManagedProcess(new ProcessBuilder(
                    "osascript",
                    "-e",
                    String.format(
                            "do shell script \"%s\" with prompt \"%s\" with administrator privileges",
                            String.format("installer -pkg %s -target /Applications", installer),
                            i18n("terracotta.sudo_installing")
                    )
            ));
            process.pumpInputStream(SystemUtils::onLogLine);
            process.pumpErrorStream(SystemUtils::onLogLine);

            return Task.fromCompletableFuture(process.getProcess().onExit());
        });
        binaryTask = binaryTask.thenRunAsync(() -> {
            Files.move(binary, PATH, StandardCopyOption.REPLACE_EXISTING);
            Files.setPosixFilePermissions(PATH, Set.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE,
                    PosixFilePermission.GROUP_READ,
                    PosixFilePermission.GROUP_EXECUTE,
                    PosixFilePermission.OTHERS_READ,
                    PosixFilePermission.OTHERS_EXECUTE
            ));
        });

        return Task.allOf(installerTask, binaryTask);
    }

    @Override
    public List<String> launch(Path path) {
        return List.of(PATH.toString(), "--hmcl", path.toString());
    }
}
