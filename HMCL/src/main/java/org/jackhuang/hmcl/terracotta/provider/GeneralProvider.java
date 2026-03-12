/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2025  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.terracotta.provider;

import org.jackhuang.hmcl.terracotta.TerracottaBundle;

import java.nio.file.Path;
import java.util.List;

public final class GeneralProvider extends AbstractTerracottaProvider {
    private final Path executable;

    public GeneralProvider(TerracottaBundle bundle, Path executable) {
        super(bundle);
        this.executable = executable;
    }

    @Override
    public List<String> ofCommandLine(Path portTransfer) {
        return List.of(executable.toString(), "--hmcl", portTransfer.toString());
    }
}
