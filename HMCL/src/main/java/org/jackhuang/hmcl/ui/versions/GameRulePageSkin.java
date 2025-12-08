package org.jackhuang.hmcl.ui.versions;

import com.github.steveice10.opennbt.tag.builtin.ByteTag;
import com.github.steveice10.opennbt.tag.builtin.IntTag;
import com.github.steveice10.opennbt.tag.builtin.Tag;
import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXListView;
import com.jfoenix.controls.JFXTextField;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.SkinBase;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.util.Holder;
import org.jackhuang.hmcl.util.Lang;

import static org.jackhuang.hmcl.ui.ToolbarListPageSkin.createToolbarButton2;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

class GameRulePageSkin extends SkinBase<GameRulePage> {

    private final HBox searchBar;
    private final JFXTextField searchField;
    JFXListView<GameRulePageSkin.GameRuleInfo> listView = new JFXListView<>();

    protected GameRulePageSkin(GameRulePage control) {
        super(control);
        StackPane pane = new StackPane();
        pane.setPadding(new Insets(10));
        pane.getStyleClass().addAll("notice-pane");

        ComponentList root = new ComponentList();
        root.getStyleClass().add("no-padding");

        {
            searchBar = new HBox();
            searchBar.setAlignment(Pos.CENTER);
            searchBar.setPadding(new Insets(0, 5, 0, 5));
            searchField = new JFXTextField();
            searchField.setPromptText(i18n("search"));
            HBox.setHgrow(searchField, Priority.ALWAYS);
            JFXButton closeSearchBar = createToolbarButton2(null, SVG.CLOSE,
                    searchField::clear);
            FXUtils.onEscPressed(searchField, closeSearchBar::fire);
            searchBar.getChildren().addAll(searchField, closeSearchBar);
            root.getContent().add(searchBar);
        }

        SpinnerPane center = new SpinnerPane();
        ComponentList.setVgrow(center, Priority.ALWAYS);
        center.getStyleClass().add("large-spinner-pane");
        center.setContent(listView);
        Holder<Object> lastCell = new Holder<>();
        listView.setItems(getSkinnable().getItems());
        listView.setCellFactory(x -> new GameRuleListCell(listView, lastCell));
        FXUtils.ignoreEvent(listView, KeyEvent.KEY_PRESSED, e -> e.getCode() == KeyCode.ESCAPE);
        root.getContent().add(center);

        pane.getChildren().add(root);
        getChildren().add(pane);

    }

    static class GameRuleInfo {

        String ruleKey;
        String displayName;
        Tag tag;

        BorderPane container = new BorderPane();

        public GameRuleInfo(String ruleKey, String displayName, Boolean onValue, ByteTag byteTag) {
            this.ruleKey = ruleKey;
            this.displayName = displayName;
            this.tag = byteTag;

            OptionToggleButton toggleButton = new OptionToggleButton();
            toggleButton.setTitle(displayName);
            toggleButton.setSubtitle(ruleKey);
            toggleButton.setSelected(onValue);

            toggleButton.selectedProperty().addListener((observable, oldValue, newValue) -> {
                ByteTag theByteTag = (ByteTag) tag;
                theByteTag.setValue((byte) (newValue ? 1 : 0));
                LOG.trace(theByteTag.toString());
            });

            HBox.setHgrow(container, Priority.ALWAYS);
            container.setCenter(toggleButton);
        }

        public GameRuleInfo(String ruleKey, String displayName, Integer currentValue, int minValue, int maxValue, IntTag intTag) {
            this.ruleKey = ruleKey;
            this.displayName = displayName;
            this.tag = intTag;

            VBox vbox = new VBox();
            vbox.getChildren().addAll(new Label(displayName), new Label(ruleKey));
            vbox.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(vbox, Priority.ALWAYS);

            container.setPadding(new Insets(8, 8, 8, 16));
            JFXTextField textField = new JFXTextField();
            textField.maxWidth(10);
            textField.minWidth(10);
            textField.textProperty().set(currentValue.toString());
            FXUtils.setValidateWhileTextChanged(textField, true);
            textField.setValidators(new NumberRangeValidator(i18n("input.integer"), i18n("input.number_range", minValue, maxValue), minValue, maxValue, false));
            textField.textProperty().addListener((observable, oldValue, newValue) -> {
                IntTag theIntTag = (IntTag) tag;
                Integer value = Lang.toIntOrNull(newValue);
                if (value == null) {
                    return;
                } else if (value > maxValue || value < minValue) {
                    return;
                } else {
                    theIntTag.setValue(value);
                }
                LOG.trace(theIntTag.toString());
            });

            HBox.setHgrow(container, Priority.ALWAYS);
            container.setCenter(vbox);
            container.setRight(textField);
        }

    }

    static class GameRuleListCell extends MDListCell<GameRuleInfo> {

        public GameRuleListCell(JFXListView<GameRuleInfo> listView, Holder<Object> lastCell) {
            super(listView, lastCell);
        }

        @Override
        protected void updateControl(GameRuleInfo item, boolean empty) {
            if (empty) return;

            getContainer().getChildren().setAll(item.container);
        }
    }
}
