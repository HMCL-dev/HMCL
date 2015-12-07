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
package org.jackhuang.hellominecraft.launcher.version;

import org.jackhuang.hellominecraft.launcher.settings.Profile;
import org.jackhuang.hellominecraft.launcher.settings.Settings;
import org.jackhuang.hellominecraft.launcher.utils.assets.IAssetsHandler;
import org.jackhuang.hellominecraft.tasks.Task;
import org.jackhuang.hellominecraft.tasks.TaskWindow;
import rx.concurrency.Schedulers;

/**
 *
 * @author huangyuhui
 */
public class MinecraftService {

    Profile profile;

    public MinecraftService(Profile profile) {
        this.profile = profile;
    }

    public Task downloadAssets(String mcVersion) {
        return new Task() {

            @Override
            public void executeTask() throws Throwable {
                IAssetsHandler type = IAssetsHandler.ASSETS_HANDLER;
                type.getList(profile.getMinecraftProvider().getVersionById(mcVersion), profile.getMinecraftProvider())
                    .subscribeOn(Schedulers.newThread())
                    .observeOn(Schedulers.eventQueue())
                    .subscribe((value) -> TaskWindow.getInstance().addTask(type.getDownloadTask(Settings.getInstance().getDownloadSource().getProvider())).start());
            }

            @Override
            public String getInfo() {
                return "Download Assets";
            }
        };
    }
}
