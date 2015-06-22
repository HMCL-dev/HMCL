package org.jackhuang.mojang.authlib.yggdrasil.request;

import org.jackhuang.mojang.authlib.GameProfile;
import org.jackhuang.mojang.authlib.yggdrasil.YggdrasilUserAuthentication;

public class RefreshRequest {

    public String clientToken;
    public String accessToken;
    public GameProfile selectedProfile;
    public boolean requestUser = true;

    public RefreshRequest(YggdrasilUserAuthentication authenticationService) {
        this(authenticationService, null);
    }

    public RefreshRequest(YggdrasilUserAuthentication authenticationService, GameProfile profile) {
        this.clientToken = authenticationService.getAuthenticationService().getClientToken();
        this.accessToken = authenticationService.getAuthenticatedToken();
        this.selectedProfile = profile;
    }
}
