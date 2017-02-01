/*
 * Hello Minecraft!.
 * Copyright (C) 2013  huangyuhui <huanghongxun2008@126.com>
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
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hellominecraft.util.log.message;

/**
 *
 * @author huangyuhui
 */
public class SimpleMessage
    implements IMessage {

    private static final long serialVersionUID = -8398002534962715992L;
    private final String message;

    public SimpleMessage() {
        this(null);
    }

    public SimpleMessage(String message) {
        this.message = message;
    }

    @Override
    public String getFormattedMessage() {
        return this.message;
    }

    @Override
    public String getFormat() {
        return this.message;
    }

    @Override
    public Object[] getParameters() {
        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if ((o == null) || (getClass() != o.getClass()))
            return false;

        SimpleMessage that = (SimpleMessage) o;

        return this.message != null ? this.message.equals(that.message) : that.message == null;
    }

    @Override
    public int hashCode() {
        return this.message != null ? this.message.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "SimpleMessage[message=" + this.message + "]";
    }

    @Override
    public Throwable getThrowable() {
        return null;
    }
}
