/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2017  huangyuhui <huanghongxun2008@126.com>
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
package org.jackhuang.hmcl.task;

import java.util.EventListener;

/**
 *
 * @author huangyuhui
 */
public abstract class TaskListener implements EventListener {

    public void onStart() {
    }

    public void onReady(Task task) {
    }

    public void onFinished(Task task) {
    }

    public void onFailed(Task task, Throwable throwable) {
    }

    public void onStop(boolean success, TaskExecutor executor) {
    }

    public static class DefaultTaskListener extends TaskListener {
        private DefaultTaskListener() {
        }

        public static final DefaultTaskListener INSTANCE = new DefaultTaskListener();
    }
}
