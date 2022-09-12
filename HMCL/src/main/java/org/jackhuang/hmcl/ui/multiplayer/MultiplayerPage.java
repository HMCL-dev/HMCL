/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.ui.multiplayer;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXDialogLayout;
import de.javawi.jstun.test.DiscoveryInfo;
import de.javawi.jstun.test.DiscoveryTest;
import javafx.beans.property.*;
import javafx.scene.control.Label;
import javafx.scene.control.Skin;
import org.jackhuang.hmcl.setting.DownloadProviders;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.ui.decorator.DecoratorAnimatedPage;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.util.HMCLService;
import org.jackhuang.hmcl.util.Result;
import org.jackhuang.hmcl.util.TaskCancellationAction;
import org.jackhuang.hmcl.util.io.ChecksumMismatchException;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CancellationException;
import java.util.function.Consumer;
import java.util.logging.Level;

import static org.jackhuang.hmcl.setting.ConfigHolder.globalConfig;
import static org.jackhuang.hmcl.ui.FXUtils.runInFX;
import static org.jackhuang.hmcl.util.Lang.resolveException;
import static org.jackhuang.hmcl.util.Logging.LOG;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class MultiplayerPage extends DecoratorAnimatedPage implements DecoratorPage, PageAware {
    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>(State.fromTitle(i18n("multiplayer")));

    private final ReadOnlyObjectWrapper<@Nullable Result<DiscoveryInfo>> natState = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyObjectWrapper<MultiplayerManager.HiperSession> session = new ReadOnlyObjectWrapper<>();
    private final IntegerProperty port = new SimpleIntegerProperty();
    private final StringProperty address = new SimpleStringProperty();

    private Consumer<MultiplayerManager.HiperExitEvent> onExit;
    private Consumer<MultiplayerManager.HiperIPEvent> onIPAllocated;

    public MultiplayerPage() {
        testNAT();
    }

    @Override
    public void onPageShown() {
        checkAgreement(this::downloadHiPerIfNecessary);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new MultiplayerPageSkin(this);
    }

    public Result<DiscoveryInfo> getNatState() {
        return natState.get();
    }

    public ReadOnlyObjectProperty<Result<DiscoveryInfo>> natStateProperty() {
        return natState.getReadOnlyProperty();
    }

    public int getPort() {
        return port.get();
    }

    public IntegerProperty portProperty() {
        return port;
    }

    public void setPort(int port) {
        this.port.set(port);
    }

    public String getAddress() {
        return address.get();
    }

    public StringProperty addressProperty() {
        return address;
    }

    public void setAddress(String address) {
        this.address.set(address);
    }

    public MultiplayerManager.HiperSession getSession() {
        return session.get();
    }

    public ReadOnlyObjectProperty<MultiplayerManager.HiperSession> sessionProperty() {
        return session.getReadOnlyProperty();
    }

    private void testNAT() {
        Task.supplyAsync(() -> {
            DiscoveryTest tester = new DiscoveryTest(null, 0, "stun.miwifi.com", 3478);
            return tester.test();
        }).whenComplete(Schedulers.javafx(), (info, exception) -> {
            if (exception == null) {
                natState.set(Result.ok(info));
            } else {
                natState.set(Result.error());
            }
            LOG.log(Level.INFO, "Nat test result " + MultiplayerPageSkin.getNATType(natState.get()), exception);
        }).start();
    }

    private void checkAgreement(Runnable runnable) {
        if (globalConfig().getMultiplayerAgreementVersion() < MultiplayerManager.HIPER_AGREEMENT_VERSION) {
            JFXDialogLayout agreementPane = new JFXDialogLayout();
            agreementPane.setHeading(new Label(i18n("launcher.agreement")));
            agreementPane.setBody(new Label(i18n("multiplayer.agreement.prompt")));
            JFXHyperlink agreementLink = new JFXHyperlink(i18n("launcher.agreement"));
            agreementLink.setOnAction(e -> HMCLService.openRedirectLink("multiplayer-agreement"));
            JFXButton yesButton = new JFXButton(i18n("launcher.agreement.accept"));
            yesButton.getStyleClass().add("dialog-accept");
            yesButton.setOnAction(e -> {
                globalConfig().setMultiplayerAgreementVersion(MultiplayerManager.HIPER_AGREEMENT_VERSION);
                runnable.run();
                agreementPane.fireEvent(new DialogCloseEvent());
            });
            JFXButton noButton = new JFXButton(i18n("launcher.agreement.decline"));
            noButton.getStyleClass().add("dialog-cancel");
            noButton.setOnAction(e -> {
                agreementPane.fireEvent(new DialogCloseEvent());
                fireEvent(new PageCloseEvent());
            });
            agreementPane.setActions(agreementLink, yesButton, noButton);
            Controllers.dialog(agreementPane);
        } else {
            runnable.run();
        }
    }

    private void downloadHiPerIfNecessary() {
        if (!MultiplayerManager.HIPER_PATH.toFile().exists()) {
            setDisabled(true);
            Controllers.taskDialog(MultiplayerManager.downloadHiper()
                    .whenComplete(Schedulers.javafx(), exception -> {
                        setDisabled(false);
                        if (exception != null) {
                            if (exception instanceof CancellationException) {
                                Controllers.showToast(i18n("message.cancelled"));
                            } else if (exception instanceof MultiplayerManager.HiperUnsupportedPlatformException) {
                                Controllers.dialog(i18n("multiplayer.download.unsupported"), i18n("install.failed.downloading"), MessageDialogPane.MessageType.ERROR);
                                fireEvent(new PageCloseEvent());
                            } else {
                                Controllers.dialog(DownloadProviders.localizeErrorMessage(exception), i18n("install.failed.downloading"), MessageDialogPane.MessageType.ERROR);
                                fireEvent(new PageCloseEvent());
                            }
                        } else {
                            Controllers.showToast(i18n("multiplayer.download.success"));
                        }
                    }), i18n("multiplayer.download"), TaskCancellationAction.NORMAL);
        } else {
            setDisabled(false);
        }
    }

    private String localizeErrorMessage(Throwable t) {
        Throwable e = resolveException(t);
        if (e instanceof CancellationException) {
            LOG.info("Connection rejected by the server");
            return i18n("message.cancelled");
        } else if (e instanceof MultiplayerManager.HiperInvalidConfigurationException) {
            LOG.info("Hiper invalid configuration");
            return i18n("multiplayer.token.malformed");
        } else if (e instanceof MultiplayerManager.HiperNotExistsException) {
            LOG.log(Level.WARNING, "Hiper not found " + ((MultiplayerManager.HiperNotExistsException) e).getFile(), e);
            return i18n("multiplayer.error.file_not_found");
        } else if (e instanceof MultiplayerManager.HiperExitException) {
            LOG.info("HiPer exited accidentally");
            int exitCode = ((MultiplayerManager.HiperExitException) e).getExitCode();
            return i18n("multiplayer.exit", exitCode);
        } else if (e instanceof MultiplayerManager.HiperInvalidTokenException) {
            LOG.info("invalid token");
            return i18n("multiplayer.token.invalid");
        } else if (e instanceof ChecksumMismatchException) {
            return i18n("exception.artifact_malformed");
        } else {
            return e.getLocalizedMessage();
        }
    }

    public void start() {
        MultiplayerManager.startHiper(globalConfig().getMultiplayerToken())
                .thenAcceptAsync(session -> {
                    this.session.set(session);
                    onExit = session.onExit().registerWeak(this::onExit);
                    onIPAllocated = session.onIPAllocated().registerWeak(this::onIPAllocated);
                }, Schedulers.javafx())
                .exceptionally(throwable -> {
                    runInFX(() -> Controllers.dialog(localizeErrorMessage(throwable), null, MessageDialogPane.MessageType.ERROR));
                    return null;
                });
    }

    public void stop() {
        if (getSession() != null) {
            getSession().stop();
        }
        clearSession();
    }

    private void clearSession() {
        this.session.set(null);
        this.onExit = null;
        this.onIPAllocated = null;
    }

    private void onIPAllocated(MultiplayerManager.HiperIPEvent event) {
        runInFX(() -> {
            this.address.set(event.getIP());
        });
    }

    private void onExit(MultiplayerManager.HiperExitEvent event) {
        runInFX(() -> {
            switch (event.getExitCode()) {
                case 0:
                    break;
                case MultiplayerManager.HiperExitEvent.CERTIFICATE_EXPIRED:
                    MultiplayerManager.clearConfiguration();
                    Controllers.dialog(i18n("multiplayer.token.expired"));
                    break;
                case MultiplayerManager.HiperExitEvent.INVALID_CONFIGURATION:
                    MultiplayerManager.clearConfiguration();
                    Controllers.dialog(i18n("multiplayer.token.malformed"));
                    break;
                case MultiplayerManager.HiperExitEvent.INTERRUPTED:
                    // do nothing
                    break;
                default:
                    Controllers.dialog(i18n("multiplayer.exit", event.getExitCode()));
                    break;
            }

            clearSession();
        });
    }

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state;
    }

}
