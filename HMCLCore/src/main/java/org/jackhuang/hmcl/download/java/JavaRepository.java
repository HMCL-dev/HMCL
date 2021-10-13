package org.jackhuang.hmcl.download.java;

import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.game.GameJavaVersion;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.CacheRepository;
import org.jackhuang.hmcl.util.platform.Architecture;
import org.jackhuang.hmcl.util.platform.JavaVersion;
import org.jackhuang.hmcl.util.platform.OperatingSystem;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.logging.Level;

import static org.jackhuang.hmcl.util.Logging.LOG;

public final class JavaRepository {
    private JavaRepository() {
    }

    public static Task<?> downloadJava(GameJavaVersion javaVersion, DownloadProvider downloadProvider) {
        return new JavaDownloadTask(javaVersion, getJavaStoragePath(), downloadProvider)
                .thenRunAsync(() -> {
                    Optional<String> platform = getCurrentJavaPlatform();
                    if (platform.isPresent()) {
                        addJava(getJavaHome(javaVersion, platform.get()));
                    }
                });
    }

    public static void addJava(Path javaHome) throws InterruptedException, IOException {
        if (Files.isDirectory(javaHome)) {
            Path executable = JavaVersion.getExecutable(javaHome);
            if (Files.isRegularFile(executable)) {
                JavaVersion.getJavas().add(JavaVersion.fromExecutable(executable));
            }
        }
    }

    public static void initialize() throws IOException, InterruptedException {
        Optional<String> platformOptional = getCurrentJavaPlatform();
        if (platformOptional.isPresent()) {
            String platform = platformOptional.get();
            Path javaStoragePath = getJavaStoragePath();
            if (Files.isDirectory(javaStoragePath)) {
                try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(javaStoragePath)) {
                    for (Path component : dirStream) {
                        Path javaHome = component.resolve(platform).resolve(component.getFileName());
                        try {
                            addJava(javaHome);
                        } catch (IOException e) {
                            LOG.log(Level.WARNING, "Failed to determine Java at " + javaHome, e);
                        }
                    }
                }
            }
        }
    }

    public static Optional<String> getCurrentJavaPlatform() {
        if (OperatingSystem.CURRENT_OS == OperatingSystem.LINUX) {
            if (Architecture.SYSTEM_ARCH == Architecture.X86) {
                return Optional.of("linux-i386");
            } else if (Architecture.SYSTEM_ARCH == Architecture.X86_64) {
                return Optional.of("linux");
            }
        } else if (OperatingSystem.CURRENT_OS == OperatingSystem.OSX) {
            if (Architecture.SYSTEM_ARCH == Architecture.X86_64) {
                return Optional.of("mac-os");
            }
        } else if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS) {
            if (Architecture.SYSTEM_ARCH == Architecture.X86) {
                return Optional.of("windows-x86");
            } else if (Architecture.SYSTEM_ARCH == Architecture.X86_64) {
                return Optional.of("windows-x64");
            }
        }
        return Optional.empty();
    }

    public static Path getJavaStoragePath() {
        return CacheRepository.getInstance().getCacheDirectory().resolve("java");
    }

    public static Path getJavaHome(GameJavaVersion javaVersion, String platform) {
        return getJavaStoragePath().resolve(javaVersion.getComponent()).resolve(platform).resolve(javaVersion.getComponent());
    }
}
