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
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
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
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
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
        TransitionPane transition = new TransitionPane();

        ObjectProperty<String> statusProperty = new SimpleObjectProperty<>();
        DoubleProperty progressProperty = new SimpleDoubleProperty();
        ObservableList<Node> nodesProperty = FXCollections.observableList(new ArrayList<>());

        FXUtils.applyDragListener(this, path -> {
            TerracottaState state = UI_STATE.get();

            if (state instanceof TerracottaState.Uninitialized ||
                    state instanceof TerracottaState.Preparing preparing && preparing.hasInstallFence() ||
                    state instanceof TerracottaState.Fatal fatal && fatal.getType() == TerracottaState.Fatal.Type.NETWORK
            ) {
                return Files.isReadable(path) && FileUtils.getName(path).toLowerCase(Locale.ROOT).endsWith(".tar.gz");
            } else {
                return false;
            }
        }, files -> {
            Path path = files.get(0);

            if (!TerracottaManager.validate(path)) {
                Controllers.dialog(
                        i18n("terracotta.from_local.file_name_mismatch", TerracottaMetadata.PACKAGE_NAME, FileUtils.getName(path)),
                        i18n("message.error"),
                        MessageDialogPane.MessageType.ERROR
                );
                return;
            }

            TerracottaState state = UI_STATE.get(), next;
            if (state instanceof TerracottaState.Uninitialized || state instanceof TerracottaState.Preparing preparing && preparing.hasInstallFence()) {
                if (state instanceof TerracottaState.Uninitialized uninitialized && !uninitialized.hasLegacy()) {
                    Controllers.confirmWithCountdown(i18n("terracotta.confirm.desc"), i18n("terracotta.confirm.title"), 5,
                            MessageDialogPane.MessageType.INFO, () -> {
                                TerracottaState.Preparing s = TerracottaManager.install(path);
                                if (s != null) {
                                    UI_STATE.set(s);
                                }
                            }, null);
                    return;
                }

                next = TerracottaManager.install(path);
            } else if (state instanceof TerracottaState.Fatal fatal && fatal.getType() == TerracottaState.Fatal.Type.NETWORK) {
                next = TerracottaManager.recover(path);
            } else {
                return;
            }
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

                LineButton download = LineButton.of();
                download.setLeftImage(FXUtils.newBuiltinImage("/assets/img/terracotta.png"));
                download.setTitle(i18n(String.format("terracotta.status.uninitialized.%s.title", fork)));
                download.setSubtitle(i18n("terracotta.status.uninitialized.desc"));
                download.setRightIcon(SVG.ARROW_FORWARD);
                FXUtils.onClicked(download, () -> {
                    TerracottaState.Preparing s = TerracottaManager.install(null);
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

                LineButton host = LineButton.of();
                host.setLeftIcon(SVG.HOST);
                host.setTitle(i18n("terracotta.status.waiting.host.title"));
                host.setSubtitle(i18n("terracotta.status.waiting.host.desc"));
                host.setRightIcon(SVG.ARROW_FORWARD);
                FXUtils.onClicked(host, () -> {
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

                LineButton guest = LineButton.of();
                guest.setLeftIcon(SVG.ADD_CIRCLE);
                guest.setTitle(i18n("terracotta.status.waiting.guest.title"));
                guest.setSubtitle(i18n("terracotta.status.waiting.guest.desc"));
                guest.setRightIcon(SVG.ARROW_FORWARD);
                FXUtils.onClicked(guest, () -> {
                    Controllers.prompt(i18n("terracotta.status.waiting.guest.prompt.title"), (code, resolve, reject) -> {
                        Task<TerracottaState.GuestStarting> task = TerracottaManager.setGuesting(code);
                        if (task != null) {
                            task.whenComplete(Schedulers.javafx(), (s, e) -> {
                                if (e != null) {
                                    reject.accept(i18n("terracotta.status.waiting.guest.prompt.invalid"));
                                } else {
                                    resolve.run();
                                    UI_STATE.set(s);
                                }
                            }).setSignificance(Task.TaskSignificance.MINOR).start();
                        } else {
                            resolve.run();
                        }
                    });
                });

                if (ThreadLocalRandom.current().nextDouble() < 0.02D) {
                    LineButton feedback = LineButton.of();
                    feedback.setLeftIcon(SVG.FEEDBACK);
                    feedback.setTitle(i18n("terracotta.feedback.title"));
                    feedback.setSubtitle(i18n("terracotta.feedback.desc"));
                    feedback.setRightIcon(SVG.OPEN_IN_NEW);
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

                LineButton room = LineButton.of();
                room.setLeftIcon(SVG.ARROW_BACK);
                room.setTitle(i18n("terracotta.back"));
                room.setSubtitle(i18n("terracotta.status.scanning.back"));
                FXUtils.onClicked(room, () -> {
                    TerracottaState.Waiting s = TerracottaManager.setWaiting();
                    if (s != null) {
                        UI_STATE.set(s);
                    }
                });

                nodesProperty.setAll(body, room);
            } else if (state instanceof TerracottaState.HostStarting) {
                statusProperty.set(i18n("terracotta.status.host_starting"));
                progressProperty.set(-1);

                LineButton room = LineButton.of();
                room.setLeftIcon(SVG.ARROW_BACK);
                room.setTitle(i18n("terracotta.back"));
                room.setSubtitle(i18n("terracotta.status.host_starting.back"));
                FXUtils.onClicked(room, () -> {
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

                    LineButton copy = LineButton.of();
                    copy.setLeftIcon(SVG.CONTENT_COPY);
                    copy.setTitle(i18n("terracotta.status.host_ok.code.copy"));
                    copy.setSubtitle(i18n("terracotta.status.host_ok.code.desc"));
                    FXUtils.onClicked(copy, () -> copyCode(cs));

                    LineButton back = LineButton.of();
                    back.setLeftIcon(SVG.ARROW_BACK);
                    back.setTitle(i18n("terracotta.back"));
                    back.setSubtitle(i18n("terracotta.status.host_ok.back"));
                    FXUtils.onClicked(back, () -> {
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
            } else if (state instanceof TerracottaState.GuestStarting) {
                statusProperty.set(i18n("terracotta.status.guest_starting"));
                progressProperty.set(-1);

                LineButton room = LineButton.of();
                room.setLeftIcon(SVG.ARROW_BACK);
                room.setTitle(i18n("terracotta.back"));
                room.setSubtitle(i18n("terracotta.status.guest_starting.back"));
                FXUtils.onClicked(room, () -> {
                    TerracottaState.Waiting s = TerracottaManager.setWaiting();
                    if (s != null) {
                        UI_STATE.set(s);
                    }
                });

                nodesProperty.setAll(room);
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

                    LineButton tutorial = LineButton.of();
                    tutorial.setTitle(i18n("terracotta.status.guest_ok.title"));
                    tutorial.setSubtitle(i18n("terracotta.status.guest_ok.desc", guestOK.getUrl()));

                    LineButton back = LineButton.of();
                    back.setLeftIcon(SVG.ARROW_BACK);
                    back.setTitle(i18n("terracotta.back"));
                    back.setSubtitle(i18n("terracotta.status.guest_ok.back"));
                    FXUtils.onClicked(back, () -> {
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

                LineButton back = LineButton.of();
                back.setLeftIcon(SVG.ARROW_BACK);
                back.setTitle(i18n("terracotta.back"));
                back.setSubtitle(i18n("terracotta.status.exception.back"));
                FXUtils.onClicked(back, () -> {
                    TerracottaState.Waiting s = TerracottaManager.setWaiting();
                    if (s != null) {
                        UI_STATE.set(s);
                    }
                });

                SpinnerPane exportLog = new SpinnerPane();
                LineButton exportLogInner = LineButton.of();
                exportLogInner.setLeftIcon(SVG.OUTPUT);
                exportLogInner.setTitle(i18n("terracotta.export_log"));
                exportLogInner.setSubtitle(i18n("terracotta.export_log.desc"));
                exportLog.setContent(exportLogInner);
                exportLog.getProperties().put("ComponentList.noPadding", true);
                // FIXME: SpinnerPane loses its content width in loading state.
                exportLog.minHeightProperty().bind(back.heightProperty());

                FXUtils.onClicked(exportLogInner, () -> {
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
                    LineButton retry = LineButton.of();
                    retry.setLeftIcon(SVG.RESTORE);
                    retry.setTitle(i18n("terracotta.status.fatal.retry"));
                    retry.setSubtitle(message);
                    FXUtils.onClicked(retry, () -> {
                        TerracottaState s = TerracottaManager.recover(null);
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

    private ComponentList getThirdPartyDownloadNodes() {
        ComponentSublist locals = new ComponentSublist();
        locals.setComponentPadding(false);

        LineButton header = LineButton.of(false);
        header.setLeftImage(FXUtils.newBuiltinImage("/assets/img/terracotta.png"));
        header.setTitle(i18n("terracotta.from_local.title"));
        header.setSubtitle(i18n("terracotta.from_local.desc"));
        locals.setHeaderLeft(header);

        for (TerracottaMetadata.Link link : TerracottaMetadata.PACKAGE_LINKS) {
            HBox node = new HBox();
            node.setAlignment(Pos.CENTER_LEFT);
            node.setPadding(new Insets(10, 16, 10, 16));

            Label description = new Label(link.description().getText(I18n.getLocale().getCandidateLocales()));
            HBox placeholder = new HBox();
            HBox.setHgrow(placeholder, Priority.ALWAYS);
            Node icon = SVG.OPEN_IN_NEW.createIcon(16);
            node.getChildren().setAll(description, placeholder, icon);

            String url = link.link();
            RipplerContainer container = new RipplerContainer(node);
            container.setOnMouseClicked(ev -> Controllers.dialog(
                    i18n("terracotta.from_local.guide", TerracottaMetadata.PACKAGE_NAME),
                    i18n("message.info"), MessageDialogPane.MessageType.INFO,
                    () -> FXUtils.openLink(url)
            ));
            container.getProperties().put("ComponentList.noPadding", true);
            locals.getContent().add(container);
        }
        return locals;
    }

    private void copyCode(String code) {
        FXUtils.copyText(code, i18n("terracotta.status.host_ok.code.copy.toast"));
    }

    private static final class LineButton extends RipplerContainer {
        private final WeakListenerHolder holder = new WeakListenerHolder();

        private final ObjectProperty<Node> left = new SimpleObjectProperty<>(this, "left");
        private final ObjectProperty<Node> right = new SimpleObjectProperty<>(this, "right");
        private final StringProperty title = new SimpleStringProperty(this, "title", "");
        private final StringProperty subTitle = new SimpleStringProperty(this, "subTitle", "");

        public static LineButton of() {
            return of(true);
        }

        public static LineButton of(boolean padding) {
            HBox container = new HBox();
            if (padding) {
                container.setPadding(new Insets(10, 16, 10, 16));
            }
            container.setAlignment(Pos.CENTER_LEFT);
            container.setCursor(Cursor.HAND);
            container.setSpacing(16);

            LineButton button = new LineButton(container);
            VBox spacing = new VBox();
            HBox.setHgrow(spacing, Priority.ALWAYS);
            button.holder.add(FXUtils.observeWeak(() -> {
                List<Node> nodes = new ArrayList<>(4);
                Node left = button.left.get();
                if (left != null) {
                    nodes.add(left);
                }

                {
                    // FIXME: It's sucked to have the following TwoLineListItem-liked logic whose subtitle is a TextFlow.
                    VBox middle = new VBox();
                    middle.getStyleClass().add("two-line-list-item");
                    middle.setMouseTransparent(true);
                    {
                        HBox firstLine = new HBox();
                        firstLine.getStyleClass().add("first-line");
                        {
                            Label lblTitle = new Label(button.title.get());
                            lblTitle.getStyleClass().add("title");
                            firstLine.getChildren().setAll(lblTitle);
                        }

                        HBox secondLine = new HBox();
                        secondLine.getStyleClass().add("second-line");
                        {
                            Text text = new Text(button.subTitle.get());

                            TextFlow lblSubtitle = new TextFlow(text);
                            lblSubtitle.getStyleClass().add("subtitle");
                            secondLine.getChildren().setAll(lblSubtitle);
                        }

                        middle.getChildren().setAll(firstLine, secondLine);
                    }
                    nodes.add(middle);
                }

                nodes.add(spacing);

                Node right = button.right.get();
                if (right != null) {
                    nodes.add(right);
                }

                container.getChildren().setAll(nodes);
            }, button.title, button.subTitle, button.left, button.right));
            button.getProperties().put("ComponentList.noPadding", true);

            return button;
        }

        private LineButton(Node container) {
            super(container);
        }

        public void setTitle(String title) {
            this.title.set(title);
        }

        public void setSubtitle(String subtitle) {
            this.subTitle.set(subtitle);
        }

        public void setLeftImage(Image left) {
            this.left.set(new ImageView(left));
        }

        public void setLeftIcon(SVG left) {
            this.left.set(left.createIcon(28));
        }

        public void setRightIcon(SVG right) {
            this.right.set(right.createIcon(28));
        }
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
