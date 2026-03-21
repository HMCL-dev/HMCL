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
package org.jackhuang.hmcl.ui.construct;

import com.jfoenix.validation.base.ValidatorBase;
import javafx.beans.NamedArg;
import javafx.scene.control.TextInputControl;
import org.jackhuang.hmcl.util.Lang;

/// NumberRangeValidator only check whether inputted number is in range, but not if it is a number,
/// if the input is not a number, NumberRangeValidator will not show error message
public class NumberRangeValidator extends ValidatorBase {
    private final int minValue;
    private final int maxValue;

    public NumberRangeValidator(@NamedArg("outOfLimitMessage") String outOfLimitMessage, @NamedArg("minValue") int minValue, @NamedArg("maxValue") int maxValue) {
        super(outOfLimitMessage);
        this.minValue = minValue;
        this.maxValue = maxValue;
    }

    @Override
    protected void eval() {
        if (srcControl.get() instanceof TextInputControl) {
            evalTextInputField();
        }
    }

    private void evalTextInputField() {
        TextInputControl textField = ((TextInputControl) srcControl.get());
        Integer intOrNull = Lang.toIntOrNull(textField.getText());

        if (intOrNull == null) {
            hasErrors.set(false);
        } else {
            hasErrors.set(intOrNull > maxValue || intOrNull < minValue);
        }
    }
}
