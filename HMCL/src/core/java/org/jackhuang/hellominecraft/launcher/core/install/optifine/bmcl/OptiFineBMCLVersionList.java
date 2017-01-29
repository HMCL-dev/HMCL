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
package org.jackhuang.hellominecraft.launcher.core.install.optifine.bmcl;

import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import org.jackhuang.hellominecraft.util.C;
import org.jackhuang.hellominecraft.util.ArrayUtils;
import org.jackhuang.hellominecraft.launcher.core.install.InstallerVersionList;
import org.jackhuang.hellominecraft.launcher.core.install.optifine.OptiFineVersion;
import org.jackhuang.hellominecraft.util.StrUtils;
import org.jackhuang.hellominecraft.util.tasks.Task;
import org.jackhuang.hellominecraft.util.tasks.TaskInfo;
import org.jackhuang.hellominecraft.util.tasks.download.HTTPGetTask;

/**
 *
 * @author huangyuhui
 */
public class OptiFineBMCLVersionList extends InstallerVersionList {

    private static OptiFineBMCLVersionList instance;

    public static OptiFineBMCLVersionList getInstance() {
        if (null == instance)
            instance = new OptiFineBMCLVersionList();
        return instance;
    }

    public ArrayList<OptiFineVersion> root;

    private static final Type TYPE = new TypeToken<ArrayList<OptiFineVersion>>() {
    }.getType();

    @Override
    public Task refresh(String[] needed) {
        return new TaskInfo(C.i18n("install.optifine.get_list")) {
            HTTPGetTask task = new HTTPGetTask("http://bmclapi.bangbang93.com/optifine/versionlist");

            @Override
            public Collection<Task> getDependTasks() {
                return Arrays.asList(task.setTag("BMCL Optifine Download Site"));
            }

            @Override
            public void executeTask(boolean areDependTasksSucceeded) throws Throwable {
                String s = task.getResult();

                versionMap = new HashMap<>();
                versions = new ArrayList<>();
                
                HashSet<String> duplicates = new HashSet<>();

                if (s == null)
                    return;
                root = C.GSON.fromJson(s, TYPE);
                for (OptiFineVersion v : root) {
                    v.setVersion(v.type + '_' + v.patch);
                    v.setMirror(String.format("http://bmclapi2.bangbang93.com/optifine/%s/%s/%s", v.getMCVersion(), v.type, v.patch));
                    if (duplicates.contains(v.getMirror()))
                        continue;
                    else
                        duplicates.add(v.getMirror());
                    InstallerVersion iv = new InstallerVersion(v.getVersion(), StrUtils.formatVersion(v.getMCVersion()));

                    List<InstallerVersion> al = ArrayUtils.tryGetMapWithList(versionMap, StrUtils.formatVersion(v.getMCVersion()));
                    iv.installer = iv.universal = v.getMirror();
                    al.add(iv);
                    versions.add(iv);
                }

                Collections.sort(versions, InstallerVersionComparator.INSTANCE);
            }
        };
    }

    @Override
    public String getName() {
        return "OptiFine - BMCLAPI(By: bangbang93)";
    }

}
