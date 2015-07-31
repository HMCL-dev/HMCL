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
package org.jackhuang.hellominecraft.launcher.utils;

import java.io.File;
import javax.swing.filechooser.FileFilter;

/**
 *
 * @author huangyuhui
 */
public class FileNameFilter extends FileFilter {

    String acceptedName;

    public FileNameFilter(String acceptedName) {
        this.acceptedName = acceptedName;
    }

    @Override
    public boolean accept(File f) {
        return f.isDirectory() || f.getName().equals(acceptedName);
    }

    @Override
    public String getDescription() {
        return acceptedName;
    }
}
