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
package org.jackhuang.hmcl.ui.instances;

import javafx.geometry.Pos;
import org.jackhuang.hmcl.event.Event;
import org.jackhuang.hmcl.game.HMCLGameInstance;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.setting.GameDirectoryManager;
import org.jackhuang.hmcl.setting.GameInstanceIconType;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.WeakListenerHolder;
import org.jackhuang.hmcl.ui.construct.AdvancedListItem;
import org.jackhuang.hmcl.ui.construct.ImageContainer;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class GameAdvancedListItem extends AdvancedListItem {
    private final ImageContainer imageContainer;
    private final WeakListenerHolder holder = new WeakListenerHolder();
    private @Nullable HMCLGameRepository repository;
    @SuppressWarnings("unused")
    private @Nullable Consumer<Event> onInstanceIconChangedListener;

    public GameAdvancedListItem() {
        this.imageContainer = new ImageContainer(LEFT_GRAPHIC_SIZE);
        imageContainer.setMouseTransparent(true);
        AdvancedListItem.setAlignment(imageContainer, Pos.CENTER);
        setLeftGraphic(imageContainer);

        holder.add(FXUtils.onWeakChangeAndOperate(GameDirectoryManager.selectedInstanceProperty(), this::loadInstance));
    }

    private void loadInstance(@Nullable HMCLGameInstance instance) {
        if (GameDirectoryManager.getSelectedRepository() != repository) {
            repository = GameDirectoryManager.getSelectedRepository();
            onInstanceIconChangedListener = repository.onInstanceIconChanged.registerWeak(event ->
                    FXUtils.runInFX(() -> loadInstance(repository.getSelectedInstance())));
        }
        if (instance != null) {
            setTitle(i18n("instance.manage.manage"));
            setSubtitle(instance.getId().toString());
            imageContainer.setImage(instance.getIconImage());
            return;
        }

        setTitle(i18n("instance.empty"));
        setSubtitle(i18n("instance.empty.add"));
        imageContainer.setImage(GameInstanceIconType.DEFAULT.getIcon());
    }
}
