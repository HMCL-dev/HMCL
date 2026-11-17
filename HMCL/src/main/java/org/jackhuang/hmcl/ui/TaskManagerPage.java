// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui;

import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.TabController;
import org.jackhuang.hmcl.ui.construct.TabItem;
import org.jackhuang.hmcl.ui.decorator.DecoratorAnimatedPage;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jetbrains.annotations.NotNullByDefault;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// Displays active tasks and pending completion actions in a single sidebar tab.
@NotNullByDefault
public final class TaskManagerPage extends DecoratorAnimatedPage implements DecoratorPage {

    /// Navigation metadata for the task list.
    private final ReadOnlyObjectWrapper<State> state =
            new ReadOnlyObjectWrapper<>(State.fromTitle(i18n("task.list")));

    /// Creates and selects the task list tab backed by the application-wide task manager.
    public TaskManagerPage() {
        TransitionPane transitionPane = new TransitionPane();
        TabItem<TaskListPage> tasksTab = new TabItem<>(TaskListPage::new);
        TabController tabController = new TabController(transitionPane, tasksTab);
        tabController.selectTab(tasksTab);

        getSidebar().addNavigationDrawerTab(tabController, tasksTab, i18n("task.list"), SVG.LIST_ALT);
        setContent(transitionPane);
    }

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }
}
