package org.jackhuang.hmcl.auth.yggdrasil;

import org.jackhuang.hmcl.auth.AccountFactory;
import org.jackhuang.hmcl.util.ExceptionalSupplier;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.NetworkUtils;
import org.jackhuang.hmcl.util.UUIDTypeAdapter;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.jackhuang.hmcl.auth.yggdrasil.AuthlibInjectorAccount.STORAGE_KEY_SERVER_BASE_URL;
import static org.jackhuang.hmcl.auth.yggdrasil.YggdrasilAccount.*;

public class AuthlibInjectorAccountFactory extends AccountFactory<YggdrasilAccount> {
    private final ExceptionalSupplier<String, ?> injectorJarPathSupplier;

    public AuthlibInjectorAccountFactory(ExceptionalSupplier<String, ?> injectorJarPathSupplier) {
        this.injectorJarPathSupplier = injectorJarPathSupplier;
    }

    @Override
    public AuthlibInjectorAccount fromUsername(String username, String password, Object additionalData) {
        if (!(additionalData instanceof String) || !NetworkUtils.isURL((String) additionalData))
            throw new IllegalArgumentException("Additional data should be server base url string for authlib injector accounts.");

        AuthlibInjectorAccount account = new AuthlibInjectorAccount(injectorJarPathSupplier, (String) additionalData, username);
        account.setPassword(password);
        return account;
    }

    @Override
    public AuthlibInjectorAccount fromStorageImpl(Map<Object, Object> storage) {
        String username = Lang.get(storage, STORAGE_KEY_USER_NAME, String.class)
                .orElseThrow(() -> new IllegalArgumentException("storage does not have key " + STORAGE_KEY_USER_NAME));
        String serverBaseURL = Lang.get(storage, STORAGE_KEY_SERVER_BASE_URL, String.class)
                .orElseThrow(() -> new IllegalArgumentException("storage does not have key " + STORAGE_KEY_SERVER_BASE_URL));

        AuthlibInjectorAccount account = new AuthlibInjectorAccount(injectorJarPathSupplier, serverBaseURL, username);
        account.setUserId(Lang.get(storage, STORAGE_KEY_USER_ID, String.class).orElse(username));
        account.setAccessToken(Lang.get(storage, STORAGE_KEY_ACCESS_TOKEN, String.class).orElse(null));
        account.setClientToken(Lang.get(storage, STORAGE_KEY_CLIENT_TOKEN, String.class)
                .orElseThrow(() -> new IllegalArgumentException("storage does not have key " + STORAGE_KEY_CLIENT_TOKEN)));

        Lang.get(storage, STORAGE_KEY_USER_PROPERTIES, List.class)
                .ifPresent(account.getUserProperties()::fromList);
        Optional<String> profileId = Lang.get(storage, STORAGE_KEY_PROFILE_ID, String.class);
        Optional<String> profileName = Lang.get(storage, STORAGE_KEY_PROFILE_NAME, String.class);
        GameProfile profile = null;
        if (profileId.isPresent() && profileName.isPresent()) {
            profile = new GameProfile(UUIDTypeAdapter.fromString(profileId.get()), profileName.get());
            Lang.get(storage, STORAGE_KEY_PROFILE_PROPERTIES, List.class)
                    .ifPresent(profile.getProperties()::fromList);
        }
        account.setSelectedProfile(profile);
        return account;
    }
}
