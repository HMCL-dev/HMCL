/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2025 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.util;

import javafx.scene.Scene;
import org.jackhuang.hmcl.ui.FXUtils;

import java.nio.file.*;

/**
 * @author Wulian233
 */
public class CSSHotReload {

    public static void initHotReload(Scene scene, Path cssFolder, String... cssFiles) {
        WatchService watchService;
        try {
            watchService = FileSystems.getDefault().newWatchService();
            cssFolder.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);
        } catch (Throwable ignored) {
            return;
        }

        Thread thread = new Thread(() -> {
            while (true) {
                WatchKey key;
                try {
                    key = watchService.take();
                } catch (Throwable ignored) {
                    break;
                }

                for (WatchEvent<?> event : key.pollEvents()) {
                    Path changed = (Path) event.context();

                    for (String css : cssFiles) {
                        if (changed.endsWith(css)) {
                            reloadCSS(scene, cssFolder.resolve(css), css);
                        }
                    }
                }

                key.reset();
            }
        });

        thread.setDaemon(true);
        thread.start();
    }

    private static void reloadCSS(Scene scene, Path file, String cssName) {
        FXUtils.runInFX(() -> {
            try {
                Path temp = Files.createTempFile("css-", "-" + cssName);
                Files.copy(file, temp, StandardCopyOption.REPLACE_EXISTING);

                String url = temp.toUri().toString();

                scene.getStylesheets().removeIf(s -> s.contains(cssName));
                scene.getStylesheets().add(url);

            } catch (Throwable ignored) {}
        });
    }
}
