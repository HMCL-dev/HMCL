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

/**
 * An abstract representation of a Minecraft game rule stored as an NBT tag.
 * <p>
 * This class acts as a generic wrapper for a specific game rule's NBT tag,
 * providing a unified interface to modify its value. It abstracts the underlying
 * NBT tag implementation, allowing different types of game rule values (like integers,
 * booleans, or strings) to be handled consistently.
 * <p>
 * Subclasses must implement the {@link #changeValue(Object)} method to define
 * how an input value of type {@code T} is converted and applied to the wrapped
 * NBT tag of type {@code V}.
 *
 * @param <T> The type of the value used to update the game rule (e.g., {@link String}, {@link Boolean}).
 * @param <V> The specific {@link Tag} subtype being wrapped (e.g., {@link IntTag}, {@link ByteTag}).
 */
public sealed abstract class GameRuleNBT<T, V extends Tag> permits GameRuleNBT.IntGameRuleNBT, GameRuleNBT.ByteGameRuleNBT, GameRuleNBT.StringIntGameRuleNBT, GameRuleNBT.StringByteGameRuleNBT {

    private final V gameRuleTag;

    protected GameRuleNBT(V gameRuleTag) {
        this.gameRuleTag = gameRuleTag;
    }

    public abstract void changeValue(T newValue);

    public V getGameRuleTag() {
        return gameRuleTag;
    }

    static final class IntGameRuleNBT extends GameRuleNBT<String, IntTag> {

        public IntGameRuleNBT(IntTag gameRuleTag) {
            super(gameRuleTag);
        }

        @Override
        public void changeValue(String newValue) {
            Integer value = Lang.toIntOrNull(newValue);
            if (value != null) {
                getGameRuleTag().setValue(value);
            }
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

    static final class StringIntGameRuleNBT extends GameRuleNBT<String, StringTag> {

        public StringIntGameRuleNBT(StringTag gameRuleTag) {
            super(gameRuleTag);
        }

        @Override
        public void changeValue(String newValue) {
            getGameRuleTag().setValue(newValue);
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
