package org.jackhuang.hmcl.game;

public record QuickPlayOption(Type type, String target) {

    enum Type {
        SINGLEPLAYER, MULTIPLAYER, REALM
    }

}
