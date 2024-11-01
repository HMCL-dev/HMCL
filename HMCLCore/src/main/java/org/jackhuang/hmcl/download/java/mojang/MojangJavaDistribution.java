/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2024 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.download.java.mojang;

import org.jackhuang.hmcl.download.java.JavaDistribution;
import org.jackhuang.hmcl.download.java.JavaPackageType;
import org.jackhuang.hmcl.download.java.JavaRemoteVersion;

import java.util.Collections;
import java.util.Set;

/**
 * @author Glavo
 */
public final class MojangJavaDistribution implements JavaDistribution<JavaRemoteVersion> {

    public static final MojangJavaDistribution DISTRIBUTION = new MojangJavaDistribution();

    private MojangJavaDistribution() {
    }

    @Override
    public String getDisplayName() {
        return "Mojang";
    }

    @Override
    public Set<JavaPackageType> getSupportedPackageTypes() {
        return Collections.singleton(JavaPackageType.JRE);
    }
}
