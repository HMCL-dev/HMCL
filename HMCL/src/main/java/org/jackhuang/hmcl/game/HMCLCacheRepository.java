/**
 * Hello Minecraft! Launcher
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.game;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import org.jackhuang.hmcl.download.DefaultCacheRepository;

import java.nio.file.Paths;

public class HMCLCacheRepository extends DefaultCacheRepository {

    private final StringProperty directory = new SimpleStringProperty();

    public HMCLCacheRepository() {
        directory.addListener((a, b, t) -> changeDirectory(Paths.get(t)));
    }

    public String getDirectory() {
        return directory.get();
    }

    public StringProperty directoryProperty() {
        return directory;
    }

    public void setDirectory(String directory) {
        this.directory.set(directory);
    }

    public static final HMCLCacheRepository REPOSITORY = new HMCLCacheRepository();
}
