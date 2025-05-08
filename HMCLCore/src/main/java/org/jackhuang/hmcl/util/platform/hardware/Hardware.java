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
package org.jackhuang.hmcl.util.platform.hardware;

import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.platform.linux.LinuxHardwareDetector;
import org.jackhuang.hmcl.util.platform.macos.MacOSHardwareDetector;
import org.jackhuang.hmcl.util.platform.windows.WindowsHardwareDetector;

import java.util.List;

public final class Hardware {

    public static final HardwareDetector DETECTOR;

    static {
        if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS)
            DETECTOR = new WindowsHardwareDetector();
        else if (OperatingSystem.CURRENT_OS == OperatingSystem.LINUX)
            DETECTOR = new LinuxHardwareDetector();
        else if (OperatingSystem.CURRENT_OS == OperatingSystem.OSX)
            DETECTOR = new MacOSHardwareDetector();
        else
            DETECTOR = new HardwareDetector();
    }

    public static final List<GraphicsCard> GRAPHICS_CARDS = DETECTOR.detectGraphicsCards();

    private Hardware() {
    }
}
