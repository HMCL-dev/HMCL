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
package org.jackhuang.hellominecraft.launcher.api;

import javax.swing.JFrame;
import org.jackhuang.hellominecraft.launcher.core.auth.IAuthenticator;
import org.jackhuang.hellominecraft.util.func.Consumer;

/**
 * Each plugin must implement this interface.
 *
 * @author huangyuhui
 */
public interface IPlugin {

    /**
     * Register authenticators by calling IAuthenticator.LOGINS.add.
     *
     * @param apply call apply.accept(your authenticator)
     */
    void onRegisterAuthenticators(Consumer<IAuthenticator> apply);

    /**
     * Call callback.addTab to add your customized panel to MainFrame RootPane.
     *
     * @param frame MainFrame
     * @param callback call this if you want.
     */
    void onAddTab(JFrame frame, AddTabCallback callback);
}
