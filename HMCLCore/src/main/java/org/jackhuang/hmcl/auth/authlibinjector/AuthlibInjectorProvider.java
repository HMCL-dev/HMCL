// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.auth.authlibinjector;

import org.glavo.url.WebURL;
import org.glavo.uuid.UUIDs;
import org.jackhuang.hmcl.auth.AuthenticationException;
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilProvider;

import java.util.UUID;

public class AuthlibInjectorProvider implements YggdrasilProvider {

    private final String apiRoot;

    public AuthlibInjectorProvider(String apiRoot) {
        this.apiRoot = apiRoot;
    }

    @Override
    public WebURL getAuthenticationURL() throws AuthenticationException {
        return WebURL.parse(apiRoot + "authserver/authenticate");
    }

    @Override
    public WebURL getRefreshmentURL() throws AuthenticationException {
        return WebURL.parse(apiRoot + "authserver/refresh");
    }

    @Override
    public WebURL getValidationURL() throws AuthenticationException {
        return WebURL.parse(apiRoot + "authserver/validate");
    }

    @Override
    public WebURL getInvalidationURL() throws AuthenticationException {
        return WebURL.parse(apiRoot + "authserver/invalidate");
    }

    @Override
    public WebURL getSkinUploadURL(UUID uuid) throws UnsupportedOperationException {
        return WebURL.parse(apiRoot + "api/user/profile/" + UUIDs.toCompactString(uuid) + "/skin");
    }

    @Override
    public WebURL getProfilePropertiesURL(UUID uuid) throws AuthenticationException {
        return WebURL.parse(apiRoot + "sessionserver/session/minecraft/profile/" + UUIDs.toCompactString(uuid));
    }

    @Override
    public String toString() {
        return apiRoot;
    }
}
