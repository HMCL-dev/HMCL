/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2017  huangyuhui <huanghongxun2008@126.com>
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
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hmcl.ui.wizard

import com.jfoenix.concurrency.JFXUtilities
import com.jfoenix.controls.JFXProgressBar
import javafx.application.Platform
import javafx.scene.Node
import javafx.scene.control.Label
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import org.jackhuang.hmcl.task.Scheduler
import org.jackhuang.hmcl.task.Task
import org.jackhuang.hmcl.task.TaskExecutor
import org.jackhuang.hmcl.task.TaskListener
import java.util.*
import kotlin.concurrent.thread

interface AbstractWizardDisplayer : WizardDisplayer {
    val wizardController: WizardController
    val cancelQueue: Queue<Any>

    override fun handleDeferredWizardResult(settings: Map<String, Any>, deferredResult: DeferredWizardResult) {
        val vbox = VBox()
        val progressBar = JFXProgressBar()
        val label = Label()
        progressBar.maxHeight = 10.0
        vbox.children += progressBar
        vbox.children += label

        navigateTo(StackPane().apply { children += vbox }, Navigation.NavigationDirection.FINISH)

        cancelQueue.add(thread {
            deferredResult.start(settings, object : ResultProgressHandle {
                private var running = true

                override fun setProgress(currentStep: Int, totalSteps: Int) {
                    progressBar.progress = 1.0 * currentStep / totalSteps
                }

                override fun setProgress(description: String, currentStep: Int, totalSteps: Int) {
                    label.text = description
                    progressBar.progress = 1.0 * currentStep / totalSteps
                }

                override fun setBusy(description: String) {
                    progressBar.progress = JFXProgressBar.INDETERMINATE_PROGRESS
                }

                override fun finished(result: Any) {
                    running = false
                }

                override fun failed(message: String, canNavigateBack: Boolean) {
                    label.text = message
                    running = false
                }

                override val isRunning: Boolean
                    get() = running

            })

            if (!Thread.currentThread().isInterrupted)
                JFXUtilities.runInFX {
                    navigateTo(Label("Successful"), Navigation.NavigationDirection.FINISH)
                }
        })
    }

    override fun handleTask(settings: Map<String, Any>, task: Task) {
        val vbox = VBox()
        val tasksBar = JFXProgressBar()
        val label = Label()
        tasksBar.maxHeight = 10.0
        vbox.children += tasksBar
        vbox.children += label

        var finishedTasks = 0

        navigateTo(StackPane().apply { children += vbox }, Navigation.NavigationDirection.FINISH)

        task.executor().let { executor ->
            executor.taskListener = object : TaskListener {
                override fun onReady(task: Task) {
                    Platform.runLater { tasksBar.progressProperty().set(finishedTasks * 1.0 / executor.totTask.get()) }
                }

                override fun onFinished(task: Task) {
                    Platform.runLater {
                        label.text = task.title
                        ++finishedTasks
                        tasksBar.progressProperty().set(finishedTasks * 1.0 / executor.totTask.get())
                    }
                }

                override fun onFailed(task: Task) {
                    Platform.runLater {
                        label.text = task.title
                        ++finishedTasks
                        tasksBar.progressProperty().set(finishedTasks * 1.0 / executor.totTask.get())
                    }
                }

                override fun onTerminate() {
                    Platform.runLater { navigateTo(Label("Successful"), Navigation.NavigationDirection.FINISH) }
                }

            }

            cancelQueue.add(executor)

            executor.submit(Task.of(Scheduler.JAVAFX) {
                navigateTo(Label("Successful"), Navigation.NavigationDirection.FINISH)
            })
        }.start()
    }

    override fun onCancel() {
        while (cancelQueue.isNotEmpty()) {
            val x = cancelQueue.poll()
            when (x) {
                is TaskExecutor -> x.cancel()
                is Thread -> x.interrupt()
            }
        }
    }
}