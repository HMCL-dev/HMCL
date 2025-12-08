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

public abstract class GameRuleNBT<T> {

    private Tag gameRuleTag;

    public abstract void changeValue(T newValue);

    public Tag getGameRuleTag() {
        return gameRuleTag;
    }

    public void setGameRuleTag(Tag gameRuleTag) {
        this.gameRuleTag = gameRuleTag;
    }

    static class IntGameRuleNBT extends GameRuleNBT<String> {

        public IntGameRuleNBT(IntTag gameRuleTag) {
            setGameRuleTag(gameRuleTag);
        }

        @Override
        public void changeValue(String newValue) {
            IntTag intTag = (IntTag) getGameRuleTag();
            Integer value = Lang.toIntOrNull(newValue);
            intTag.setValue(value);
        }
    }

    static class ByteRuleNBT extends GameRuleNBT<Boolean> {

        public ByteRuleNBT(ByteTag gameRuleTag) {
            setGameRuleTag(gameRuleTag);
        }

        @Override
        public void changeValue(Boolean newValue) {
            ByteTag byteTag = (ByteTag) getGameRuleTag();
            byteTag.setValue((byte) (newValue ? 1 : 0));
        }
    }

    static class StringIntGameRuleNBT extends GameRuleNBT<String> {

        public StringIntGameRuleNBT(StringTag gameRuleTag) {
            setGameRuleTag(gameRuleTag);
        }

        @Override
        public void changeValue(String newValue) {
            StringTag stringTag = (StringTag) getGameRuleTag();
            stringTag.setValue(newValue);
        }
    }

    static class StringByteGameRuleNBT extends GameRuleNBT<Boolean> {

        public StringByteGameRuleNBT(StringTag gameRuleTag) {
            setGameRuleTag(gameRuleTag);
        }

        @Override
        public void changeValue(Boolean newValue) {
            StringTag stringTag = (StringTag) getGameRuleTag();
            stringTag.setValue(newValue ? "true" : "false");
        }
    }
}
