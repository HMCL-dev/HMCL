/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.ui.construct;

import com.jfoenix.controls.JFXSpinner;
import javafx.beans.property.*;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.SkinBase;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.animation.TransitionPane;

/// A spinner pane that can show spinner, failed reason, or content.
public class SpinnerPane extends Control {
    private static final String DEFAULT_STYLE_CLASS = "spinner-pane";

    public SpinnerPane() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
    }

    public void showSpinner() {
        setLoading(true);
    }

    public void hideSpinner() {
        setFailedReason(null);
        setLoading(false);
    }

    private void updateContent() {
        if (getSkin() instanceof Skin skin) {
            skin.updateContent();
        }
    }

    private ObjectProperty<Node> content;

    public ObjectProperty<Node> contentProperty() {
        if (content == null)
            content = new ObjectPropertyBase<>() {
                @Override
                public Object getBean() {
                    return SpinnerPane.this;
                }

                @Override
                public String getName() {
                    return "content";
                }

                @Override
                protected void invalidated() {
                    updateContent();
                }
            };
        return content;
    }

    public Node getContent() {
        return contentProperty().get();
    }

    public void setContent(Node content) {
        contentProperty().set(content);
    }

    private BooleanProperty loading;

    public BooleanProperty loadingProperty() {
        if (loading == null)
            loading = new BooleanPropertyBase() {
                @Override
                public Object getBean() {
                    return SpinnerPane.this;
                }

                @Override
                public String getName() {
                    return "loading";
                }

                @Override
                protected void invalidated() {
                    updateContent();
                }
            };
        return loading;
    }

    public boolean isLoading() {
        return loading != null && loading.get();
    }

    public void setLoading(boolean loading) {
        loadingProperty().set(loading);
    }

    private StringProperty failedReason;

    public StringProperty failedReasonProperty() {
        if (failedReason == null)
            failedReason = new StringPropertyBase() {
                @Override
                public Object getBean() {
                    return SpinnerPane.this;
                }

                @Override
                public String getName() {
                    return "failedReason";
                }

                @Override
                protected void invalidated() {
                    updateContent();
                }
            };
        return failedReason;
    }

    public String getFailedReason() {
        return failedReason != null ? failedReason.get() : null;
    }

    public void setFailedReason(String failedReason) {
        failedReasonProperty().set(failedReason);
    }

    private ObjectProperty<EventHandler<Event>> onFailedAction;

    public final ObjectProperty<EventHandler<Event>> onFailedActionProperty() {
        if (onFailedAction == null) {
            onFailedAction = new ObjectPropertyBase<>() {
                @Override
                public Object getBean() {
                    return SpinnerPane.this;
                }

                @Override
                public String getName() {
                    return "onFailedAction";
                }

                @Override
                protected void invalidated() {
                    setEventHandler(FAILED_ACTION, get());
                }
            };
        }
        return onFailedAction;
    }

    public final EventHandler<Event> getOnFailedAction() {
        return onFailedAction != null ? onFailedAction.get() : null;
    }

    public final void setOnFailedAction(EventHandler<Event> value) {
        onFailedActionProperty().set(value);
    }

    @Override
    protected SkinBase<SpinnerPane> createDefaultSkin() {
        return new Skin(this);
    }

    private static final class Skin extends SkinBase<SpinnerPane> {
        private final TransitionPane root = new TransitionPane();

        Skin(SpinnerPane control) {
            super(control);

            updateContent();
            this.getChildren().setAll(root);
        }

        private StackPane contentPane;
        private StackPane spinnerPane;
        private StackPane failedPane;
        private Label failedReasonLabel;

        void updateContent() {
            SpinnerPane control = getSkinnable();

            Node nextContent;
            if (control.isLoading()) {
                if (spinnerPane == null) {
                    spinnerPane = new StackPane(new JFXSpinner());
                    spinnerPane.getStyleClass().add("notice-pane");
                }
                nextContent = spinnerPane;
            } else if (control.getFailedReason() != null) {
                if (failedPane == null) {
                    failedReasonLabel = new Label();
                    failedPane = new StackPane(failedReasonLabel);
                    failedPane.getStyleClass().add("notice-pane");
                    FXUtils.onClicked(failedPane, () -> control.fireEvent(new Event(SpinnerPane.FAILED_ACTION)));
                }
                failedReasonLabel.setText(control.getFailedReason());
                nextContent = failedPane;
            } else {
                if (contentPane == null) {
                    contentPane = new StackPane();
                }

                Node content = control.getContent();
                if (content != null)
                    contentPane.getChildren().setAll(content);
                else
                    contentPane.getChildren().clear();

                nextContent = contentPane;
            }

            if (nextContent != failedPane && failedReasonLabel != null) {
                failedReasonLabel.setText(null);
            }

            root.setContent(nextContent, ContainerAnimations.FADE);
        }
    }

    public static final EventType<Event> FAILED_ACTION = new EventType<>(Event.ANY, "FAILED_ACTION");
}
