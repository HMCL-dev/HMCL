/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.ui.terracotta;

import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.AdvancedListBox;
import org.jackhuang.hmcl.ui.construct.AdvancedListItem;
import org.jackhuang.hmcl.ui.construct.PageAware;
import org.jackhuang.hmcl.ui.construct.TabHeader;
import org.jackhuang.hmcl.ui.decorator.DecoratorAnimatedPage;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;

import java.util.Locale;

import static org.jackhuang.hmcl.ui.versions.VersionPage.wrap;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class TerracottaPage extends DecoratorAnimatedPage implements DecoratorPage, PageAware {
    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>(State.fromTitle(i18n("terracotta")));
    private final TabHeader tab;
    private final TabHeader.Tab<TerracottaControllerPage> statusPage = new TabHeader.Tab<>("statusPage");
    private final TransitionPane transitionPane = new TransitionPane();

    public TerracottaPage() {
        statusPage.setNodeSupplier(TerracottaControllerPage::new);
        tab = new TabHeader(statusPage);
        tab.select(statusPage);

        transitionPane.setContent(statusPage.getNode(), ContainerAnimations.NONE);
        FXUtils.onChange(tab.getSelectionModel().selectedItemProperty(), newValue -> {
            transitionPane.setContent(newValue.getNode(), ContainerAnimations.FADE);
        });

        AdvancedListItem chatItem = new AdvancedListItem();
        chatItem.setLeftGraphic(wrap(SVG.CHAT));
        chatItem.setActionButtonVisible(false);
        chatItem.setTitle(i18n("chat"));
        chatItem.setOnAction(e -> FXUtils.openLink(Metadata.GROUPS_URL));

        AdvancedListBox sideBar = new AdvancedListBox()
                .addNavigationDrawerTab(tab, statusPage, i18n("terracotta.status"), SVG.TUNE)
                .startCategory(i18n("help").toUpperCase(Locale.ROOT))
                .add(chatItem);
        FXUtils.setLimitWidth(sideBar, 200);
        setLeft(sideBar);

        setCenter(transitionPane);
    }

    @Override
    public void onPageShown() {
        tab.onPageShown();
    }

    @Override
    public void onPageHidden() {
        tab.onPageHidden();
    }

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }
}
