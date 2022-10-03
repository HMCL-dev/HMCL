package org.jackhuang.hmcl.ui.construct;

import javafx.event.EventHandler;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import org.jackhuang.hmcl.mod.ModpackFile;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/**
 * This page is used to ask player which optional file they want to install
 * Support CurseForge modpack yet
 */
public class OptionalFilesSelectionPane extends GridPane {
    Set<ModpackFile> selected = new HashSet<>();

    public OptionalFilesSelectionPane() {
    }

    public void updateOptionalFileList(List<? extends ModpackFile> files) {
        int i = 0;
        for(ModpackFile file : files) {
            selected.add(file);
            if(file.isOptional()) {
                BorderPane entryPane = new BorderPane();

                CheckBox checkBox = new CheckBox(file.getFileName());
                checkBox.setSelected(true);
                checkBox.setOnMouseClicked(event -> {
                    if(checkBox.isSelected()) {
                        selected.add(file);
                    } else {
                        selected.remove(file);
                    }
                });
                entryPane.setLeft(checkBox);

                this.addRow(i++, entryPane);
            }
        }
        if(i == 0) {
            Label label = new Label();
            label.setText(i18n("modpack.no_optional_files"));
            this.addRow(0, label);
        }
    }

    public Set<ModpackFile> getSelected() {
        return selected;
    }
}
