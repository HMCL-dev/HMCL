/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2025  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.terracotta;

import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.task.DownloadException;
import org.jackhuang.hmcl.task.GetTask;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.terracotta.provider.AbstractTerracottaProvider;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.util.FXThread;
import org.jackhuang.hmcl.util.InvocationDispatcher;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.Pair;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.io.HttpRequest;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jackhuang.hmcl.util.platform.ManagedProcess;
import org.jackhuang.hmcl.util.platform.SystemUtils;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import static org.jackhuang.hmcl.util.Pair.pair;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class TerracottaManager {
    private TerracottaManager() {
    }

    private static final AtomicReference<TerracottaState> STATE_V = new AtomicReference<>(TerracottaState.Bootstrap.INSTANCE);
    private static final ReadOnlyObjectWrapper<TerracottaState> STATE = new ReadOnlyObjectWrapper<>(STATE_V.getPlain());
    private static final InvocationDispatcher<TerracottaState> STATE_D = InvocationDispatcher.runOn(Platform::runLater, STATE::set);

    static {
        Schedulers.io().execute(() -> {
            try {
                if (TerracottaMetadata.PROVIDER == null)
                    throw new IOException("Unsupported platform: " + org.jackhuang.hmcl.util.platform.Platform.CURRENT_PLATFORM);

                switch (TerracottaMetadata.PROVIDER.status()) {
                    case NOT_EXIST -> setState(new TerracottaState.Uninitialized(false));
                    case LEGACY_VERSION -> setState(new TerracottaState.Uninitialized(true));
                    case READY -> launch(setState(new TerracottaState.Launching()), false);
                }
            } catch (Exception e) {
                LOG.warning("Cannot initialize Terracotta.", e);
                compareAndSet(TerracottaState.Bootstrap.INSTANCE, new TerracottaState.Fatal(TerracottaState.Fatal.Type.UNKNOWN));
            }
        });
    }

    public static ReadOnlyObjectProperty<TerracottaState> stateProperty() {
        return STATE.getReadOnlyProperty();
    }

    private static final Thread DAEMON = Lang.thread(TerracottaManager::runBackground, "Terracotta Background Daemon", true);

    @FXThread // Written in FXThread, read-only on background daemon
    private static volatile boolean daemonRunning = false;

    private static void runBackground() {
        final long ACTIVE = TimeUnit.MILLISECONDS.toNanos(500);
        final long BACKGROUND = TimeUnit.SECONDS.toMillis(15);

        while (true) {
            if (daemonRunning) {
                LockSupport.parkNanos(ACTIVE);
            } else {
                long deadline = System.currentTimeMillis() + BACKGROUND;
                do {
                    LockSupport.parkUntil(deadline);
                } while (!daemonRunning && System.currentTimeMillis() < deadline - 100);
            }

            if (!(STATE_V.get() instanceof TerracottaState.PortSpecific state)) {
                continue;
            }
            int port = state.port;
            int index = state instanceof TerracottaState.Ready ready ? ready.index : Integer.MIN_VALUE;

            TerracottaState next;
            try {
                TerracottaState.Ready object = HttpRequest.GET(String.format("http://127.0.0.1:%d/state", port))
                        .retry(5)
                        .getJson(TerracottaState.Ready.class);
                if (object.index <= index) {
                    continue;
                }
                object.port = port;
                next = object;
            } catch (Exception e) {
                LOG.warning("Cannot fetch state from Terracotta.", e);
                next = new TerracottaState.Fatal(TerracottaState.Fatal.Type.TERRACOTTA);
            }

            compareAndSet(state, next);
        }
    }

    @FXThread
    public static void switchDaemon(boolean active) {
        FXUtils.checkFxUserThread();

        boolean dr = daemonRunning;
        if (dr != active) {
            daemonRunning = active;
            if (active) {
                LockSupport.unpark(DAEMON);
            }
        }
    }

    private static AbstractTerracottaProvider getProvider() {
        AbstractTerracottaProvider provider = TerracottaMetadata.PROVIDER;
        if (provider == null) {
            throw new AssertionError("Terracotta Provider must NOT be null.");
        }
        return provider;
    }

    public static boolean isInvalidBundle(Path file) {
        return !FileUtils.getName(file).equalsIgnoreCase(TerracottaMetadata.PACKAGE_NAME);
    }

    @FXThread
    public static TerracottaState.Preparing download() {
        FXUtils.checkFxUserThread();

        TerracottaState state = STATE_V.get();
        if (!(state instanceof TerracottaState.Uninitialized || state instanceof TerracottaState.Fatal && ((TerracottaState.Fatal) state).isRecoverable())
        ) {
            return null;
        }

        TerracottaState.Preparing preparing = new TerracottaState.Preparing(new ReadOnlyDoubleWrapper(-1), true);

        Task.composeAsync(() -> getProvider().download(preparing))
                .thenComposeAsync(pkg -> {
                    if (!preparing.requestInstallFence()) {
                        return null;
                    }

                    return getProvider().install(pkg).thenRunAsync(() -> {
                        TerracottaState.Launching launching = new TerracottaState.Launching();
                        if (compareAndSet(preparing, launching)) {
                            launch(launching, true);
                        }
                    });
                }).whenComplete(exception -> {
                    if (exception instanceof CancellationException) {
                        // no-op
                    } else if (exception instanceof DownloadException) {
                        compareAndSet(preparing, new TerracottaState.Fatal(TerracottaState.Fatal.Type.NETWORK));
                    } else {
                        compareAndSet(preparing, new TerracottaState.Fatal(TerracottaState.Fatal.Type.INSTALL));
                    }
                }).start();

        return compareAndSet(state, preparing) ? preparing : null;
    }

    @FXThread
    public static TerracottaState.Preparing install(Path bundle) {
        FXUtils.checkFxUserThread();
        if (isInvalidBundle(bundle)) {
            return null;
        }

        TerracottaState state = STATE_V.get();
        TerracottaState.Preparing preparing;
        if (state instanceof TerracottaState.Preparing previousPreparing && previousPreparing.requestInstallFence()) {
            preparing = previousPreparing;
        } else if (state instanceof TerracottaState.Uninitialized || state instanceof TerracottaState.Fatal && ((TerracottaState.Fatal) state).isRecoverable()) {
            preparing = new TerracottaState.Preparing(new ReadOnlyDoubleWrapper(-1), false);
        } else {
            return null;
        }

        Task.composeAsync(() -> getProvider().install(bundle))
                .thenRunAsync(() -> {
                    TerracottaState.Launching launching = new TerracottaState.Launching();
                    if (compareAndSet(preparing, launching)) {
                        launch(launching, true);
                    }
                })
                .whenComplete(exception -> {
                    compareAndSet(preparing, new TerracottaState.Fatal(TerracottaState.Fatal.Type.INSTALL));
                }).start();

        return state != preparing && compareAndSet(state, preparing) ? preparing : null;
    }

    @FXThread
    public static TerracottaState recover() {
        FXUtils.checkFxUserThread();

        TerracottaState state = STATE_V.get();
        if (!(state instanceof TerracottaState.Fatal && ((TerracottaState.Fatal) state).isRecoverable())) {
            return null;
        }

        try {
            // FIXME: A temporary limit has been employed in TerracottaBundle#checkExisting, making
            //        hash check accept 50MB at most. Calling it on JavaFX should be safe.
            return switch (getProvider().status()) {
                case NOT_EXIST, LEGACY_VERSION -> download();
                case READY -> {
                    TerracottaState.Launching launching = setState(new TerracottaState.Launching());
                    launch(launching, false);
                    yield launching;
                }
            };
        } catch (RuntimeException | IOException e) {
            LOG.warning("Cannot determine Terracotta state.", e);
            return setState(new TerracottaState.Fatal(TerracottaState.Fatal.Type.UNKNOWN));
        }
    }

    private static void launch(TerracottaState.Launching state, boolean removeLegacy) {
        Task.supplyAsync(() -> {
            Path path = Files.createTempDirectory(String.format("hmcl-terracotta-%d", ThreadLocalRandom.current().nextLong())).resolve("http").toAbsolutePath();
            ManagedProcess process = new ManagedProcess(new ProcessBuilder(getProvider().ofCommandLine(path)));
            process.pumpInputStream(SystemUtils::onLogLine);
            process.pumpErrorStream(SystemUtils::onLogLine);

            long exitTime = -1;
            while (true) {
                if (Files.exists(path)) {
                    JsonObject object = JsonUtils.fromNonNullJson(Files.readString(path), JsonObject.class);
                    return object.get("port").getAsInt();
                }

                if (!process.isRunning()) {
                    if (exitTime == -1) {
                        exitTime = System.currentTimeMillis();
                    } else if (System.currentTimeMillis() - exitTime >= 10000) {
                        throw new IllegalStateException(String.format("Process has exited for 10s, code = %s", process.getExitCode()));
                    }
                }
            }
        }).whenComplete(Schedulers.javafx(), (port, exception) -> {
            TerracottaState next;
            if (exception == null) {
                next = new TerracottaState.Unknown(port);
                if (removeLegacy) {
                    TerracottaMetadata.removeLegacyVersionFiles();
                }
            } else {
                next = new TerracottaState.Fatal(TerracottaState.Fatal.Type.TERRACOTTA);
            }
            compareAndSet(state, next);
        }).start();
    }

    public static Task<String> exportLogs() {
        if (STATE_V.get() instanceof TerracottaState.PortSpecific portSpecific) {
            return new GetTask(URI.create(String.format("http://127.0.0.1:%d/log?fetch=true", portSpecific.port)))
                    .setSignificance(Task.TaskSignificance.MINOR);
        }
        return Task.completed(null);
    }

    public static TerracottaState.Waiting setWaiting() {
        TerracottaState state = STATE_V.get();
        if (state instanceof TerracottaState.PortSpecific portSpecific) {
            new GetTask(URI.create(String.format("http://127.0.0.1:%d/state/ide", portSpecific.port)))
                    .setSignificance(Task.TaskSignificance.MINOR)
                    .start();
            return new TerracottaState.Waiting(-1, -1, null);
        }
        return null;
    }

    private static String getPlayerName() {
        Account account = Accounts.getSelectedAccount();
        return account != null ? account.getCharacter() : i18n("terracotta.player_anonymous");
    }

    public static TerracottaState.HostScanning setScanning() {
        TerracottaState state = STATE_V.get();
        if (state instanceof TerracottaState.PortSpecific portSpecific) {
            Task.supplyAsync(Schedulers.io(), TerracottaNodeList::fetch)
                    .thenComposeAsync(nodes -> {
                        List<Pair<String, String>> query = new ArrayList<>(nodes.size() + 1);
                        query.add(pair("player", getPlayerName()));
                        for (URI node : nodes) {
                            query.add(pair("public_nodes", node.toString()));
                        }
                        return new GetTask(NetworkUtils.withQuery(
                                "http://127.0.0.1:%d/state/scanning".formatted(portSpecific.port), query
                        )).setSignificance(Task.TaskSignificance.MINOR);
                    }).start();

            return new TerracottaState.HostScanning(-1, -1, null);
        }
        return null;
    }

    public static Task<TerracottaState.GuestConnecting> setGuesting(String room) {
        TerracottaState state = STATE_V.get();
        if (state instanceof TerracottaState.PortSpecific portSpecific) {
            return Task.supplyAsync(Schedulers.io(), TerracottaNodeList::fetch)
                    .thenComposeAsync(nodes -> {
                        ArrayList<Pair<String, String>> query = new ArrayList<>(nodes.size() + 2);
                        query.add(pair("room", room));
                        query.add(pair("player", getPlayerName()));
                        for (URI node : nodes) {
                            query.add(pair("public_nodes", node.toString()));
                        }
                        return new GetTask(NetworkUtils.withQuery("http://127.0.0.1:%d/state/guesting".formatted(portSpecific.port), query))
                                .setSignificance(Task.TaskSignificance.MINOR)
                                .thenSupplyAsync(() -> new TerracottaState.GuestConnecting(-1, -1, null))
                                .setSignificance(Task.TaskSignificance.MINOR);
                    });
        } else {
            return null;
        }
    }

    private static <T extends TerracottaState> T setState(T value) {
        if (value == null) {
            throw new AssertionError();
        }

        STATE_V.set(value);
        STATE_D.accept(value);
        return value;
    }

    private static boolean compareAndSet(TerracottaState previous, TerracottaState next) {
        if (next == null) {
            throw new AssertionError();
        }

        if (STATE_V.compareAndSet(previous, next)) {
            STATE_D.accept(next);
            return true;
        } else {
            return false;
        }
    }
}
