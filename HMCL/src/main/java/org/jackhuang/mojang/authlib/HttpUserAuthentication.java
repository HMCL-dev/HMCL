package org.jackhuang.mojang.authlib;

public abstract class HttpUserAuthentication extends BaseUserAuthentication {

    protected HttpUserAuthentication(HttpAuthenticationService authenticationService) {
	super(authenticationService);
    }

    @Override
    public HttpAuthenticationService getAuthenticationService() {
	return (HttpAuthenticationService) super.getAuthenticationService();
    }
}
