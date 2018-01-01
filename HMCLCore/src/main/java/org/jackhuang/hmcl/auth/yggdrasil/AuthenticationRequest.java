/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hmcl.auth.yggdrasil;

import java.util.Map;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.Pair;

/**
 *
 * @author huangyuhui
 */
public final class AuthenticationRequest {

    /**
     * The user name of Minecraft account.
     */
    private final String username;

    /**
     * The password of Minecraft account.
     */
    private final String password;

    /**
     * The client token of this game.
     */
    private final String clientToken;

    private final Map<String, Object> agent = Lang.mapOf(
            new Pair("name", "minecraft"),
            new Pair("version", 1));

    private final boolean requestUser = true;

    public AuthenticationRequest(String username, String password, String clientToken) {
        this.username = username;
        this.password = password;
        this.clientToken = clientToken;
    }

    public String getUsername() {
        return username;
    }

    public String getClientToken() {
        return clientToken;
    }

    public Map<String, Object> getAgent() {
        return agent;
    }

    public boolean isRequestUser() {
        return requestUser;
    }

}
