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

import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.control.skin.TreeViewSkin;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.util.Callback;
import org.glavo.nbt.NBTElement;
import org.glavo.nbt.chunk.Chunk;
import org.glavo.nbt.chunk.ChunkRegion;
import org.glavo.nbt.tag.*;
import org.jackhuang.hmcl.ui.FXUtils;

import java.util.EnumMap;
import java.util.Locale;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/**
 * @author Glavo
 */
public final class NBTTreeView extends TreeView<NBTElement> {

    private static final EnumMap<TagType, Image> icons = new EnumMap<>(TagType.class);

    private static Image getIcon(NBTElement element) {
        if (element instanceof Tag tag) {
            return icons.computeIfAbsent(tag.getType(), type -> {
                String tagName;

                int idx = type.name().indexOf('_');
                if (idx < 0) {
                    tagName = type.name().charAt(0) + type.name().substring(1).toLowerCase(Locale.ROOT);
                } else {
                    tagName = type.name().charAt(0) + type.name().substring(1, idx + 1).toLowerCase(Locale.ROOT)
                            + type.name().charAt(idx + 1) + type.name().substring(idx + 2).toLowerCase(Locale.ROOT);
                }

                return FXUtils.newBuiltinImage("/assets/img/nbt/TAG_" + tagName + ".png");
            });
        } else if (element instanceof ChunkRegion)
            return FXUtils.newBuiltinImage("/assets/img/nbt/TAG_List.png");
        else if (element instanceof Chunk)
            return FXUtils.newBuiltinImage("/assets/img/nbt/TAG_Compound.png");
        else
            return null;
    }

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

    private static Callback<TreeView<NBTElement>, TreeCell<NBTElement>> cellFactory() {
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
            public void updateItem(NBTElement item, boolean empty) {
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

                imageView.setImage(getIcon(item));
                imageView.setFitHeight(16);
                imageView.setFitWidth(16);

                if (((Item) getTreeItem()).getText() != null) {
                    setText(((Item) getTreeItem()).getText());
                } else {
                    if (item instanceof ValueTag<?> valueTag) {
                        setTagText(valueTag.getValue().toString());
                    } else if (item instanceof ArrayTag<?> arrayTag) {
                        setTagText(arrayTag.size());
                    } else if (item instanceof ParentTag<?> parentTag) {
                        setTagText(parentTag.size());
                    } else {
                        setTagText(null);
                    }
                }
            }
        };
    }

    public static Item buildTree(NBTElement element) {
        var item = new Item(element);

        if (element instanceof Chunk chunk) {
            if (chunk.getRootTag() != null) {
                for (Tag subTag : chunk.getRootTag()) {
                    item.getChildren().add(buildTree(subTag));
                }
            }
        } else if (element instanceof CompoundTag compoundTag) {
            for (Tag subTag : compoundTag) {
                item.getChildren().add(buildTree(subTag));
            }
        } else if (element instanceof ListTag<?> listTag) {
            int idx = 0;
            for (Tag subTag : listTag) {
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

    public static class Item extends TreeItem<NBTElement> {
        private String text;
        private String name;

        public Item() {
        }

        public Item(NBTElement value) {
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
            if (name != null) {
                return name;
            }
            return getValue() instanceof Tag tag ? tag.getName() : "";
        }
    }
}
