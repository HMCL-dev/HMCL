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

import javax.swing.*;
import java.io.File;

public final class Main {

    public static void main(String[] args) {
        checkJavaFX();
        checkDirectoryPath();
        Launcher.main(args);
    }

    private static void checkDirectoryPath() {
        String currentDirectory = new File("").getAbsolutePath();
        if (currentDirectory.contains("!")) {
            // No Chinese translation because both Swing and JavaFX cannot render Chinese character properly when exclamation mark exists in the path.
            showErrorAndExit("Exclamation mark(!) is not allowed in the path where HMCL is in.\n"
                    + "The path is " + currentDirectory);
        }
    }

    private static void checkJavaFX() {
        try {
            Class.forName("javafx.application.Application");
        } catch (ClassNotFoundException e) {
            showErrorAndExit("JavaFX is missing.\n"
                    + "If you are using Java 11 or above, please downgrade to Java 8 or 10.\n"
                    + "If you are using OpenJDK, please ensure OpenJFX is included.");
        }
    }

    private static void showErrorAndExit(String message) {
        System.err.println(message);
        System.err.println("A fatal error has occurred, forcibly exiting.");
        JOptionPane.showMessageDialog(null, message, "Error", JOptionPane.ERROR_MESSAGE);
        System.exit(1);
    }

}
