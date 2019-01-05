/**
 * Hello Minecraft! Launcher
 * Copyright (C) 2019  huangyuhui <huanghongxun2008@126.com> and contributors
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
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.animation.TransitionHandler;

@DefaultProperty("content")
public class SpinnerPane extends StackPane {
    private final TransitionHandler transitionHandler = new TransitionHandler(this);
    private final JFXSpinner spinner = new JFXSpinner();
    private final StackPane contentPane = new StackPane();
    private final ObjectProperty<Node> content = new SimpleObjectProperty<>(this, "content");

    public SpinnerPane() {
        getChildren().setAll(contentPane);

        content.addListener((a, b, newValue) -> contentPane.getChildren().setAll(newValue));
    }

    public void showSpinner() {
        transitionHandler.setContent(spinner, ContainerAnimations.FADE.getAnimationProducer());
    }

    public void hideSpinner() {
        transitionHandler.setContent(contentPane, ContainerAnimations.FADE.getAnimationProducer());
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
}
