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
package org.jackhuang.hmcl.gamerule;

import com.github.steveice10.opennbt.tag.builtin.ByteTag;
import com.github.steveice10.opennbt.tag.builtin.IntTag;
import com.github.steveice10.opennbt.tag.builtin.StringTag;
import com.github.steveice10.opennbt.tag.builtin.Tag;
import org.jackhuang.hmcl.util.Lang;

/// A sealed abstract wrapper for a game rule stored in NBT.
///
/// This class **holds a single NBT `Tag` instance** (`gameRuleTag`) and provides a unified API ([#changeValue(Object)])
/// to update that tag’s underlying value from a higher-level Java value.
///
/// @param <T> The Java type used to represent the game rule's value (e.g., [String], [Boolean]).
/// @param <V> The specific NBT [Tag] type that this object wraps and persists.
public sealed abstract class GameRuleNBT<T, V extends Tag> permits GameRuleNBT.IntGameRuleNBT, GameRuleNBT.ByteGameRuleNBT, GameRuleNBT.StringIntGameRuleNBT, GameRuleNBT.StringByteGameRuleNBT {

    private final V gameRuleTag;

    protected GameRuleNBT(V gameRuleTag) {
        this.gameRuleTag = gameRuleTag;
    }

    public abstract void changeValue(T newValue);

    public V getGameRuleTag() {
        return gameRuleTag;
    }

    static final class IntGameRuleNBT extends GameRuleNBT<Integer, IntTag> {

        public IntGameRuleNBT(IntTag gameRuleTag) {
            super(gameRuleTag);
        }

        @Override
        public void changeValue(Integer newValue) {
            getGameRuleTag().setValue(newValue);
        }
    }

    static final class ByteGameRuleNBT extends GameRuleNBT<Boolean, ByteTag> {

        public ByteGameRuleNBT(ByteTag gameRuleTag) {
            super(gameRuleTag);
        }

        @Override
        public void changeValue(Boolean newValue) {
            getGameRuleTag().setValue((byte) (newValue ? 1 : 0));
        }
    }

    static final class StringIntGameRuleNBT extends GameRuleNBT<Integer, StringTag> {

        public StringIntGameRuleNBT(StringTag gameRuleTag) {
            super(gameRuleTag);
        }

        @Override
        public void changeValue(Integer newValue) {
            getGameRuleTag().setValue(String.valueOf(newValue));
        }
    }

    static final class StringByteGameRuleNBT extends GameRuleNBT<Boolean, StringTag> {

        public StringByteGameRuleNBT(StringTag gameRuleTag) {
            super(gameRuleTag);
        }

        @Override
        public void changeValue(Boolean newValue) {
            getGameRuleTag().setValue(newValue ? "true" : "false");
        }
    }
}
