package org.jackhuang.hellominecraft.launcher.utils.auth.yggdrasil.request;

import java.util.HashMap;

public class AuthenticationRequest {

    public HashMap<String, Object> agent;
    public String username;
    public String password;
    public String clientToken;
    public boolean requestUser = true;

    public AuthenticationRequest(String username, String password, String clientToken) {
        agent = new HashMap<>();
        agent.put("name", "Minecraft");
        agent.put("version", 1);
        
        this.username = username;
        this.password = password;
        this.clientToken = clientToken;
    }
}
