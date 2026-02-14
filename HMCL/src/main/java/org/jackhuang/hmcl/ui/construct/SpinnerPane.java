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
import javafx.animation.FadeTransition;
import javafx.beans.DefaultProperty;
import javafx.beans.InvalidationListener;
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
import org.jackhuang.hmcl.ui.animation.Motion;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.util.Lang;

@DefaultProperty("content")
public class SpinnerPane extends Control {
    private final ObjectProperty<Node> content = new SimpleObjectProperty<>(this, "content");
    private final BooleanProperty loading = new SimpleBooleanProperty(this, "loading");
    private final StringProperty failedReason = new SimpleStringProperty(this, "failedReason");

    public SpinnerPane(Node content) {
        this();
        setContent(content);
    }

    public SpinnerPane() {
        getStyleClass().add("spinner-pane");
    }

    public void showSpinner() {
        setLoading(true);
    }

    public void hideSpinner() {
        setFailedReason(null);
        setLoading(false);
    }

    public Node getContent() {
        return content.get();
    }

    public ObjectProperty<Node> contentProperty() {
        return content;
    }

    public void setContent(Node content) {
        this.content.set(content);
    }

    public boolean isLoading() {
        return loading.get();
    }

    public BooleanProperty loadingProperty() {
        return loading;
    }

    public void setLoading(boolean loading) {
        this.loading.set(loading);
    }

    public String getFailedReason() {
        return failedReason.get();
    }

    public StringProperty failedReasonProperty() {
        return failedReason;
    }

    public void setFailedReason(String failedReason) {
        this.failedReason.set(failedReason);
    }

    public final ObjectProperty<EventHandler<Event>> onFailedActionProperty() {
        return onFailedAction;
    }

    public final void setOnFailedAction(EventHandler<Event> value) {
        onFailedActionProperty().set(value);
    }

    public final EventHandler<Event> getOnFailedAction() {
        return onFailedActionProperty().get();
    }

    private final ObjectProperty<EventHandler<Event>> onFailedAction = new SimpleObjectProperty<EventHandler<Event>>(this, "onFailedAction") {
        @Override
        protected void invalidated() {
            setEventHandler(FAILED_ACTION, get());
        }
    };

    @Override
    protected SkinBase<SpinnerPane> createDefaultSkin() {
        return new Skin(this);
    }

    private static final class Skin extends SkinBase<SpinnerPane> {
        private final JFXSpinner spinner = new JFXSpinner();

        private final StackPane contentPane = new StackPane();

        private final StackPane topPane = new StackPane();
        private final StackPane failedPane = new StackPane();
        private final Label failedReasonLabel = new Label();

        private final TransitionPane transition = new TransitionPane();

        private static final StackPane EMPTY = Lang.apply(new StackPane(), (stackPane) -> stackPane.setMouseTransparent(true));

        @SuppressWarnings("FieldCanBeLocal")
        private final InvalidationListener observer;

        private FadeTransition contentFadeTransition;

        Skin(SpinnerPane control) {
            super(control);

            topPane.getChildren().setAll(spinner);
            topPane.getStyleClass().add("notice-pane");
            failedPane.getStyleClass().add("notice-pane");
            failedPane.getChildren().setAll(failedReasonLabel);
            FXUtils.onClicked(failedPane, () -> {
                EventHandler<Event> action = control.getOnFailedAction();
                if (action != null)
                    action.handle(new Event(FAILED_ACTION));
            });

            FXUtils.onChangeAndOperate(getSkinnable().content, newValue -> {
                if (newValue == null) {
                    contentPane.getChildren().clear();
                } else {
                    contentPane.getChildren().setAll(newValue);
                }
            });

            transition.setPickOnBounds(false);
            transition.setMouseTransparent(true);

            getChildren().setAll(new StackPane(contentPane, transition));

            observer = FXUtils.observeWeak(() -> {
                boolean isError = getSkinnable().getFailedReason() != null;
                boolean isLoading = getSkinnable().isLoading();
                boolean showOverlay = isError || isLoading;

                animateContentOpacity(showOverlay ? 0.0 : 1.0);
                contentPane.setMouseTransparent(showOverlay);

                transition.setMouseTransparent(!showOverlay);

                if (isError) {
                    failedReasonLabel.setText(getSkinnable().getFailedReason());
                    transition.setContent(failedPane, ContainerAnimations.FADE);
                } else if (isLoading) {
                    transition.setContent(topPane, ContainerAnimations.FADE);
                } else {
                    transition.setContent(EMPTY, ContainerAnimations.FADE);
                }
            }, getSkinnable().loadingProperty(), getSkinnable().failedReasonProperty());
        }

        private void animateContentOpacity(double targetOpacity) {
            if (contentFadeTransition != null) {
                contentFadeTransition.stop();
            }

            if (Math.abs(contentPane.getOpacity() - targetOpacity) < 0.01) {
                contentPane.setOpacity(targetOpacity);
                return;
            }

            contentFadeTransition = new FadeTransition(Motion.SHORT4, contentPane);
            contentFadeTransition.setFromValue(contentPane.getOpacity());
            contentFadeTransition.setToValue(targetOpacity);
            contentFadeTransition.play();
        }
    }

    public static final EventType<Event> FAILED_ACTION = new EventType<>(Event.ANY, "FAILED_ACTION");
}

