package org.jackhuang.hmcl.auth.authlibinjector;

import org.jackhuang.hmcl.auth.AccountFactory;
import org.jackhuang.hmcl.auth.AuthenticationException;
import org.jackhuang.hmcl.auth.CharacterSelector;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilService;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilSession;
import org.jackhuang.hmcl.util.ExceptionalSupplier;
import org.jackhuang.hmcl.util.NetworkUtils;

import java.net.Proxy;
import java.util.Map;
import java.util.Objects;

import static org.jackhuang.hmcl.util.Lang.tryCast;

public class AuthlibInjectorAccountFactory extends AccountFactory<AuthlibInjectorAccount> {
    private final ExceptionalSupplier<String, ?> injectorJarPathSupplier;

    public AuthlibInjectorAccountFactory(ExceptionalSupplier<String, ?> injectorJarPathSupplier) {
        this.injectorJarPathSupplier = injectorJarPathSupplier;
    }

    @Override
    public AuthlibInjectorAccount create(CharacterSelector selector, String username, String password, Object serverBaseURL, Proxy proxy) throws AuthenticationException {
        Objects.requireNonNull(selector);
        Objects.requireNonNull(username);
        Objects.requireNonNull(password);
        Objects.requireNonNull(proxy);

        if (!(serverBaseURL instanceof String) || !NetworkUtils.isURL((String) serverBaseURL))
            throw new IllegalArgumentException("Additional data should be server base url string for authlib injector accounts.");

        AuthlibInjectorAccount account = new AuthlibInjectorAccount(new YggdrasilService(new AuthlibInjectorProvider((String) serverBaseURL), proxy),
                (String) serverBaseURL, injectorJarPathSupplier, username, null, null);
        account.logInWithPassword(password, selector);
        return account;
    }

    @Override
    public AuthlibInjectorAccount fromStorage(Map<Object, Object> storage, Proxy proxy) {
        Objects.requireNonNull(storage);
        Objects.requireNonNull(proxy);

        String username = tryCast(storage.get("username"), String.class)
                .orElseThrow(() -> new IllegalArgumentException("storage does not have username"));
        String character = tryCast(storage.get("clientToken"), String.class)
                .orElseThrow(() -> new IllegalArgumentException("storage does not have selected character name."));
        String apiRoot = tryCast(storage.get("serverBaseURL"), String.class)
                .orElseThrow(() -> new IllegalArgumentException("storage does not have API root."));

        return new AuthlibInjectorAccount(new YggdrasilService(new AuthlibInjectorProvider(apiRoot), proxy),
                apiRoot, injectorJarPathSupplier, username, character, YggdrasilSession.fromStorage(storage));
    }
}
