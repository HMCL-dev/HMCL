package org.jackhuang.hmcl.game;

import java.util.function.BiConsumer;

public record QuickPlayOption(Type type, String target) {

    enum Type {
        SINGLEPLAYER(LaunchOptions.Builder::setWorldFolderName),
        MULTIPLAYER(LaunchOptions.Builder::setServerIp),
        REALM(LaunchOptions.Builder::setRealmID);

        private final BiConsumer<LaunchOptions.Builder, String> setter;

        Type(BiConsumer<LaunchOptions.Builder, String> setter) {
            this.setter = setter;
        }

        public void apply(LaunchOptions.Builder builder, String target) {
            setter.accept(builder, target);
        }
    }
}
