/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.setting;

import javafx.scene.image.Image;
import org.jackhuang.hmcl.mod.ModLoaderType;
import org.jackhuang.hmcl.ui.FXUtils;

public enum VersionIconType {
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

    public static VersionIconType getIconType(ModLoaderType modLoaderType) {
        return switch (modLoaderType) {
            case FORGE -> VersionIconType.FORGE;
            case NEO_FORGED -> VersionIconType.NEO_FORGE;
            case FABRIC -> VersionIconType.FABRIC;
            case QUILT -> VersionIconType.QUILT;
            case LITE_LOADER -> VersionIconType.CHICKEN;
            case CLEANROOM -> VersionIconType.CLEANROOM;
            default -> VersionIconType.COMMAND;
        };
    }

    private final String resourceUrl;

    VersionIconType(String resourceUrl) {
        this.resourceUrl = resourceUrl;
    }

    public Image getIcon() {
        return FXUtils.newBuiltinImage(resourceUrl);
    }
}
