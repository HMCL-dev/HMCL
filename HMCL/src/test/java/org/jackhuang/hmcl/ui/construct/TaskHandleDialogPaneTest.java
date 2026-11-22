// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import com.jfoenix.controls.JFXSpinner;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.SVGPath;
import org.jackhuang.hmcl.download.InstallStages;
import org.jackhuang.hmcl.game.GameComponentType;
import org.jackhuang.hmcl.setting.LauncherSettings;
import org.jackhuang.hmcl.setting.SettingsManager;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutionContext;
import org.jackhuang.hmcl.task.TaskHandle;
import org.jackhuang.hmcl.task.TaskListener;
import org.jackhuang.hmcl.task.TaskStage;
import org.jackhuang.hmcl.task.TaskStageInstance;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.DialogUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.TaskManagerPage;
import org.jackhuang.hmcl.ui.decorator.Decorator;
import org.jackhuang.hmcl.util.TaskCancellationAction;
import org.jackhuang.hmcl.util.TaskManager;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// Verifies background task lifetimes and reuse of progress dialogs.
@NotNullByDefault
@EnabledIf("org.jackhuang.hmcl.JavaFXLauncher#isStarted")
final class TaskHandleDialogPaneTest {

    /// A stage with a translated heading for task list tests.
    private static final TaskStage CHECKING = new TaskStage("update.checking");
    /// A second displayed stage for grouping and ordering assertions.
    private static final TaskStage LOGGING_IN = new TaskStage("launch.state.logging_in");

    /// Settings replaced temporarily to initialize dialog animation preferences.
    private static @Nullable Object previousSettings;

    /// Installs in-memory settings with synchronous dialog transitions for lifecycle assertions.
    @BeforeAll
    static void prepareSettings() throws Exception {
        Field field = SettingsManager.class.getDeclaredField("launcherSettings");
        field.setAccessible(true);
        previousSettings = field.get(null);
        LauncherSettings settings = new LauncherSettings();
        // The default depends on runtime graphics capabilities and can defer onDialogShown to an animation pulse.
        settings.animationDisabledProperty().set(true);
        field.set(null, settings);
    }

    /// Restores the settings that were present before these tests.
    @AfterAll
    static void restoreSettings() throws Exception {
        Field field = SettingsManager.class.getDeclaredField("launcherSettings");
        field.setAccessible(true);
        field.set(null, previousSettings);
    }

    /// Verifies dialog reuse and the task button's idle, running, and pending presentations.
    @Test
    void restoresThroughMainWindow() throws Exception {
        onFxThread(() -> {
            Field field = Controllers.class.getDeclaredField("decorator");
            field.setAccessible(true);
            @Nullable Object previous = field.get(null);
            Decorator decorator = new Decorator(new StackPane(), stage -> { });
            new Scene(decorator.getRoot());
            field.set(null, decorator);
            TaskHandleDialogPane pane = new TaskHandleDialogPane(TaskCancellationAction.NORMAL);
            TaskHandleDialogPane second = new TaskHandleDialogPane(TaskCancellationAction.NORMAL);
            try {
                ButtonBase tasksButton = decorator.getRoot().lookupAll(".jfx-decorator-button").stream()
                        .filter(node -> i18n("task.list").equals(node.getAccessibleText()))
                        .map(ButtonBase.class::cast).findFirst().orElseThrow();
                Node icon = Objects.requireNonNull(tasksButton.getGraphic());
                ControlledHandle handle = new ControlledHandle();
                pane.setHandle(handle, false);
                handle.start();
                Controllers.dialog(pane);
                assertEquals(TaskManager.Status.IDLE, TaskManager.getInstance().statusProperty().get());
                assertSame(icon, tasksButton.getGraphic());
                Node progress = pane.getCenter();
                pane.runInBackground();
                assertEquals(TaskManager.Status.RUNNING, TaskManager.getInstance().statusProperty().get());
                Node spinner = Objects.requireNonNull(tasksButton.getGraphic());
                assertInstanceOf(JFXSpinner.class, spinner);
                pane.restore();
                pane.restore();
                assertEquals(TaskManager.Status.IDLE, TaskManager.getInstance().statusProperty().get());
                assertSame(icon, tasksButton.getGraphic());
                assertTrue(DialogUtils.isShowing(pane));
                assertSame(progress, pane.getCenter());
                @Nullable JFXDialogPane stack = (JFXDialogPane) pane.getProperties()
                        .get(DialogUtils.PROPERTY_PARENT_PANE_REF);
                assertNotNull(stack);
                assertEquals(1, stack.size());
                assertSame(pane, stack.peek().orElseThrow());
                assertEquals(1, handle.starts);

                ControlledHandle secondHandle = new ControlledHandle();
                second.setHandle(secondHandle, false);
                secondHandle.start();
                Controllers.dialog(second);
                second.runInBackground();
                Node nextSpinner = Objects.requireNonNull(tasksButton.getGraphic());
                assertInstanceOf(JFXSpinner.class, nextSpinner);
                assertNotSame(spinner, nextSpinner);
                pane.runInBackground();
                pane.restore();
                assertEquals(TaskManager.Status.RUNNING, TaskManager.getInstance().statusProperty().get());
                assertSame(nextSpinner, tasksButton.getGraphic());

                AtomicInteger actions = new AtomicInteger();
                pane.runInBackground();
                assertFalse(pane.runOrDeferCompletionAction("Install", "Ready to install", actions::incrementAndGet));
                assertEquals(TaskManager.Status.RUNNING, TaskManager.getInstance().statusProperty().get());
                assertSame(nextSpinner, tasksButton.getGraphic());
                second.restore();
                assertEquals(TaskManager.Status.PENDING, TaskManager.getInstance().statusProperty().get());
                SVGPath pendingIcon = assertInstanceOf(SVGPath.class, tasksButton.getGraphic());
                assertEquals(SVG.PENDING.getPath(), pendingIcon.getContent());
                second.runInBackground();
                assertEquals(TaskManager.Status.RUNNING, TaskManager.getInstance().statusProperty().get());
                Node resumedSpinner = assertInstanceOf(JFXSpinner.class, tasksButton.getGraphic());
                assertNotSame(nextSpinner, resumedSpinner);
                pane.runPendingAction();
                assertEquals(1, actions.get());
                assertEquals(TaskManager.Status.RUNNING, TaskManager.getInstance().statusProperty().get());
                assertSame(resumedSpinner, tasksButton.getGraphic());

                assertFalse(second.runOrDeferCompletionAction("Install", "Ready to install", actions::incrementAndGet));
                assertEquals(TaskManager.Status.PENDING, TaskManager.getInstance().statusProperty().get());
                assertEquals(SVG.PENDING.getPath(), assertInstanceOf(SVGPath.class, tasksButton.getGraphic()).getContent());
                second.setHandle(null);
                assertEquals(TaskManager.Status.IDLE, TaskManager.getInstance().statusProperty().get());
                assertSame(icon, tasksButton.getGraphic());
            } finally {
                DialogUtils.close(pane);
                pane.setHandle(null);
                DialogUtils.close(second);
                second.setHandle(null);
                field.set(null, previous);
            }
            return true;
        });
    }

    /// Displays the failure even when both task notifications arrive before the next JavaFX pulse.
    @Test
    void displaysFailureForImmediatelyFailedTask() throws Exception {
        String message = "Could not finish the visible task";
        TaskListPane pane = onFxThread(() -> {
            TaskListPane list = new TaskListPane();
            new Scene(list, 480, 240);
            TaskHandle handle = Task.runAsync("Visible task", () -> {
                throw new IOException(message);
            }).newHandle();
            list.setHandle(handle);
            assertFalse(handle.test());
            return list;
        });
        onFxThread(() -> {
            pane.applyCss();
            pane.layout();
            assertTrue(pane.lookupAll(".label").stream()
                    .filter(Label.class::isInstance)
                    .map(Label.class::cast)
                    .anyMatch(label -> message.equals(label.getText())));
            return true;
        });
    }

    /// Displays a task rejected before its body starts under its inherited stage.
    @Test
    void displaysRejectedTaskUnderItsStage() throws Exception {
        var rejection = new RejectedExecutionException("The update worker is unavailable");
        Task<?> child = Task.runAsync("Check updates", command -> {
            throw rejection;
        }, () -> fail("The rejected task ran"));
        TaskHandle handle = child.withStage(CHECKING)
                .withStageHints(CHECKING, LOGGING_IN)
                .newHandle();
        TaskListPane pane = showTaskList(handle);

        assertFalse(handle.test());
        assertSame(rejection, child.exceptionNow());
        onFxThread(() -> {
            ListCell<?> item = findRow(pane, "Check updates");
            assertTrue(findRow(pane, i18n("update.checking")).getIndex() < item.getIndex());
            assertTrue(item.getIndex() < findRow(pane, i18n("launch.state.logging_in")).getIndex());
            BorderPane row = (BorderPane) item.getGraphic();
            assertEquals(rejection.getMessage(), ((Label) row.getRight()).getText());
            return true;
        });
    }

    /// Places both a declaring task and its child between their stage heading and the next stage.
    @Test
    void groupsOwnerAndInheritingChildUnderTheirStage() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Task<?> owner = new Task<@Nullable Void>() {
            @Override
            public TaskStage getStage() {
                return CHECKING;
            }

            @Override
            public @Nullable Void execute(TaskExecutionContext context) throws Exception {
                return context.run(Task.runAsync("Child task", () -> {
                    entered.countDown();
                    assertTrue(release.await(10, TimeUnit.SECONDS));
                }));
            }
        }.setName("Owner task");
        TaskHandle handle = owner.withStageHints(CHECKING, LOGGING_IN).newHandle();
        TaskListPane pane = showTaskList(handle);
        CompletableFuture<Boolean> result = CompletableFuture.supplyAsync(handle::test);
        try {
            assertTrue(entered.await(10, TimeUnit.SECONDS));
            onFxThread(() -> {
                int start = findRow(pane, i18n("update.checking")).getIndex();
                int end = findRow(pane, i18n("launch.state.logging_in")).getIndex();
                int ownerIndex = findRow(pane, "Owner task").getIndex();
                int childIndex = findRow(pane, "Child task").getIndex();
                assertTrue(start < ownerIndex && ownerIndex < end);
                assertTrue(start < childIndex && childIndex < end);
                return true;
            });
        } finally {
            release.countDown();
        }
        assertTrue(result.get(10, TimeUnit.SECONDS));
    }

    /// Retains a failed independent stage when a sibling shares its row in either completion order.
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void retainsFailedStageAcrossSiblingCompletions(boolean failureFirst) throws Exception {
        TaskStage firstStage = new TaskStage("first");
        TaskStage secondStage = new TaskStage("second");
        CountDownLatch bothStarted = new CountDownLatch(2);
        CountDownLatch firstFinished = new CountDownLatch(1);
        Task<?> first = Task.runAsync(() -> {
            assertTrue(bothStarted.await(10, TimeUnit.SECONDS));
            if (failureFirst)
                throw new IOException("First stage failed");
        }).withStage(firstStage);
        Task<?> second = Task.runAsync(() -> {
            assertTrue(firstFinished.await(10, TimeUnit.SECONDS));
            if (!failureFirst)
                throw new IOException("Second stage failed");
        }).withStage(secondStage);
        TaskHandle handle = Task.allOf(first, second)
                .withStageHints(new Task.StageHint(CHECKING, List.of(firstStage, secondStage)))
                .newHandle();
        TaskListPane pane = showTaskList(handle);
        handle.addTaskListener(new TaskListener() {
            @Override
            public void onStageStarted(TaskStageInstance instance) {
                bothStarted.countDown();
            }

            @Override
            public void onStageFinished(TaskStageInstance instance, @Nullable Throwable failure) {
                if (instance.stage().equals(firstStage))
                    firstFinished.countDown();
            }
        });

        assertFalse(handle.test());
        assertInstanceOf(IOException.class, (failureFirst ? first : second).exceptionNow());
        assertEquals(Task.TaskState.SUCCEEDED, (failureFirst ? second : first).getState());
        assertEquals(SVG.CLOSE.getPath(), stageIcon(pane, i18n("update.checking")));
    }

    /// Displays the enclosing stage's outcome on recovery and counts only successful items.
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void displaysRecoveredStageAndCompletedItemCount(boolean mergeStages) throws Exception {
        Task<?> owner = new Task<@Nullable Void>() {
            @Override
            public TaskStage getStage() {
                return CHECKING;
            }

            @Override
            public @Nullable Void execute(TaskExecutionContext context) throws Exception {
                context.addStageItems(2);
                context.addStageItems(1);
                context.completeStageItem();
                context.run(Task.completed(1).withCounter());
                assertThrows(IOException.class, () -> context.run(Task.failed(new IOException("Check failed"))
                        .withCounter().withStage(LOGGING_IN)));
                return null;
            }
        };
        if (mergeStages)
            owner = owner.withStageHints(new Task.StageHint(CHECKING, List.of(LOGGING_IN)));
        TaskHandle handle = owner.newHandle();
        TaskListPane pane = showTaskList(handle);

        assertTrue(handle.test());
        assertEquals(SVG.CHECK.getPath(), stageIcon(pane, i18n("update.checking") + " - 2/3"));
        if (!mergeStages)
            assertEquals(SVG.CLOSE.getPath(), stageIcon(pane, i18n("launch.state.logging_in")));
    }

    /// A grouping hint binds a stage to its row only if the stage has not already opened its own row.
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void groupsStageOnlyWhenHintArrivesFirst(boolean hintFirst) throws Exception {
        TaskStage groupStage = CHECKING;
        TaskStage partStage = LOGGING_IN;
        Task.StageHint group = new Task.StageHint(groupStage, List.of(partStage));
        Task<?> work = new Task<@Nullable Void>() {
            /// Reports one complete item in the enclosing stage.
            @Override
            public @Nullable Void execute(TaskExecutionContext context) {
                context.addStageItems(1);
                context.completeStageItem();
                return null;
            }
        };
        TaskHandle handle = (hintFirst
                ? work.withStage(partStage).withStageHints(group)
                : work.withStageHints(group).withStage(partStage)).newHandle();
        TaskListPane pane = showTaskList(handle);
        assertTrue(handle.test());

        String completedRow = i18n((hintFirst ? groupStage : partStage).key()) + " - 1/1";
        assertEquals(SVG.CHECK.getPath(), stageIcon(pane, completedRow));
        onFxThread(() -> {
            ListCell<?> first = findRow(pane, completedRow);
            assertEquals(0, first.getIndex());
            assertEquals(hintFirst ? 1 : 2, first.getListView().getItems().size());
            if (!hintFirst)
                assertEquals(1, findRow(pane, i18n(groupStage.key())).getIndex());
            return true;
        });
    }

    /// Installation stages reuse their planned row only when both component and version match.
    @Test
    void displaysEachComponentVersionInItsPlannedRow() throws Exception {
        GameComponentType component = GameComponentType.NEO_FORGE;
        String firstVersion = "21.1.170";
        String secondVersion = "21.1.171";
        TaskHandle handle = Task.allOf(
                        Task.completed(1).withStage(InstallStages.component(component, secondVersion)),
                        Task.completed(2).withStage(InstallStages.component(component, firstVersion)))
                .withStageHints(InstallStages.component(component, firstVersion), InstallStages.component(component, secondVersion),
                        new TaskStage("hmcl.install.neoforge"))
                .newHandle();
        TaskListPane pane = showTaskList(handle);
        assertTrue(handle.test());

        String name = i18n("install.installer.neoforge");
        String first = i18n("install.installer.install", name + " " + firstVersion);
        String second = i18n("install.installer.install", name + " " + secondVersion);
        assertEquals(SVG.CHECK.getPath(), stageIcon(pane, first));
        assertEquals(SVG.CHECK.getPath(), stageIcon(pane, second));
        onFxThread(() -> {
            ListCell<?> row = findRow(pane, first);
            assertEquals(3, row.getListView().getItems().size());
            assertEquals(0, row.getIndex());
            assertEquals(1, findRow(pane, second).getIndex());
            assertEquals(2, findRow(pane, i18n("install.installer.install", name)).getIndex());
            return true;
        });
    }

    /// Attaches a list to a scene so its stage rows are available for inspection.
    private static TaskListPane showTaskList(TaskHandle handle) throws Exception {
        return onFxThread(() -> {
            TaskListPane pane = new TaskListPane();
            new Scene(pane, 480, 240);
            pane.setHandle(handle);
            return pane;
        });
    }

    /// Returns the visible row with the supplied title on the JavaFX thread.
    private static ListCell<?> findRow(TaskListPane pane, String title) {
        pane.applyCss();
        pane.layout();
        for (Node cell : pane.lookupAll(".list-cell")) {
            if (cell instanceof ListCell<?> item && item.getGraphic() instanceof BorderPane row
                    && row.getCenter() instanceof Label label && title.equals(label.getText()))
                return item;
        }
        return fail("Missing task list row: " + title);
    }

    /// Returns the displayed status icon for the named stage.
    private static String stageIcon(TaskListPane pane, String title) throws Exception {
        return onFxThread(() -> {
            BorderPane row = (BorderPane) findRow(pane, title).getGraphic();
            return assertInstanceOf(SVGPath.class, row.getLeft().lookup("SVGPath")).getContent();
        });
    }

    /// Verifies disabled cancellation remains disabled and an enabled action runs only once.
    @Test
    void cancellationHonorsActionAndWaitsForCompletion() throws Exception {
        TaskHandleDialogPane pane = onFxThread(() -> {
            TaskHandleDialogPane dialog = new TaskHandleDialogPane(TaskCancellationAction.NO_CANCEL);
            ControlledHandle handle = new ControlledHandle();
            dialog.setHandle(handle, false);
            handle.start();
            @Nullable ButtonBase cancel = (ButtonBase) dialog.lookup(".dialog-cancel");
            assertNotNull(cancel);
            assertTrue(cancel.isDisabled());
            cancel.fire();
            assertFalse(handle.isCancelled());
            dialog.setCancel(null);
            assertTrue(cancel.isDisabled());
            AtomicInteger cancellations = new AtomicInteger();
            dialog.setCancel(ignored -> cancellations.incrementAndGet());
            assertFalse(cancel.isDisabled());
            cancel.fire();
            cancel.fire();
            assertTrue(handle.isCancelled());
            assertEquals(1, cancellations.get());
            assertTrue(TaskManager.getInstance().getTasks().contains(dialog));
            handle.stop(false);
            return dialog;
        });
        onFxThread(() -> {
            assertFalse(TaskManager.getInstance().getTasks().contains(pane));
            return true;
        });
    }

    /// Verifies hidden work continues and its completion removes only its own dialog entry.
    @Test
    void backgroundTaskContinuesUntilCompletion() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch stopped = new CountDownLatch(1);
        AtomicInteger executions = new AtomicInteger();
        TaskHandle handle = Task.runAsync(() -> {
            executions.incrementAndGet();
            entered.countDown();
            assertTrue(release.await(10, TimeUnit.SECONDS));
        }).newHandle();
        TaskHandleDialogPane pane = onFxThread(() -> {
            TaskHandleDialogPane dialog = new TaskHandleDialogPane(TaskCancellationAction.NORMAL);
            dialog.setHandle(handle);
            handle.addTaskListener(new TaskListener() {
                /// Signals after the pane's completion callback has been queued.
                @Override
                public void onExecutionFinished(TaskHandle completed, boolean success) {
                    stopped.countDown();
                }
            });
            StackPane container = new StackPane();
            new Scene(container);
            DialogUtils.show(container, dialog);
            handle.start();
            return dialog;
        });
        try {
            assertTrue(entered.await(10, TimeUnit.SECONDS));
            onFxThread(() -> {
                AtomicInteger backgroundCalls = new AtomicInteger();
                pane.setOnBackground(backgroundCalls::incrementAndGet);
                Node progress = pane.getCenter();
                StackPane container = new StackPane();
                new Scene(container);
                for (int i = 0; i < 3; i++) {
                    pane.runInBackground();
                    pane.runInBackground();
                    assertEquals(TaskManager.Status.RUNNING, TaskManager.getInstance().statusProperty().get());
                    assertFalse(DialogUtils.isShowing(pane));
                    assertFalse(handle.isCancelled());
                    assertTrue(TaskManager.getInstance().getTasks().contains(pane));
                    // Reuse the same pane through the dialog stack used by Controllers.dialog.
                    DialogUtils.show(container, pane);
                    DialogUtils.show(container, pane);
                    assertEquals(TaskManager.Status.IDLE, TaskManager.getInstance().statusProperty().get());
                    pane.restore();
                    assertSame(progress, pane.getCenter());
                    @Nullable JFXDialogPane stack = (JFXDialogPane) pane.getProperties()
                            .get(DialogUtils.PROPERTY_PARENT_PANE_REF);
                    assertNotNull(stack);
                    assertEquals(1, stack.size());
                }
                assertEquals(1, backgroundCalls.get());
                pane.runInBackground();
                Label other = new Label("Another dialog");
                DialogUtils.show(container, other);
                return true;
            });
        } finally {
            release.countDown();
        }
        assertTrue(stopped.await(10, TimeUnit.SECONDS));
        onFxThread(() -> {
            assertFalse(TaskManager.getInstance().getTasks().contains(pane));
            assertFalse(DialogUtils.isShowing(pane));
            assertEquals(TaskManager.Status.IDLE, TaskManager.getInstance().statusProperty().get());
            pane.restore();
            assertFalse(DialogUtils.isShowing(pane));
            return true;
        });
        assertEquals(1, executions.get());
        assertNull(handle.getException());
    }

    /// Verifies hiding or restoring a queued dialog cannot start it ahead of background work.
    @Test
    void queuedDialogCanBeHiddenAndRestored() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger executions = new AtomicInteger();
        List<CompletableFuture<Boolean>> completions = new ArrayList<>();
        try {
            onFxThread(() -> {
                Field field = Controllers.class.getDeclaredField("decorator");
                field.setAccessible(true);
                @Nullable Object previous = field.get(null);
                Decorator decorator = new Decorator(new StackPane(), stage -> { });
                new Scene(decorator.getRoot());
                field.set(null, decorator);
                TaskHandleDialogPane first = new TaskHandleDialogPane(TaskCancellationAction.NORMAL);
                TaskHandleDialogPane second = new TaskHandleDialogPane(TaskCancellationAction.NORMAL);
                try {
                    first.setHandle(Task.runAsync(() -> assertTrue(release.await(10, TimeUnit.SECONDS))).newHandle());
                    Controllers.dialog(first);
                    completions.add(first.startHandle());
                    first.runInBackground();
                    second.setHandle(Task.runAsync(executions::incrementAndGet).newHandle());
                    Controllers.dialog(second);
                    completions.add(second.startHandle());
                    assertTrue(second.queuedProperty().get());
                    assertEquals(TaskManager.Status.RUNNING, TaskManager.getInstance().statusProperty().get());
                    second.runInBackground();
                    assertEquals(TaskManager.Status.RUNNING, TaskManager.getInstance().statusProperty().get());
                    assertFalse(DialogUtils.isShowing(second));
                    second.restore();
                    second.restore();
                    assertTrue(DialogUtils.isShowing(second));
                    assertTrue(second.queuedProperty().get());
                    assertEquals(0, executions.get());
                    second.runInBackground();
                    first.setHandle(null);
                    assertEquals(TaskManager.Status.PENDING, TaskManager.getInstance().statusProperty().get());
                } finally {
                    DialogUtils.close(first);
                    DialogUtils.close(second);
                    first.setHandle(null);
                    second.setHandle(null);
                    field.set(null, previous);
                }
                return true;
            });
        } finally {
            release.countDown();
        }
        for (CompletableFuture<Boolean> completion : completions)
            assertTrue(completion.get(10, TimeUnit.SECONDS));
        assertEquals(1, executions.get());
    }

    /// A rejected start clears the dialog even if it was sent to the background while queued.
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void rejectsPreviouslyStartedHandle(boolean waitForSlot) throws Exception {
        TaskHandle used = Task.completed(1).newHandle();
        assertTrue(used.test());
        TaskHandleDialogPane dialog = onFxThread(() -> {
            TaskHandleDialogPane pane = new TaskHandleDialogPane(TaskCancellationAction.NORMAL);
            pane.setHandle(used);
            StackPane container = new StackPane();
            new Scene(container);
            DialogUtils.show(container, pane);
            return pane;
        });
        CompletableFuture<Boolean> rejected = onFxThread(() -> {
            ControlledHandle blocker = new ControlledHandle();
            if (waitForSlot)
                TaskManager.getInstance().submit(blocker, null);
            try {
                CompletableFuture<Boolean> submitted = dialog.startHandle();
                if (waitForSlot) {
                    assertTrue(dialog.queuedProperty().get());
                    dialog.runInBackground();
                    assertTrue(dialog.runningInBackgroundProperty().get());
                }
                return submitted;
            } finally {
                if (waitForSlot)
                    blocker.stop(true);
            }
        });
        assertInstanceOf(IllegalStateException.class,
                assertThrows(ExecutionException.class, () -> rejected.get(10, TimeUnit.SECONDS)).getCause());
        onFxThread(() -> {
            assertSame(rejected, dialog.startHandle());
            assertFalse(dialog.queuedProperty().get());
            assertFalse(dialog.runningInBackgroundProperty().get());
            assertFalse(dialog.canStartChild());
            assertNull(dialog.pendingActionProperty().get());
            assertFalse(TaskManager.getInstance().getTasks().contains(dialog));
            assertFalse(DialogUtils.isShowing(dialog));
            return true;
        });
    }

    /// Cancelling the returned future leaves an accepted handle attached until it actually stops.
    @Test
    void cancellingSubmissionFutureKeepsRunningDialog() throws Exception {
        TaskHandleDialogPane dialog = onFxThread(() -> {
            ControlledHandle handle = new ControlledHandle();
            TaskHandleDialogPane pane = new TaskHandleDialogPane(TaskCancellationAction.NORMAL);
            pane.setHandle(handle);
            try {
                assertTrue(pane.startHandle().cancel(false));
                assertTrue(pane.canStartChild());
                assertTrue(TaskManager.getInstance().getTasks().contains(pane));
            } finally {
                handle.stop(true);
            }
            return pane;
        });
        onFxThread(() -> {
            assertFalse(dialog.canStartChild());
            assertFalse(TaskManager.getInstance().getTasks().contains(dialog));
            return true;
        });
    }

    /// Verifies both successful and failed completion remove hidden tasks from the list.
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void completionRemovesHiddenTask(boolean success) throws Exception {
        TaskHandleDialogPane pane = onFxThread(() -> {
            TaskHandleDialogPane dialog = new TaskHandleDialogPane(TaskCancellationAction.NO_CANCEL);
            ControlledHandle handle = new ControlledHandle();
            dialog.setHandle(handle, false);
            handle.start();
            assertTrue(TaskManager.getInstance().getTasks().contains(dialog));
            StackPane container = new StackPane();
            new Scene(container);
            DialogUtils.show(container, dialog);
            dialog.runInBackground();
            assertEquals(TaskManager.Status.RUNNING, TaskManager.getInstance().statusProperty().get());
            assertThrows(UnsupportedOperationException.class,
                    () -> TaskManager.getInstance().getTasks().clear());
            handle.stop(success);
            return dialog;
        });
        onFxThread(() -> {
            assertFalse(TaskManager.getInstance().getTasks().contains(pane));
            assertEquals(TaskManager.Status.IDLE, TaskManager.getInstance().statusProperty().get());
            return true;
        });
    }

    /// Verifies a visible task list switches from progress to a retained, single-use install action.
    @Test
    void backgroundCompletionWaitsForInstallAction() throws Exception {
        AtomicInteger installations = new AtomicInteger();
        TaskManagerPage page = onFxThread(() -> {
            TaskManagerPage result = new TaskManagerPage();
            new Scene(result, 600, 400);
            return result;
        });
        TaskHandleDialogPane pane = onFxThread(() -> {
            TaskHandleDialogPane dialog = new TaskHandleDialogPane(TaskCancellationAction.NORMAL);
            dialog.setTitle("Example modpack");
            ControlledHandle handle = new ControlledHandle();
            dialog.setHandle(handle);
            StackPane container = new StackPane();
            new Scene(container);
            DialogUtils.show(container, dialog);
            handle.start();
            dialog.runInBackground();
            page.applyCss();
            page.layout();
            assertFalse(page.lookupAll(".jfx-spinner").isEmpty());
            assertFalse(dialog.runOrDeferCompletionAction(i18n("button.install"), i18n("modpack.install.ready"),
                    installations::incrementAndGet));
            handle.stop(true);
            return dialog;
        });
        onFxThread(() -> {
            try {
                assertEquals(0, installations.get());
                assertFalse(DialogUtils.isShowing(pane));
                assertTrue(TaskManager.getInstance().getTasks().contains(pane));
                assertEquals(TaskManager.Status.PENDING, TaskManager.getInstance().statusProperty().get());
                page.applyCss();
                page.layout();
                ButtonBase install = page.lookupAll(".jfx-button").stream()
                        .filter(ButtonBase.class::isInstance)
                        .map(ButtonBase.class::cast)
                        .filter(button -> i18n("button.install").equals(button.getText()))
                        .findFirst().orElseThrow();
                assertTrue(page.lookupAll(".label").stream().anyMatch(node -> node instanceof Label label
                        && i18n("modpack.install.ready").equals(label.getText())));
                assertTrue(page.lookupAll(".jfx-spinner").isEmpty());
                install.fire();
                pane.runPendingAction();
                assertEquals(1, installations.get());
                assertNull(pane.pendingActionProperty().get());
                assertFalse(TaskManager.getInstance().getTasks().contains(pane));
            } finally {
                pane.setHandle(null);
            }
            return true;
        });
    }

    /// Verifies foreground completion, including a restored dialog, continues immediately.
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void foregroundCompletionRunsImmediately(boolean restore) throws Exception {
        TaskHandleDialogPane pane = onFxThread(() -> {
            TaskHandleDialogPane dialog = new TaskHandleDialogPane(TaskCancellationAction.NORMAL);
            ControlledHandle handle = new ControlledHandle();
            dialog.setHandle(handle);
            StackPane container = new StackPane();
            new Scene(container);
            DialogUtils.show(container, dialog);
            handle.start();
            if (restore) {
                dialog.runInBackground();
                DialogUtils.show(container, dialog);
            }
            AtomicInteger installations = new AtomicInteger();
            assertTrue(dialog.runOrDeferCompletionAction("Install", "Ready to install", installations::incrementAndGet));
            assertEquals(1, installations.get());
            assertNull(dialog.pendingActionProperty().get());
            handle.stop(true);
            return dialog;
        });
        onFxThread(() -> {
            assertFalse(TaskManager.getInstance().getTasks().contains(pane));
            assertFalse(DialogUtils.isShowing(pane));
            return true;
        });
    }

    /// Verifies failed completion and detachment discard a deferred action without invoking it.
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void unsuccessfulCompletionDiscardsPendingAction(boolean detach) throws Exception {
        AtomicInteger installations = new AtomicInteger();
        TaskHandleDialogPane pane = onFxThread(() -> {
            TaskHandleDialogPane dialog = new TaskHandleDialogPane(TaskCancellationAction.NORMAL);
            ControlledHandle handle = new ControlledHandle();
            dialog.setHandle(handle);
            StackPane container = new StackPane();
            new Scene(container);
            DialogUtils.show(container, dialog);
            handle.start();
            dialog.runInBackground();
            assertFalse(dialog.runOrDeferCompletionAction("Install", "Ready to install", installations::incrementAndGet));
            if (detach)
                dialog.setHandle(null);
            handle.stop(false);
            return dialog;
        });
        onFxThread(() -> {
            assertNull(pane.pendingActionProperty().get());
            pane.runPendingAction();
            assertEquals(0, installations.get());
            assertFalse(TaskManager.getInstance().getTasks().contains(pane));
            assertEquals(TaskManager.Status.IDLE, TaskManager.getInstance().statusProperty().get());
            return true;
        });
    }

    /// Verifies late completion from a detached handle cannot remove its replacement.
    @Test
    void staleCompletionDoesNotRemoveReplacement() throws Exception {
        TaskHandleDialogPane pane = onFxThread(() -> {
            TaskHandleDialogPane dialog = new TaskHandleDialogPane(null);
            ControlledHandle previous = new ControlledHandle();
            dialog.setHandle(previous, false);
            previous.start();
            previous.stop(true);
            ControlledHandle replacement = new ControlledHandle();
            dialog.setHandle(replacement, false);
            replacement.start();
            dialog.setHandle(replacement, false);
            return dialog;
        });
        onFxThread(() -> {
            assertTrue(TaskManager.getInstance().getTasks().contains(pane));
            assertEquals(1, TaskManager.getInstance().getTasks().stream().filter(it -> it == pane).count());
            pane.setHandle(null);
            assertFalse(TaskManager.getInstance().getTasks().contains(pane));
            return true;
        });
    }

    /// Verifies closing detached content cannot close an unrelated dialog or run its callback twice.
    @Test
    void closingHiddenDialogIsIdempotent() throws Exception {
        onFxThread(() -> {
            StackPane container = new StackPane();
            new Scene(container);
            Label first = new Label("First");
            Label second = new Label("Second");
            DialogUtils.show(container, first);
            DialogUtils.close(first);
            DialogUtils.show(container, second);
            DialogUtils.close(first);
            assertTrue(DialogUtils.isShowing(second));
            assertFalse(DialogUtils.isShowing(first));
            assertNull(first.getProperties().get(DialogUtils.PROPERTY_PARENT_PANE_REF));
            DialogUtils.close(second);
            return true;
        });
    }

    /// Executes an operation on the JavaFX thread and waits for its result.
    private static <T> T onFxThread(Callable<T> action) throws Exception {
        FutureTask<T> future = new FutureTask<>(action);
        Platform.runLater(future);
        return future.get(30, TimeUnit.SECONDS);
    }

    /// Emits handle lifecycle events under test control without scheduling work.
    @NotNullByDefault
    private static final class ControlledHandle extends TaskHandle {

        /// The number of times execution has been requested.
        private int starts;

        /// Creates a handle with an unused root task.
        private ControlledHandle() {
            super(Task.runAsync(() -> { }));
        }

        @Override
        public TaskHandle start() {
            starts++;
            taskListeners.forEach(listener -> listener.onExecutionStarted(this));
            return this;
        }

        /// Rejects synchronous execution, which this test handle does not support.
        @Override
        public boolean test() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void cancel() {
            cancelled = true;
        }

        /// Reports completion with the supplied result.
        private void stop(boolean success) {
            taskListeners.forEach(listener -> listener.onExecutionFinished(this, success));
        }
    }
}
