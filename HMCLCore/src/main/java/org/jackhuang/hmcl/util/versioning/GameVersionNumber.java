package org.jackhuang.hmcl.util.versioning;

public abstract class GameVersionNumber {

    GameVersionNumber() {
    }


    abstract Type getType();

    enum Type {
        PRE_CLASSIC,

    }


    static final class PreClassic extends GameVersionNumber {
        @Override
        Type getType() {
            return Type.PRE_CLASSIC;
        }
    }
}
