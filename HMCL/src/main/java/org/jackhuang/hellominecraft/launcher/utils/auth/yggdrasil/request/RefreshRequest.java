package org.jackhuang.hellominecraft.launcher.utils.auth.yggdrasil.request;

import org.jackhuang.hellominecraft.launcher.utils.auth.yggdrasil.GameProfile;
import org.jackhuang.hellominecraft.launcher.utils.auth.yggdrasil.YggdrasilUserAuthentication;

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
