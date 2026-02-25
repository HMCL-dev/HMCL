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
package org.jackhuang.hmcl.ui.terracotta;

import com.jfoenix.controls.JFXProgressBar;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextFlow;
import org.jackhuang.hmcl.game.LauncherHelper;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.Profiles;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.terracotta.TerracottaManager;
import org.jackhuang.hmcl.terracotta.TerracottaMetadata;
import org.jackhuang.hmcl.terracotta.TerracottaState;
import org.jackhuang.hmcl.terracotta.profile.TerracottaProfile;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.WeakListenerHolder;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.ui.versions.Versions;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.i18n.LocaleUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.io.Zipper;
import org.jackhuang.hmcl.util.logging.Logger;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

import static org.jackhuang.hmcl.setting.ConfigHolder.config;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class TerracottaControllerPage extends StackPane {
    private static final String FEEDBACK_TIP = "terracotta-feedback";
    private static final ObjectProperty<TerracottaState> UI_STATE = new SimpleObjectProperty<>();

    static {
        FXUtils.onChangeAndOperate(TerracottaManager.stateProperty(), state -> {
            if (state != null) {
                UI_STATE.set(state);
            }
        });
    }

    private final WeakListenerHolder holder = new WeakListenerHolder();

    /* FIXME: It's sucked to have such a long logic, containing UI for all states defined in TerracottaState, with unclear control flows.
         Consider moving UI into multiple files for each state respectively. */
    public TerracottaControllerPage() {
        holder.add(FXUtils.observeWeak(() -> {
            // Run daemon process only if HMCL is focused and is displaying current node.
            TerracottaManager.switchDaemon(getScene() != null && Controllers.getStage().isFocused());
        }, this.sceneProperty(), Controllers.getStage().focusedProperty()));

        TransitionPane transition = new TransitionPane();

        ObjectProperty<String> statusProperty = new SimpleObjectProperty<>();
        DoubleProperty progressProperty = new SimpleDoubleProperty();
        ObservableList<Node> nodesProperty = FXCollections.observableList(new ArrayList<>());

        FXUtils.applyDragListener(this, path -> {
            TerracottaState state = UI_STATE.get();

            if (state instanceof TerracottaState.Uninitialized ||
                    state instanceof TerracottaState.Preparing preparing && preparing.hasInstallFence() ||
                    state instanceof TerracottaState.Fatal fatal && fatal.isRecoverable()
            ) {
                return Files.isReadable(path) && FileUtils.getName(path).toLowerCase(Locale.ROOT).endsWith(".tar.gz");
            } else {
                return false;
            }
        }, files -> {
            Path path = files.get(0);

            if (TerracottaManager.isInvalidBundle(path)) {
                Controllers.dialog(
                        i18n("terracotta.from_local.file_name_mismatch", TerracottaMetadata.PACKAGE_NAME, FileUtils.getName(path)),
                        i18n("message.error"),
                        MessageDialogPane.MessageType.ERROR
                );
                return;
            }

            TerracottaState.Preparing next = TerracottaManager.install(path);
            if (next != null) {
                UI_STATE.set(next);
            }
        });

        ChangeListener<TerracottaState> listener = (_uiState, legacyState, state) -> {
            if (legacyState != null && legacyState.isUIFakeState() && !state.isUIFakeState() && legacyState.getClass() == state.getClass()) {
                return;
            }

            progressProperty.unbind();

            if (state instanceof TerracottaState.Bootstrap) {
                statusProperty.set(i18n("terracotta.status.bootstrap"));
                progressProperty.set(-1);
                nodesProperty.setAll();
            } else if (state instanceof TerracottaState.Uninitialized uninitialized) {
                String fork = uninitialized.hasLegacy() ? "update" : "not_exist";

                statusProperty.set(i18n("terracotta.status.uninitialized." + fork));
                progressProperty.set(0);

                TextFlow body = FXUtils.segmentToTextFlow(i18n("terracotta.confirm.desc"), Controllers::onHyperlinkAction);
                body.getStyleClass().add("terracotta-hint");
                body.setLineSpacing(4);

                var download = createLargeTitleLineButton();
                download.setLeading(FXUtils.newBuiltinImage("/assets/img/terracotta.png"));
                download.setTitle(i18n(String.format("terracotta.status.uninitialized.%s.title", fork)));
                download.setSubtitle(i18n("terracotta.status.uninitialized.desc"));
                download.setTrailingIcon(SVG.ARROW_FORWARD, ICON_SIZE);
                download.setOnAction(event -> {
                    TerracottaState.Preparing s = TerracottaManager.download();
                    if (s != null) {
                        UI_STATE.set(s);
                    }

                    if (uninitialized.hasLegacy() && I18n.isUseChinese()) {
                        Object feedback = config().getShownTips().get(FEEDBACK_TIP);
                        if (!(feedback instanceof Number number) || number.intValue() < 1) {
                            Controllers.confirm(i18n("terracotta.feedback.desc"), i18n("terracotta.feedback.title"), () -> {
                                FXUtils.openLink(TerracottaMetadata.FEEDBACK_LINK);
                                config().getShownTips().put(FEEDBACK_TIP, 1);
                            }, () -> {
                            });
                        }
                    }
                });

                nodesProperty.setAll(body, download, getThirdPartyDownloadNodes());
            } else if (state instanceof TerracottaState.Preparing) {
                statusProperty.set(i18n("terracotta.status.preparing"));
                progressProperty.bind(((TerracottaState.Preparing) state).progressProperty());
                nodesProperty.setAll(getThirdPartyDownloadNodes());
            } else if (state instanceof TerracottaState.Launching) {
                statusProperty.set(i18n("terracotta.status.launching"));
                progressProperty.set(-1);
                nodesProperty.setAll();
            } else if (state instanceof TerracottaState.Unknown) {
                statusProperty.set(i18n("terracotta.status.unknown"));
                progressProperty.set(-1);
                nodesProperty.setAll();
            } else if (state instanceof TerracottaState.Waiting) {
                statusProperty.set(i18n("terracotta.status.waiting"));
                progressProperty.set(1);

                TextFlow flow = FXUtils.segmentToTextFlow(i18n("terracotta.confirm.desc"), Controllers::onHyperlinkAction);
                flow.getStyleClass().add("terracotta-hint");
                flow.setLineSpacing(4);

                var host = createLargeTitleLineButton();
                host.setLeading(SVG.HOST, ICON_SIZE);
                host.setTitle(i18n("terracotta.status.waiting.host.title"));
                host.setSubtitle(i18n("terracotta.status.waiting.host.desc"));
                host.setTrailingIcon(SVG.ARROW_FORWARD, ICON_SIZE);
                host.setOnAction(event -> {
                    if (LauncherHelper.countMangedProcesses() >= 1) {
                        TerracottaState.HostScanning s1 = TerracottaManager.setScanning();
                        if (s1 != null) {
                            UI_STATE.set(s1);
                        }
                    } else {
                        Controllers.dialog(new MessageDialogPane.Builder(
                                i18n("terracotta.status.waiting.host.launch.desc"),
                                i18n("terracotta.status.waiting.host.launch.title"),
                                MessageDialogPane.MessageType.QUESTION
                        ).addAction(i18n("version.launch"), () -> {
                            Profile profile = Profiles.getSelectedProfile();
                            Versions.launch(profile, profile.getSelectedVersion(), launcherHelper -> {
                                launcherHelper.setKeep();
                                launcherHelper.setDisableOfflineSkin();
                            });
                        }).addCancel(i18n("terracotta.status.waiting.host.launch.skip"), () -> {
                            TerracottaState.HostScanning s1 = TerracottaManager.setScanning();
                            if (s1 != null) {
                                UI_STATE.set(s1);
                            }
                        }).addCancel(() -> {
                        }).build());
                    }
                });

                var guest = createLargeTitleLineButton();
                guest.setLeading(SVG.ADD_CIRCLE, ICON_SIZE);
                guest.setTitle(i18n("terracotta.status.waiting.guest.title"));
                guest.setSubtitle(i18n("terracotta.status.waiting.guest.desc"));
                guest.setTrailingIcon(SVG.ARROW_FORWARD, ICON_SIZE);
                guest.setOnAction(event -> {
                    Controllers.prompt(i18n("terracotta.status.waiting.guest.prompt.title"), (code, handler) -> {
                        Task<TerracottaState.GuestConnecting> task = TerracottaManager.setGuesting(code);
                        if (task != null) {
                            task.whenComplete(Schedulers.javafx(), (s, e) -> {
                                if (e != null) {
                                    handler.reject(i18n("terracotta.status.waiting.guest.prompt.invalid"));
                                } else {
                                    handler.resolve();
                                    UI_STATE.set(s);
                                }
                            }).setSignificance(Task.TaskSignificance.MINOR).start();
                        } else {
                            handler.resolve();
                        }
                    });
                });

                if (ThreadLocalRandom.current().nextDouble() < 0.02D) {
                    var feedback = createLargeTitleLineButton();
                    feedback.setLeading(SVG.FEEDBACK, ICON_SIZE);
                    feedback.setTitle(i18n("terracotta.feedback.title"));
                    feedback.setSubtitle(i18n("terracotta.feedback.desc"));
                    feedback.setTrailingIcon(SVG.OPEN_IN_NEW, ICON_SIZE);
                    FXUtils.onClicked(feedback, () -> FXUtils.openLink(TerracottaMetadata.FEEDBACK_LINK));

                    nodesProperty.setAll(flow, host, guest, feedback);
                } else {
                    nodesProperty.setAll(flow, host, guest);
                }
            } else if (state instanceof TerracottaState.HostScanning) {
                statusProperty.set(i18n("terracotta.status.scanning"));
                progressProperty.set(-1);

                TextFlow body = FXUtils.segmentToTextFlow(i18n("terracotta.status.scanning.desc"), Controllers::onHyperlinkAction);
                body.getStyleClass().add("terracotta-hint");
                body.setLineSpacing(4);

                var room = createLargeTitleLineButton();
                room.setLeading(SVG.ARROW_BACK, ICON_SIZE);
                room.setTitle(i18n("terracotta.back"));
                room.setSubtitle(i18n("terracotta.status.scanning.back"));
                room.setOnAction(event -> {
                    TerracottaState.Waiting s = TerracottaManager.setWaiting();
                    if (s != null) {
                        UI_STATE.set(s);
                    }
                });

                nodesProperty.setAll(body, room);
            } else if (state instanceof TerracottaState.HostStarting) {
                statusProperty.set(i18n("terracotta.status.host_starting"));
                progressProperty.set(-1);

                var room = createLargeTitleLineButton();
                room.setLeading(SVG.ARROW_BACK, ICON_SIZE);
                room.setTitle(i18n("terracotta.back"));
                room.setSubtitle(i18n("terracotta.status.host_starting.back"));
                room.setOnAction(event -> {
                    TerracottaState.Waiting s = TerracottaManager.setWaiting();
                    if (s != null) {
                        UI_STATE.set(s);
                    }
                });

                nodesProperty.setAll(room);
            } else if (state instanceof TerracottaState.HostOK hostOK) {
                if (hostOK.isForkOf(legacyState)) {
                    if (nodesProperty.get(nodesProperty.size() - 1) instanceof PlayerProfileUI profileUI) {
                        profileUI.updateProfiles(hostOK.getProfiles());
                    } else { // Should NOT happen
                        nodesProperty.add(new PlayerProfileUI(hostOK.getProfiles()));
                    }
                    return;
                } else {
                    String cs = hostOK.getCode();
                    copyCode(cs);

                    statusProperty.set(i18n("terracotta.status.host_ok"));
                    progressProperty.set(1);

                    VBox code = new VBox(4);
                    code.setAlignment(Pos.CENTER);
                    {
                        Label desc = new Label(i18n("terracotta.status.host_ok.code"));
                        desc.setMouseTransparent(true);

                        Label label = new Label(cs);
                        label.setMouseTransparent(true);
                        label.setStyle("-fx-font-size: 24");
                        label.setAlignment(Pos.CENTER);
                        VBox.setMargin(label, new Insets(10, 0, 10, 0));

                        code.getChildren().setAll(desc, label);
                    }
                    code.setCursor(Cursor.HAND);
                    FXUtils.onClicked(code, () -> copyCode(cs));

                    var copy = createLargeTitleLineButton();
                    copy.setLeading(SVG.CONTENT_COPY, ICON_SIZE);
                    copy.setTitle(i18n("terracotta.status.host_ok.code.copy"));
                    copy.setSubtitle(i18n("terracotta.status.host_ok.code.desc"));
                    FXUtils.onClicked(copy, () -> copyCode(cs));

                    var back = createLargeTitleLineButton();
                    back.setLeading(SVG.ARROW_BACK, ICON_SIZE);
                    back.setTitle(i18n("terracotta.back"));
                    back.setSubtitle(i18n("terracotta.status.host_ok.back"));
                    back.setOnAction(event -> {
                        TerracottaState.Waiting s = TerracottaManager.setWaiting();
                        if (s != null) {
                            UI_STATE.set(s);
                        }
                    });

                    if (hostOK.getProfiles().isEmpty()) {
                        nodesProperty.setAll(code, copy, back);
                    } else {
                        nodesProperty.setAll(code, copy, back, new PlayerProfileUI(hostOK.getProfiles()));
                    }
                }
            } else if (state instanceof TerracottaState.GuestConnecting || state instanceof TerracottaState.GuestStarting) {
                statusProperty.set(i18n("terracotta.status.guest_starting"));
                progressProperty.set(-1);

                var room = createLargeTitleLineButton();
                room.setLeading(SVG.ARROW_BACK, ICON_SIZE);
                room.setTitle(i18n("terracotta.back"));
                room.setSubtitle(i18n("terracotta.status.guest_starting.back"));
                room.setOnAction(event -> {
                    TerracottaState.Waiting s = TerracottaManager.setWaiting();
                    if (s != null) {
                        UI_STATE.set(s);
                    }
                });

                nodesProperty.clear();
                if (state instanceof TerracottaState.GuestStarting) {
                    TerracottaState.GuestStarting.Difficulty difficulty = ((TerracottaState.GuestStarting) state).getDifficulty();
                    if (difficulty != null && difficulty != TerracottaState.GuestStarting.Difficulty.UNKNOWN) {
                        var info = createLargeTitleLineButton();
                        info.setLeading(switch (difficulty) {
                            case UNKNOWN -> throw new AssertionError();
                            case EASIEST, SIMPLE -> SVG.INFO;
                            case MEDIUM, TOUGH -> SVG.WARNING;
                        }, ICON_SIZE);

                        String difficultyID = difficulty.name().toLowerCase(Locale.ROOT);
                        info.setTitle(i18n(String.format("terracotta.difficulty.%s", difficultyID)));
                        info.setSubtitle(i18n("terracotta.difficulty.estimate_only"));

                        nodesProperty.add(info);
                    }
                }

                nodesProperty.add(room);
            } else if (state instanceof TerracottaState.GuestOK guestOK) {
                if (guestOK.isForkOf(legacyState)) {
                    if (nodesProperty.get(nodesProperty.size() - 1) instanceof PlayerProfileUI profileUI) {
                        profileUI.updateProfiles(guestOK.getProfiles());
                    } else { // Should NOT happen
                        nodesProperty.add(new PlayerProfileUI(guestOK.getProfiles()));
                    }
                    return;
                } else {
                    statusProperty.set(i18n("terracotta.status.guest_ok"));
                    progressProperty.set(1);

                    var tutorial = createLargeTitleLineButton();
                    tutorial.setTitle(i18n("terracotta.status.guest_ok.title"));
                    tutorial.setSubtitle(i18n("terracotta.status.guest_ok.desc", guestOK.getUrl()));

                    var back = createLargeTitleLineButton();
                    back.setLeading(SVG.ARROW_BACK, ICON_SIZE);
                    back.setTitle(i18n("terracotta.back"));
                    back.setSubtitle(i18n("terracotta.status.guest_ok.back"));
                    back.setOnAction(event -> {
                        TerracottaState.Waiting s = TerracottaManager.setWaiting();
                        if (s != null) {
                            UI_STATE.set(s);
                        }
                    });

                    if (guestOK.getProfiles().isEmpty()) {
                        nodesProperty.setAll(tutorial, back);
                    } else {
                        nodesProperty.setAll(tutorial, back, new PlayerProfileUI(guestOK.getProfiles()));
                    }
                }
            } else if (state instanceof TerracottaState.Exception exception) {
                statusProperty.set(i18n("terracotta.status.exception.desc." + exception.getType().name().toLowerCase(Locale.ROOT)));
                progressProperty.set(1);
                nodesProperty.setAll();

                var back = createLargeTitleLineButton();
                back.setLeading(SVG.ARROW_BACK, ICON_SIZE);
                back.setTitle(i18n("terracotta.back"));
                back.setSubtitle(i18n("terracotta.status.exception.back"));
                back.setOnAction(event -> {
                    TerracottaState.Waiting s = TerracottaManager.setWaiting();
                    if (s != null) {
                        UI_STATE.set(s);
                    }
                });

                SpinnerPane exportLog = new SpinnerPane();
                var exportLogInner = createLargeTitleLineButton();
                exportLogInner.setLeading(SVG.OUTPUT, ICON_SIZE);
                exportLogInner.setTitle(i18n("terracotta.export_log"));
                exportLogInner.setSubtitle(i18n("terracotta.export_log.desc"));
                exportLog.setContent(exportLogInner);
                ComponentList.setNoPadding(exportLog);
                // FIXME: SpinnerPane loses its content width in loading state.
                exportLog.minHeightProperty().bind(back.heightProperty());

                exportLogInner.setOnAction(event -> {
                    exportLog.setLoading(true);

                    TerracottaManager.exportLogs().thenAcceptAsync(Schedulers.io(), data -> {
                        if (data == null || data.isEmpty()) {
                            return;
                        }

                        Path path = Path.of("terracotta-log-" + LocalDateTime.now().format(
                                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss")
                        ) + ".zip").toAbsolutePath();
                        try (Zipper zipper = new Zipper(path)) {
                            zipper.putTextFile(data, StandardCharsets.UTF_8, "terracotta.log");
                            try (OutputStream os = zipper.putStream("hmcl-latest.log")) {
                                Logger.LOG.exportLogs(os);
                            }
                        }
                        FXUtils.showFileInExplorer(path);
                    }).thenRunAsync(
                            () -> Thread.sleep(3000)
                    ).whenComplete(
                            Schedulers.javafx(),
                            e -> exportLog.setLoading(false)
                    ).start();
                });

                nodesProperty.setAll(back, exportLog);
            } else if (state instanceof TerracottaState.Fatal fatal) {
                String message = i18n("terracotta.status.fatal." + fatal.getType().name().toLowerCase(Locale.ROOT));

                statusProperty.set(message);
                progressProperty.set(1);

                if (fatal.isRecoverable()) {
                    var retry = createLargeTitleLineButton();
                    retry.setLeading(SVG.RESTORE, ICON_SIZE);
                    retry.setTitle(i18n("terracotta.status.fatal.retry"));
                    retry.setSubtitle(message);
                    retry.setOnAction(event -> {
                        TerracottaState s = TerracottaManager.recover();
                        if (s != null) {
                            UI_STATE.set(s);
                        }
                    });

                    if (fatal.getType() == TerracottaState.Fatal.Type.NETWORK) {
                        nodesProperty.setAll(retry, getThirdPartyDownloadNodes());
                    } else {
                        nodesProperty.setAll(retry);
                    }
                } else {
                    nodesProperty.setAll();
                }
            } else {
                throw new AssertionError(state.getClass().getName());
            }

            ComponentList components = new ComponentList();
            {
                VBox statusPane = new VBox(8);
                VBox.setMargin(statusPane, new Insets(0, 0, 0, 4));
                {
                    Label status = new Label();
                    status.textProperty().bind(statusProperty);
                    JFXProgressBar progress = new JFXProgressBar();
                    progress.progressProperty().bind(progressProperty);
                    progress.setMaxWidth(Double.MAX_VALUE);

                    statusPane.getChildren().setAll(status, progress);
                }

                ObservableList<Node> children = components.getContent();
                children.add(statusPane);
                children.addAll(nodesProperty);
            }
            // Prevent the shadow of components from being clipped
            StackPane.setMargin(components, new Insets(0, 0, 5, 0));
            transition.setContent(components, ContainerAnimations.SLIDE_UP_FADE_IN);
        };
        listener.changed(UI_STATE, null, UI_STATE.get());
        holder.add(listener);
        UI_STATE.addListener(new WeakChangeListener<>(listener));

        VBox content = new VBox(10);
        content.getChildren().add(ComponentList.createComponentListTitle(i18n("terracotta.status")));
        if (!LocaleUtils.IS_CHINA_MAINLAND) {
            HintPane hintPane = new HintPane(MessageDialogPane.MessageType.WARNING);
            hintPane.setText(i18n("terracotta.unsupported.region"));
            content.getChildren().add(hintPane);
        }
        content.getChildren().add(transition);
        content.setPadding(new Insets(10));
        content.setFillWidth(true);

        ScrollPane scrollPane = new ScrollPane(content);
        FXUtils.smoothScrolling(scrollPane);
        scrollPane.setFitToWidth(true);

        getChildren().setAll(scrollPane);
    }

    private ComponentSublist getThirdPartyDownloadNodes() {
        ComponentSublist locals = new ComponentSublist();

        var header = new LinePane();
        header.getStyleClass().add("no-padding");
        header.setLargeTitle(true);
        header.setMinHeight(LinePane.USE_COMPUTED_SIZE);
        header.setMouseTransparent(true);
        header.setLeading(FXUtils.newBuiltinImage("/assets/img/terracotta.png"));
        header.setTitle(i18n("terracotta.from_local.title"));
        header.setSubtitle(i18n("terracotta.from_local.desc"));
        locals.setHeaderLeft(header);

        for (TerracottaMetadata.Link link : TerracottaMetadata.PACKAGE_LINKS) {
            LineButton item = new LineButton();
            item.setTrailingIcon(SVG.OPEN_IN_NEW);
            item.setTitle(link.description().getText(I18n.getLocale().getCandidateLocales()));
            item.setOnAction(event -> Controllers.dialog(
                    i18n("terracotta.from_local.guide", TerracottaMetadata.PACKAGE_NAME),
                    i18n("message.info"), MessageDialogPane.MessageType.INFO,
                    () -> FXUtils.openLink(link.link())
            ));
            locals.getContent().add(item);
        }
        return locals;
    }

    private void copyCode(String code) {
        FXUtils.copyText(code, i18n("terracotta.status.host_ok.code.copy.toast"));
    }

    private static final double ICON_SIZE = 28;

    private static LineButton createLargeTitleLineButton() {
        var lineButton = new LineButton();
        lineButton.setLargeTitle(true);
        return lineButton;
    }

    private static final class PlayerProfileUI extends VBox {
        private final TransitionPane transition;

        public PlayerProfileUI(List<TerracottaProfile> profiles) {
            super(8);
            VBox.setMargin(this, new Insets(0, 0, 0, 4));
            {
                Label status = new Label();
                status.setText(i18n("terracotta.player_list"));

                transition = new TransitionPane();
                getChildren().setAll(status, transition);

                updateProfiles(profiles);
            }
        }

        private void updateProfiles(List<TerracottaProfile> profiles) {
            VBox pane = new VBox(8);

            for (TerracottaProfile profile : profiles) {
                TwoLineListItem item = new TwoLineListItem();
                item.setTitle(profile.getName());
                item.setSubtitle(profile.getVendor());
                item.addTag(i18n("terracotta.player_kind." + profile.getType().name().toLowerCase(Locale.ROOT)));
                pane.getChildren().add(item);
            }

            this.transition.setContent(pane, ContainerAnimations.SLIDE_UP_FADE_IN);
        }
    }
}
