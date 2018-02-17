package org.jackhuang.hmcl.auth.yggdrasil;

import org.jackhuang.hmcl.auth.AuthInfo;
import org.jackhuang.hmcl.auth.AuthenticationException;
import org.jackhuang.hmcl.auth.MultiCharacterSelector;
import org.jackhuang.hmcl.game.Arguments;
import org.jackhuang.hmcl.task.GetTask;
import org.jackhuang.hmcl.util.ExceptionalSupplier;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.NetworkUtils;

import java.net.Proxy;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class AuthlibInjectorAccount extends YggdrasilAccount {
    private final String serverBaseURL;
    private final ExceptionalSupplier<String, ?> injectorJarPath;

    public AuthlibInjectorAccount(ExceptionalSupplier<String, ?> injectorJarPath, String serverBaseURL, String username) {
        super(serverBaseURL + "authserver/", serverBaseURL + "sessionserver/", username);

        this.injectorJarPath = injectorJarPath;
        this.serverBaseURL = serverBaseURL;
    }

    @Override
    public AuthInfo logIn(MultiCharacterSelector selector, Proxy proxy) throws AuthenticationException {
        // Authlib Injector recommends launchers to pre-fetch the server basic information before launched the game to save time.
        GetTask getTask = new GetTask(NetworkUtils.toURL(serverBaseURL));
        AtomicBoolean flag = new AtomicBoolean(true);
        Thread thread = Lang.thread(() -> {
            try {
                getTask.run();
            } catch (Exception ignore) {
                flag.set(false);
            }
        });

        AuthInfo info = super.logIn(selector, proxy);
        try {
            thread.join();

            String arg = "-javaagent:" + injectorJarPath.get() + "=" + serverBaseURL;
            Arguments arguments = Arguments.addJVMArguments(null, arg);

            if (flag.get())
                arguments = Arguments.addJVMArguments(arguments, "-Dorg.to2mbn.authlibinjector.config.prefetched=" + getTask.getResult());

            return info.setArguments(arguments);
        } catch (Exception e) {
            throw new AuthenticationException("Unable to get authlib injector jar path", e);
        }
    }

    @Override
    public Map<Object, Object> toStorageImpl() {
        Map<Object, Object> map = super.toStorageImpl();
        map.put(STORAGE_KEY_SERVER_BASE_URL, serverBaseURL);
        return map;
    }

    public String getServerBaseURL() {
        return serverBaseURL;
    }

    public static final String STORAGE_KEY_SERVER_BASE_URL = "serverBaseURL";
}
