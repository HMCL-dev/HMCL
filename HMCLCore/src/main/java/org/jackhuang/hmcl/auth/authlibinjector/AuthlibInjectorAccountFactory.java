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
import java.util.function.Function;

import static org.jackhuang.hmcl.util.Lang.tryCast;

public class AuthlibInjectorAccountFactory extends AccountFactory<AuthlibInjectorAccount> {
    private final ExceptionalSupplier<String, ?> injectorJarPathSupplier;
    private Function<String, AuthlibInjectorServer> serverLookup;

    /**
     * @param serverLookup a function that looks up {@link AuthlibInjectorServer} by url
     */
    public AuthlibInjectorAccountFactory(ExceptionalSupplier<String, ?> injectorJarPathSupplier, Function<String, AuthlibInjectorServer> serverLookup) {
        this.injectorJarPathSupplier = injectorJarPathSupplier;
        this.serverLookup = serverLookup;
    }

    @Override
    public AuthlibInjectorAccount create(CharacterSelector selector, String username, String password, Object apiRoot, Proxy proxy) throws AuthenticationException {
        Objects.requireNonNull(selector);
        Objects.requireNonNull(username);
        Objects.requireNonNull(password);
        Objects.requireNonNull(proxy);

        if (!(apiRoot instanceof String) || !NetworkUtils.isURL((String) apiRoot))
            throw new IllegalArgumentException("Additional data should be API root string for authlib injector accounts.");

        AuthlibInjectorServer server = serverLookup.apply((String) apiRoot);

        AuthlibInjectorAccount account = new AuthlibInjectorAccount(new YggdrasilService(new AuthlibInjectorProvider(server.getUrl()), proxy),
                server.getUrl(), injectorJarPathSupplier, username, null, null);
        account.logInWithPassword(password, selector);
        return account;
    }

    @Override
    public AuthlibInjectorAccount fromStorage(Map<Object, Object> storage, Proxy proxy) {
        Objects.requireNonNull(storage);
        Objects.requireNonNull(proxy);

        YggdrasilSession session = YggdrasilSession.fromStorage(storage);

        String username = tryCast(storage.get("username"), String.class)
                .orElseThrow(() -> new IllegalArgumentException("storage does not have username"));
        String apiRoot = tryCast(storage.get("serverBaseURL"), String.class)
                .orElseThrow(() -> new IllegalArgumentException("storage does not have API root."));

        AuthlibInjectorServer server = serverLookup.apply(apiRoot);

        return new AuthlibInjectorAccount(new YggdrasilService(new AuthlibInjectorProvider(server.getUrl()), proxy),
                server.getUrl(), injectorJarPathSupplier, username, session.getSelectedProfile().getId(), session);
    }
}
