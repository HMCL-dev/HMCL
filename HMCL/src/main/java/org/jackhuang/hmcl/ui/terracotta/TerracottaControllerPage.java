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
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextFlow;
import org.jackhuang.hmcl.game.LauncherHelper;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.Profiles;
import org.jackhuang.hmcl.setting.Theme;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.terracotta.TerracottaManager;
import org.jackhuang.hmcl.terracotta.TerracottaState;
import org.jackhuang.hmcl.terracotta.profile.TerracottaProfile;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.WeakListenerHolder;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.ComponentList;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.ui.construct.RipplerContainer;
import org.jackhuang.hmcl.ui.construct.TwoLineListItem;
import org.jackhuang.hmcl.ui.versions.Versions;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class TerracottaControllerPage extends StackPane {
    private static final ObjectProperty<TerracottaState> UI_STATE = new SimpleObjectProperty<>();

    static {
        FXUtils.onChangeAndOperate(TerracottaManager.stateProperty(), state -> {
            if (state != null) {
                UI_STATE.set(state);
            }
        });
    }

    private final WeakListenerHolder holder = new WeakListenerHolder();

    public TerracottaControllerPage() {
        TransitionPane transition = new TransitionPane();

        ObjectProperty<String> statusProperty = new SimpleObjectProperty<>();
        DoubleProperty progressProperty = new SimpleDoubleProperty();
        ObservableList<Node> nodesProperty = FXCollections.observableList(new ArrayList<>());

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

                TextFlow body = FXUtils.segmentToTextFlow(i18n("terracotta.network_warning"), Controllers::onHyperlinkAction);
                body.setLineSpacing(4);

                LineButton start = LineButton.of();
                start.setLeftImage(FXUtils.newBuiltinImage("/assets/img/icon.png"));
                start.setTitle(i18n(String.format("terracotta.status.uninitialized.%s.title", fork)));
                start.setSubtitle(i18n("terracotta.status.uninitialized.desc"));
                start.setRightIcon(SVG.ARROW_FORWARD);
                FXUtils.onClicked(start, () -> {
                    if (uninitialized.hasLegacy()) {
                        TerracottaState.Preparing s = TerracottaManager.initialize();
                        if (s != null) {
                            UI_STATE.set(s);
                        }
                    } else {
                        Controllers.confirmActionDanger(i18n("terracotta.confirm.desc"), i18n("terracotta.confirm.title"), () -> {
                            TerracottaState.Preparing s = TerracottaManager.initialize();
                            if (s != null) {
                                UI_STATE.set(s);
                            }
                        }, () -> {
                        });
                    }
                });

                nodesProperty.setAll(body, start);
            } else if (state instanceof TerracottaState.Preparing) {
                statusProperty.set(i18n("terracotta.status.preparing"));
                progressProperty.bind(((TerracottaState.Preparing) state).progressProperty());
                nodesProperty.setAll();
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
                            Versions.launch(profile, profile.getSelectedVersion(), LauncherHelper::setKeep);
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

                nodesProperty.setAll(flow, host, guest);
            } else if (state instanceof TerracottaState.HostScanning) {
                statusProperty.set(i18n("terracotta.status.scanning"));
                progressProperty.set(-1);

                TextFlow body = FXUtils.segmentToTextFlow(i18n("terracotta.status.scanning.desc"), Controllers::onHyperlinkAction);
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
                String cs = hostOK.getCode();

                statusProperty.set(i18n("terracotta.status.host_ok"));
                progressProperty.set(1);

                VBox code = new VBox(4);
                code.setAlignment(Pos.CENTER);
                {
                    Label desc = new Label(i18n("terracotta.status.host_ok.code"));
                    {
                        ClipboardContent cp = new ClipboardContent();
                        cp.putString(cs);
                        Clipboard.getSystemClipboard().setContent(cp);
                    }

                    // FIXME: The implementation to display Room Code is ambiguous. Consider using more clearer JavaFX Element in the future.
                    TextField label = new TextField(cs);
                    label.setEditable(false);
                    label.setFocusTraversable(false);
                    label.setAlignment(Pos.CENTER);
                    label.setStyle("-fx-background-color: transparent; -fx-border-color: transparent;");
                    VBox.setMargin(label, new Insets(10, 0, 10, 0));
                    label.setScaleX(1.8);
                    label.setScaleY(1.8);
                    holder.add(FXUtils.onWeakChange(label.selectedTextProperty(), string -> {
                        if (string != null && !string.isEmpty() && !cs.equals(string)) {
                            label.selectAll();
                        }
                    }));

                    code.getChildren().setAll(desc, label);
                }
                FXUtils.onClicked(code, () -> FXUtils.copyText(cs));

                LineButton copy = LineButton.of();
                copy.setLeftIcon(SVG.CONTENT_COPY);
                copy.setTitle(i18n("terracotta.status.host_ok.code.copy"));
                copy.setSubtitle(i18n("terracotta.status.host_ok.code.desc"));
                FXUtils.onClicked(copy, () -> FXUtils.copyText(cs));

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

                nodesProperty.setAll(code, copy, back);
                displayProfiles(hostOK.getProfiles(), nodesProperty);
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
                statusProperty.set(i18n("terracotta.status.guest_ok"));
                progressProperty.set(1);
                LineButton room = LineButton.of();
                room.setLeftIcon(SVG.ARROW_BACK);
                room.setTitle(i18n("terracotta.back"));
                room.setSubtitle(i18n("terracotta.status.guest_ok.back"));
                FXUtils.onClicked(room, () -> {
                    TerracottaState.Waiting s = TerracottaManager.setWaiting();
                    if (s != null) {
                        UI_STATE.set(s);
                    }
                });

                LineButton tutorial = LineButton.of();
                tutorial.setTitle(i18n("terracotta.status.guest_ok.title"));
                tutorial.setSubtitle(i18n("terracotta.status.guest_ok.desc", guestOK.getUrl()));

                nodesProperty.setAll(tutorial, room);
                displayProfiles(guestOK.getProfiles(), nodesProperty);
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

                nodesProperty.setAll(back);
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
                        TerracottaState s = TerracottaManager.recover();
                        if (s != null) {
                            UI_STATE.set(s);
                        }
                    });
                    nodesProperty.setAll(retry);
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

            transition.setContent(components, ContainerAnimations.SWIPE_LEFT_FADE_SHORT);
        };
        listener.changed(UI_STATE, null, UI_STATE.get());
        holder.add(listener);
        UI_STATE.addListener(new WeakChangeListener<>(listener));

        VBox content = new VBox(10);
        content.getChildren().addAll(ComponentList.createComponentListTitle(i18n("terracotta.status")), transition);
        content.setPadding(new Insets(10));
        content.setFillWidth(true);

        ScrollPane scrollPane = new ScrollPane(content);
        FXUtils.smoothScrolling(scrollPane);
        scrollPane.setFitToWidth(true);

        getChildren().setAll(scrollPane);
    }

    private static void displayProfiles(List<TerracottaProfile> profiles, ObservableList<Node> nodes) {
        VBox statusPane = new VBox(8);
        VBox.setMargin(statusPane, new Insets(0, 0, 0, 4));

        Label status = new Label();
        status.setText(i18n("terracotta.player_list"));
        statusPane.getChildren().setAll(status);

        for (TerracottaProfile profile : profiles) {
            TwoLineListItem item = new TwoLineListItem();
            item.setTitle(profile.getName());
            item.setSubtitle(profile.getVendor());
            item.getTags().setAll(i18n("terracotta.player_kind." + profile.getType().name().toLowerCase(Locale.ROOT)));

            statusPane.getChildren().add(item);
        }

        nodes.add(statusPane);
    }

    private static final class LineButton extends RipplerContainer {
        private final WeakListenerHolder holder = new WeakListenerHolder();

        private final TwoLineListItem middle = new TwoLineListItem();
        private final ObjectProperty<Node> left = new SimpleObjectProperty<>();
        private final ObjectProperty<Node> right = new SimpleObjectProperty<>();

        public static LineButton of() {
            HBox container = new HBox();
            container.setPadding(new Insets(10, 16, 10, 16));
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

                nodes.add(button.middle);
                nodes.add(spacing);

                Node right = button.right.get();
                if (right != null) {
                    nodes.add(right);
                }

                container.getChildren().setAll(nodes);
            }, button.middle.titleProperty(), button.middle.subtitleProperty(), button.left, button.right));
            button.getProperties().put("ComponentList.noPadding", true);

            return button;
        }

        private LineButton(Node container) {
            super(container);
        }

        public void setTitle(String title) {
            this.middle.setTitle(title);
        }

        public void setSubtitle(String subtitle) {
            this.middle.setSubtitle(subtitle);
        }

        public void setLeftImage(Image left) {
            this.left.set(new ImageView(left));
        }

        public void setLeftIcon(SVG left) {
            this.left.set(left.createIcon(Theme.blackFill(), 28));
        }

        public void setRightIcon(SVG right) {
            this.right.set(right.createIcon(Theme.blackFill(), 28));
        }
    }
}
