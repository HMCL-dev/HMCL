package org.jackhuang.mojang.authlib.minecraft;

import org.jackhuang.mojang.authlib.AuthenticationService;

public abstract class BaseMinecraftSessionService
	implements MinecraftSessionService {

    private final AuthenticationService authenticationService;

    protected BaseMinecraftSessionService(AuthenticationService authenticationService) {
	this.authenticationService = authenticationService;
    }

    public AuthenticationService getAuthenticationService() {
	return this.authenticationService;
    }
}
