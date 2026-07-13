/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.jackhuang.hmcl.ui.versions;

import org.jetbrains.annotations.NotNullByDefault;

/// Marks an entry rendered in the grouped instance list.
@NotNullByDefault
public sealed interface GameListEntry permits GameListItem, GameListGroupItem {
}
