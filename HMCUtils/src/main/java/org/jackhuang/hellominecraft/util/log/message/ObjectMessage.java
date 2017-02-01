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

public class ObjectMessage
    implements IMessage {

    private static final long serialVersionUID = -5903272448334166185L;
    private final Object obj;

    public ObjectMessage(Object obj) {
        if (obj == null)
            obj = "null";
        this.obj = obj;
    }

    @Override
    public String getFormattedMessage() {
        return this.obj.toString();
    }

    @Override
    public String getFormat() {
        return this.obj.toString();
    }

    @Override
    public Object[] getParameters() {
        return new Object[] { this.obj };
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if ((o == null) || (getClass() != o.getClass()))
            return false;

        ObjectMessage that = (ObjectMessage) o;

        return this.obj != null ? this.obj.equals(that.obj) : that.obj == null;
    }

    @Override
    public int hashCode() {
        return this.obj != null ? this.obj.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "ObjectMessage[obj=" + this.obj.toString() + "]";
    }

    @Override
    public Throwable getThrowable() {
        return (this.obj instanceof Throwable) ? (Throwable) this.obj : null;
    }
}
