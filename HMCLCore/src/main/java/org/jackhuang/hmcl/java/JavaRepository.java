package org.jackhuang.hmcl.java;

import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.game.GameJavaVersion;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.platform.Platform;

import java.util.Collection;

/**
 * @author Glavo
 */
public interface JavaRepository {
    Collection<JavaRuntime> getAllJava(Platform platform);

    Task<JavaRuntime> getInstallJavaTask(DownloadProvider downloadProvider, Platform platform, GameJavaVersion gameJavaVersion);

    Task<Void> getUninstallJavaTask(JavaRuntime java);
}
