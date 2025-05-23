/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2025 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.ui.versions;

import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.ListPageBase;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * @author Glavo
 */
public final class SchematicsPage extends ListPageBase<WorldListItem> implements VersionPage.VersionLoadable {

    private Profile profile;
    private String id;

    @Override
    public void loadVersion(Profile profile, String version) {
        this.profile = profile;
        this.id = id;
    }

    public void refresh() {
        if (profile == null || id == null)
            return;
        setLoading(true);
        Task.runAsync(() -> {

                })
                .whenComplete(Schedulers.javafx(), (result, exception) -> {
                    setLoading(false);
                    if (exception == null) {

                    } else {
                        LOG.warning("Failed to load schematics", exception);
                    }
                }).start();
    }
}
