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
package org.jackhuang.hellominecraft.util.log.appender;

import java.io.Serializable;
import org.jackhuang.hellominecraft.util.log.layout.ILayout;

/**
 *
 * @author huangyuhui
 */
public abstract class AbstractAppender implements IAppender {

    String name;
    private final ILayout<? extends Serializable> layout;
    private final boolean ignoreExceptions;

    public AbstractAppender(String name, ILayout<? extends Serializable> layout) {
        this(name, layout, true);
    }

    public AbstractAppender(String name, ILayout<? extends Serializable> layout, boolean ignoreExceptions) {
        this.name = name;
        this.layout = layout;
        this.ignoreExceptions = ignoreExceptions;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean ignoreExceptions() {
        return ignoreExceptions;
    }

    @Override
    public ILayout<? extends Serializable> getLayout() {
        return this.layout;
    }
}
