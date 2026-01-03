/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2025 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.ui.nbt;

import com.jfoenix.controls.JFXButton;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.scene.layout.BorderPane;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.ui.construct.PageCloseEvent;
import org.jackhuang.hmcl.ui.construct.SpinnerPane;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.util.StringUtils;

import java.io.IOException;
import java.nio.file.Path;

import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * @author Glavo
 */
public final class NBTEditorPage extends SpinnerPane implements DecoratorPage {
    private final ReadOnlyObjectWrapper<State> state;
    private final Path file;
    private final NBTFileType type;

    private final BorderPane root = new BorderPane();
    JFXButton cancelButton;

    public NBTEditorPage(Path file) throws IOException {
        getStyleClass().add("gray-background");

        this.state = new ReadOnlyObjectWrapper<>(new State(i18n("nbt.title", file.toString()), null, true, true, true));
        this.file = file;
        this.type = NBTFileType.ofFile(file);

        if (type == null) {
            throw new IOException("Unknown type of file " + file);
        }

        setContent(root);
        setLoading(true);

//        FXUtils.applyDragListener(this,
//                NBTFileType::isNBTFileByExtension,
//                paths -> {
//                    Path path = paths.get(0);
//                    try {
//                        fireEvent(new PageCloseEvent());
//                        Controllers.navigate(new NBTEditorPage(path));
//                    } catch (Throwable e) {
//                        LOG.warning("Fail to open nbt file", e);
//                        Controllers.dialog(i18n("nbt.open.failed") + "\n\n" + StringUtils.getStackTrace(e),
//                                i18n("message.error"), MessageDialogPane.MessageType.ERROR);
//                    }
//                });

        cancelButton = FXUtils.newRaisedButton(i18n("button.cancel"));
        cancelButton.setOnAction(e -> fireEvent(new PageCloseEvent()));
        onEscPressed(this, cancelButton::fire);

        loadTree();
    }

    public void loadTree() {
        Task.supplyAsync(() -> type.readAsTree(file))
                .whenComplete(Schedulers.javafx(), (result, exception) -> {
                    if (exception == null) {
                        setLoading(false);
                        NBTTreeView view = new NBTTreeView(result);
                        BorderPane.setMargin(view, new Insets(10));
                        onEscPressed(view, cancelButton::fire);
                        root.setCenter(view);
                    } else {
                        LOG.warning("Fail to open nbt file", exception);
                        Controllers.dialog(i18n("nbt.open.failed") + "\n\n" + StringUtils.getStackTrace(exception), null, MessageDialogPane.MessageType.WARNING, cancelButton::fire);
                    }
                }).start();
    }

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state;
    }

    @Override
    public void refresh() {
        root.setCenter(null);
        setLoading(true);
        loadTree();
    }

    @Override
    public BooleanProperty refreshableProperty() {
        return new SimpleBooleanProperty(true);
    }
}
