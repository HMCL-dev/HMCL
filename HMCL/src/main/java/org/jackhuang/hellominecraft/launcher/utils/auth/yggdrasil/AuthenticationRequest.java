package org.jackhuang.hellominecraft.launcher.utils.auth.yggdrasil;

import java.util.HashMap;

public class AuthenticationRequest {

    public HashMap<String, Object> agent;
    public String username, password, clientToken;
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
