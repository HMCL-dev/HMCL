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
package org.jackhuang.hmcl.ui.construct

import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.scene.control.MultipleSelectionModel

class NoneMultipleSelectionModel<T> : MultipleSelectionModel<T>() {
    override fun isEmpty() = true
    override fun selectAll() = Unit
    override fun selectIndices(index: Int, vararg indices: Int) = Unit
    override fun select(obj: T) = Unit
    override fun select(index: Int) = Unit
    override fun selectLast() = Unit
    override fun selectFirst() = Unit
    override fun selectNext() = Unit
    override fun clearSelection(index: Int) = Unit
    override fun clearSelection() = Unit
    override fun clearAndSelect(index: Int) = Unit
    override fun selectPrevious() = Unit
    override fun isSelected(index: Int) = false
    override fun getSelectedItems(): ObservableList<T> = FXCollections.emptyObservableList<T>()
    override fun getSelectedIndices(): ObservableList<Int> = FXCollections.emptyObservableList<Int>()
}