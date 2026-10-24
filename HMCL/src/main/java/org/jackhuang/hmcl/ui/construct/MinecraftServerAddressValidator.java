/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
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

import com.jfoenix.validation.base.ValidatorBase;
import javafx.scene.control.TextInputControl;
import org.jackhuang.hmcl.util.ServerAddress;

import java.util.Objects;

public class MinecraftServerAddressValidator extends ValidatorBase {
    private final boolean ignoreEmpty;

    public MinecraftServerAddressValidator(String message, boolean ignoreEmpty) {
        super(message);
        this.ignoreEmpty = ignoreEmpty;
    }

    @Override
    protected void eval() {
        if (srcControl.get() instanceof TextInputControl) {
            evalTextInputField();
        }
    }

    private void evalTextInputField() {
        TextInputControl textField = ((TextInputControl) srcControl.get());
        String text = textField.getText();

        if (text == null || text.isEmpty()) {
            if (ignoreEmpty) {
                hasErrors.set(false);
                return;
            }
        }

        try {
            ServerAddress.parse(Objects.requireNonNull(text));
            hasErrors.set(false);
        } catch (Exception e) {
            hasErrors.set(true);
        }
    }
}
