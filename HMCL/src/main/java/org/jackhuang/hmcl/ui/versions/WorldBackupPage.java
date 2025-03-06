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

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import org.jackhuang.hmcl.ui.ListPageBase;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;

/**
 * @author Glavo
 */
public final class WorldBackupPage extends ListPageBase<WorldBackupPage.BackupInfo> implements DecoratorPage {

    private final ObjectProperty<State> state = new SimpleObjectProperty<>();

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state;
    }

    public static final class BackupInfo {

    }
}
