package net.burningtnt.hmat.game;

import net.burningtnt.hmat.LogAnalyzable;
import net.burningtnt.hmat.solver.Solver;
import org.jackhuang.hmcl.game.GameJavaVersion;
import org.jackhuang.hmcl.java.JavaManager;
import org.jackhuang.hmcl.java.JavaRuntime;
import org.jackhuang.hmcl.setting.DownloadProviders;
import org.jackhuang.hmcl.setting.JavaVersionType;
import org.jackhuang.hmcl.setting.VersionSetting;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.function.ExceptionalConsumer;
import org.jackhuang.hmcl.util.platform.Platform;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;

final class SolverCollection {
    private SolverCollection() {
    }

    static ExceptionalConsumer<JavaRuntime, RuntimeException> ofModifyJRE(LogAnalyzable input) {
        return jre -> {
            VersionSetting vs = input.getRepository().getVersionSetting(input.getVersion().getId());
            vs.setJavaVersionType(JavaVersionType.DETECTED);
            vs.setJavaVersion(jre.getVersion());
            vs.setDefaultJavaPath(jre.getBinary().toString());
        };
    }

    static Solver ofReinstallJRE(LogAnalyzable input) {
        return Solver.ofTask(JavaManager.getUninstallJavaTask(input.getLaunchOptions().getJava()).thenComposeAsync(() -> {
            GameVersionNumber gameVersion = GameVersionNumber.asGameVersion(input.getRepository().getGameVersion(input.getVersion()));
            JavaRuntime runtime = JavaManager.findSuitableJava(gameVersion, input.getVersion());
            if (runtime != null) {
                return Task.supplyAsync(() -> runtime);
            }
            GameJavaVersion gameJavaVersion = GameJavaVersion.getMinimumJavaVersion(gameVersion);
            if (gameJavaVersion == null) {
                gameJavaVersion = GameJavaVersion.JAVA_8;
            }

            return JavaManager.getDownloadJavaTask(DownloadProviders.getDownloadProvider(), Platform.CURRENT_PLATFORM, gameJavaVersion);
        }).thenAcceptAsync(Schedulers.javafx(), ofModifyJRE(input)));
    }
}
