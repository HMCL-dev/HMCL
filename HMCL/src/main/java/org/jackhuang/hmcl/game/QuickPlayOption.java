package org.jackhuang.hmcl.game;

public class QuickPlayOption {
    Type type;
    String target;

    public QuickPlayOption(Type type, String target) {
        this.type = type;
        this.target = target;
    }

    enum Type {
        SINGLEPLAYER, MULTIPLAYER, REALM
    }

}
