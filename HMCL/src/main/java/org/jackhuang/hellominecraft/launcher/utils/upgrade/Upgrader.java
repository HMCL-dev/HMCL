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
package org.jackhuang.hellominecraft.launcher.utils.upgrade;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.jar.JarOutputStream;
import java.util.jar.Pack200;
import org.jackhuang.hellominecraft.C;
import org.jackhuang.hellominecraft.launcher.utils.MCUtils;
import org.jackhuang.hellominecraft.tasks.Task;
import org.jackhuang.hellominecraft.tasks.download.FileDownloadTask;
import org.jackhuang.hellominecraft.utils.system.FileUtils;
import org.tukaani.xz.XZInputStream;

/**
 *
 * @author huangyuhui
 */
public class Upgrader extends Task {

    public static final File BASE_FOLDER = MCUtils.getWorkingDirectory("hmcl");
    public static final File HMCL_VER_FILE = new File(BASE_FOLDER, "hmclver.json");

    public static File getSelf(String ver) {
        return new File(BASE_FOLDER, "HMCL-" + ver + ".jar");
    }

    private final String downloadLink, newestVersion;
    File tempFile;

    public Upgrader(String downloadLink, String newestVersion) throws IOException {
        this.downloadLink = downloadLink;
        this.newestVersion = newestVersion;
        tempFile = File.createTempFile("hmcl", ".pack.xz");
    }

    @Override
    public Collection<Task> getDependTasks() {
        return Arrays.asList(new FileDownloadTask(downloadLink, tempFile));
    }

    @Override
    public boolean executeTask() {
        HashMap<String, String> json = new HashMap<>();
        File f = getSelf(newestVersion);
        try {
            if (!f.getParentFile().exists()) f.getParentFile().mkdirs();
            
            for (int i = 0; f.exists(); i++) f = new File(BASE_FOLDER, "HMCL-" + newestVersion + (i > 0 ? "-" + i : "") + ".jar");
            f.createNewFile();
            
            try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(f))) {
                Pack200.newUnpacker().unpack(new XZInputStream(new FileInputStream(tempFile)), jos);
            }
            json.put("ver", newestVersion);
            json.put("loc", f.getAbsolutePath());
            String result = C.gson.toJson(json);
            FileUtils.write(HMCL_VER_FILE, result);
            return true;
        } catch (IOException e) {
            setFailReason(e);
            return false;
        }
    }

    @Override
    public String getInfo() {
        return "Upgrade";
    }

}
