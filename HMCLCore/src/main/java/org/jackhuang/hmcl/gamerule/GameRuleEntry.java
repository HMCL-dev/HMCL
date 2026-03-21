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
package org.jackhuang.hmcl.gamerule;

import com.github.steveice10.opennbt.tag.builtin.Tag;

public sealed interface GameRuleEntry permits GameRuleEntry.IntEntry, GameRuleEntry.BooleanEntry {
    GameRule getGameRule();

    GameRuleNBT<?, ? extends Tag> getGameRuleNBT();

    record IntEntry(GameRule.IntGameRule rule, GameRuleNBT<Integer, ? extends Tag> nbt) implements GameRuleEntry {
        @Override
        public GameRule getGameRule() {
            return rule;
        }

        @Override
        public GameRuleNBT<?, ? extends Tag> getGameRuleNBT() {
            return nbt;
        }
    }

    record BooleanEntry(GameRule.BooleanGameRule rule,
                        GameRuleNBT<Boolean, ? extends Tag> nbt) implements GameRuleEntry {

        @Override
        public GameRule getGameRule() {
            return rule;
        }

        @Override
        public GameRuleNBT<?, ? extends Tag> getGameRuleNBT() {
            return nbt;
        }
    }
}
