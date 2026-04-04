/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.ui.versions;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.ui.construct.ComponentList;
import org.jackhuang.hmcl.ui.construct.DialogPane;
import org.jackhuang.hmcl.ui.construct.LineSelectButton;
import org.jackhuang.hmcl.ui.construct.LineToggleButton;
import org.jackhuang.hmcl.util.platform.OperatingSystem;

import java.util.concurrent.CompletableFuture;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class ScriptFormatDialog extends DialogPane {

    public record Result(String format, boolean appendSuffix) {}

    private final CompletableFuture<Result> future = new CompletableFuture<>();
    private final LineSelectButton<String> formatSelectButton;
    private final LineToggleButton appendSuffixToggle;

    public ScriptFormatDialog(String unsupportedExtension) {
        String title = unsupportedExtension.isEmpty()
                ? i18n("version.launch_script.unsupported.title.no_ext")
                : i18n("version.launch_script.unsupported.title");
        setTitle(title);

        String message = unsupportedExtension.isEmpty()
                ? i18n("version.launch_script.unsupported.message.no_ext")
                : i18n("version.launch_script.unsupported.message", unsupportedExtension);
        Label messageLabel = new Label(message);
        messageLabel.setWrapText(true);
        messageLabel.setPadding(new Insets(0, 0, 10, 0));

        formatSelectButton = new LineSelectButton<>();
        formatSelectButton.setTitle(i18n("version.launch_script.select_type"));
        formatSelectButton.setConverter(item -> item);
        formatSelectButton.setItems(getAvailableFormats());
        formatSelectButton.setValue(getAvailableFormats()[0]);

        appendSuffixToggle = new LineToggleButton();
        appendSuffixToggle.setTitle(i18n("version.launch_script.append_suffix"));
        appendSuffixToggle.setSelected(true);

        ComponentList content = new ComponentList();
        content.getContent().addAll(formatSelectButton, appendSuffixToggle);

        setBody(new VBox(messageLabel, content));
    }

    @Override
    protected void onAccept() {
        String format = formatSelectButton.getValue();
        if (format != null) {
            future.complete(new Result(format, appendSuffixToggle.isSelected()));
        }
        super.onAccept();
    }

    @Override
    protected void onCancel() {
        future.cancel(false);
        super.onCancel();
    }

    private String[] getAvailableFormats() {
        return OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS
                ? new String[]{i18n("extension.bat"), i18n("extension.ps1")}
                : new String[]{i18n("extension.sh"), i18n("extension.ps1")};
    }

    public CompletableFuture<Result> getCompletableFuture() {
        return future;
    }
}
