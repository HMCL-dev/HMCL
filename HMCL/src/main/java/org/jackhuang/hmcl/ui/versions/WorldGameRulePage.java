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
package org.jackhuang.hmcl.ui.versions;

import com.github.steveice10.opennbt.tag.builtin.CompoundTag;
import com.github.steveice10.opennbt.tag.builtin.StringTag;
import com.github.steveice10.opennbt.tag.builtin.Tag;
import com.jfoenix.controls.JFXTextField;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.game.World;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * @author Xiaotian
 */
public class WorldGameRulePage extends SpinnerPane implements WorldManagePage.WorldRefreshable {

    // 注意： 若采用驼峰式命名法的在i18n中的键名为snake case版本的。
    // 代码会判断 游戏规则名 是否为驼峰式命名，若是，这直接将其作为键名；若不是，则会使用index + 1的 游戏规则名 作为键名
    // 若修改了名称， 如useLocatorBar -> locatorBar， 则两个名称都要写进去
    static final List<String> PLAYER = List.of(
            "disableElytraMovementCheck", "elytra_movement_check",
            "disablePlayerMovementCheck", "player_movement_check",
            "doImmediateRespawn", "immediate_respawn",
            "doLimitedCrafting", "limited_crafting",
            "drowningDamage", "drowning_damage",
            "enderPearlsVanishOnDeath", "ender_pearls_vanish_on_death",
            "fallDamage", "fall_damage",
            "fireDamage", "fire_damage",
            "freezeDamage", "freeze_damage",
            "keepInventory", "keep_inventory",
            "locatorBar", "locator_bar",
            "useLocatorBar", "locator_bar",
            "naturalRegeneration", "natural_health_regeneration",
            "playersNetherPortalCreativeDelay", "players_nether_portal_creative_delay",
            "playersNetherPortalDefaultDelay", "players_nether_portal_default_delay",
            "playersSleepingPercentage", "players_sleeping_percentage",
            "pvp",
            "spawnRadius", "respawn_radius",
            "spectatorsGenerateChunks", "spectators_generate_chunks"
    );

    static final List<String> MOBS = List.of(
            "disableRaids", "raids",
            "forgiveDeadPlayers", "forgive_dead_players",
            "maxEntityCramming", "max_entity_cramming",
            "mobGriefing", "mob_griefing",
            "universalAnger", "universal_anger"
            );

    static final List<String> SPAWNING = List.of(
            "doInsomnia", "spawn_phantoms",
            "doMobSpawning", "spawn_mobs",
            "doPatrolSpawning", "spawn_patrols",
            "doTraderSpawning", "spawn_wandering_traders",
            "doWardenSpawning", "spawn_wardens",
            "spawnMonsters", "spawn_monsters"
            );

    static final List<String> DROPS = List.of(
            "blockExplosionDropDecay", "block_explosion_drop_decay",
            "doEntityDrops", "entity_drops",
            "doMobLoot", "mob_drops",
            "doTileDrops", "block_drops",
            "mobExplosionDropDecay", "mob_explosion_drop_decay",
            "projectilesCanBreakBlocks", "projectiles_can_break_blocks",
            "tntExplosionDropDecay", "tnt_explosion_drop_decay"
    );

    static final List<String> WORLD_UPDATES = List.of(
            "allowFireTicksAwayFromPlayer", "allow_fire_ticks_away_from_player", // 于 25w44a 删除，但为了i18n保留
            "doDaylightCycle", "advance_time",
            "doFireTick", "do_fire_tick", // 于 25w44a 删除，但为了i18n保留
            "doVinesSpread", "spread_vines",
            "doWeatherCycle", "advance_weather",
            "lavaSourceConversion", "lava_source_conversion",
            "randomTickSpeed", "random_tick_speed",
            "snowAccumulationHeight", "max_snow_accumulation_height",
            "waterSourceConversion", "water_source_conversion",
            "fire_spread_radius_around_player" // 25w44a添加
    );

    static final List<String> CHAT = List.of(
            "announceAdvancements", "show_advancement_messages",
            "commandBlockOutput", "command_block_output",
            "logAdminCommands", "log_admin_commands",
            "sendCommandFeedback", "send_command_feedback",
            "showDeathMessages", "show_death_messages"
    );

    static final List<String> MISCELLANEOUS = List.of(
            "allowEnteringNetherUsingPortals", "allow_entering_nether_using_portals",
            "enableCommandBlocks", "command_blocks_work",
            "commandBlocksEnabled", "command_blocks_work",
            "commandModificationBlockLimit", "max_block_modifications",
            "globalSoundEvents", "global_sound_events",
            "maxCommandChainLength", "max_command_sequence_length",
            "maxCommandForkCount", "max_command_forks",
            "reducedDebugInfo", "reduced_debug_info",
            "spawnerBlocksEnabled", "spawner_blocks_work",
            "tntExplodes", "tnt_explodes"
    );

    private final World world;
    private final @NotNull WorldManagePage worldManagePage;

    private CompoundTag nbt;
    private Map<String, String> gameRules;
    private boolean isReadOnly;

    public WorldGameRulePage(@NotNull WorldManagePage worldManagePage) {
        this.world = worldManagePage.getWorld();
        this.worldManagePage = worldManagePage;
        refresh();
    }

    @Override
    public void refresh() {
        nbt = world.getLevelData();
        this.isReadOnly = worldManagePage.isReadOnly();
        this.setLoading(true);
        Task.supplyAsync(world::getGameRules)
                .whenComplete(Schedulers.javafx(), (result, exception) -> {
                    if (exception == null) {
                        this.gameRules = result;
                        updateControls();
                        setLoading(false);
                    } else {
                        LOG.warning("Failed to load game rules", exception);
                        setFailedReason(i18n("world.game_rule.failed"));
                    }
                }).start();
    }

    private void updateControls() {
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToHeight(true);
        scrollPane.setFitToWidth(true);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);
        setContent(scrollPane);

        VBox rootPane = new VBox();
        rootPane.setFillWidth(true);
        scrollPane.setContent(rootPane);
        FXUtils.smoothScrolling(scrollPane);
        rootPane.getStyleClass().add("card-list");

        ComponentList player = new ComponentList();
        ComponentList mobs = new ComponentList();
        ComponentList spawning = new ComponentList();
        ComponentList drops = new ComponentList();
        ComponentList worldUpdates = new ComponentList();
        ComponentList chat = new ComponentList();
        ComponentList miscellaneous = new ComponentList();
        ComponentList other = new ComponentList();

        gameRules.forEach((rule, value) -> {
            if (PLAYER.contains(rule)) {
                player.getContent().add(generateGameRuleComponent(rule, value, PLAYER));
            } else if (MOBS.contains(rule)) {
                mobs.getContent().add(generateGameRuleComponent(rule, value, MOBS));
            } else if (SPAWNING.contains(rule)) {
                spawning.getContent().add(generateGameRuleComponent(rule, value, SPAWNING));
            } else if (DROPS.contains(rule)) {
                drops.getContent().add(generateGameRuleComponent(rule, value, DROPS));
            } else if (WORLD_UPDATES.contains(rule)) {
                worldUpdates.getContent().add(generateGameRuleComponent(rule, value, WORLD_UPDATES));
            } else if (CHAT.contains(rule)) {
                chat.getContent().add(generateGameRuleComponent(rule, value, CHAT));
            } else if (MISCELLANEOUS.contains(rule)) {
                miscellaneous.getContent().add(generateGameRuleComponent(rule, value, MISCELLANEOUS));
            } else {
                other.getContent().add(generateGameRuleComponent(rule, value, null));
            }
        });

        {
            rootPane.getChildren().addAll(ComponentList.createComponentListTitle(i18n("world.game_rule.player")), player);
            rootPane.getChildren().addAll(ComponentList.createComponentListTitle(i18n("world.game_rule.mobs")), mobs);
            rootPane.getChildren().addAll(ComponentList.createComponentListTitle(i18n("world.game_rule.spawning")), spawning);
            rootPane.getChildren().addAll(ComponentList.createComponentListTitle(i18n("world.game_rule.drops")), drops);
            rootPane.getChildren().addAll(ComponentList.createComponentListTitle(i18n("world.game_rule.worldUpdates")), worldUpdates);
            rootPane.getChildren().addAll(ComponentList.createComponentListTitle(i18n("world.game_rule.chat")), miscellaneous);
            if (!other.getContent().isEmpty()) {
                rootPane.getChildren().addAll(ComponentList.createComponentListTitle(i18n("world.game_rule.other")), other);
            }
        }

    }

    private @NotNull LineComponent generateGameRuleComponent(String rule, String value, @Nullable List<String> gameRuleGroup) {
        String translatedRule = getTranslatedRule(rule, gameRuleGroup);
        try {
            int i = Integer.parseInt(value);
            return generateIntRuleComponent(rule, translatedRule, i);
        } catch (NumberFormatException ignored) {
            if (Objects.equals(value, "true")) {
                return generateBooleanRuleComponent(rule, translatedRule, true);
            } else if (Objects.equals(value, "false")) {
                return generateBooleanRuleComponent(rule, translatedRule, false);
            } else {
                return generateReadOnlyComponent(translatedRule, value);
            }
        }
    }

    private @NotNull LineComponent generateBooleanRuleComponent(String rule, String translatedRule, boolean value) {
        var ltb = new LineToggleButton();

        ltb.setTitle(translatedRule);
        ltb.setDisable(isReadOnly);
        ltb.setSelected(value);
        bind(rule, ltb);

        return ltb;
    }

    private @NotNull LineComponent generateIntRuleComponent(String rule, String translatedRule, int value) {
        var ltb = new EditableLinePane(value, 200);

        ltb.setTitle(translatedRule);
        ltb.setDisable(isReadOnly);
        bind(rule, ltb);

        return ltb;
    }

    private @NotNull LineComponent generateReadOnlyComponent(String translatedRule, String value) {
        var ltp = new LineTextPane();
        ltp.setTitle(translatedRule);
        ltp.setText(value);

        return ltp;
    }

    private void bind(String gameRule, @NotNull LineComponent component) {
        if (component instanceof LineToggleButton ltb) {
            ltb.selectedProperty().addListener((o, oldValue, newValue) -> {
                gameRules.put(gameRule, newValue.toString());
                if (! saveGameRules()) {
                    errorDialog(i18n("world.game_rule.error.save"), "Failed to save game rule: %s".formatted(gameRule));
                    gameRules.put(gameRule, oldValue.toString());
                    ltb.setSelected(oldValue);
                }
            });
        } else if (component instanceof EditableLinePane elp) {
            elp.valueProperty.addListener((o, oldValue, newValue) -> {
                if (newValue.intValue() >= 0) {
                    String oldString = String.valueOf(oldValue);
                    gameRules.put(gameRule, newValue.toString());
                    if (!saveGameRules()) {
                        errorDialog(i18n("world.game_rule.error.save"), "Failed to save game rule: %s".formatted(gameRule));
                        gameRules.put(gameRule, oldString);
                        elp.textField.setText(oldString);
                    }
                } else {
                    elp.textField.setText(oldValue.toString());
                }
            });
        }
    }
    
    private void errorDialog(String message, String logMessage) {
        LOG.error(logMessage);
        Controllers.dialog(message, i18n("message.error"), MessageDialogPane.MessageType.ERROR);
    }

    private static String getTranslatedRule(String rule, @Nullable List<String> gameRuleGroup) {
        String translateKey;

        if (StringUtils.containsUpperCase(rule)) {
            if (gameRuleGroup != null) {
                int snakeCasedRuleIndex = gameRuleGroup.indexOf(rule) + 1;
                if (snakeCasedRuleIndex > gameRuleGroup.size()) {
                    translateKey = "world.game_rule.rules.%s".formatted(rule);
                } else {
                    translateKey = "world.game_rule.rules.%s".formatted(gameRuleGroup.get(snakeCasedRuleIndex));
                }
            } else {
                return rule;
            }
        } else {
            translateKey = "world.game_rule.rules.%s".formatted(rule);
        }
        if (I18n.hasKey(translateKey)) {
            return i18n(translateKey);
        } else {
            return rule;
        }
    }

    public boolean saveGameRules() {
        if (nbt.get("Data") instanceof CompoundTag data &&
                data.get("GameRules") instanceof CompoundTag nbtGameRules) {
            Map<String, Tag> mapGameRules = new HashMap<>();
            gameRules.forEach((rule, value) -> {
                mapGameRules.put(rule, new StringTag(rule, value));
            });
            nbtGameRules.setValue(mapGameRules);
            try {
                world.writeLevelDat(nbt);
            } catch (IOException e) {
                return false;
            }
            return true;
        }
        return false;
    }

    private static final class EditableLinePane extends LinePane {
        private final JFXTextField textField = new JFXTextField();
        private SimpleIntegerProperty valueProperty;
        private int value;

        private EditableLinePane(int valueProperty, int prefWidth) {
            super();
            this.valueProperty = new SimpleIntegerProperty(valueProperty);
            FXUtils.bindInt(textField, this.valueProperty);
            textField.getProperties().put(WorldInfoPage.class.getName() + ".valueProperty", value);
            textField.setText(String.valueOf(valueProperty));
            textField.setPrefWidth(prefWidth);
            textField.setValidators(new NumberValidator());
            this.setRight(textField);
        }
    }

}
