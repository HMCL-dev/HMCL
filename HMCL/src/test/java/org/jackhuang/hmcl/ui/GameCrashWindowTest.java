/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.ui;

import org.jackhuang.hmcl.JavaFXLauncher;
import org.jackhuang.hmcl.game.ClassicVersion;
import org.jackhuang.hmcl.game.LaunchOptions;
import org.jackhuang.hmcl.java.JavaInfo;
import org.jackhuang.hmcl.game.Log;
import org.jackhuang.hmcl.launch.ProcessListener;
import org.jackhuang.hmcl.java.JavaRuntime;
import org.jackhuang.hmcl.util.platform.ManagedProcess;
import org.jackhuang.hmcl.util.platform.Platform;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

public class GameCrashWindowTest {

    @Test
    @Disabled
    public void test() throws Exception {
        JavaFXLauncher.start();

        ManagedProcess process = new ManagedProcess(null, Arrays.asList("commands", "2"));

        String logs = Files.readString(new File("../HMCLCore/src/test/resources/logs/too_old_java.txt").toPath());

        CountDownLatch latch = new CountDownLatch(1);
        FXUtils.runInFX(() -> {
            Path workingPath = Path.of(System.getProperty("user.dir"));

            GameCrashWindow window = new GameCrashWindow(process, ProcessListener.ExitType.APPLICATION_ERROR, null,
                    new ClassicVersion(),
                    new LaunchOptions.Builder()
                            .setJava(new JavaRuntime(workingPath, new JavaInfo(Platform.SYSTEM_PLATFORM, "16", null), false, false))
                            .setGameDir(workingPath)
                            .create(),
                    Arrays.stream(logs.split("\\n"))
                            .map(Log::new)
                            .collect(Collectors.toList()));

            window.showAndWait();

            latch.countDown();
        });
        latch.await();
    }
}
