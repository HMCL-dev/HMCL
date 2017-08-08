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
package org.jackhuang.hmcl.ui

import com.jfoenix.controls.*
import javafx.application.Platform
import javafx.fxml.FXML
import javafx.scene.Node
import javafx.scene.control.ScrollPane
import javafx.scene.layout.StackPane
import javafx.beans.property.SimpleStringProperty
import javafx.beans.property.StringProperty
import javafx.scene.control.Label
import org.jackhuang.hmcl.auth.Account
import org.jackhuang.hmcl.auth.OfflineAccount
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilAccount
import org.jackhuang.hmcl.setting.Settings
import org.jackhuang.hmcl.task.Scheduler
import org.jackhuang.hmcl.task.with
import org.jackhuang.hmcl.task.Task
import org.jackhuang.hmcl.ui.wizard.HasTitle
import java.util.concurrent.Callable

class AccountsPage : StackPane(), HasTitle {
    override val titleProperty: StringProperty = SimpleStringProperty(this, "title", "Accounts")

    @FXML lateinit var scrollPane: ScrollPane
    @FXML lateinit var masonryPane: JFXMasonryPane
    @FXML lateinit var dialog: JFXDialog
    @FXML lateinit var txtUsername: JFXTextField
    @FXML lateinit var txtPassword: JFXPasswordField
    @FXML lateinit var lblPassword: Label
    @FXML lateinit var lblCreationWarning: Label
    @FXML lateinit var cboType: JFXComboBox<String>

    init {
        loadFXML("/assets/fxml/account.fxml")
        children.remove(dialog)
        dialog.dialogContainer = this

        JFXScrollPane.smoothScrolling(scrollPane)

        cboType.selectionModel.selectedIndexProperty().addListener { _, _, newValue ->
            val visible = newValue != 0
            lblPassword.isVisible = visible
            txtPassword.isVisible = visible
        }
        cboType.selectionModel.select(0)

        loadAccounts()
    }

    fun loadAccounts() {
        val children = mutableListOf<Node>()
        var i = 0
        for ((_, account) in Settings.getAccounts()) {
            children += buildNode(++i, account)
        }
        masonryPane.children.setAll(children)
        Platform.runLater { scrollPane.requestLayout() }
    }

    private fun buildNode(i: Int, account: Account): Node {
        return AccountItem(i, Math.random() * 100 + 100, Math.random() * 100 + 100).apply {
            lblUser.text = account.username
            lblType.text = when(account) {
                is OfflineAccount -> "Offline Account"
                is YggdrasilAccount -> "Yggdrasil Account"
                else -> throw Error("Unsupported account: $account")
            }
            btnDelete.setOnMouseClicked {
                Settings.deleteAccount(account.username)
                Platform.runLater(this@AccountsPage::loadAccounts)
            }
        }
    }

    private fun getDefaultColor(i: Int): String {
        var color = "#FFFFFF"
        when (i) {
            0 -> color = "#8F3F7E"
            1 -> color = "#B5305F"
            2 -> color = "#CE584A"
            3 -> color = "#DB8D5C"
            4 -> color = "#DA854E"
            5 -> color = "#E9AB44"
            6 -> color = "#FEE435"
            7 -> color = "#99C286"
            8 -> color = "#01A05E"
            9 -> color = "#4A8895"
            10 -> color = "#16669B"
            11 -> color = "#2F65A5"
            12 -> color = "#4E6A9C"
            else -> {
            }
        }
        return color
    }

    fun addNewAccount() {
        dialog.show()
    }

    fun onCreationAccept() {
        val type = cboType.selectionModel.selectedIndex
        val username = txtUsername.text
        val password = txtPassword.text
        val task = Task.of(Callable {
            try {
                val account = when (type) {
                    0 -> OfflineAccount.fromUsername(username)
                    1 -> YggdrasilAccount.fromUsername(username, password)
                    else -> throw UnsupportedOperationException()
                }

                account.logIn(Settings.PROXY)
                account
            } catch (e: Exception) {
                e
            }
        })
        task.subscribe(Scheduler.JAVAFX) {
            val account = task.result
            if (account is Account) {
                Settings.addAccount(account)
                dialog.close()
                loadAccounts()
            } else if (account is Exception) {
                lblCreationWarning.text = account.localizedMessage
            }
        }
    }

    fun onCreationCancel() {
        dialog.close()
    }
}