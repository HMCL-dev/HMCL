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
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Skin;
import org.jackhuang.hmcl.game.World;
import org.jackhuang.hmcl.gamerule.GameRule;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.ListPageBase;
import org.jackhuang.hmcl.util.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public class GameRulePage extends ListPageBase<GameRulePageSkin.GameRuleInfo> {

    private WorldManagePage worldManagePage;
    private World world;
    private CompoundTag levelDat;

    Map<String, GameRule> gameRuleMap = GameRule.getCloneGameRuleMap();

    ObservableList<GameRulePageSkin.GameRuleInfo> gameRuleList;

    public GameRulePage(WorldManagePage worldManagePage) {
        this.worldManagePage = worldManagePage;
        this.world = worldManagePage.getWorld();

        gameRuleList = FXCollections.observableArrayList();
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
        CompoundTag gameRuleCompoundTag;
        gameRuleCompoundTag = dataTag.get("game_rules");
        if (gameRuleCompoundTag == null) {
            gameRuleCompoundTag = dataTag.get("GameRules");
        }

        if (gameRuleCompoundTag == null) {
            LOG.warning("Neither 'game_rules' nor 'GameRules' tag found in level.dat");
            return;
        }

        gameRuleCompoundTag.iterator().forEachRemaining(gameRuleTag -> {
            GameRule.createGameRuleNbt(gameRuleTag).ifPresent(gameRuleNBT -> {
                GameRule.getFullGameRule(gameRuleTag, gameRuleMap).ifPresent(gameRule -> {
                    String displayText = "";
                    try {
                        if (StringUtils.isNotBlank(gameRule.getDisplayI18nKey())) {
                            displayText = i18n(gameRule.getDisplayI18nKey());
                        }
                    } catch (Exception e) {
                        LOG.warning("Failed to get i18n text for key: " + gameRule.getDisplayI18nKey(), e);
                    }
                    if (gameRule instanceof GameRule.IntGameRule intGameRule) {
                        gameRuleList.add(new GameRulePageSkin.GameRuleInfo(intGameRule.getRuleKey().get(0), displayText, intGameRule.getValue(), intGameRule.getMinValue(), intGameRule.getMaxValue(), intGameRule.getDefaultValue(), gameRuleNBT, this::saveLevelDat));
                    } else if (gameRule instanceof GameRule.BooleanGameRule booleanGameRule) {
                        gameRuleList.add(new GameRulePageSkin.GameRuleInfo(booleanGameRule.getRuleKey().get(0), displayText, booleanGameRule.getValue(), booleanGameRule.getDefaultValue(), gameRuleNBT, this::saveLevelDat));
                    }
                });
            });
        });
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new GameRulePageSkin(this);
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

    @NotNull Predicate<GameRulePageSkin.GameRuleInfo> updateSearchPredicate(String queryString) {
        if (queryString.isBlank()) {
            return gameRuleInfo -> true;
        }

        final Predicate<String> stringPredicate;
        if (queryString.startsWith("regex:")) {
            try {
                Pattern pattern = Pattern.compile(StringUtils.substringAfter(queryString, "regex:"));
                stringPredicate = s -> s != null && pattern.matcher(s).find();
            } catch (Exception e) {
                return dataPack -> false;
            }
        } else {
            String lowerCaseFilter = queryString.toLowerCase(Locale.ROOT);
            stringPredicate = s -> s != null && s.toLowerCase(Locale.ROOT).contains(lowerCaseFilter);
        }

        return gameRuleInfo -> stringPredicate.test(gameRuleInfo.displayName) || stringPredicate.test(gameRuleInfo.ruleKey);
    }
}
