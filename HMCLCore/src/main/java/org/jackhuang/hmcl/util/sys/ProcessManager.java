/*
 * Hello Minecraft!.
 * Copyright (C) 2013  huangyuhui <huanghongxun2008@126.com>
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
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hmcl.util.sys;

import java.util.HashSet;
import org.jackhuang.hmcl.api.IProcess;

/**
 *
 * @author huangyuhui
 */
public class ProcessManager {

    private static final HashSet<IProcess> GAME_PROCESSES = new HashSet<>();

    public static void registerProcess(IProcess jp) {
        GAME_PROCESSES.add(jp);
    }

    public static void stopAllProcesses() {
        for (IProcess jp : GAME_PROCESSES)
            jp.stop();
        GAME_PROCESSES.clear();
    }

    public static void onProcessStopped(IProcess p) {
        GAME_PROCESSES.remove(p);
    }
}
