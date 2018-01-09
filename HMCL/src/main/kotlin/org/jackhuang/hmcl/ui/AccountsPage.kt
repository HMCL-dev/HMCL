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
import javafx.beans.property.SimpleStringProperty
import javafx.beans.property.StringProperty
import javafx.fxml.FXML
import javafx.scene.Node
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.control.ToggleGroup
import javafx.scene.layout.StackPane
import org.jackhuang.hmcl.auth.Account
import org.jackhuang.hmcl.auth.MultiCharacterSelector
import org.jackhuang.hmcl.auth.OfflineAccount
import org.jackhuang.hmcl.auth.OfflineAccountFactory
import org.jackhuang.hmcl.auth.yggdrasil.InvalidCredentialsException
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilAccount
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilAccountFactory
import org.jackhuang.hmcl.game.HMCLMultiCharacterSelector
import org.jackhuang.hmcl.i18n
import org.jackhuang.hmcl.setting.Settings
import org.jackhuang.hmcl.task.Schedulers
import org.jackhuang.hmcl.ui.wizard.DecoratorPage
import org.jackhuang.hmcl.util.onChange
import org.jackhuang.hmcl.util.onChangeAndOperate
import org.jackhuang.hmcl.util.taskResult

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

    init {
        loadFXML("/assets/fxml/account.fxml")
        children.remove(dialog)
        dialog.dialogContainer = this

        scrollPane.smoothScrolling()

        txtUsername.setValidateWhileTextChanged()
        txtPassword.setValidateWhileTextChanged()

        cboType.selectionModel.selectedIndexProperty().onChange {
            val visible = it != 0
            txtPassword.isVisible = visible
        }
        cboType.selectionModel.select(0)

        txtPassword.setOnAction { onCreationAccept() }
        txtUsername.setOnAction { onCreationAccept() }

        Settings.selectedAccountProperty.onChangeAndOperate { account ->
            masonryPane.children.forEach { node ->
                if (node is AccountItem) {
                    node.chkSelected.isSelected = account?.username == node.lblUser.text
                }
            }
        }

        loadAccounts()

        if (Settings.getAccounts().isEmpty())
            addNewAccount()
    }

    fun loadAccounts() {
        val children = mutableListOf<Node>()
        var i = 0
        val group = ToggleGroup()
        for ((_, account) in Settings.getAccounts()) {
            children += buildNode(++i, account, group)
        }
        group.selectedToggleProperty().onChange {
            if (it != null)
                Settings.selectedAccount = it.properties["account"] as Account
        }
        masonryPane.resetChildren(children)
        Platform.runLater {
            masonryPane.requestLayout()
            scrollPane.requestLayout()
        }
    }

    private fun buildNode(i: Int, account: Account, group: ToggleGroup): Node {
        return AccountItem(i, account, group).apply {
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
        lblCreationWarning.text = ""
        taskResult("create_account") {
            try {
                val account = when (type) {
                    0 -> OfflineAccountFactory.INSTANCE.fromUsername(username)
                    1 -> YggdrasilAccountFactory.INSTANCE.fromUsername(username, password)
                    else -> throw UnsupportedOperationException()
                }

                account.logIn(HMCLMultiCharacterSelector.INSTANCE, Settings.proxy)
                account
            } catch (e: Exception) {
                e
            }
        }.subscribe(Schedulers.javafx()) {
            val account: Any = it["create_account"]
            if (account is Account) {
                Settings.addAccount(account)
                dialog.close()
                loadAccounts()
            } else if (account is InvalidCredentialsException) {
                lblCreationWarning.text = i18n("login.wrong_password")
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