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
package org.jackhuang.hmcl.ui.multiplayer;

import de.javawi.jstun.test.DiscoveryInfo;
import de.javawi.jstun.test.DiscoveryTest;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class MultiplayerPage extends Control implements DecoratorPage {
    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>(State.fromTitle(i18n("multiplayer"), -1));

    private final ObjectProperty<MultiplayerManager.State> multiplayerState = new SimpleObjectProperty<>();
    private final ReadOnlyObjectWrapper<DiscoveryInfo> natState = new ReadOnlyObjectWrapper<>();

    public MultiplayerPage() {
        testNAT();
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new MultiplayerPageSkin(this);
    }

    public MultiplayerManager.State getMultiplayerState() {
        return multiplayerState.get();
    }

    public ObjectProperty<MultiplayerManager.State> multiplayerStateProperty() {
        return multiplayerState;
    }

    public void setMultiplayerState(MultiplayerManager.State multiplayerState) {
        this.multiplayerState.set(multiplayerState);
    }

    public DiscoveryInfo getNatState() {
        return natState.get();
    }

    public ReadOnlyObjectProperty<DiscoveryInfo> natStateProperty() {
        return natState.getReadOnlyProperty();
    }

    public void testNAT() {
        Task.supplyAsync(() -> {
            DiscoveryTest tester = new DiscoveryTest(null, 0, "stun.qq.com", 3478);
            return tester.test();
        }).whenComplete(Schedulers.javafx(), (info, exception) -> {
            if (exception == null) {
                natState.set(info);
            } else {
                natState.set(null);
            }
        }).start();
    }

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state;
    }
}
