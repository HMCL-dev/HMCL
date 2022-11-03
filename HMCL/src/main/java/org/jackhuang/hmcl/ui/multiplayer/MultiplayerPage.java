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
import javafx.beans.property.*;
import javafx.scene.control.Label;
import javafx.scene.control.Skin;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.auth.offline.OfflineAccount;
import org.jackhuang.hmcl.event.Event;
import org.jackhuang.hmcl.setting.DownloadProviders;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.Profiles;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.ui.decorator.DecoratorAnimatedPage;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.ui.versions.Versions;
import org.jackhuang.hmcl.util.HMCLService;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.TaskCancellationAction;
import org.jackhuang.hmcl.util.io.ChecksumMismatchException;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.platform.CommandBuilder;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.platform.SystemUtils;

import java.io.File;
import java.util.Date;
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

    private final ReadOnlyObjectWrapper<MultiplayerManager.HiperSession> session = new ReadOnlyObjectWrapper<>();
    private final IntegerProperty port = new SimpleIntegerProperty();
    private final StringProperty address = new SimpleStringProperty();
    private final ReadOnlyObjectWrapper<Date> expireTime = new ReadOnlyObjectWrapper<>();

    private Consumer<MultiplayerManager.HiperExitEvent> onExit;
    private Consumer<MultiplayerManager.HiperIPEvent> onIPAllocated;
    private Consumer<MultiplayerManager.HiperShowValidUntilEvent> onValidUntil;

    private final ReadOnlyObjectWrapper<LocalServerBroadcaster> broadcaster = new ReadOnlyObjectWrapper<>();
    private Consumer<Event> onBroadcasterExit = null;

    public MultiplayerPage() {
    }

    @Override
    public void onPageShown() {
        checkAgreement(this::downloadHiPerIfNecessary);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new MultiplayerPageSkin(this);
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

    public LocalServerBroadcaster getBroadcaster() {
        return broadcaster.get();
    }

    public ReadOnlyObjectWrapper<LocalServerBroadcaster> broadcasterProperty() {
        return broadcaster;
    }

    public void setBroadcaster(LocalServerBroadcaster broadcaster) {
        this.broadcaster.set(broadcaster);
    }

    public Date getExpireTime() {
        return expireTime.get();
    }

    public ReadOnlyObjectWrapper<Date> expireTimeProperty() {
        return expireTime;
    }

    public void setExpireTime(Date expireTime) {
        this.expireTime.set(expireTime);
    }

    public MultiplayerManager.HiperSession getSession() {
        return session.get();
    }

    public ReadOnlyObjectProperty<MultiplayerManager.HiperSession> sessionProperty() {
        return session.getReadOnlyProperty();
    }

    void launchGame() {
        Profile profile = Profiles.getSelectedProfile();
        Versions.launch(profile, profile.getSelectedVersion(), (launcherHelper) -> {
            launcherHelper.setKeep();
            Account account = launcherHelper.getAccount();
            if (account instanceof OfflineAccount && !(account instanceof MultiplayerOfflineAccount)) {
                OfflineAccount offlineAccount = (OfflineAccount) account;
                launcherHelper.setAccount(new MultiplayerOfflineAccount(
                        offlineAccount.getDownloader(),
                        offlineAccount.getUsername(),
                        offlineAccount.getUUID(),
                        offlineAccount.getSkin()
                ));
            }
        });
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
            LOG.warning("HiPer invalid configuration");
            return i18n("multiplayer.token.malformed");
        } else if (e instanceof ChecksumMismatchException) {
            LOG.log(Level.WARNING, "Failed to verify HiPer files", e);
            return i18n("multiplayer.error.file_not_found");
        } else if (e instanceof MultiplayerManager.HiperExitException) {
            int exitCode = ((MultiplayerManager.HiperExitException) e).getExitCode();
            LOG.warning("HiPer exited unexpectedly with exit code " + exitCode);
            return i18n("multiplayer.exit", exitCode);
        } else if (e instanceof MultiplayerManager.HiperInvalidTokenException) {
            LOG.warning("invalid token");
            return i18n("multiplayer.token.invalid");
        } else {
            LOG.log(Level.WARNING, "Unknown HiPer exception", e);
            return e.getLocalizedMessage() + "\n" + StringUtils.getStackTrace(e);
        }
    }

    public void start() {
        MultiplayerManager.startHiper(globalConfig().getMultiplayerToken())
                .thenAcceptAsync(session -> {
                    this.session.set(session);
                    onExit = session.onExit().registerWeak(this::onExit);
                    onIPAllocated = session.onIPAllocated().registerWeak(this::onIPAllocated);
                    onValidUntil = session.onValidUntil().registerWeak(this::onValidUntil);
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
        if (getBroadcaster() != null) {
            getBroadcaster().close();
        }
        clearSession();
    }

    public void broadcast(String url) {
        LocalServerBroadcaster broadcaster = new LocalServerBroadcaster(url);
        this.onBroadcasterExit = broadcaster.onExit().registerWeak(this::onBroadcasterExit);
        broadcaster.start();
        this.broadcaster.set(broadcaster);
    }

    public void stopBroadcasting() {
        if (getBroadcaster() != null) {
            getBroadcaster().close();
            setBroadcaster(null);
        }
    }

    private void onBroadcasterExit(Event event) {
        runInFX(() -> {
            if (this.broadcaster.get() == event.getSource()) {
                this.broadcaster.set(null);
            }
        });
    }

    private void clearSession() {
        this.session.set(null);
        this.expireTime.set(null);
        this.onExit = null;
        this.onIPAllocated = null;
        this.onValidUntil = null;
        this.broadcaster.set(null);
        this.onBroadcasterExit = null;
    }

    private void onIPAllocated(MultiplayerManager.HiperIPEvent event) {
        runInFX(() -> this.address.set(event.getIP()));
    }

    private void onValidUntil(MultiplayerManager.HiperShowValidUntilEvent event) {
        runInFX(() -> this.expireTime.set(event.getValidUntil()));
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
                case MultiplayerManager.HiperExitEvent.NO_SUDO_PRIVILEGES:
                    if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS) {
                        Controllers.confirm(i18n("multiplayer.error.failed_sudo.windows"), null, MessageDialogPane.MessageType.WARNING, () -> {
                            FXUtils.openLink("https://docs.hmcl.net/multiplayer/admin.html");
                        }, null);
                    } else if (OperatingSystem.CURRENT_OS == OperatingSystem.LINUX) {
                        Controllers.dialog(i18n("multiplayer.error.failed_sudo.linux", MultiplayerManager.HIPER_PATH.toString()), null, MessageDialogPane.MessageType.WARNING);
                    } else if (OperatingSystem.CURRENT_OS == OperatingSystem.OSX) {
                        Controllers.confirm(i18n("multiplayer.error.failed_sudo.mac"), null, MessageDialogPane.MessageType.INFO, () -> {
                            try {
                                String text = "%hmcl-hiper ALL=(ALL:ALL) NOPASSWD: " + MultiplayerManager.HIPER_PATH.toString().replaceAll("[ @!(),:=\\\\]", "\\\\$0") + "\n";

                                File sudoersTmp = File.createTempFile("sudoer", ".tmp");
                                sudoersTmp.deleteOnExit();
                                FileUtils.writeText(sudoersTmp, text);

                                SystemUtils.callExternalProcess(
                                        "osascript", "-e", String.format("do shell script \"%s\" with administrator privileges", String.join(";",
                                                "dscl . create /Groups/hmcl-hiper PrimaryGroupID 758",
                                                "dscl . merge /Groups/hmcl-hiper GroupMembership " + CommandBuilder.toShellStringLiteral(System.getProperty("user.name")) + "",
                                                "mkdir -p /private/etc/sudoers.d",
                                                "mv -f " + CommandBuilder.toShellStringLiteral(sudoersTmp.toString()) + " /private/etc/sudoers.d/hmcl-hiper",
                                                "chown root /private/etc/sudoers.d/hmcl-hiper",
                                                "chmod 0440 /private/etc/sudoers.d/hmcl-hiper"
                                        ).replaceAll("[\\\\\"]", "\\\\$0"))
                                );
                            } catch (Throwable e) {
                                LOG.log(Level.WARNING, "Failed to modify sudoers", e);
                            }
                        }, null);
                    }
                    break;
                case MultiplayerManager.HiperExitEvent.INTERRUPTED:
                    // do nothing
                    break;
                case MultiplayerManager.HiperExitEvent.FAILED_GET_DEVICE:
                    Controllers.dialog(i18n("multiplayer.error.failed_get_device"));
                    break;
                case MultiplayerManager.HiperExitEvent.FAILED_LOAD_CONFIG:
                    Controllers.dialog(i18n("multiplayer.error.failed_load_config"));
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
