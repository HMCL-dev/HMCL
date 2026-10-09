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
package org.jackhuang.hmcl.ui.instances.server;

import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import org.jackhuang.hmcl.server.ServerStatusResult;
import org.jackhuang.hmcl.server.pinger.ServerStatusPinger;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

public class ObservableServerStatus {

    private static final Semaphore PING_SEMAPHORE = new Semaphore(5);

    private final String serverIp;
    private final ReadOnlyObjectWrapper<ServerStatusResult> resultProperty = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyBooleanWrapper pingingProperty = new ReadOnlyBooleanWrapper();
    private final AtomicInteger refreshId = new AtomicInteger();

    public ObservableServerStatus(String serverIp) {
        this.serverIp = serverIp;
    }

    public void refreshIfNoResultAsync(boolean urgent) {
        if (resultProperty.get() == null) {
            refreshAsync(urgent);
        }
    }

    public void refreshIfNoResultOrFailedAsync(boolean urgent) {
        ServerStatusResult result = resultProperty.get();
        if (result == null || result.isFailure()) {
            refreshAsync(urgent);
        }
    }

    public void refreshAsync(boolean urgent) {
        Task.runAsync(Schedulers.javafx(), () -> {

            if (pingingProperty.get() && !urgent) {
                return;
            }

            int currentRefreshId = refreshId.incrementAndGet();
            pingingProperty.set(true);

            Task.supplyAsync(Schedulers.io(), () -> {
                // expired.
                if (currentRefreshId != refreshId.get()) return null;

                if (urgent) {
                    return ServerStatusPinger.getStatus(serverIp);
                }

                PING_SEMAPHORE.acquireUninterruptibly();
                try {
                    // expired.
                    if (currentRefreshId != refreshId.get()) return null;

                    return ServerStatusPinger.getStatus(serverIp);
                } finally {
                    PING_SEMAPHORE.release();
                }
            }).whenComplete(Schedulers.javafx(), (result, ignored) -> {
                // expired.
                if (currentRefreshId != refreshId.get()) return;

                try {
                    resultProperty.set(result);
                } finally {
                    pingingProperty.set(false);
                }
            }).start();
        }).start();
    }

    public ReadOnlyBooleanProperty pingingProperty() {
        return pingingProperty.getReadOnlyProperty();
    }

    public ReadOnlyObjectProperty<ServerStatusResult> resultProperty() {
        return resultProperty.getReadOnlyProperty();
    }
}
