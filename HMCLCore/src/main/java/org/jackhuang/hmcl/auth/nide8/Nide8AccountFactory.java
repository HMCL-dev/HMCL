package org.jackhuang.hmcl.auth.nide8;

import org.jackhuang.hmcl.auth.AccountFactory;
import org.jackhuang.hmcl.auth.AuthenticationException;
import org.jackhuang.hmcl.auth.CharacterSelector;
import org.jackhuang.hmcl.util.javafx.ObservableOptionalCache;

import java.net.MalformedURLException;
import java.util.Map;
import java.util.Objects;

import static org.jackhuang.hmcl.util.Lang.tryCast;

public class Nide8AccountFactory extends AccountFactory<Nide8Account> {

    private final Nide8Service service;

    public Nide8AccountFactory(Nide8Service service) {
        this.service = service;
    }

    @Override
    public AccountLoginType getLoginType() {
        return AccountLoginType.USERNAME_PASSWORD;
    }

    @Override
    public Nide8Account create(CharacterSelector selector, String username, String password, ProgressCallback progressCallback, Object additionalData) throws AuthenticationException {
        Objects.requireNonNull(selector);
        Objects.requireNonNull(username);
        Objects.requireNonNull(password);
        Objects.requireNonNull(additionalData);

        try {
            return new Nide8Account(service, (String) additionalData, username, password);
        } catch (MalformedURLException e) {
            return null;
        }
    }

    @Override
    public Nide8Account fromStorage(Map<Object, Object> storage) {
        Objects.requireNonNull(storage);

        Nide8Session session = Nide8Session.fromStorage(storage);

        String username = tryCast(storage.get("username"), String.class)
                .orElseThrow(() -> new IllegalArgumentException("storage does not have username"));
        String serverID = tryCast(storage.get("serverID"), String.class)
                .orElseThrow(() -> new IllegalArgumentException("storage does not have serverID"));

        tryCast(storage.get("profileProperties"), Map.class).ifPresent(
                it -> {
                    @SuppressWarnings("unchecked")
                    Map<String, String> properties = it;
                    Nide8GameProfile selected = session.getSelectedProfile();
                    ObservableOptionalCache<Nide8LoginObj, Nide8GameProfile, AuthenticationException> profileRepository = service.getProfileRepository();
                    Nide8LoginObj obj = new Nide8LoginObj(selected.getServerID(), selected.getId());
                    profileRepository.put(obj, new Nide8GameProfile(selected, properties));
                    profileRepository.invalidate(obj);
                });

        return new Nide8Account(service, serverID, username, session);
    }
}
