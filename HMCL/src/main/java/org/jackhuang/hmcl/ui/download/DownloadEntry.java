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

import javafx.beans.binding.Bindings;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import org.jackhuang.hmcl.task.TaskExecutor;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class DownloadEntry {
    private final short ProgressIsUncertain = -1;

    public static final String STATUS_WAITING = "waiting";
    public static final String STATUS_RUNNING = "running";
    public static final String STATUS_DONE = "done";
    public static final String STATUS_FAILED = "failed";

    private final StringProperty name = new SimpleStringProperty();
    private final StringProperty status = new SimpleStringProperty();
    private final DoubleProperty progress = new SimpleDoubleProperty(ProgressIsUncertain);
    private final TaskExecutor executor;


    public DownloadEntry(TaskExecutor executor, String name) {
        this.executor = executor;
        this.name.set(name);
        this.status.set(STATUS_WAITING);
    }


    public String getName() { return name.get(); }
    public StringProperty nameProperty() { return name; }

    public String getStatus() { return status.get(); }
    public StringProperty statusProperty() { return status; }

    public StringBinding statusI18NBinding() {
        return Bindings.createStringBinding(() -> {
            String s = status.get();
            if (STATUS_DONE.equals(s)) return i18n("download.task.status.done");
            if (STATUS_FAILED.equals(s)) return i18n("download.task.status.failed");
            if (STATUS_RUNNING.equals(s)) return i18n("download.task.status.running");
            if (STATUS_WAITING.equals(s)) return i18n("download.task.status.waiting");
            return i18n("download.task.status.null");
        }, status);
    }

    public double getProgress() { return progress.get(); }
    public DoubleProperty progressProperty() { return progress; }

    public TaskExecutor getExecutor() { return executor; }
}
