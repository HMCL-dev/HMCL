// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import javafx.scene.layout.VBox;
import org.glavo.m3fx.controls.M3Button;
import org.glavo.m3fx.controls.M3ButtonVariant;
import org.glavo.m3fx.controls.M3DatePickerField;
import org.glavo.m3fx.controls.M3TextInputVariant;
import org.glavo.m3fx.localization.M3Localizer;
import org.jackhuang.hmcl.util.Pair;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.i18n.translator.Translator_lzh;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// Selects an inclusive date range. Either endpoint may be left blank.
@NotNullByDefault
public final class DateRangeDialog extends LauncherDialog {
    /// Opens with both dates set to today and delivers the endpoints on confirmation.
    public DateRangeDialog(@Nullable Consumer<Pair<@Nullable LocalDate, @Nullable LocalDate>> onConfirm) {
        getDialogPane().setHeadline(i18n("button.select_date"));
        LocalDate today = LocalDate.now();
        boolean classicalChinese = I18n.getTranslator() instanceof Translator_lzh;
        var localizer = new M3Localizer(classicalChinese ? Locale.TRADITIONAL_CHINESE : I18n.getLocale().getLocale()) {
            /// Uses the launcher's calendar label for the popup button.
            @Override
            public String getOpenDatePickerText() {
                return i18n("button.select_date");
            }

            /// Displays day numbers in Chinese numerals when using Classical Chinese.
            @Override
            public String formatDayOfMonth(LocalDate date) {
                return classicalChinese ? Translator_lzh.numberToString(date.getDayOfMonth()) : super.formatDayOfMonth(date);
            }
        };
        M3DatePickerField from = dateField(today, localizer, i18n("button.select_date.from"));
        M3DatePickerField to = dateField(today, localizer, i18n("button.select_date.to"));
        setFormContent(new VBox(12, from, to));

        var cancel = new M3Button(i18n("button.cancel"), M3ButtonVariant.TEXT);
        cancel.setCancelButton(true);
        var ok = new M3Button(i18n("button.ok"), M3ButtonVariant.TEXT);
        ok.setOnAction(event -> {
            for (M3DatePickerField field : List.of(from, to)) {
                if (!field.commitEditorText()) {
                    event.consume();
                    field.getValidationFocusTarget().requestFocus();
                    return;
                }
            }
            if (onConfirm != null)
                onConfirm.accept(Pair.pair(from.getValue(), to.getValue()));
        });
        getDialogPane().getActions().setAll(cancel, ok);
    }

    /// Creates a labeled endpoint with a calendar in the launcher's language.
    private static M3DatePickerField dateField(LocalDate initialDate, M3Localizer localizer, String label) {
        var field = new M3DatePickerField(initialDate);
        field.setVariant(M3TextInputVariant.OUTLINED);
        field.setLocalizer(localizer);
        field.setLabelText(label);
        field.setInvalidTextErrorText(i18n("button.select_date.invalid"));
        return field;
    }
}
