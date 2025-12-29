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
package org.jackhuang.hmcl.ui.versions;

import com.github.steveice10.opennbt.tag.builtin.CompoundTag;
import com.github.steveice10.opennbt.tag.builtin.Tag;
import javafx.beans.Observable;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Skin;
import org.jackhuang.hmcl.game.World;
import org.jackhuang.hmcl.gamerule.GameRule;
import org.jackhuang.hmcl.gamerule.GameRuleNBT;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.ListPageBase;
import org.jackhuang.hmcl.util.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public class GameRulePage extends ListPageBase<GameRuleInfo<?>> {

    private final WorldManagePage worldManagePage;
    private final World world;
    private CompoundTag levelDat;

    ObservableList<GameRuleInfo<?>> gameRuleList;
    private boolean batchUpdating = false;

    public GameRulePage(WorldManagePage worldManagePage) {
        this.worldManagePage = worldManagePage;
        this.world = worldManagePage.getWorld();

        gameRuleList = FXCollections.observableArrayList(gamerule -> {
            if (gamerule instanceof GameRuleInfo.BooleanGameRuleInfo booleanGameRuleInfo) {
                return new Observable[]{booleanGameRuleInfo.currentValueProperty()};
            } else if (gamerule instanceof GameRuleInfo.IntGameRuleInfo intGameRuleInfo) {
                return new Observable[]{intGameRuleInfo.currentValueProperty()};
            }
            return new Observable[]{};
        });
        setItems(gameRuleList);

        this.setLoading(true);
        Task.supplyAsync(this::loadWorldInfo)
                .whenComplete(Schedulers.javafx(), ((result, exception) -> {
                    if (exception == null) {
                        this.levelDat = result;
                        updateControls();
                        setLoading(false);
                    } else {
                        LOG.warning("Failed to load level.dat", exception);
                        setFailedReason(i18n("world.info.failed"));
                    }
                })).start();
    }

    public void updateControls() {
        CompoundTag dataTag = levelDat.get("Data");
        CompoundTag gameRuleCompoundTag = dataTag.get("game_rules");
        if (gameRuleCompoundTag == null) {
            gameRuleCompoundTag = dataTag.get("GameRules");
        }

        if (gameRuleCompoundTag == null) {
            LOG.warning("Neither 'game_rules' nor 'GameRules' tag found in level.dat");
            return;
        }

        gameRuleCompoundTag.iterator().forEachRemaining(gameRuleTag -> {
            GameRule.createGameRuleNBT(gameRuleTag).ifPresent(gameRuleNBT -> {
                GameRule.getFullGameRule(gameRuleTag).ifPresent(gameRule -> {
                    if (gameRule instanceof GameRule.IntGameRule intGameRule) {
                        @SuppressWarnings("unchecked") GameRuleNBT<String, Tag> typedGameRuleNBT = (GameRuleNBT<String, Tag>) gameRuleNBT;
                        gameRuleList.add(new GameRuleInfo.IntGameRuleInfo(intGameRule, typedGameRuleNBT, this::saveLevelDatIfNotInBatchUpdating, world.getGameVersion()));
                    } else if (gameRule instanceof GameRule.BooleanGameRule booleanGameRule) {
                        @SuppressWarnings("unchecked") GameRuleNBT<Boolean, Tag> typedGameRuleNBT = (GameRuleNBT<Boolean, Tag>) gameRuleNBT;
                        gameRuleList.add(new GameRuleInfo.BooleanGameRuleInfo(booleanGameRule, typedGameRuleNBT, this::saveLevelDatIfNotInBatchUpdating, world.getGameVersion()));
                    }
                });
            });
        });
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new GameRulePageSkin(this);
    }

    public boolean isBatchUpdating() {
        return batchUpdating;
    }

    public void setBatchUpdating(boolean isResettingAll) {
        this.batchUpdating = isResettingAll;
    }

    private CompoundTag loadWorldInfo() throws IOException {
        if (!Files.isDirectory(world.getFile()))
            throw new IOException("Not a valid world directory");

        return world.readLevelDat();
    }

    void saveLevelDat() {
        LOG.info("Saving level.dat of world " + world.getWorldName());
        Task.runAsync(Schedulers.io(), () -> this.world.writeLevelDat(levelDat))
                .whenComplete(Schedulers.defaultScheduler(), ((result, exception) -> {
                    if (exception != null) {
                        LOG.warning("Failed to save level.dat of world " + world.getWorldName(), exception);
                    }
                })).start();
    }

    void saveLevelDatIfNotInBatchUpdating() {
        if (!batchUpdating) {
            saveLevelDat();
        }
    }

    void resettingAllGameRule() {
        batchUpdating = true;
        for (GameRuleInfo<?> gameRuleInfo : getItems()) {
            gameRuleInfo.resetValue();
        }
        saveLevelDat();
        batchUpdating = false;
        Controllers.showToast(i18n("gamerule.restore_default_values_all.finish.toast"));
    }

    @NotNull Predicate<GameRuleInfo<?>> updateSearchPredicate(String queryString) {
        if (StringUtils.isBlank(queryString)) {
            return gameRuleInfo -> true;
        }

        final Predicate<String> stringPredicate;
        if (queryString.startsWith("regex:")) {
            try {
                Pattern pattern = Pattern.compile(StringUtils.substringAfter(queryString, "regex:"));
                stringPredicate = s -> s != null && pattern.matcher(s).find();
            } catch (Exception e) {
                return gameRuleInfo -> false;
            }
        } else {
            String lowerCaseFilter = queryString.toLowerCase(Locale.ROOT);
            stringPredicate = s -> s != null && s.toLowerCase(Locale.ROOT).contains(lowerCaseFilter);
        }

        return gameRuleInfo -> stringPredicate.test(gameRuleInfo.getDisplayName()) || stringPredicate.test(gameRuleInfo.getRuleKey());
    }
}
