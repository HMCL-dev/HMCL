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
package org.jackhuang.hmcl.ui.main;

import javafx.geometry.Insets;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.ComponentList;
import org.jackhuang.hmcl.ui.construct.LineButton;
import org.jackhuang.hmcl.ui.construct.LineRadioButton;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.i18n.SupportedLocale;

import static org.jackhuang.hmcl.setting.SettingsManager.settings;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class LanguageSettingsPage extends ScrollPane {
    public LanguageSettingsPage() {
        this.setFitToWidth(true);

        VBox rootPane = new VBox(10);
        rootPane.setPadding(new Insets(10));
        this.setContent(rootPane);
        FXUtils.smoothScrolling(this);

        {
            ComponentList contributionPaneList = new ComponentList();

            {
                LineButton contributionButton = LineButton.createExternalLinkButton(Metadata.LOCALIZATION_URL);
                contributionButton.setTitle(i18n("settings.launcher.language.contribution.title"));
                contributionButton.setSubtitle(i18n("settings.launcher.language.contribution.subtitle"));
                contributionButton.setLeading(SVG.VOLUNTEER_ACTIVISM_OUTLINE, 28);

                contributionPaneList.getContent().addAll(contributionButton);
            }

            rootPane.getChildren().addAll(ComponentList.createComponentListTitle(i18n("settings.launcher.language.contribution")), contributionPaneList);
        }

        {
            ComponentList languagePaneList = new ComponentList();

            var toggleGroup = new ToggleGroup();
            var currentLocale = I18n.getLocale();
            {
                for (var locale : SupportedLocale.getSupportedLocales()) {
                    LineRadioButton lineRadioButton = new LineRadioButton(toggleGroup);

                    var completeness = locale.getTranslationCompleteness() != 0.0 ? String.format("%d%%", (int) Math.ceil(locale.getTranslationCompleteness() * 100)) : "";

                    if (locale.isSameLanguage(currentLocale)) {
                        lineRadioButton.setSubtitle(i18n("settings.launcher.language.completion", completeness));
                    } else {
                        lineRadioButton.setSubtitle(locale.getDisplayName(currentLocale) + " / " + i18n("settings.launcher.language.completion", completeness));
                    }

                    lineRadioButton.getRadioButton().setUserData(locale);

                    if (locale.equals(currentLocale)) {
                        toggleGroup.selectToggle(lineRadioButton.getRadioButton());
                    }

                    lineRadioButton.setTitle(locale.getDisplayName(locale));
                    languagePaneList.getContent().addAll(lineRadioButton);
                }
                toggleGroup.selectedToggleProperty().map(Toggle::getUserData).addListener(
                        (observable, oldValue, newValue) -> {
                            settings().languageProperty().set((SupportedLocale) newValue);
                        }
                );
            }

            rootPane.getChildren().addAll(ComponentList.createComponentListTitle(i18n("settings.launcher.language")), languagePaneList);
        }
    }
}
