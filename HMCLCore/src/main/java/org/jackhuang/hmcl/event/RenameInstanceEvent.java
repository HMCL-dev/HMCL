/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.event;

import org.jackhuang.hmcl.game.GameInstanceID;
import org.jackhuang.hmcl.game.GameRepository;
import org.jackhuang.hmcl.util.ToStringBuilder;
import org.jetbrains.annotations.NotNullByDefault;

/// This event gets fired when a minecraft instance is being removed.
///
/// This event is fired on the [org.jackhuang.hmcl.event.EventBus#EVENT_BUS]
///
/// @author huangyuhui
@NotNullByDefault
public final class RenameInstanceEvent extends Event {

    private final GameInstanceID from, to;

    /**
     *
     * @param source {@link GameRepository}
     * @param from the instance id.
     */
    public RenameInstanceEvent(Object source, GameInstanceID from, GameInstanceID to) {
        super(source);
        this.from = from;
        this.to = to;
    }

    public GameInstanceID getFrom() {
        return from;
    }

    public GameInstanceID getTo() {
        return to;
    }

    @Override
    public boolean hasResult() {
        return true;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("source", source)
                .append("from", from)
                .append("to", to)
                .toString();
    }
}
