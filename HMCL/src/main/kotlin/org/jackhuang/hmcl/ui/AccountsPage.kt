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
import javafx.beans.value.ChangeListener
import javafx.scene.control.Label
import javafx.scene.control.ToggleGroup
import org.jackhuang.hmcl.auth.Account
import org.jackhuang.hmcl.auth.OfflineAccount
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilAccount
import org.jackhuang.hmcl.i18n
import org.jackhuang.hmcl.setting.Settings
import org.jackhuang.hmcl.task.Scheduler
import org.jackhuang.hmcl.task.Task
import org.jackhuang.hmcl.task.task
import org.jackhuang.hmcl.ui.wizard.DecoratorPage
import java.util.concurrent.Callable

class AccountsPage() : StackPane(), DecoratorPage {
    override val titleProperty: StringProperty = SimpleStringProperty(this, "title", "Accounts")

    @FXML lateinit var scrollPane: ScrollPane
    @FXML lateinit var masonryPane: JFXMasonryPane
    @FXML lateinit var dialog: JFXDialog
    @FXML lateinit var txtUsername: JFXTextField
    @FXML lateinit var txtPassword: JFXPasswordField
    @FXML lateinit var lblCreationWarning: Label
    @FXML lateinit var cboType: JFXComboBox<String>
    @FXML lateinit var progressBar: JFXProgressBar

    val listener = ChangeListener<Account?> { _, _, newValue ->
        masonryPane.children.forEach {
            if (it is AccountItem) {
                it.chkSelected.isSelected = newValue?.username == it.lblUser.text
            }
        }
    }

    init {
        loadFXML("/assets/fxml/account.fxml")
        children.remove(dialog)
        dialog.dialogContainer = this

        scrollPane.smoothScrolling()

        txtUsername.textProperty().addListener { _ ->
            txtUsername.validate()
        }
        txtUsername.validate()

        txtPassword.textProperty().addListener { _ ->
            txtPassword.validate()
        }
        txtPassword.validate()

        cboType.selectionModel.selectedIndexProperty().addListener { _, _, newValue ->
            val visible = newValue != 0
            txtPassword.isVisible = visible
        }
        cboType.selectionModel.select(0)

        Settings.selectedAccountProperty.addListener(listener)

        loadAccounts()

        if (Settings.getAccounts().isEmpty())
            addNewAccount()
    }

    override fun onClose() {
        Settings.selectedAccountProperty.removeListener(listener)
    }

    fun loadAccounts() {
        val children = mutableListOf<Node>()
        var i = 0
        val group = ToggleGroup()
        for ((_, account) in Settings.getAccounts()) {
            children += buildNode(++i, account, group)
        }
        group.selectedToggleProperty().addListener { _, _, newValue ->
            if (newValue != null)
                Settings.selectedAccount = newValue.properties["account"] as Account
        }
        masonryPane.resetChildren(children)
        Platform.runLater {
            masonryPane.requestLayout()
            scrollPane.requestLayout()
        }
    }

    private fun buildNode(i: Int, account: Account, group: ToggleGroup): Node {
        return AccountItem(i, group).apply {
            chkSelected.properties["account"] = account
            chkSelected.isSelected = Settings.selectedAccount == account
            lblUser.text = account.username
            lblType.text = accountType(account)
            btnDelete.setOnMouseClicked {
                Settings.deleteAccount(account.username)
                Platform.runLater(this@AccountsPage::loadAccounts)
            }
        }
    }

    fun addNewAccount() {
        txtUsername.text = ""
        txtPassword.text = ""
        dialog.show()
    }

    fun onCreationAccept() {
        val type = cboType.selectionModel.selectedIndex
        val username = txtUsername.text
        val password = txtPassword.text
        progressBar.isVisible = true
        val task = task(Callable {
            try {
                val account = when (type) {
                    0 -> OfflineAccount.fromUsername(username)
                    1 -> YggdrasilAccount.fromUsername(username, password)
                    else -> throw UnsupportedOperationException()
                }

                account.logIn(Settings.proxy)
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
            progressBar.isVisible = false
        }
    }

    fun onCreationCancel() {
        dialog.close()
    }
}

fun accountType(account: Account) =
        when(account) {
            is OfflineAccount -> i18n("login.methods.offline")
            is YggdrasilAccount -> i18n("login.methods.yggdrasil")
            else -> throw Error("${i18n("login.methods.no_method")}: $account")
        }