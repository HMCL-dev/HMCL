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
package org.jackhuang.hmcl.api.game;

import java.io.File;

/**
 * @author huangyuhui
 */
public class DecompressLibraryJob {

    public File[] decompressFiles;
    public Extract[] extractRules;
    private File decompressTo;

    /**
     * The length of these 2 arrays must be the same.
     *
     * @param decompressFiles
     * @param extractRules
     * @param decompressTo folder
     */
    public DecompressLibraryJob(File[] decompressFiles, Extract[] extractRules, File decompressTo) {
        this.decompressFiles = decompressFiles.clone();
        this.extractRules = extractRules.clone();
        this.decompressTo = decompressTo;
    }

    public File getDecompressTo() {
        return decompressTo;
    }

    public void setDecompressTo(File decompressTo) {
        this.decompressTo = decompressTo;
    }

}
