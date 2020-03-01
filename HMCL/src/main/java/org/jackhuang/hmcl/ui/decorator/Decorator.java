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
package org.jackhuang.hmcl.ui.decorator;

import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import javafx.scene.layout.Background;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class Decorator extends Control {
    private final ListProperty<Node> drawer = new SimpleListProperty<>(FXCollections.observableArrayList());
    private final ListProperty<Node> content = new SimpleListProperty<>(FXCollections.observableArrayList());
    private final ListProperty<Node> container = new SimpleListProperty<>(FXCollections.observableArrayList());
    private final ObjectProperty<Background> contentBackground = new SimpleObjectProperty<>();
    private final ObjectProperty<DecoratorPage.State> state = new SimpleObjectProperty<>();
    private final StringProperty drawerTitle = new SimpleStringProperty();
    private final ObjectProperty<Runnable> onCloseButtonAction = new SimpleObjectProperty<>();
    private final ObjectProperty<EventHandler<ActionEvent>> onCloseNavButtonAction = new SimpleObjectProperty<>();
    private final ObjectProperty<EventHandler<ActionEvent>> onBackNavButtonAction = new SimpleObjectProperty<>();
    private final ObjectProperty<EventHandler<ActionEvent>> onRefreshNavButtonAction = new SimpleObjectProperty<>();
    private final BooleanProperty canRefresh = new SimpleBooleanProperty(false);
    private final BooleanProperty canBack = new SimpleBooleanProperty(false);
    private final BooleanProperty canClose = new SimpleBooleanProperty(false);
    private final BooleanProperty showCloseAsHome = new SimpleBooleanProperty(false);
    private final Stage primaryStage;
    private StackPane drawerWrapper;

    public Decorator(Stage primaryStage) {
        this.primaryStage = primaryStage;

        primaryStage.initStyle(StageStyle.UNDECORATED);
    }

    public Stage getPrimaryStage() {
        return primaryStage;
    }

    public StackPane getDrawerWrapper() {
        return drawerWrapper;
    }

    void setDrawerWrapper(StackPane drawerWrapper) {
        this.drawerWrapper = drawerWrapper;
    }

    public ObservableList<Node> getDrawer() {
        return drawer.get();
    }

    public ListProperty<Node> drawerProperty() {
        return drawer;
    }

    public void setDrawer(ObservableList<Node> drawer) {
        this.drawer.set(drawer);
    }

    public ObservableList<Node> getContent() {
        return content.get();
    }

    public ListProperty<Node> contentProperty() {
        return content;
    }

    public void setContent(ObservableList<Node> content) {
        this.content.set(content);
    }

    public DecoratorPage.State getState() {
        return state.get();
    }

    public ObjectProperty<DecoratorPage.State> stateProperty() {
        return state;
    }

    public void setState(DecoratorPage.State state) {
        this.state.set(state);
    }

    public String getDrawerTitle() {
        return drawerTitle.get();
    }

    public StringProperty drawerTitleProperty() {
        return drawerTitle;
    }

    public void setDrawerTitle(String drawerTitle) {
        this.drawerTitle.set(drawerTitle);
    }

    public Runnable getOnCloseButtonAction() {
        return onCloseButtonAction.get();
    }

    public ObjectProperty<Runnable> onCloseButtonActionProperty() {
        return onCloseButtonAction;
    }

    public void setOnCloseButtonAction(Runnable onCloseButtonAction) {
        this.onCloseButtonAction.set(onCloseButtonAction);
    }

    public ObservableList<Node> getContainer() {
        return container.get();
    }

    public ListProperty<Node> containerProperty() {
        return container;
    }

    public void setContainer(ObservableList<Node> container) {
        this.container.set(container);
    }

    public Background getContentBackground() {
        return contentBackground.get();
    }

    public ObjectProperty<Background> contentBackgroundProperty() {
        return contentBackground;
    }

    public void setContentBackground(Background contentBackground) {
        this.contentBackground.set(contentBackground);
    }

    public BooleanProperty canRefreshProperty() {
        return canRefresh;
    }

    public BooleanProperty canBackProperty() {
        return canBack;
    }

    public BooleanProperty canCloseProperty() {
        return canClose;
    }

    public BooleanProperty showCloseAsHomeProperty() {
        return showCloseAsHome;
    }

    public ObjectProperty<EventHandler<ActionEvent>> onBackNavButtonActionProperty() {
        return onBackNavButtonAction;
    }

    public ObjectProperty<EventHandler<ActionEvent>> onCloseNavButtonActionProperty() {
        return onCloseNavButtonAction;
    }

    public ObjectProperty<EventHandler<ActionEvent>> onRefreshNavButtonActionProperty() {
        return onRefreshNavButtonAction;
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new DecoratorSkin(this);
    }

    public void minimize() {
        primaryStage.setIconified(true);
    }

    public void close() {
        onCloseButtonAction.get().run();
    }
}
