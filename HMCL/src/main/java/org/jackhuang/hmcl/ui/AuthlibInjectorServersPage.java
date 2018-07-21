/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com>
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
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hmcl.ui;

import static org.jackhuang.hmcl.ui.FXUtils.loadFXML;
import static org.jackhuang.hmcl.ui.FXUtils.smoothScrolling;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorServer;
import org.jackhuang.hmcl.ui.wizard.DecoratorPage;
import org.jackhuang.hmcl.util.MappedObservableList;

import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import static org.jackhuang.hmcl.setting.ConfigHolder.CONFIG;

public class AuthlibInjectorServersPage extends StackPane implements DecoratorPage {
    private final StringProperty title = new SimpleStringProperty(this, "title", i18n("account.injector.manage.title"));

    @FXML private ScrollPane scrollPane;
    @FXML private VBox listPane;
    @FXML private StackPane contentPane;

    private ObservableList<AuthlibInjectorServerItem> serverItems;

    public AuthlibInjectorServersPage() {
        loadFXML(this, "/assets/fxml/authlib-injector-servers.fxml");
        smoothScrolling(scrollPane);

        serverItems = MappedObservableList.create(CONFIG.getAuthlibInjectorServers(), this::createServerItem);
        Bindings.bindContent(listPane.getChildren(), serverItems);
    }

    private AuthlibInjectorServerItem createServerItem(AuthlibInjectorServer server) {
        return new AuthlibInjectorServerItem(server,
                item -> CONFIG.getAuthlibInjectorServers().remove(item.getServer()));
    }

    @FXML
    private void onAdd() {
        Controllers.dialog(new AddAuthlibInjectorServerPane());
    }

    public String getTitle() {
        return title.get();
    }

    @Override
    public StringProperty titleProperty() {
        return title;
    }

    public void setTitle(String title) {
        this.title.set(title);
    }
}
