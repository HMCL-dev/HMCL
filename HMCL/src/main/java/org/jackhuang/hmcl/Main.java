/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com>
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
package org.jackhuang.hmcl;

import org.apache.commons.compress.utils.Charsets;
import org.jackhuang.hmcl.util.Logging;
import javax.swing.JOptionPane;
import java.io.File;

public final class Main {

    public static void main(String[] args) {
        String currentDirectory = new File("").getAbsolutePath();
        Logging.LOG.info("Current directory: " + currentDirectory);
        if (currentDirectory.contains("!")) {
            Logging.LOG.severe("Exclamation mark(!) is not allowed in the path where HMCL is in. Forcibly exit.");

            // No Chinese translation because both Swing and JavaFX cannot render Chinese character properly when exclamation mark exists in the path.
            String message = "Exclamation mark(!) is not allowed in the path where HMCL is in.\nThe path is " + currentDirectory;
            JOptionPane.showMessageDialog(null, message, "Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        } else
            Launcher.main(args);
    }
}
