package org.jackhuang.hmcl.auth.nide8;

import java.util.UUID;

public class Nide8LoginObj {
    public String serverID;
    public UUID uuid;

    public Nide8LoginObj(String serverID, UUID uuid) {
        this.serverID = serverID;
        this.uuid = uuid;
    }
}
