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
package org.jackhuang.hmcl.auth.authlibinjector;

import org.jackhuang.hmcl.auth.AuthInfo;
import org.jackhuang.hmcl.auth.AuthenticationException;
import org.jackhuang.hmcl.auth.CharacterSelector;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilAccount;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilService;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilSession;
import org.jackhuang.hmcl.game.Arguments;
import org.jackhuang.hmcl.task.GetTask;
import org.jackhuang.hmcl.util.ExceptionalSupplier;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.NetworkUtils;

import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.nio.charset.StandardCharsets.UTF_8;

public class AuthlibInjectorAccount extends YggdrasilAccount {
    private final String serverBaseURL;
    private final ExceptionalSupplier<String, ?> injectorJarPath;

    protected AuthlibInjectorAccount(YggdrasilService service, String serverBaseURL, ExceptionalSupplier<String, ?> injectorJarPath, String username, String character, YggdrasilSession session) {
        super(service, username, character, session);

        this.injectorJarPath = injectorJarPath;
        this.serverBaseURL = serverBaseURL;
    }

    @Override
    public AuthInfo logIn() throws AuthenticationException {
        return inject(super::logIn);
    }

    @Override
    protected AuthInfo logInWithPassword(String password, CharacterSelector selector) throws AuthenticationException {
        return inject(() -> super.logInWithPassword(password, selector));
    }

    private AuthInfo inject(ExceptionalSupplier<AuthInfo, AuthenticationException> supplier) throws AuthenticationException {
        // Authlib Injector recommends launchers to pre-fetch the server basic information before launched the game to save time.
        GetTask getTask = new GetTask(NetworkUtils.toURL(serverBaseURL));
        AtomicBoolean flag = new AtomicBoolean(true);
        Thread thread = Lang.thread(() -> flag.set(getTask.test()));

        AuthInfo info = supplier.get();
        try {
            thread.join();

            Arguments arguments = new Arguments().addJVMArguments("-javaagent:" + injectorJarPath.get() + "=" + serverBaseURL);

            if (flag.get())
                arguments = arguments.addJVMArguments("-Dorg.to2mbn.authlibinjector.config.prefetched=" + new String(Base64.getEncoder().encode(getTask.getResult().getBytes()), UTF_8));

            return info.withArguments(arguments);
        } catch (Exception e) {
            throw new AuthenticationException("Unable to get authlib injector jar path", e);
        }
    }

    @Override
    public Map<Object, Object> toStorage() {
        Map<Object, Object> map = super.toStorage();
        map.put("serverBaseURL", serverBaseURL);
        return map;
    }

    public String getServerBaseURL() {
        return serverBaseURL;
    }

}
