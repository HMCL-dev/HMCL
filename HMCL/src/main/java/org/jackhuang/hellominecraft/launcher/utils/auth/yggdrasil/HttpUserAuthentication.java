package org.jackhuang.hellominecraft.launcher.utils.auth.yggdrasil;

public abstract class HttpUserAuthentication extends BaseUserAuthentication {

    protected HttpUserAuthentication(HttpAuthenticationService authenticationService) {
	super(authenticationService);
    }

    @Override
    public HttpAuthenticationService getAuthenticationService() {
	return (HttpAuthenticationService) super.getAuthenticationService();
    }
}
