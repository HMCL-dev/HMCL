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
package org.jackhuang.hmcl.core.install;

import java.io.Serializable;
import java.util.Comparator;
import org.jackhuang.hmcl.core.install.InstallerVersionList.InstallerVersion;

/**
 *
 * @author huangyuhui
 */
public class InstallerVersionNewerComparator implements Comparator<InstallerVersion>, Serializable {

    private static final long serialVersionUID = 14758562453742645L;

    @Override
    public int compare(InstallerVersion o1, InstallerVersion o2) {
        return o2.compareTo(o1);
    }
}
