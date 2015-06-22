/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
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
