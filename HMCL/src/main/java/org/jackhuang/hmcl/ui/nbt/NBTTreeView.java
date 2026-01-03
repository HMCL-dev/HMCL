/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2025 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.ui.nbt;

import com.github.steveice10.opennbt.tag.builtin.CompoundTag;
import com.github.steveice10.opennbt.tag.builtin.ListTag;
import com.github.steveice10.opennbt.tag.builtin.Tag;
import com.jfoenix.controls.JFXPopup;
import com.jfoenix.controls.JFXTreeView;
import javafx.scene.Node;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.*;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.IconedMenuItem;
import org.jackhuang.hmcl.ui.construct.MenuSeparator;
import org.jackhuang.hmcl.ui.construct.PopupMenu;
import org.jackhuang.hmcl.util.StringUtils;

import java.lang.reflect.Array;
import java.util.EnumMap;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/**
 * @author Glavo
 */
public final class NBTTreeView extends JFXTreeView<Tag> {
    final KeyCombination COPY_COMBO = new KeyCodeCombination(KeyCode.C, KeyCombination.SHORTCUT_DOWN);
    private final EnumMap<NBTTagType, Image> icons = new EnumMap<>(NBTTagType.class);

    public NBTTreeView(NBTTreeView.Item tree) {
        this.setRoot(tree);
        this.setCellFactory(view -> new TagTreeCell(icons));

        this.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (!COPY_COMBO.match(event)) return;

            TreeItem<Tag> current = getSelectionModel().getSelectedItem();

            if (current instanceof Item item && item.getText() != null) {
                FXUtils.copyText(NBTUtils.getSNBT(item.getValue()));
                event.consume();
            }
        });

        this.setOnContextMenuRequested(event -> {

            TreeItem<Tag> current = getSelectionModel().getSelectedItem();
            if (current instanceof Item item) {
                showPopupMenu(item, event, this);
            }
        });
    }

    private void showPopupMenu(Item item, ContextMenuEvent event, Node node) {
        PopupMenu menu = new PopupMenu();
        JFXPopup popup = new JFXPopup(menu);

        IconedMenuItem copyShownItem = new IconedMenuItem(SVG.CONTENT_COPY, "copy shown text", () -> {
            String tagValue = item.getText();
            FXUtils.copyText(tagValue);
        }, popup);

        IconedMenuItem copyRawItem = new IconedMenuItem(SVG.CONTENT_COPY, "copy as snbt", () -> {
            String tagValue = NBTUtils.getSNBT(item.getValue());
            FXUtils.copyText(tagValue);
        }, popup);

        menu.getContent().addAll(
                copyShownItem,
                copyRawItem
        );

        if (!item.isLeaf()) {
            IconedMenuItem expandItem;
            if (item.isExpanded()) {
                expandItem = new IconedMenuItem(SVG.REMOVE, "fold", () -> item.setExpanded(false), popup);
            } else {
                expandItem = new IconedMenuItem(SVG.ADD, "expand", () -> item.setExpanded(true), popup);
            }
            menu.getContent().addAll(
                    new MenuSeparator(),
                    expandItem
            );
        }

        popup.show(node, JFXPopup.PopupVPosition.TOP, JFXPopup.PopupHPosition.LEFT, event.getX(), event.getY());
    }

    public static Item buildTree(Tag tag) {
        Item item = new Item(tag);

        if (tag instanceof CompoundTag compoundTag) {
            for (Tag subTag : compoundTag) {
                item.getChildren().add(buildTree(subTag));
            }
        } else if (tag instanceof ListTag listTag) {
            int idx = 0;
            for (Tag subTag : listTag) {
                Item subTree = buildTree(subTag);
                subTree.setCustomName(String.valueOf(idx++));
                item.getChildren().add(subTree);
            }
        }

        return item;
    }

    public CompoundTag getRootTag() {
        return ((CompoundTag) getRoot().getValue());
    }

    public static class Item extends TreeItem<Tag> {

        private String text;
        private String customName;

        public Item() {
        }

        public Item(Tag value) {
            super(value);
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public void setCustomName(String customName) {
            this.customName = customName;
        }

        public String getCustomName() {
            return customName;
        }
    }

    private static class TagTreeCell extends TreeCell<Tag> {

        private final ImageView imageView = new ImageView();
        private final EnumMap<NBTTagType, Image> icons;

        public TagTreeCell(EnumMap<NBTTagType, Image> icons) {
            this.icons = icons;
            this.setGraphic(imageView);
            imageView.setFitHeight(16);
            imageView.setFitWidth(16);
        }

        private void setTagText(String text, boolean containName) {
            Item item = (Item) getTreeItem();
            String displayText = text == null ? "" : text;

            if (!containName) {
                String customName = item.getCustomName();
                String name = item.getValue().getName();

                if (StringUtils.isNotBlank(customName)) {
                    displayText = customName + ": " + (text == null ? "" : text);
                } else if (StringUtils.isNotBlank(name)) {
                    displayText = name + ": " + (text == null ? "" : text);
                } else {
                    displayText = text;
                }
            }
            item.setText(displayText);
            setText(displayText);
        }

        private void setTagText(int nEntries) {
            setTagText(i18n("nbt.entries", nEntries), false);
        }

        @Override
        public void updateItem(Tag item, boolean empty) {
            super.updateItem(item, empty);

            if (item == null) {
                imageView.setImage(null);
                setText(null);
                return;
            }

            NBTTagType tagType = NBTTagType.typeOf(item);
            imageView.setImage(icons.computeIfAbsent(tagType, type -> new Image(type.getIconUrl())));

            if (((Item) getTreeItem()).getText() != null) {
                setText(((Item) getTreeItem()).getText());
            } else {
                switch (tagType) {
                    case BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, STRING -> setTagText(NBTUtils.getSNBT(item), true);
                    case BYTE_ARRAY, INT_ARRAY, LONG_ARRAY -> setTagText(Array.getLength(item.getValue()));
                    case LIST -> setTagText(((ListTag) item).size());
                    case COMPOUND -> setTagText(((CompoundTag) item).size());
                    default -> setTagText(null, true);
                }
            }
        }
    }
}
