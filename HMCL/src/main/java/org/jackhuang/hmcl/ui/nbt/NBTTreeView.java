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
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.control.skin.TreeViewSkin;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.util.Callback;
import org.jackhuang.hmcl.ui.FXUtils;

import java.lang.reflect.Array;
import java.util.EnumMap;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/**
 * @author Glavo
 */
public final class NBTTreeView extends TreeView<Tag> {

    public NBTTreeView(NBTTreeView.Item tree) {
        this.setRoot(tree);
        if (tree != null) tree.setExpanded(true);
        this.setCellFactory(cellFactory());
    }

    @Override
    protected javafx.scene.control.Skin<?> createDefaultSkin() {
        return new TreeViewSkin<Tag>(this) {
            {
                FXUtils.smoothScrolling(getVirtualFlow());
            }
        };
    }

    private static Callback<TreeView<Tag>, TreeCell<Tag>> cellFactory() {
        EnumMap<NBTTagType, Image> icons = new EnumMap<>(NBTTagType.class);

        return view -> new TreeCell<>() {
            private void setTagText(String text) {
                String name = ((Item) getTreeItem()).getName();

                if (name == null) {
                    setText(text);
                } else if (text == null) {
                    setText(name);
                } else {
                    setText(name + ": " + text);
                }
            }

            private void setTagText(int nEntries) {
                setTagText(i18n("nbt.entries", nEntries));
            }

            @Override
            public void updateItem(Tag item, boolean empty) {
                super.updateItem(item, empty);

                ImageView imageView = (ImageView) this.getGraphic();
                if (imageView == null) {
                    imageView = new ImageView();
                    this.setGraphic(imageView);
                }

                if (item == null) {
                    imageView.setImage(null);
                    setText(null);
                    return;
                }

                NBTTagType tagType = NBTTagType.typeOf(item);
                imageView.setImage(icons.computeIfAbsent(tagType, type -> new Image(type.getIconUrl())));
                imageView.setFitHeight(16);
                imageView.setFitWidth(16);

                if (((Item) getTreeItem()).getText() != null) {
                    setText(((Item) getTreeItem()).getText());
                } else {
                    switch (tagType) {
                        case BYTE:
                        case SHORT:
                        case INT:
                        case LONG:
                        case FLOAT:
                        case DOUBLE:
                        case STRING:
                            setTagText(item.getValue().toString());
                            break;
                        case BYTE_ARRAY:
                        case INT_ARRAY:
                        case LONG_ARRAY:
                            setTagText(Array.getLength(item.getValue()));
                            break;
                        case LIST:
                            setTagText(((ListTag) item).size());
                            break;
                        case COMPOUND:
                            setTagText(((CompoundTag) item).size());
                            break;
                        default:
                            setTagText(null);
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
        FXUtils.onChangeAndOperate(item.expandedProperty(), expanded -> {
            if (expanded && item.getChildren().size() == 1) item.getChildren().get(0).setExpanded(true);
        });

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
