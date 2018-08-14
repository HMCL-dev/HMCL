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
package org.jackhuang.hmcl.util.upgrade;

import java.util.List;
import org.jackhuang.hmcl.api.event.SimpleEvent;
import org.jackhuang.hmcl.api.VersionNumber;
import org.jackhuang.hmcl.api.func.Consumer;

/**
 *
 * @author huangyuhui
 */
public abstract class IUpgrader implements Consumer<SimpleEvent<VersionNumber>> {

    public static final IUpgrader NOW_UPGRADER = new AppDataUpgrader();

    /**
     * Paring arguments to decide on whether the upgrade is needed.
     *
     * @param nowVersion now launcher version
     * @param args       Application CommandLine Arguments
     */
    public abstract void parseArguments(VersionNumber nowVersion, List<String> args);

    /**
     * Just download the new app.
     *
     * @param sender Should be VersionChecker
     * @param number the newest version
     *
     * @return should return true
     */
    @Override
    public abstract void accept(SimpleEvent<VersionNumber> event);
}
