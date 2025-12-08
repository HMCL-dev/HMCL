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

import com.github.steveice10.opennbt.tag.builtin.ByteTag;
import com.github.steveice10.opennbt.tag.builtin.CompoundTag;
import com.github.steveice10.opennbt.tag.builtin.IntTag;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Skin;
import org.jackhuang.hmcl.game.World;
import org.jackhuang.hmcl.gamerule.GameRule;
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
        boolean isNewGameRuleFormat;

        CompoundTag dataTag = levelDat.get("Data");
        CompoundTag gameRuleCompoundTag;
        gameRuleCompoundTag = dataTag.get("game_rules");
        if (gameRuleCompoundTag != null) {
            isNewGameRuleFormat = true;
        } else {
            gameRuleCompoundTag = dataTag.get("GameRules");
            isNewGameRuleFormat = false;
        }
        if (isNewGameRuleFormat) {
            gameRuleCompoundTag.iterator().forEachRemaining(gameRuleTag -> {
                //LOG.trace(gameRuleTag.toString());
                GameRule finalGameRule;
                if (gameRuleTag instanceof IntTag intTag) {
                    finalGameRule = GameRule.createSimpleGameRule(intTag.getName(), intTag.getValue());
                    GameRule gr = gameRuleMap.getOrDefault(intTag.getName(), null);
                    if (gr instanceof GameRule.IntGameRule intGR) {
                        GameRule.mixGameRule(finalGameRule, intGR);
                    }
                    String displayText;
                    try {
                        displayText = i18n(finalGameRule.getDisplayI18nKey());
                    } catch (Exception e) {
                        displayText = finalGameRule.getDisplayI18nKey();
                    }
                    if (finalGameRule instanceof GameRule.IntGameRule intGameRule) {
                        LOG.trace("find one: " + finalGameRule.getRuleKey() + intGameRule.getValue() + "minValue: " + intGameRule.getMinValue() + ", maxValue" + intGameRule.getMaxValue() + ", intTag is " + intTag.getValue());
                        gameRuleList.add(new GameRulePageSkin.GameRuleInfo(finalGameRule.getRuleKey().get(0), displayText, intGameRule.getValue(), intGameRule.getMinValue(), intGameRule.getMaxValue(), intTag));
                    }
                } else if (gameRuleTag instanceof ByteTag byteTag) {
                    finalGameRule = GameRule.createSimpleGameRule(byteTag.getName(), byteTag.getValue() == 1);
                    GameRule gr = gameRuleMap.getOrDefault(byteTag.getName(), null);
                    if (gr instanceof GameRule.BooleanGameRule booleanGR) {
                        GameRule.mixGameRule(finalGameRule, booleanGR);
                        //LOG.trace("find one: " + finalGameRule.getRuleKey() + ", byteTag is " + byteTag.getValue());
                    }
                    String displayText;
                    try {
                        displayText = i18n(finalGameRule.getDisplayI18nKey());
                    } catch (Exception e) {
                        displayText = finalGameRule.getDisplayI18nKey();
                    }
                    if (finalGameRule instanceof GameRule.BooleanGameRule booleanGameRule) {
                        gameRuleList.add(new GameRulePageSkin.GameRuleInfo(finalGameRule.getRuleKey().get(0), displayText, booleanGameRule.getValue(), byteTag));
                    }
                } else {
                    return;
                }
            });
        } else {
            gameRuleCompoundTag.iterator().forEachRemaining(gameRuleTag -> {
                LOG.trace(gameRuleTag.toString());
            });
        }

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
}
