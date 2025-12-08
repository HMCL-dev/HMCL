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
import org.jackhuang.hmcl.gamerule.GameRuleNBT;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.ListPageBase;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;

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
        if (gameRuleCompoundTag != null) {
        } else {
            gameRuleCompoundTag = dataTag.get("GameRules");
        }
        gameRuleCompoundTag.iterator().forEachRemaining(gameRuleTag -> {
            //LOG.trace(gameRuleTag.toString());
            GameRule finalGameRule;

            GameRuleNBT gameRuleNbt = GameRule.createGameRuleNbt(gameRuleTag).orElse(null);
            finalGameRule = GameRule.getFullGameRule(gameRuleTag, gameRuleMap).orElse(null);
            if (gameRuleNbt == null || finalGameRule == null) {
                return;
            }

            //LOG.trace(finalGameRule.getRuleKey().toString());

            String displayText;
            try {
                displayText = i18n(finalGameRule.getDisplayI18nKey());
            } catch (Exception e) {
                displayText = finalGameRule.getDisplayI18nKey();
            }

            if (finalGameRule instanceof GameRule.IntGameRule intGameRule) {
                gameRuleList.add(new GameRulePageSkin.GameRuleInfo(intGameRule.getRuleKey().get(0), displayText, intGameRule.getValue(), intGameRule.getMinValue(), intGameRule.getMaxValue(), gameRuleNbt, this::saveLevelDat));
            } else if (finalGameRule instanceof GameRule.BooleanGameRule booleanGameRule) {
                gameRuleList.add(new GameRulePageSkin.GameRuleInfo(booleanGameRule.getRuleKey().get(0), displayText, booleanGameRule.getValue(), gameRuleNbt, this::saveLevelDat));
            }
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
        try {
            this.world.writeLevelDat(levelDat);
        } catch (IOException e) {
            LOG.warning("Failed to save level.dat of world " + world.getWorldName(), e);
        }
    }
}
