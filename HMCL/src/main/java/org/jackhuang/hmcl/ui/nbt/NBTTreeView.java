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
import com.jfoenix.controls.JFXTreeView;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.util.Callback;
import org.jackhuang.hmcl.ui.FXUtils;

import java.lang.reflect.Array;
import java.util.EnumMap;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/**
 * @author Glavo
 */
public final class NBTTreeView extends JFXTreeView<Tag> {
    final KeyCombination COPY_COMBO = new KeyCodeCombination(KeyCode.C, KeyCombination.SHORTCUT_DOWN);

    public NBTTreeView(NBTTreeView.Item tree) {
        this.setRoot(tree);
        this.setCellFactory(cellFactory());

        this.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (!COPY_COMBO.match(event)) return;

            TreeItem<Tag> current = getSelectionModel().getSelectedItem();

            if (current instanceof Item item && item.getText() != null) {
                FXUtils.copyText(item.getText());
                event.consume();
            }
        });
    }

    private static Callback<TreeView<Tag>, TreeCell<Tag>> cellFactory() {
        EnumMap<NBTTagType, Image> icons = new EnumMap<>(NBTTagType.class);

        return view -> new TreeCell<>() {
            final ImageView imageView;

            {
                imageView = new ImageView();
                this.setGraphic(imageView);
                imageView.setFitHeight(16);
                imageView.setFitWidth(16);
            }

            private void setTagText(String text) {
                Item item = (Item) getTreeItem();
                String name = item.getName();

                String displayText;
                if (name == null) {
                    displayText = text;
                } else if (text == null) {
                    displayText = name;
                } else {
                    displayText = name + ": " + text;
                }
                item.setText(displayText);
                setText(displayText);
            }

            private void setTagText(int nEntries) {
                setTagText(i18n("nbt.entries", nEntries));
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
                        case BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, STRING -> setTagText(item.getValue().toString());
                        case BYTE_ARRAY, INT_ARRAY, LONG_ARRAY -> setTagText(Array.getLength(item.getValue()));
                        case LIST -> setTagText(((ListTag) item).size());
                        case COMPOUND -> setTagText(((CompoundTag) item).size());
                        default -> setTagText(null);
                    }
                }
            }
        };
    }

    public static Item buildTree(Tag tag) {
        Item item = new Item(tag);

        if (tag instanceof CompoundTag) {
            for (Tag subTag : ((CompoundTag) tag)) {
                item.getChildren().add(buildTree(subTag));
            }
        } else if (tag instanceof ListTag) {
            int idx = 0;
            for (Tag subTag : ((ListTag) tag)) {
                Item subTree = buildTree(subTag);
                subTree.setName(String.valueOf(idx++));
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
        private String name;

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

        public void setName(String name) {
            this.name = name;
        }

        public String getName() {
            return name == null ? getValue().getName() : name;
        }
    }
}
