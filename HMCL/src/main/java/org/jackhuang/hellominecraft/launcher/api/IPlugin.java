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

import org.jackhuang.hellominecraft.launcher.core.service.IMinecraftService;
import org.jackhuang.hellominecraft.launcher.settings.Profile;
import org.jackhuang.hellominecraft.launcher.core.auth.AuthenticationException;
import org.jackhuang.hellominecraft.launcher.core.auth.IAuthenticator;
import org.jackhuang.hellominecraft.launcher.core.auth.UserProfileProvider;
import org.jackhuang.hellominecraft.utils.functions.Consumer;

/**
 *
 * @author huangyuhui
 */
public interface IPlugin {

    /**
     * You can modify the application actions by this method.
     *
     * @param profile info to the Minecraft Loader
     *
     * @return For example, you can implement IMinecraftProvider to support
     *         MultiMC
     */
    IMinecraftService provideMinecraftService(Profile profile);

    /**
     * Register authenticators by calling IAuthenticator.LOGINS.add.
     *
     * @param apply call apply.accept(your authenticator)
     */
    void onRegisterAuthenticators(Consumer<IAuthenticator> apply);

    /**
     * Open your customized UI.
     */
    void showUI();

    /**
     * Add your server ip or modify the access token.
     *
     * @param result What you want.
     */
    void onProcessingLoginResult(UserProfileProvider result) throws AuthenticationException;

    void onInitializingProfile(Profile p);
}
