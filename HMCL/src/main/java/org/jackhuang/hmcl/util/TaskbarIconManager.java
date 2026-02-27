/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026  huangyuhui <huanghongxun2008@126.com> and contributors
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

import java.net.URL;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class TaskbarIconManager {
    public static void setIcon(String iconPath) {
        if (!java.awt.Taskbar.isTaskbarSupported()) {
            return;
        }

        try {
            URL resource = TaskbarIconManager.class.getResource(iconPath);
            if (resource == null) {
                LOG.warning("Icon resource not found: " + iconPath);
                return;
            }

            java.awt.Image image = java.awt.Toolkit.getDefaultToolkit().getImage(resource);
            java.awt.Taskbar.getTaskbar().setIconImage(image);
        } catch (Throwable e) {
            LOG.warning("Failed to set AWT taskbar icon: " + iconPath, e);
        }
    }

    private TaskbarIconManager() {}
}
