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
import javafx.beans.DefaultProperty;
import javafx.beans.InvalidationListener;
import javafx.beans.property.*;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.SkinBase;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.animation.TransitionPane;

@DefaultProperty("content")
public class SpinnerPane extends Control {
    private final ObjectProperty<Node> content = new SimpleObjectProperty<>(this, "content");
    private final BooleanProperty loading = new SimpleBooleanProperty(this, "loading");
    private final StringProperty failedReason = new SimpleStringProperty(this, "failedReason");

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

    @Override
    protected Skin createDefaultSkin() {
        return new Skin(this);
    }

    private static class Skin extends SkinBase<SpinnerPane> {
        private final JFXSpinner spinner = new JFXSpinner();
        private final StackPane contentPane = new StackPane();
        private final StackPane topPane = new StackPane();
        private final TransitionPane root = new TransitionPane();
        private final StackPane failedPane = new StackPane();
        private final Label failedReasonLabel = new Label();
        @SuppressWarnings("FieldCanBeLocal") // prevent from gc.
        private final InvalidationListener observer;

        protected Skin(SpinnerPane control) {
            super(control);

            root.getStyleClass().add("spinner-pane");
            topPane.getChildren().setAll(spinner);
            topPane.getStyleClass().add("notice-pane");
            failedPane.getChildren().setAll(failedReasonLabel);

            FXUtils.onChangeAndOperate(getSkinnable().content, newValue -> {
                if (newValue == null) {
                    contentPane.getChildren().clear();
                } else {
                    contentPane.getChildren().setAll(newValue);
                }
            });
            getChildren().setAll(root);

            observer = FXUtils.observeWeak(() -> {
                if (getSkinnable().getFailedReason() != null) {
                    root.setContent(failedPane, ContainerAnimations.FADE.getAnimationProducer());
                    failedReasonLabel.setText(getSkinnable().getFailedReason());
                } else if (getSkinnable().isLoading()) {
                    root.setContent(topPane, ContainerAnimations.FADE.getAnimationProducer());
                } else {
                    root.setContent(contentPane, ContainerAnimations.FADE.getAnimationProducer());
                }
            }, getSkinnable().loadingProperty(), getSkinnable().failedReasonProperty());
        }
    }

    public interface State {}

    public static class LoadedState implements State {}

    public static class LoadingState implements State {}

    public static class FailedState implements State {
        private final String reason;

        public FailedState(String reason) {
            this.reason = reason;
        }

        public String getReason() {
            return reason;
        }
    }
}
