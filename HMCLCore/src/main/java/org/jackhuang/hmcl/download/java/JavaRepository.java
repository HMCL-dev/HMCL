package org.jackhuang.hmcl.download.java;

import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.game.GameJavaVersion;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.CacheRepository;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.platform.Architecture;
import org.jackhuang.hmcl.util.platform.JavaVersion;
import org.jackhuang.hmcl.util.platform.OperatingSystem;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.stream.Stream;

import static org.jackhuang.hmcl.util.Logging.LOG;

public final class JavaRepository {
    private JavaRepository() {
    }

    public static Task<JavaVersion> downloadJava(GameJavaVersion javaVersion, DownloadProvider downloadProvider) {
        return new JavaDownloadTask(javaVersion, getJavaStoragePath(), downloadProvider)
                .thenSupplyAsync(() -> {
                    String platform = getSystemJavaPlatform().orElseThrow(JavaDownloadTask.UnsupportedPlatformException::new);
                    return addJava(getJavaHome(javaVersion, platform));
                });
    }

    public static JavaVersion addJava(Path javaHome) throws InterruptedException, IOException {
        if (Files.isDirectory(javaHome)) {
            Path executable = JavaVersion.getExecutable(javaHome);
            if (Files.isRegularFile(executable)) {
                JavaVersion javaVersion = JavaVersion.fromExecutable(executable);
                JavaVersion.getJavas().add(javaVersion);
                return javaVersion;
            }
        }

        throw new IOException("Incorrect java home " + javaHome);
    }

    public static Stream<Optional<Path>> findMinecraftRuntimeDirs() {
        switch (OperatingSystem.CURRENT_OS) {
            case WINDOWS:
                return Stream.of(
                        FileUtils.tryGetPath(System.getenv("localappdata"),
                                "Packages\\Microsoft.4297127D64EC6_8wekyb3d8bbwe\\LocalCache\\Local\\runtime"),
                        FileUtils.tryGetPath(
                                Optional.ofNullable(System.getenv("ProgramFiles(x86)")).orElse("C:\\Program Files (x86)"),
                                "Minecraft Launcher\\runtime"));
            case LINUX:
                return Stream.of(FileUtils.tryGetPath(System.getProperty("user.home", ".minecraft/runtime")));
            case OSX:
                return Stream.of(FileUtils.tryGetPath("/Library/Application Support/minecraft/runtime"),
                        FileUtils.tryGetPath(System.getProperty("user.home"), "Library/Application Support/minecraft/runtime"));
            default:
                return Stream.empty();
        }
    }

    public static Stream<Path> findJavaHomeInMinecraftRuntimeDir(Path runtimeDir) {
        if (!Files.isDirectory(runtimeDir))
            return Stream.empty();
        // Examples:
        // $HOME/Library/Application Support/minecraft/runtime/java-runtime-beta/mac-os/java-runtime-beta/jre.bundle/Contents/Home
        // $HOME/.minecraft/runtime/java-runtime-beta/linux/java-runtime-beta
        Optional<String> platformOptional = getSystemJavaPlatform();
        if (!platformOptional.isPresent()) return Stream.empty();
        String platform = platformOptional.get();
        List<Path> javaHomes = new ArrayList<>();
        try (DirectoryStream<Path> dir = Files.newDirectoryStream(runtimeDir)) {
            // component can be jre-legacy, java-runtime-alpha, java-runtime-beta, java-runtime-gamma or any other being added in the future.
            for (Path component : dir) {
                Path javaHome = component.resolve(platform).resolve(component.getFileName());
                if (OperatingSystem.CURRENT_OS == OperatingSystem.OSX) {
                    javaHomes.add(javaHome.resolve("jre.bundle/Contents/Home"));
                }
                javaHomes.add(javaHome);
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to list java-runtime directory " + runtimeDir, e);
        }
        return javaHomes.stream();
    }

    public static Optional<String> getSystemJavaPlatform() {
        if (OperatingSystem.CURRENT_OS == OperatingSystem.LINUX) {
            if (Architecture.SYSTEM_ARCH == Architecture.X86) {
                return Optional.of("linux-i386");
            } else if (Architecture.SYSTEM_ARCH == Architecture.X86_64) {
                return Optional.of("linux");
            }
        } else if (OperatingSystem.CURRENT_OS == OperatingSystem.OSX) {
            if (Architecture.SYSTEM_ARCH == Architecture.X86_64 || Architecture.SYSTEM_ARCH == Architecture.ARM64) {
                return Optional.of("mac-os");
            }
        } else if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS) {
            if (Architecture.SYSTEM_ARCH == Architecture.X86) {
                return Optional.of("windows-x86");
            } else if (Architecture.SYSTEM_ARCH == Architecture.X86_64) {
                return Optional.of("windows-x64");
            } else if (Architecture.SYSTEM_ARCH == Architecture.ARM64) {
                if (OperatingSystem.SYSTEM_BUILD_NUMBER >= 21277) {
                    return Optional.of("windows-x64");
                } else {
                    return Optional.of("windows-x86");
                }
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
