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
package org.jackhuang.hmcl.ui.versions;

import javafx.geometry.Pos;
import org.jackhuang.hmcl.event.Event;
import org.jackhuang.hmcl.event.EventBus;
import org.jackhuang.hmcl.event.RefreshedVersionsEvent;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.setting.GameDirectoryManager;
import org.jackhuang.hmcl.setting.VersionIconType;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.WeakListenerHolder;
import org.jackhuang.hmcl.ui.construct.AdvancedListItem;
import org.jackhuang.hmcl.ui.construct.ImageContainer;

import java.util.function.Consumer;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class GameAdvancedListItem extends AdvancedListItem {
    private final ImageContainer imageContainer;
    private final WeakListenerHolder holder = new WeakListenerHolder();
    private HMCLGameRepository repository;
    @SuppressWarnings({"unused", "FieldCanBeLocal"})
    private Consumer<Event> onVersionIconChangedListener;

    @SuppressWarnings({"unused", "FieldCanBeLocal"})
    private Consumer<RefreshedVersionsEvent> onRefreshedVersionsListener;

    public GameAdvancedListItem() {
        this.imageContainer = new ImageContainer(LEFT_GRAPHIC_SIZE);
        imageContainer.setMouseTransparent(true);
        AdvancedListItem.setAlignment(imageContainer, Pos.CENTER);
        setLeftGraphic(imageContainer);

        holder.add(FXUtils.onWeakChangeAndOperate(GameDirectoryManager.selectedInstanceProperty(), it -> this.loadVersion()));
    }

    private void loadVersion() {
        String version = GameDirectoryManager.getSelectedInstance();

        boolean repositoryChanged = GameDirectoryManager.getSelectedRepository() != repository;
        if (repositoryChanged) {
            repository = GameDirectoryManager.getSelectedRepository();
            onVersionIconChangedListener = repository.onVersionIconChanged.registerWeak(event -> {
                this.loadVersion();
            });

            if (!repository.isLoaded()) {
                onRefreshedVersionsListener = EventBus.EVENT_BUS.channel(RefreshedVersionsEvent.class)
                        .registerWeak(event -> loadVersion());
                return;
            }
        }
        FXUtils.runInFX(() -> {
            if (version != null && repository != null && repository.hasVersion(version)) {
                setTitle(i18n("version.manage.manage"));
                setSubtitle(version);
                imageContainer.setImage(repository.getVersionIconImage(version));
            } else {
                setTitle(i18n("version.empty"));
                setSubtitle(i18n("version.empty.add"));
                imageContainer.setImage(VersionIconType.DEFAULT.getIcon());
            }
        });
    }
}
