// SPDX-FileCopyrightText: Copyright (C) 2021 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.setting;

import javafx.scene.image.Image;
import org.jackhuang.hmcl.addon.mod.ModLoaderType;
import org.jackhuang.hmcl.game.GameComponentType;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jetbrains.annotations.Nullable;

public enum GameInstanceIconType {
    DEFAULT("/assets/img/grass.png"),

    GRASS("/assets/img/grass.png"),
    CHEST("/assets/img/chest.png"),
    CHICKEN("/assets/img/chicken.png"),
    COMMAND("/assets/img/command.png"),
    OPTIFINE("/assets/img/optifine.png"),
    CRAFT_TABLE("/assets/img/craft_table.png"),
    FABRIC("/assets/img/fabric.png"),
    FORGE("/assets/img/forge.png"),
    NEO_FORGE("/assets/img/neoforge.png"),
    FURNACE("/assets/img/furnace.png"),
    QUILT("/assets/img/quilt.png"),
    APRIL_FOOLS("/assets/img/april_fools.png"),
    CLEANROOM("/assets/img/cleanroom.png"),
    LEGACY_FABRIC("/assets/img/legacyfabric.png")
    ;

    // Please append new items at last

    public static GameInstanceIconType getIconType(ModLoaderType modLoaderType) {
        return switch (modLoaderType) {
            case FORGE -> GameInstanceIconType.FORGE;
            case NEO_FORGE -> GameInstanceIconType.NEO_FORGE;
            case FABRIC -> GameInstanceIconType.FABRIC;
            case LEGACY_FABRIC -> GameInstanceIconType.LEGACY_FABRIC;
            case QUILT -> GameInstanceIconType.QUILT;
            case LITE_LOADER -> GameInstanceIconType.CHICKEN;
            case CLEANROOM -> GameInstanceIconType.CLEANROOM;
            default -> GameInstanceIconType.COMMAND;
        };
    }

    public static @Nullable GameInstanceIconType getIconType(GameComponentType componentType) {
        return switch (componentType) {
            case GAME -> GameInstanceIconType.GRASS;
            case FABRIC, FABRIC_API -> GameInstanceIconType.FABRIC;
            case LEGACY_FABRIC, LEGACY_FABRIC_API -> GameInstanceIconType.LEGACY_FABRIC;
            case FORGE -> GameInstanceIconType.FORGE;
            case CLEANROOM -> GameInstanceIconType.CLEANROOM;
            case LITELOADER -> GameInstanceIconType.CHICKEN;
            case OPTIFINE -> GameInstanceIconType.OPTIFINE;
            case QUILT, QUILT_API -> GameInstanceIconType.QUILT;
            case NEO_FORGE -> GameInstanceIconType.NEO_FORGE;
            default -> null;
        };
    }


    private final String resourceUrl;

    GameInstanceIconType(String resourceUrl) {
        this.resourceUrl = resourceUrl;
    }

    public Image getIcon() {
        return FXUtils.newBuiltinImage(resourceUrl);
    }
}
