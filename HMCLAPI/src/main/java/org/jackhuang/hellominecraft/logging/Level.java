/*
 * Copyright 2013 huangyuhui <huanghongxun2008@126.com>
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.
 */
package org.jackhuang.hellominecraft.logging;

/**
 *
 * @author hyh
 */
public enum Level {

    OFF(0),
    FATAL(1),
    ERROR(2),
    WARN(3),
    INFO(4),
    DEBUG(5),
    TRACE(6),
    ALL(2147483647);

    public final int level;

    private Level(int i) {
	level = i;
    }

    public boolean lessOrEqual(Level level) {
	return this.level <= level.level;
    }

    public boolean lessOrEqual(int level) {
	return this.level <= level;
    }

}
