package net.burningtnt.hmat.solver;

import net.burningtnt.hmat.LogAnalyzable;
import org.jackhuang.hmcl.game.GameJavaVersion;
import org.jackhuang.hmcl.java.JavaManager;
import org.jackhuang.hmcl.java.JavaRuntime;
import org.jackhuang.hmcl.setting.DownloadProviders;
import org.jackhuang.hmcl.setting.JavaVersionType;
import org.jackhuang.hmcl.setting.VersionSetting;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.platform.Platform;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;

public interface Solver {
    int BTN_NEXT = 0;

    /**
     * Executed in FXThread.
     */
    void configure(SolverConfigurator configurator);

    /**
     * Executed in FXThread.
     *
     * @param selectionID BTN_NEXT if user click 'Next'. Others if user click selection buttons.
     */
    void callbackSelection(SolverConfigurator configurator, int selectionID);

    static Solver ofTask(Task<?> task) {
        return new Solver() {
            @Override
            public void callbackSelection(SolverConfigurator configurator, int selectionID) {
                configurator.transferTo(null);
            }

            @Override
            public void configure(SolverConfigurator configurator) {
                configurator.bindTask(task);
            }
        };
    }

    static Solver ofUninstallJRE(LogAnalyzable input) {
        return ofTask(JavaManager.uninstallJava(input.getLaunchOptions().getJava()).thenComposeAsync(() -> {
            GameVersionNumber gameVersion = GameVersionNumber.asGameVersion(input.getRepository().getGameVersion(input.getVersion()));
            JavaRuntime runtime = JavaManager.findSuitableJava(gameVersion, input.getVersion());
            if (runtime != null) {
                return Task.supplyAsync(() -> runtime);
            }
            GameJavaVersion gameJavaVersion = GameJavaVersion.getMinimumJavaVersion(gameVersion);
            if (gameJavaVersion == null) {
                gameJavaVersion = GameJavaVersion.JAVA_8;
            }

            return JavaManager.installJava(DownloadProviders.getDownloadProvider(), Platform.CURRENT_PLATFORM, gameJavaVersion);
        }).thenAcceptAsync(Schedulers.javafx(), jre -> {
            VersionSetting vs = input.getRepository().getVersionSetting(input.getVersion().getId());
            vs.setJavaVersionType(JavaVersionType.DETECTED);
            vs.setJavaVersion(jre.getVersion());
            vs.setDefaultJavaPath(jre.getBinary().toString());
        }));
    }
}
