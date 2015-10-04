package org.jackhuang.hellominecraft.launcher.utils.auth.yggdrasil.request;

import java.util.HashMap;
import org.jackhuang.hellominecraft.launcher.utils.auth.yggdrasil.YggdrasilUserAuthentication;

public class AuthenticationRequest {

    public HashMap<String, Object> agent;
    public String username;
    public String password;
    public String clientToken;
    public boolean requestUser = true;

    public AuthenticationRequest(YggdrasilUserAuthentication authenticationService, String username, String password) {
        agent = new HashMap<>();
        agent.put("name", "Minecraft");
        agent.put("version", 1);
        
        this.username = username;
        this.clientToken = authenticationService.getAuthenticationService().getClientToken();
        this.password = password;
    }
}
