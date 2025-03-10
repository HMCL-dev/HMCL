/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2024 huangyuhui <huanghongxun2008@126.com> and contributors
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
package com.jfoenix.adapters;

import javafx.scene.Node;
import javafx.scene.control.IndexedCell;
import javafx.scene.control.skin.VirtualFlow;

import java.util.Objects;

public final class VirtualFlowAdapter<T extends IndexedCell<?>> {
    @SuppressWarnings("unchecked")
    public static <T extends IndexedCell<?>> VirtualFlowAdapter<T> wrap(Node node) {
        Objects.requireNonNull(node);
        return new VirtualFlowAdapter<>((VirtualFlow<T>) node);
    }

    private final VirtualFlow<T> flow;

    VirtualFlowAdapter(VirtualFlow<T> flow) {
        this.flow = flow;
    }

    public Node getFlow() {
        return flow;
    }

    public int getCellCount() {
        return flow.getCellCount();
    }

    public T getCell(int index) {
        return flow.getCell(index);
    }
}
