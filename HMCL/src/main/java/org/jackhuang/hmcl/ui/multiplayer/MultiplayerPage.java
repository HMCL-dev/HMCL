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

import de.javawi.jstun.test.DiscoveryInfo;
import de.javawi.jstun.test.DiscoveryTest;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import org.jackhuang.hmcl.event.Event;
import org.jackhuang.hmcl.setting.DownloadProviders;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutor;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.util.StringUtils;

import java.util.concurrent.CancellationException;
import java.util.function.Consumer;
import java.util.logging.Level;

import static org.jackhuang.hmcl.setting.ConfigHolder.config;
import static org.jackhuang.hmcl.ui.FXUtils.runInFX;
import static org.jackhuang.hmcl.util.Logging.LOG;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class MultiplayerPage extends Control implements DecoratorPage, PageAware {
    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>(State.fromTitle(i18n("multiplayer"), -1));

    private final ObjectProperty<MultiplayerManager.State> multiplayerState = new SimpleObjectProperty<>(MultiplayerManager.State.DISCONNECTED);
    private final ReadOnlyStringWrapper token = new ReadOnlyStringWrapper();
    private final ReadOnlyObjectWrapper<DiscoveryInfo> natState = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyIntegerWrapper gamePort = new ReadOnlyIntegerWrapper(-1);
    private final ReadOnlyObjectWrapper<MultiplayerManager.CatoSession> session = new ReadOnlyObjectWrapper<>();
    private final ObservableList<MultiplayerChannel.CatoClient> clients = FXCollections.observableArrayList();

    private Consumer<MultiplayerManager.CatoExitEvent> onExit;
    private Consumer<MultiplayerManager.CatoIdEvent> onIdGenerated;
    private Consumer<Event> onPeerConnected;

    public MultiplayerPage() {
        testNAT();
    }

    @Override
    public void onPageShown() {
        downloadCatoIfNecessary();
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new MultiplayerPageSkin(this);
    }

    public ObservableList<MultiplayerChannel.CatoClient> getClients() {
        return clients;
    }

    public MultiplayerManager.State getMultiplayerState() {
        return multiplayerState.get();
    }

    public ObjectProperty<MultiplayerManager.State> multiplayerStateProperty() {
        return multiplayerState;
    }

    public void setMultiplayerState(MultiplayerManager.State multiplayerState) {
        this.multiplayerState.set(multiplayerState);
    }

    public DiscoveryInfo getNatState() {
        return natState.get();
    }

    public ReadOnlyObjectProperty<DiscoveryInfo> natStateProperty() {
        return natState.getReadOnlyProperty();
    }

    public String getToken() {
        return token.get();
    }

    public ReadOnlyStringProperty tokenProperty() {
        return token.getReadOnlyProperty();
    }

    public int getGamePort() {
        return gamePort.get();
    }

    public ReadOnlyIntegerProperty gamePortProperty() {
        return gamePort.getReadOnlyProperty();
    }

    public MultiplayerManager.CatoSession getSession() {
        return session.get();
    }

    public ReadOnlyObjectProperty<MultiplayerManager.CatoSession> sessionProperty() {
        return session.getReadOnlyProperty();
    }

    private void testNAT() {
        Task.supplyAsync(() -> {
            DiscoveryTest tester = new DiscoveryTest(null, 0, "stun.qq.com", 3478);
            return tester.test();
        }).whenComplete(Schedulers.javafx(), (info, exception) -> {
            if (exception == null) {
                natState.set(info);
            } else {
                natState.set(null);
            }
        }).start();
    }

    private void downloadCatoIfNecessary() {
        if (StringUtils.isBlank(MultiplayerManager.getCatoPath())) {
            Controllers.dialog(i18n("multiplayer.download."), i18n("install.failed.downloading"), MessageDialogPane.MessageType.ERROR);
            fireEvent(new PageCloseEvent());
            return;
        }

        if (!MultiplayerManager.getCatoExecutable().toFile().exists()) {
            setDisabled(true);
            TaskExecutor executor = MultiplayerManager.downloadCato()
                    .whenComplete(Schedulers.javafx(), exception -> {
                        setDisabled(false);
                        if (exception != null) {
                            if (exception instanceof CancellationException) {
                                Controllers.showToast(i18n("message.cancelled"));
                            } else {
                                Controllers.dialog(DownloadProviders.localizeErrorMessage(exception), i18n("install.failed.downloading"), MessageDialogPane.MessageType.ERROR);
                                fireEvent(new PageCloseEvent());
                            }
                        } else {
                            Controllers.showToast(i18n("multiplayer.download.success"));
                        }
                    }).executor();
            Controllers.taskDialog(executor, i18n("multiplayer.download"));
            executor.start();
        } else {
            setDisabled(false);
        }
    }

    public void copyInvitationCode() {
        if (getSession() == null || !getSession().isReady() || gamePort.get() < 0 || getMultiplayerState() != MultiplayerManager.State.MASTER) {
            throw new IllegalStateException("CatoSession not ready");
        }

        FXUtils.copyText(getSession().generateInvitationCode(getSession().getServer().getPort()));
    }

    public void createRoom() {
        if (getSession() != null || getMultiplayerState() != MultiplayerManager.State.DISCONNECTED) {
            throw new IllegalStateException("CatoSession already ready");
        }

        Controllers.dialog(new CreateMultiplayerRoomDialog((result, resolve, reject) -> {
            int gamePort = result.getAd();
            try {
                MultiplayerManager.CatoSession session = MultiplayerManager.createSession(config().getMultiplayerToken(), result.getMotd(), gamePort);
                session.getServer().onClientAdded().register(event -> {
                    runInFX(() -> {
                        clients.add(event);
                    });
                });
                session.getServer().onClientDisconnected().register(event -> {
                    runInFX(() -> {
                        clients.remove(event);
                    });
                });
                initCatoSession(session);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Failed to create session", e);
                reject.accept(i18n("multiplayer.session.create.error"));
                return;
            }

            this.gamePort.set(gamePort);
            setMultiplayerState(MultiplayerManager.State.CONNECTING);
            resolve.run();
        }));
    }

    public void joinRoom() {
        if (getSession() != null || getMultiplayerState() != MultiplayerManager.State.DISCONNECTED) {
            throw new IllegalStateException("CatoSession already ready");
        }

        Controllers.prompt(new PromptDialogPane.Builder(i18n("multiplayer.session.join"), (result, resolve, reject) -> {
            String invitationCode = ((PromptDialogPane.Builder.StringQuestion) result.get(1)).getValue();
            MultiplayerManager.Invitation invitation;
            try {
                invitation = MultiplayerManager.parseInvitationCode(invitationCode);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Failed to join session", e);
                reject.accept(i18n("multiplayer.session.join.invitation_code.error"));
                return;
            }

            int localPort; // invitation channel
            try {
                localPort = MultiplayerManager.findAvailablePort();
            } catch (Exception e) {
                reject.accept(i18n("multiplayer.session.join.port.error"));
                return;
            }

            try {
                MultiplayerManager.joinSession(config().getMultiplayerToken(), invitation.getVersion(), invitation.getSessionName(), invitation.getId(), invitation.getChannelPort(), localPort)
                        .thenAcceptAsync(session -> {
                            initCatoSession(session);

                            session.getClient().onDisconnected().register(() -> {
                                runInFX(() -> {
                                    stopCatoSession();
                                    Controllers.dialog(i18n("multiplayer.session.join.lost_connection"));
                                });
                            });

                            gamePort.set(session.getClient().getGamePort());
                            setMultiplayerState(MultiplayerManager.State.SLAVE);
                            resolve.run();
                        }, Platform::runLater).exceptionally(throwable -> {
                            LOG.log(Level.WARNING, "Failed to join sessoin");
                            reject.accept(i18n("multiplayer.session.error"));
                            return null;
                        });
            } catch (MultiplayerManager.IncompatibleCatoVersionException e) {
                reject.accept(i18n("multiplayer.session.join.invitation_code.version"));
            }
        })
                .addQuestion(new PromptDialogPane.Builder.HintQuestion(i18n("multiplayer.session.join.hint")))
                .addQuestion(new PromptDialogPane.Builder.StringQuestion(i18n("multiplayer.session.join.invitation_code"), "", new RequiredValidator())));
    }

    public void closeRoom() {
        if (getSession() == null || !getSession().isReady() || getMultiplayerState() != MultiplayerManager.State.MASTER) {
            throw new IllegalStateException("CatoSession not ready");
        }

        Controllers.confirm(i18n("multiplayer.session.close.warning"), i18n("message.warning"), MessageDialogPane.MessageType.WARNING,
                this::stopCatoSession, null);
    }

    public void quitRoom() {
        if (getSession() == null || !getSession().isReady() || getMultiplayerState() != MultiplayerManager.State.SLAVE) {
            throw new IllegalStateException("CatoSession not ready");
        }

        Controllers.confirm(i18n("multiplayer.session.quit.warning"), i18n("message.warning"), MessageDialogPane.MessageType.WARNING,
                this::stopCatoSession, null);
    }

    public void cancelRoom() {
        if (getSession() == null || getSession().isReady() || getMultiplayerState() != MultiplayerManager.State.CONNECTING) {
            throw new IllegalStateException("CatoSession not existing or already ready");
        }

        stopCatoSession();
    }

    private void initCatoSession(MultiplayerManager.CatoSession session) {
        runInFX(() -> {
            onExit = session.onExit().registerWeak(this::onCatoExit);
            onIdGenerated = session.onIdGenerated().registerWeak(this::onCatoIdGenerated);
            onPeerConnected = session.onPeerConnected().registerWeak(this::onCatoPeerConnected);

            this.clients.clear();
            this.session.set(session);
        });
    }

    private void stopCatoSession() {
        getSession().stop();
        clearCatoSession();
    }

    private void clearCatoSession() {
        this.session.set(null);
        this.token.set(null);
        this.gamePort.set(-1);
        this.multiplayerState.set(MultiplayerManager.State.DISCONNECTED);
    }

    private void onCatoExit(MultiplayerManager.CatoExitEvent event) {
        runInFX(() -> {
            boolean ready = ((MultiplayerManager.CatoSession) event.getSource()).isReady();
            switch (event.getExitCode()) {
                case 0:
                    break;
                case MultiplayerManager.CatoExitEvent.EXIT_CODE_SESSION_EXPIRED:
                    Controllers.dialog(i18n("multiplayer.session.expired"));
                    break;
                case 1:
                    if (!ready) {
                        Controllers.dialog(i18n("multiplayer.exit.timeout"));
                    }
                    break;
                case -1:
                    // do nothing
                    break;
                default:
                    if (!((MultiplayerManager.CatoSession) event.getSource()).isReady()) {
                        Controllers.dialog(i18n("multiplayer.exit.before_ready", event.getExitCode()));
                    } else {
                        Controllers.dialog(i18n("multiplayer.exit.after_ready", event.getExitCode()));
                    }
                    break;
            }

            clearCatoSession();
        });
    }

    private void onCatoPeerConnected(Event event) {
        runInFX(() -> {
        });
    }

    private void onCatoIdGenerated(MultiplayerManager.CatoIdEvent event) {
        runInFX(() -> {
            token.set(event.getId());
            setMultiplayerState(((MultiplayerManager.CatoSession) event.getSource()).getType());
        });
    }

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state;
    }
}
