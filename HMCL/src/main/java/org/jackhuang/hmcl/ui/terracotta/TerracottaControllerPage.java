// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.terracotta;

import javafx.beans.binding.Binding;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.geometry.Insets;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextFlow;
import org.glavo.m3fx.controls.M3Banner;
import org.glavo.m3fx.controls.M3ExpandableSettingItem;
import org.glavo.m3fx.controls.M3ListItem;
import org.glavo.m3fx.controls.M3ListPane;
import org.glavo.m3fx.controls.M3ListSectionHeader;
import org.glavo.m3fx.controls.M3ProgressBar;
import org.glavo.m3fx.controls.M3Text;
import org.glavo.m3fx.controls.M3TextRole;
import org.jackhuang.hmcl.game.LauncherHelper;
import org.jackhuang.hmcl.setting.GameDirectoryManager;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.terracotta.TerracottaManager;
import org.jackhuang.hmcl.terracotta.TerracottaMetadata;
import org.jackhuang.hmcl.terracotta.TerracottaState;
import org.jackhuang.hmcl.terracotta.profile.TerracottaProfile;
import org.jackhuang.hmcl.ui.*;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.ui.instances.Instances;
import org.jackhuang.hmcl.util.RegionUtils;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.io.Zipper;
import org.jackhuang.hmcl.util.javafx.BindingMapping;
import org.jetbrains.annotations.Nullable;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

import static org.jackhuang.hmcl.setting.SettingsManager.state;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

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

    @SuppressWarnings("FieldCanBeLocal")
    private final Binding<Boolean> focusedBinding;
    @SuppressWarnings("FieldCanBeLocal")
    private final WeakListenerHolder holder = new WeakListenerHolder();

    /// The player section for the active room, including when its list is empty.
    private @Nullable PlayerProfileUI players;

    /* FIXME: It's sucked to have such a long logic, containing UI for all states defined in TerracottaState, with unclear control flows.
         Consider moving UI into multiple files for each state respectively. */
    public TerracottaControllerPage() {
        focusedBinding = BindingMapping.of(this.sceneProperty())
                .flatMap(it -> it != null ? it.windowProperty() : new SimpleObjectProperty<>())
                .flatMap(it -> it != null ? it.focusedProperty() : null, () -> false);

        FXUtils.onChange(focusedBinding, focused -> {
            // Run daemon process only if HMCL is focused and is displaying current node.
            TerracottaManager.switchDaemon(getScene() != null && focused);
        });

        TransitionPane transition = new TransitionPane();

        ObjectProperty<String> statusProperty = new SimpleObjectProperty<>();
        DoubleProperty progressProperty = new SimpleDoubleProperty();

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
                        MessageType.ERROR
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

            if (players != null && state.isForkOf(legacyState)) {
                if (state instanceof TerracottaState.HostOK host)
                    players.updateProfiles(host.getProfiles());
                else if (state instanceof TerracottaState.GuestOK guest)
                    players.updateProfiles(guest.getProfiles());
                return;
            }

            players = null;
            progressProperty.unbind();
            VBox stateContent = new VBox(8);
            M3ListPane actions = createList();
            var rows = actions.getItems();

            if (state instanceof TerracottaState.Bootstrap) {
                statusProperty.set(i18n("terracotta.status.bootstrap"));
                progressProperty.set(-1);
            } else if (state instanceof TerracottaState.Uninitialized uninitialized) {
                String fork = uninitialized.hasLegacy() ? "update" : "not_exist";

                statusProperty.set(i18n("terracotta.status.uninitialized." + fork));
                progressProperty.set(0);

                stateContent.getChildren().add(createHint(i18n("terracotta.confirm.desc")));

                M3ListItem download = new M3ListItem(i18n(String.format("terracotta.status.uninitialized.%s.title", fork)));
                download.setLeading(new ImageView(FXUtils.newBuiltinImage("/assets/img/terracotta.png")));
                download.setSupportingText(i18n("terracotta.status.uninitialized.desc"));
                download.setTrailing(SVG.ARROW_FORWARD.createIcon());
                download.setOnAction(event -> {
                    TerracottaState.Preparing s = TerracottaManager.download();
                    if (s != null) {
                        UI_STATE.set(s);
                    }

                    if (uninitialized.hasLegacy() && I18n.isUseChinese()) {
                        Object feedback = state().getShownTips().get(FEEDBACK_TIP);
                        if (!(feedback instanceof Number number) || number.intValue() < 1) {
                            Controllers.confirm(i18n("terracotta.feedback.desc.update"), i18n("terracotta.feedback.title"), () -> {
                                FXUtils.openLink(TerracottaMetadata.FEEDBACK_LINK);
                                state().getShownTips().put(FEEDBACK_TIP, 1);
                            }, () -> {
                            });
                        }
                    }
                });

                rows.addAll(download, createThirdPartyDownloads(actions));
            } else if (state instanceof TerracottaState.Preparing) {
                statusProperty.set(i18n("terracotta.status.preparing"));
                progressProperty.bind(((TerracottaState.Preparing) state).progressProperty());
                rows.add(createThirdPartyDownloads(actions));
            } else if (state instanceof TerracottaState.Launching) {
                statusProperty.set(i18n("terracotta.status.launching"));
                progressProperty.set(-1);
            } else if (state instanceof TerracottaState.Unknown) {
                statusProperty.set(i18n("terracotta.status.unknown"));
                progressProperty.set(-1);
            } else if (state instanceof TerracottaState.Waiting) {
                statusProperty.set(i18n("terracotta.status.waiting"));
                progressProperty.set(1);

                stateContent.getChildren().add(createHint(i18n("terracotta.confirm.desc")));

                M3ListItem host = new M3ListItem(i18n("terracotta.status.waiting.host.title"));
                host.setLeading(SVG.HOST.createIcon());
                host.setSupportingText(i18n("terracotta.status.waiting.host.desc"));
                host.setTrailing(SVG.ARROW_FORWARD.createIcon());
                host.setOnAction(event -> {
                    if (LauncherHelper.countMangedProcesses() >= 1) {
                        TerracottaState.HostScanning s1 = TerracottaManager.setScanning();
                        if (s1 != null) {
                            UI_STATE.set(s1);
                        }
                    } else {
                        Controllers.dialog(new MessageDialogBuilder(
                                i18n("terracotta.status.waiting.host.launch.desc"),
                                i18n("terracotta.status.waiting.host.launch.title"),
                                MessageType.QUESTION
                        ).addAction(i18n("instance.launch"), () -> {
                            var repository = GameDirectoryManager.getSelectedRepository();
                            Instances.launch(repository.getSelectedInstance(), launcherHelper -> {
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

                M3ListItem guest = new M3ListItem(i18n("terracotta.status.waiting.guest.title"));
                guest.setLeading(SVG.ADD_CIRCLE.createIcon());
                guest.setSupportingText(i18n("terracotta.status.waiting.guest.desc"));
                guest.setTrailing(SVG.ARROW_FORWARD.createIcon());
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
                    }, "", new RequiredValidator(i18n("input.not_empty")));
                });

                if (ThreadLocalRandom.current().nextDouble() < 0.02D) {
                    M3ListItem feedback = new M3ListItem(i18n("terracotta.feedback.title"));
                    feedback.setLeading(SVG.FEEDBACK.createIcon());
                    feedback.setSupportingText(i18n("terracotta.feedback.desc"));
                    feedback.setTrailing(SVG.OPEN_IN_NEW.createIcon());
                    feedback.setOnAction(event -> FXUtils.openLink(TerracottaMetadata.FEEDBACK_LINK));

                    rows.addAll(host, guest, feedback);
                } else {
                    rows.addAll(host, guest);
                }
            } else if (state instanceof TerracottaState.HostScanning) {
                statusProperty.set(i18n("terracotta.status.scanning"));
                progressProperty.set(-1);

                stateContent.getChildren().add(createHint(i18n("terracotta.status.scanning.desc")));

                M3ListItem room = createBackItem(i18n("terracotta.status.scanning.back"));

                rows.add(room);
            } else if (state instanceof TerracottaState.HostStarting) {
                statusProperty.set(i18n("terracotta.status.host_starting"));
                progressProperty.set(-1);

                M3ListItem room = createBackItem(i18n("terracotta.status.host_starting.back"));

                rows.add(room);
            } else if (state instanceof TerracottaState.HostOK hostOK) {
                String cs = hostOK.getCode();
                copyCode(cs);

                statusProperty.set(i18n("terracotta.status.host_ok"));
                progressProperty.set(1);

                M3ListItem copy = new M3ListItem(cs);
                copy.setOverlineText(i18n("terracotta.status.host_ok.code"));
                copy.setSupportingText(i18n("terracotta.status.host_ok.code.desc"));
                copy.setTrailing(SVG.CONTENT_COPY.createIcon());
                copy.setAccessibleHelp(i18n("terracotta.status.host_ok.code.copy"));
                copy.setOnAction(event -> copyCode(cs));

                M3ListItem back = createBackItem(i18n("terracotta.status.host_ok.back"));

                rows.addAll(copy, back);
                players = new PlayerProfileUI(hostOK.getProfiles());
            } else if (state instanceof TerracottaState.GuestConnecting || state instanceof TerracottaState.GuestStarting) {
                statusProperty.set(i18n("terracotta.status.guest_starting"));
                progressProperty.set(-1);

                M3ListItem room = createBackItem(i18n("terracotta.status.guest_starting.back"));

                if (state instanceof TerracottaState.GuestStarting) {
                    TerracottaState.GuestStarting.Difficulty difficulty = ((TerracottaState.GuestStarting) state).getDifficulty();
                    if (difficulty != null && difficulty != TerracottaState.GuestStarting.Difficulty.UNKNOWN) {
                        String difficultyID = difficulty.name().toLowerCase(Locale.ROOT);
                        M3ListItem info = new M3ListItem(i18n("terracotta.difficulty." + difficultyID));
                        info.setFocusTraversable(false);
                        info.setMouseTransparent(true);
                        info.setLeading((switch (difficulty) {
                            case UNKNOWN -> throw new AssertionError();
                            case EASIEST, SIMPLE -> SVG.INFO;
                            case MEDIUM, TOUGH -> SVG.WARNING;
                        }).createIcon());

                        info.setSupportingText(i18n("terracotta.difficulty.estimate_only"));

                        rows.add(info);
                    }
                }

                rows.add(room);
            } else if (state instanceof TerracottaState.GuestOK guestOK) {
                statusProperty.set(i18n("terracotta.status.guest_ok"));
                progressProperty.set(1);

                M3ListItem tutorial = new M3ListItem(i18n("terracotta.status.guest_ok.title"));
                tutorial.setFocusTraversable(false);
                tutorial.setMouseTransparent(true);
                tutorial.setSupportingText(i18n("terracotta.status.guest_ok.desc", guestOK.getUrl()));

                M3ListItem back = createBackItem(i18n("terracotta.status.guest_ok.back"));

                rows.addAll(tutorial, back);
                players = new PlayerProfileUI(guestOK.getProfiles());
            } else if (state instanceof TerracottaState.Exception exception) {
                statusProperty.set(i18n("terracotta.status.exception.desc." + exception.getType().name().toLowerCase(Locale.ROOT)));
                progressProperty.set(1);

                M3ListItem back = createBackItem(i18n("terracotta.status.exception.back"));

                M3ListItem exportLog = new M3ListItem(i18n("terracotta.export_log"));
                exportLog.setLeading(SVG.OUTPUT.createIcon());
                exportLog.setSupportingText(i18n("terracotta.export_log.desc"));

                exportLog.setOnAction(event -> {
                    exportLog.setDisable(true);
                    var indicator = FXUtils.newSpinner();
                    indicator.setIndicatorSize(24);
                    exportLog.setLeading(indicator);

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
                                LOG.exportLogs(os);
                            }
                        }
                        FXUtils.showFileInExplorer(path);
                    }).thenRunAsync(
                            () -> Thread.sleep(3000)
                    ).whenComplete(
                            Schedulers.javafx(),
                            e -> {
                                exportLog.setLeading(SVG.OUTPUT.createIcon());
                                exportLog.setDisable(false);
                            }
                    ).start();
                });

                rows.addAll(back, exportLog);
            } else if (state instanceof TerracottaState.Fatal fatal) {
                String message = i18n("terracotta.status.fatal." + fatal.getType().name().toLowerCase(Locale.ROOT));

                statusProperty.set(message);
                progressProperty.set(1);

                if (fatal.isRecoverable()) {
                    M3ListItem retry = new M3ListItem(i18n("terracotta.status.fatal.retry"));
                    retry.setLeading(SVG.RESTORE.createIcon());
                    retry.setSupportingText(message);
                    retry.setOnAction(event -> {
                        TerracottaState s = TerracottaManager.recover();
                        if (s != null) {
                            UI_STATE.set(s);
                        }
                    });

                    if (fatal.getType() == TerracottaState.Fatal.Type.NETWORK) {
                        rows.addAll(retry, createThirdPartyDownloads(actions));
                    } else {
                        rows.add(retry);
                    }
                }
            } else {
                throw new AssertionError(state.getClass().getName());
            }

            if (!rows.isEmpty())
                stateContent.getChildren().add(actions);
            if (players != null)
                stateContent.getChildren().add(players);
            transition.setContent(stateContent, TabController.TabAnimation.TAB_UP);
        };
        listener.changed(UI_STATE, null, UI_STATE.get());
        holder.add(listener);
        UI_STATE.addListener(new WeakChangeListener<>(listener));

        VBox content = new VBox();
        content.setPadding(new Insets(10));
        content.getChildren().add(new M3ListSectionHeader(i18n("terracotta.status")));
        if (!"CN".equals(RegionUtils.MACHINE_REGION)) {
            M3Banner warning = new M3Banner(i18n("terracotta.unsupported.region"));
            warning.setIcon(SVG.WARNING.createIcon());
            content.getChildren().add(warning);
        }

        M3Text status = new M3Text();
        status.setRole(M3TextRole.BODY_LARGE);
        status.textProperty().bind(statusProperty);
        status.setWrapText(true);
        M3ProgressBar progress = new M3ProgressBar();
        progress.progressProperty().bind(progressProperty);
        progress.setMaxWidth(Double.MAX_VALUE);
        VBox statusPane = new VBox(8, status, progress);
        statusPane.setPadding(new Insets(8, 16, 16, 16));
        content.getChildren().addAll(statusPane, transition);

        ScrollPane scrollPane = new ScrollPane(content);
        ScrollUtils.installSmoothScrolling(scrollPane);
        scrollPane.setFitToWidth(true);

        getChildren().setAll(scrollPane);
    }

    /// Returns to the multiplayer lobby from the current connection state.
    private static M3ListItem createBackItem(String description) {
        M3ListItem back = new M3ListItem(i18n("terracotta.back"));
        back.setLeading(SVG.ARROW_BACK.createIcon());
        back.setSupportingText(description);
        back.setOnAction(event -> {
            TerracottaState.Waiting waiting = TerracottaManager.setWaiting();
            if (waiting != null)
                UI_STATE.set(waiting);
        });
        return back;
    }

    /// Creates an expandable list of download links that show installation guidance before opening.
    private static M3ExpandableSettingItem createThirdPartyDownloads(M3ListPane parent) {
        M3ExpandableSettingItem group = new M3ExpandableSettingItem(i18n("terracotta.from_local.title"));
        group.setSupportingText(i18n("terracotta.from_local.desc"));
        // The list skin caches its preferred height across changes inside an expandable row.
        group.expandedProperty().addListener(observable -> parent.requestLayout());
        ImageView icon = new ImageView(FXUtils.newBuiltinImage("/assets/img/terracotta.png"));
        icon.setMouseTransparent(true);
        group.setLeading(icon);

        M3ListPane links = new M3ListPane();
        links.setPadding(new Insets(0, 0, 0, 48));
        for (TerracottaMetadata.Link link : TerracottaMetadata.PACKAGE_LINKS) {
            M3ListItem item = new M3ListItem(link.description().getText(I18n.getLocale().getCandidateLocales()));
            item.setTrailing(SVG.OPEN_IN_NEW.createIcon());
            item.setOnAction(event -> Controllers.dialog(
                    i18n("terracotta.from_local.guide", TerracottaMetadata.PACKAGE_NAME),
                    i18n("message.info"), MessageType.INFO,
                    () -> FXUtils.openLink(link.link())
            ));
            links.getItems().add(item);
        }
        group.setContent(links);
        return group;
    }

    private void copyCode(String code) {
        FXUtils.copyText(code, i18n("terracotta.status.host_ok.code.copy.toast"));
    }

    /// Keeps adjacent rows on a single rounded surface, including their interaction layers.
    private static M3ListPane createList() {
        M3ListPane list = new M3ListPane();
        list.getStyleClass().add("terracotta-list");
        FXUtils.setOverflowHidden(list, 32);
        return list;
    }

    /// Preserves inline help links and wraps the instructions to the available width.
    private static TextFlow createHint(String text) {
        TextFlow hint = FXUtils.segmentToTextFlow(text, Controllers::onHyperlinkAction);
        hint.getStyleClass().add("terracotta-hint");
        hint.setLineSpacing(4);
        hint.setPadding(new Insets(0, 16, 8, 16));
        return hint;
    }

    /// Shows the current room members and animates roster changes.
    private static final class PlayerProfileUI extends VBox {
        /// Hosts the current player list.
        private final TransitionPane transition = new TransitionPane();

        /// Creates a section that is hidden while the room has no player profiles.
        PlayerProfileUI(List<TerracottaProfile> profiles) {
            getChildren().setAll(new M3ListSectionHeader(i18n("terracotta.player_list")), transition);
            managedProperty().bind(visibleProperty());
            updateProfiles(profiles);
        }

        /// Updates the displayed player names, vendors and room roles.
        private void updateProfiles(List<TerracottaProfile> profiles) {
            setVisible(!profiles.isEmpty());
            if (profiles.isEmpty()) {
                transition.clearContent();
                return;
            }

            M3ListPane list = createList();
            for (TerracottaProfile profile : profiles) {
                M3ListItem item = new M3ListItem(profile.getName());
                item.setSupportingText(profile.getVendor());
                item.setTrailingSupportingText(i18n("terracotta.player_kind." + profile.getType().name().toLowerCase(Locale.ROOT)));
                item.setFocusTraversable(false);
                item.setMouseTransparent(true);
                list.getItems().add(item);
            }
            transition.setContent(list, TabController.TabAnimation.TAB_UP);
        }
    }
}
