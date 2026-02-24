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
import javafx.animation.PauseTransition;
import javafx.beans.Observable;
import javafx.beans.property.BooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.scene.control.Skin;
import javafx.util.Duration;
import org.jackhuang.hmcl.game.World;
import org.jackhuang.hmcl.gamerule.GameRule;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.ListPageBase;
import org.jackhuang.hmcl.util.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Files;
import java.util.Arrays;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// @author mineDiamond
public class GameRulePage extends ListPageBase<GameRuleInfo<?>> implements WorldManagePage.WorldRefreshable {

    private final World world;
    private CompoundTag levelDat;
    private final BooleanProperty readOnly;

    private final ObservableList<GameRuleInfo<?>> gameRuleList;
    private final FilteredList<GameRuleInfo<?>> modifiedItems;
    private final ObservableList<GameRuleInfo<?>> modifiedList = FXCollections.observableArrayList();
    private final FilteredList<GameRuleInfo<?>> displayedItems = new FilteredList<>(modifiedList);

    private RuleModifiedType ruleModifiedType;

    private boolean batchUpdating = false;
    private final PauseTransition saveLevelDatPause;

    public GameRulePage(WorldManagePage worldManagePage) {
        this.world = worldManagePage.getWorld();
        this.readOnly = worldManagePage.readOnlyProperty();

        ruleModifiedType = RuleModifiedType.ALL;

        gameRuleList = FXCollections.observableArrayList(gameRule -> {
            if (gameRule instanceof GameRuleInfo.BooleanGameRuleInfo booleanGameRuleInfo) {
                return new Observable[]{booleanGameRuleInfo.currentValueProperty()};
            } else if (gameRule instanceof GameRuleInfo.IntGameRuleInfo intGameRuleInfo) {
                return new Observable[]{intGameRuleInfo.currentValueProperty()};
            }
            return new Observable[]{};
        });
        setItems(gameRuleList);
        modifiedItems = new FilteredList<>(getItems(), GameRuleInfo::getModified);
        saveLevelDatPause = new PauseTransition(Duration.millis(300));
        saveLevelDatPause.setOnFinished(event -> saveLevelDat());

        refresh();
    }

    @Override
    public void refresh() {
        gameRuleList.clear();
        modifiedList.clear();

        if (!Files.isDirectory(world.getFile())) {
            LOG.warning("Failed to load level.dat");
            setFailedReason(i18n("world.info.failed"));
            return;
        }

        this.levelDat = world.getLevelData();
        updateList();
    }

    public void updateList() {
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
                    gameRuleList.add(GameRuleInfo.createGameRuleInfo(gameRule, gameRuleNBT, this::saveLevelDatIfNotInBatchUpdating, world.getGameVersion()));
                });
            });
        });
        applyRuleModifiedType();
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new GameRulePageSkin(this);
    }

    public ObservableList<GameRuleInfo<?>> getModifiedList() {
        return modifiedList;
    }

    private void applyModifiedFilter(RuleModifiedType type) {
        switch (type) {
            case ALL -> modifiedList.setAll(getItems());
            case MODIFIED -> modifiedList.setAll(modifiedItems);
            case UNMODIFIED -> modifiedList.setAll(getItems().stream().filter(gameRuleInfo ->
                    !modifiedItems.contains(gameRuleInfo)).collect(Collectors.toSet()));
        }
    }

    public FilteredList<GameRuleInfo<?>> getModifiedItems() {
        return modifiedItems;
    }

    public FilteredList<GameRuleInfo<?>> getDisplayedItems() {
        return displayedItems;
    }

    public RuleModifiedType getRuleModifiedType() {
        return ruleModifiedType;
    }

    public void changeRuleModifiedType(RuleModifiedType type) {
        this.ruleModifiedType = type;
        applyModifiedFilter(ruleModifiedType);
    }

    public void applyRuleModifiedType() {
        applyModifiedFilter(ruleModifiedType);
    }

    public BooleanProperty readOnlyProperty() {
        return readOnly;
    }

    public boolean isBatchUpdating() {
        return batchUpdating;
    }

    public void setBatchUpdating(boolean isResettingAll) {
        this.batchUpdating = isResettingAll;
    }

    void saveLevelDat() {
        LOG.info("Saving level.dat of world " + world.getWorldName());
        world.writeLevelDatAsync();
    }

    void requestSaveLevelDat() {
        saveLevelDatPause.playFromStart();
    }

    void saveLevelDatIfNotInBatchUpdating() {
        if (!batchUpdating) {
            requestSaveLevelDat();
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

    void updateSearchPredicate(String queryString) {
        displayedItems.setPredicate(updatePredicate(queryString));
    }

    @NotNull
    private Predicate<GameRuleInfo<?>> updatePredicate(String queryString) {
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

    public enum RuleModifiedType {
        ALL, MODIFIED, UNMODIFIED;

        static final ObservableList<RuleModifiedType> items = FXCollections.observableList(Arrays.asList(values()));

        @Override
        public String toString() {
            return i18n("gamerule.filter." + name().toLowerCase(Locale.ROOT));
        }
    }
}
