/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.ui.download;

import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutor;
import org.jackhuang.hmcl.ui.versions.DownloadTaskList;

public class DifferentDownloadTask2OneTask{

    private static DownloadTaskList activeDownloadTaskList;

    public static void setActiveDownloadTaskList(DownloadTaskList list) {
        activeDownloadTaskList = list;
    }

    public void WizardTask2OneTask(Task<?> task) {
        TaskExecutor executor = task.executor();
        if (activeDownloadTaskList != null) {
            activeDownloadTaskList.addDownloadEntry(executor, task.getName());
            System.out.println("gotolist");
        }
    }

    public void ExecutorTask2OneTask(TaskExecutor executor, String name) {
        if (activeDownloadTaskList != null) {
            activeDownloadTaskList.addDownloadEntry(executor, name);
            System.out.println("gotolist");
        }
    }

}
