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
import org.jackhuang.hmcl.launch.ProcessListener;
import org.jackhuang.hmcl.util.Log4jLevel;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.platform.JavaVersion;
import org.jackhuang.hmcl.util.platform.ManagedProcess;
import org.jackhuang.hmcl.util.platform.Platform;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.util.Pair.pair;

public class GameCrashWindowTest {

    @Test
    @Ignore
    public void test() throws Exception {
        JavaFXLauncher.start();

        ManagedProcess process = new ManagedProcess(null, Arrays.asList("commands", "2"));

        String logs = FileUtils.readText(new File("C:\\Users\\huang\\Downloads\\minecraft-exported-logs-2021-10-18T21-15-43.log"));

        CountDownLatch latch = new CountDownLatch(1);
        FXUtils.runInFX(() -> {
            GameCrashWindow window = new GameCrashWindow(process, ProcessListener.ExitType.APPLICATION_ERROR, null,
                    new ClassicVersion(),
                    new LaunchOptions.Builder()
                            .setJava(new JavaVersion(Paths.get("."), "16", Platform.SYSTEM_PLATFORM))
                            .setGameDir(new File("."))
                            .create(),
                    Arrays.stream(logs.split("\\n"))
                            .map(log -> pair(log, Log4jLevel.guessLevel(log)))
                            .collect(Collectors.toList()));

            window.showAndWait();

            latch.countDown();
        });
        latch.await();
    }
}
