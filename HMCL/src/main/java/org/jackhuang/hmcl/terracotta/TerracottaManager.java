package org.jackhuang.hmcl.terracotta;

import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
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
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.util.InvocationDispatcher;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jackhuang.hmcl.util.platform.ManagedProcess;
import org.jackhuang.hmcl.util.platform.SystemUtils;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class TerracottaManager {
    private TerracottaManager() {
    }

    private static final AtomicReference<TerracottaState> STATE_V = new AtomicReference<>(TerracottaState.Bootstrap.INSTANCE);
    private static final ReadOnlyObjectWrapper<TerracottaState> STATE = new ReadOnlyObjectWrapper<>(STATE_V.getPlain());
    private static final InvocationDispatcher<TerracottaState> STATE_D = InvocationDispatcher.runOn(Platform::runLater, STATE::set);

    static {
        Task.runAsync(() -> {
            if (TerracottaMetadata.PROVIDER == null) {
                setState(new TerracottaState.Fatal(TerracottaState.Fatal.Type.OS));
                LOG.warning("Terracotta hasn't support your OS: " + org.jackhuang.hmcl.util.platform.Platform.SYSTEM_PLATFORM);
            } else {
                switch (TerracottaMetadata.PROVIDER.status()) {
                    case NOT_EXIST: {
                        setState(new TerracottaState.Uninitialized(false));
                        break;
                    }
                    case LEGACY_VERSION: {
                        setState(new TerracottaState.Uninitialized(true));
                        break;
                    }
                    case READY: {
                        TerracottaState.Launching launching = new TerracottaState.Launching();
                        setState(launching);
                        launch0(launching);
                    }
                }
            }
        }).whenComplete(exception -> {
            if (exception != null) {
                compareAndSet(TerracottaState.Bootstrap.INSTANCE, new TerracottaState.Fatal(TerracottaState.Fatal.Type.UNKNOWN));
            }
        }).start();
    }

    public static ReadOnlyObjectProperty<TerracottaState> stateProperty() {
        return STATE.getReadOnlyProperty();
    }

    static {
        Lang.thread(new BackgroundDaemon(), "Terracotta Background Daemon", true);
    }

    private static final class BackgroundDaemon implements Runnable {

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
                            .thenApplyAsync(jsonString -> {
                                TerracottaState.Ready object = JsonUtils.fromNonNullJson(jsonString, TypeToken.get(TerracottaState.Ready.class));
                                if (object.index <= index) {
                                    return null;
                                }

                                object.port = port;
                                return object;
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
                    next = new TerracottaState.Fatal(TerracottaState.Fatal.Type.TERRACOTTA);
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
        if (!(state instanceof TerracottaState.Uninitialized || state instanceof TerracottaState.Fatal && ((TerracottaState.Fatal) state).isRecoverable())) {
            return null;
        }

        ReadOnlyDoubleWrapper progress = new ReadOnlyDoubleWrapper(0);
        TerracottaState.Preparing preparing = new TerracottaState.Preparing(progress.getReadOnlyProperty());

        Task.composeAsync(() ->
                Objects.requireNonNull(TerracottaMetadata.PROVIDER).install(progress)
        ).whenComplete(exception -> {
            if (exception == null) {
                TerracottaMetadata.removeLegacy();

                TerracottaState.Launching launching = new TerracottaState.Launching();
                if (compareAndSet(preparing, launching)) {
                    launch0(launching);
                }
            } else if (exception instanceof DownloadException) {
                compareAndSet(preparing, new TerracottaState.Fatal(TerracottaState.Fatal.Type.NETWORK));
            } else {
                compareAndSet(preparing, new TerracottaState.Fatal(TerracottaState.Fatal.Type.INSTALL));
            }
        }).start();

        return setState(preparing);
    }

    public static TerracottaState recover() {
        FXUtils.checkFxUserThread();

        TerracottaState state = STATE_V.get();
        if (!(state instanceof TerracottaState.Fatal && ((TerracottaState.Fatal) state).isRecoverable())) {
            return null;
        }

        try {
            switch (Objects.requireNonNull(TerracottaMetadata.PROVIDER).status()) {
                case NOT_EXIST:
                case LEGACY_VERSION: {
                    return initialize();
                }
                case READY: {
                    TerracottaState.Launching launching = new TerracottaState.Launching();
                    setState(launching);
                    launch0(launching);
                    return launching;
                }
                default: {
                    throw new AssertionError();
                }
            }
        } catch (NullPointerException | IOException e) {
            LOG.warning("Cannot determine Terracotta state.", e);
            return setState(new TerracottaState.Fatal(TerracottaState.Fatal.Type.UNKNOWN));
        }
    }

    private static void launch0(TerracottaState.Launching state) {
        Task.supplyAsync(() -> {
            Path path = Files.createTempDirectory(String.format("hmcl-terracotta-%d", UUID.randomUUID().getLeastSignificantBits())).resolve("http").toAbsolutePath();
            ManagedProcess process = new ManagedProcess(new ProcessBuilder(Objects.requireNonNull(TerracottaMetadata.PROVIDER).launch(path)));
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
                        throw new IllegalStateException("Process has exited for 10s.");
                    }
                }
            }
        }).whenComplete(Schedulers.javafx(), (port, exception) -> {
            TerracottaState next;
            if (exception == null) {
                next = new TerracottaState.Unknown(port);
            } else {
                next = new TerracottaState.Fatal(TerracottaState.Fatal.Type.TERRACOTTA);
            }
            compareAndSet(state, next);
        }).start();
    }

    public static TerracottaState.Waiting setWaiting() {
        TerracottaState state = STATE_V.get();
        if (state instanceof TerracottaState.PortSpecific) {
            new GetTask(URI.create(String.format("http://127.0.0.1:%d/state/ide", ((TerracottaState.PortSpecific) state).port)))
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
        if (state instanceof TerracottaState.PortSpecific) {
            new GetTask(NetworkUtils.toURI(String.format(
                    "http://127.0.0.1:%d/state/scanning?player=%s", ((TerracottaState.PortSpecific) state).port, getPlayerName()))
            ).setSignificance(Task.TaskSignificance.MINOR).start();

            return new TerracottaState.HostScanning(-1, -1, null);
        }
        return null;
    }

    public static Task<TerracottaState.GuestStarting> setGuesting(String room) {
        TerracottaState state = STATE_V.get();
        if (state instanceof TerracottaState.PortSpecific) {
            return new GetTask(NetworkUtils.toURI(String.format(
                    "http://127.0.0.1:%d/state/guesting?room=%s&player=%s", ((TerracottaState.PortSpecific) state).port, room, getPlayerName()
            )))
                    .setSignificance(Task.TaskSignificance.MINOR)
                    .thenSupplyAsync(() -> new TerracottaState.GuestStarting(-1, -1, null))
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
