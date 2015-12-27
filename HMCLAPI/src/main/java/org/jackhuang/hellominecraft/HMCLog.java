/*
 * Hello Minecraft! Launcher.
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
package org.jackhuang.hellominecraft;

import org.jackhuang.hellominecraft.logging.logger.Logger;

/**
 *
 * @author huangyuhui
 */
public class HMCLog {

    public static Logger logger = new Logger("Hello Minecraft!");

    public static void log(String message) {
        logger.info(message);
    }

    public static void warn(String message) {
        logger.warn(message);
    }

    public static void debug(String message) {
        logger.debug(message);
    }

    public static void warn(String msg, Throwable t) {
        logger.warn(msg, t);
    }

    public static void debug(String msg, Throwable t) {
        logger.debug(msg, t);
    }

    public static void err(String msg) {
        logger.error(msg);
    }

    public static void err(String msg, Throwable t) {
        logger.error(msg, t);
    }

}
