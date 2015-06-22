package org.jackhuang.mojang.authlib.minecraft;

import org.jackhuang.mojang.authlib.HttpAuthenticationService;

public abstract class HttpMinecraftSessionService extends BaseMinecraftSessionService {

    protected HttpMinecraftSessionService(HttpAuthenticationService authenticationService) {
	super(authenticationService);
    }

    @Override
    public HttpAuthenticationService getAuthenticationService() {
	return (HttpAuthenticationService) super.getAuthenticationService();
    }
}
