package org.jackhuang.hmcl.ui.terracotta.core;

import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import org.jackhuang.hmcl.task.GetTask;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.util.InvocationDispatcher;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.platform.ManagedProcess;
import org.jackhuang.hmcl.util.platform.SystemUtils;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class TerracottaManager {
    private static final AtomicReference<TerracottaState> STATE_V = new AtomicReference<>();

    static {
        if (TerracottaMetadata.PROVIDER == null) {
            STATE_V.setPlain(TerracottaState.Fatal.INSTANCE);
        } else if (TerracottaMetadata.PROVIDER.exist()) {
            TerracottaState.Launching launching = new TerracottaState.Launching();
            STATE_V.setPlain(launching);

            launch(launching);
        } else {
            STATE_V.setPlain(TerracottaState.Uninitialized.INSTANCE);
        }
    }

    private static final ReadOnlyObjectWrapper<TerracottaState> STATE = new ReadOnlyObjectWrapper<>(STATE_V.getPlain());
    private static final InvocationDispatcher<TerracottaState> STATE_D = InvocationDispatcher.runOn(javafx.application.Platform::runLater, STATE::set);


    public static ReadOnlyObjectProperty<TerracottaState> stateProperty() {
        return STATE.getReadOnlyProperty();
    }

    static {
        Lang.thread(new BackgroundDaemon(), "Terracotta Background Daemon", true);
    }

    private static final class BackgroundDaemon implements Runnable {
        private static final TerracottaState.Exception.Type[] LOOKUP = {
                TerracottaState.Exception.Type.PING_HOST_FAIL,
                TerracottaState.Exception.Type.PING_HOST_RST,
                TerracottaState.Exception.Type.GUEST_ET_CRASH,
                TerracottaState.Exception.Type.HOST_ET_CRASH,
                TerracottaState.Exception.Type.PING_SERVER_RST
        };

        @Override
        public void run() {
            int failCounter = 0;
            while (true) {
                TerracottaState state = STATE_V.get();
                if (!(state instanceof TerracottaState.PortSpecific)) {
                    LockSupport.parkNanos(500_000);
                    continue;
                }

                int port = ((TerracottaState.PortSpecific) state).port;
                int index = state instanceof TerracottaState.Ready ? ((TerracottaState.Ready) state).index : Integer.MIN_VALUE;
                TerracottaState next;
                try {
                    next = new GetTask(URI.create(String.format("http://127.0.0.1:%d/state", port)))
                            .setSignificance(Task.TaskSignificance.MINOR)
                            .thenApplyAsync(jsonString -> JsonUtils.fromNonNullJson(jsonString, TypeToken.get(JsonObject.class)))
                            .setSignificance(Task.TaskSignificance.MINOR)
                            .thenApplyAsync(object -> {
                                int in = object.get("index").getAsInt();
                                if (in <= index) {
                                    return null;
                                }

                                switch (object.get("state").getAsString()) {
                                    case "waiting":
                                        return new TerracottaState.Waiting(port, in);
                                    case "scanning":
                                        return new TerracottaState.Scanning(port, in);
                                    case "hosting":
                                        return new TerracottaState.Hosting(port, in, object.get("room").getAsString());
                                    case "guesting":
                                        return new TerracottaState.Guesting(
                                                port, in,
                                                object.get("url").getAsString(), object.get("ok").getAsBoolean()
                                        );
                                    case "exception":
                                        return new TerracottaState.Exception(port, in, LOOKUP[object.get("type").getAsInt()]);
                                    default:
                                        throw new IllegalArgumentException();
                                }
                            })
                            .setSignificance(Task.TaskSignificance.MINOR)
                            .run();
                    failCounter = 0;
                } catch (Exception e) {
                    failCounter += 1;

                    if (failCounter < 5) {
                        continue;
                    }
                    LOG.warning("Cannot fetch state from Terracotta.", e);
                    next = TerracottaState.Fatal.INSTANCE;
                }

                if (next != null) {
                    compareAndSet(state, next);
                }

                LockSupport.parkNanos(500_000);
            }
        }

    }

    public static TerracottaState.Preparing initialize() {
        FXUtils.checkFxUserThread();

        TerracottaState state = STATE_V.get();
        if (!(state instanceof TerracottaState.Uninitialized)) {
            return null;
        }

        ReadOnlyDoubleWrapper progress = new ReadOnlyDoubleWrapper(0);
        TerracottaState.Preparing preparing = new TerracottaState.Preparing(progress.getReadOnlyProperty());

        Objects.requireNonNull(TerracottaMetadata.PROVIDER).install(progress).thenRunAsync(() -> {
            TerracottaState.Launching launching = new TerracottaState.Launching();
            if (compareAndSet(preparing, launching)) {
                launch(launching);
            }
        }).whenComplete(exception -> {
            compareAndSet(preparing, TerracottaState.Fatal.INSTANCE);
        }).start();

        return setState(preparing);
    }

    private static void launch(TerracottaState.Launching state) {
        try {
            Path path = Files.createTempDirectory(String.format("hmcl-terracotta-%d", UUID.randomUUID().getLeastSignificantBits())).resolve("http").toAbsolutePath();
            ManagedProcess process = new ManagedProcess(new ProcessBuilder(Objects.requireNonNull(TerracottaMetadata.PROVIDER).launch(path)));
            process.pumpInputStream(SystemUtils::onLogLine);
            process.pumpErrorStream(SystemUtils::onLogLine);

            Task.supplyAsync(() -> {
                while (true) {
                    if (Files.exists(path)) {
                        JsonObject object = JsonUtils.fromNonNullJson(Files.readString(path), JsonObject.class);
                        return object.get("port").getAsInt();
                    }

                    if (!process.isRunning()) {
                        throw new IllegalStateException("Process has exited.");
                    }
                }
            }).whenComplete(Schedulers.javafx(), (port, exception) -> {
                TerracottaState next;
                if (exception == null) {
                    next = new TerracottaState.Unknown(port);
                } else {
                    LOG.warning("Cannot get Terracotta HTTP API port: Process has exited.");
                    next = TerracottaState.Fatal.INSTANCE;
                }
                compareAndSet(state, next);
            }).start();
        } catch (Exception e) {
            LOG.warning("Cannot launch Terracotta.", e);
            setState(TerracottaState.Fatal.INSTANCE);
        }
    }

    public static TerracottaState.Waiting setWaiting() {
        TerracottaState state = STATE_V.get();
        if (state instanceof TerracottaState.PortSpecific) {
            new GetTask(URI.create(String.format("http://127.0.0.1:%d/state/ide", ((TerracottaState.PortSpecific) state).port)))
                    .setSignificance(Task.TaskSignificance.MINOR)
                    .start();
            return new TerracottaState.Waiting(-1, -1);
        }
        return null;
    }

    public static TerracottaState.Scanning setScanning() {
        TerracottaState state = STATE_V.get();
        if (state instanceof TerracottaState.PortSpecific) {
            new GetTask(URI.create(String.format("http://127.0.0.1:%d/state/scanning", ((TerracottaState.PortSpecific) state).port)))
                    .setSignificance(Task.TaskSignificance.MINOR)
                    .start();
            return new TerracottaState.Scanning(-1, -1);
        }
        return null;
    }

    public static Task<TerracottaState.Guesting> setGuesting(String room) {
        TerracottaState state = STATE_V.get();
        if (state instanceof TerracottaState.PortSpecific) {
            return new GetTask(URI.create(String.format("http://127.0.0.1:%d/state/guesting?room=%s", ((TerracottaState.PortSpecific) state).port, room)))
                    .setSignificance(Task.TaskSignificance.MINOR)
                    .thenSupplyAsync(() -> new TerracottaState.Guesting(-1, -1, null, false))
                    .setSignificance(Task.TaskSignificance.MINOR);
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
