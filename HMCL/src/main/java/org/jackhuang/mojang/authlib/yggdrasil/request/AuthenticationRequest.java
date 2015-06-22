package org.jackhuang.mojang.authlib.yggdrasil.request;

import org.jackhuang.mojang.authlib.Agent;
import org.jackhuang.mojang.authlib.yggdrasil.YggdrasilUserAuthentication;

public class AuthenticationRequest {

    public Agent agent;
    public String username;
    public String password;
    public String clientToken;
    public boolean requestUser = true;

    public AuthenticationRequest(YggdrasilUserAuthentication authenticationService, String username, String password) {
        this.agent = authenticationService.getAgent();
        this.username = username;
        this.clientToken = authenticationService.getAuthenticationService().getClientToken();
        this.password = password;
    }
}
