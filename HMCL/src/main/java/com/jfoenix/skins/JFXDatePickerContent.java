//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package com.jfoenix.skins;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXDatePicker;
import com.jfoenix.controls.JFXListView;
import com.jfoenix.svg.SVGGlyph;
import com.jfoenix.transitions.CachedTransition;
import javafx.animation.*;
import javafx.animation.Animation.Status;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;
import org.jackhuang.hmcl.setting.ConfigHolder;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.SVGContainer;
import org.jackhuang.hmcl.ui.construct.DialogCloseEvent;
import org.jackhuang.hmcl.ui.construct.RipplerContainer;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.i18n.LocaleUtils;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.chrono.ChronoLocalDate;
import java.time.chrono.Chronology;
import java.time.format.DateTimeFormatter;
import java.time.format.DecimalStyle;
import java.time.temporal.ChronoUnit;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class JFXDatePickerContent extends VBox {
    private static final String SPINNER_LABEL = "spinner-label";
    private static final String ROBOTO = "Roboto";
    private static final Color DEFAULT_CELL_COLOR = Color.valueOf("#9C9C9C");
    protected JFXDatePicker datePicker;
    private JFXButton backMonthButton;
    private JFXButton forwardMonthButton;
    private final ObjectProperty<Label> selectedYearCell = new SimpleObjectProperty<>(null);
    private Label selectedDateLabel;
    private Label selectedYearLabel;
    private Label monthYearLabel;
    protected GridPane contentGrid;
    private final StackPane calendarPlaceHolder = new StackPane();
    private final CachedTransition showTransition;
    private final CachedTransition hideTransition;
    private ParallelTransition tempImageTransition;
    private final int daysPerWeek = 7;
    private final List<DateCell> weekDaysCells = new ArrayList<>();
    private final List<DateCell> weekNumberCells = new ArrayList<>();
    protected List<DateCell> dayCells = new ArrayList<>();
    private LocalDate[] dayCellDates;
    private DateCell currentFocusedDayCell = null;
    private final ListView<String> yearsListView = new JFXListView<>() {
        {
            this.getStyleClass().addAll("date-picker-list-view", "no-padding");
            this.setCellFactory((listView) -> new ListCell<>() {
                static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");

                final Label label = new Label();
                final StackPane root;

                {
                    JFXDatePickerContent.this.selectedYearLabel.textProperty().addListener((o, oldVal, newVal) -> {
                        if (!JFXDatePickerContent.this.yearsListView.isVisible() && label.getText().equals(newVal)) {
                            JFXDatePickerContent.this.selectedYearCell.set(this.label);
                        }
                        if (label.getText().equals(newVal)) this.updateSelected(true);
                    });

                    label.getStyleClass().add(SPINNER_LABEL);
                    label.setMaxWidth(Double.MAX_VALUE);
                    StackPane pane = new StackPane(label);
                    pane.setPadding(new Insets(7));
                    RipplerContainer ripplerContainer = new RipplerContainer(pane);
                    root = new StackPane(ripplerContainer);
                    root.getStyleClass().add("data-picker-list-cell");
                    this.setGraphic(root);

                    FXUtils.onChangeAndOperate(selectedProperty(), selected -> {
                        root.pseudoClassStateChanged(SELECTED, selected);
                        if (selected) {
                            int offset = Integer.parseInt(label.getText()) - Integer.parseInt(JFXDatePickerContent.this.selectedYearLabel.getText());
                            JFXDatePickerContent.this.forward(offset, ChronoUnit.YEARS, false, false);
                        }
                    });
                }

                public void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (!empty && item != null) {
                        label.setText(item);
                        setText(null);
                        setGraphic(root);
                        if (item.equals(selectedYearLabel.getText())) updateSelected(true);
                    } else {
                        setGraphic(null);
                    }

                }
            });
        }
    };
    final DateTimeFormatter monthFormatter = DateTimeFormatter.ofPattern("MMMM");
    final DateTimeFormatter yearFormatter = DateTimeFormatter.ofPattern("y");
    final DateTimeFormatter weekNumberFormatter = DateTimeFormatter.ofPattern("w");
    final DateTimeFormatter weekDayNameFormatter = DateTimeFormatter.ofPattern("ccc");
    final DateTimeFormatter dayCellFormatter = DateTimeFormatter.ofPattern("d");
    private final ObjectProperty<YearMonth> selectedYearMonth = new SimpleObjectProperty<>(this, "selectedYearMonth");

    final ObjectProperty<LocalDate> valueProperty = new SimpleObjectProperty<>(this, "value");

    JFXDatePickerContent(final DatePicker datePicker) {
        this.datePicker = (JFXDatePicker) datePicker;
        this.getStyleClass().add("date-picker-popup");
        LocalDate date = datePicker.getValue();
        this.valueProperty.set(date);
        this.valueProperty.addListener((o, old, n) -> {
            this.displayedYearMonthProperty().set(n != null ? YearMonth.from(n) : YearMonth.now());
            this.updateValues();
        });
        this.selectedYearMonth.set(date != null ? YearMonth.from(date) : YearMonth.now());
        this.selectedYearMonth.addListener((observable, oldValue, newValue) -> this.updateValues());
        this.getChildren().add(this.createHeaderPane());
        this.getChildren().add(new Separator());
        this.contentGrid = new GridPane() {
            protected double computePrefWidth(double height) {
                int nCols = JFXDatePickerContent.this.daysPerWeek + (datePicker.isShowWeekNumbers() ? 1 : 0);
                double leftSpace = this.snapSpaceX(this.getInsets().getLeft());
                double rightSpace = this.snapSpaceX(this.getInsets().getRight());
                double hgaps = this.snapSpaceX(this.getHgap()) * (double) (nCols - 1);
                double contentWidth = super.computePrefWidth(height) - leftSpace - rightSpace - hgaps;
                return this.snapSizeX(contentWidth / (double) nCols) * (double) nCols + leftSpace + rightSpace + hgaps;
            }

            protected void layoutChildren() {
                if (this.getWidth() > (double) 0.0F && this.getHeight() > (double) 0.0F) {
                    super.layoutChildren();
                }

            }
        };
        this.contentGrid.setFocusTraversable(true);
        this.contentGrid.getStyleClass().add("calendar-grid");
        this.contentGrid.setBackground(new Background(new BackgroundFill(Color.TRANSPARENT, CornerRadii.EMPTY, Insets.EMPTY)));
        this.contentGrid.setPadding(new Insets(0.0F, 12.0F, 12.0F, 12.0F));
        this.contentGrid.setVgap(4);
        this.contentGrid.setHgap(4);
        this.createWeekDaysCells();
        this.createDayCells();

        for (int i = 0; i <= 200; ++i) {
            this.yearsListView.getItems().add(Integer.toString(1900 + i));
        }

        StackPane main = new StackPane();
        this.yearsListView.setVisible(false);
        this.yearsListView.setOpacity(0.0F);
        this.yearsListView.maxWidthProperty().bind(main.widthProperty());
        this.yearsListView.maxHeightProperty().bind(main.heightProperty());
        main.getChildren().setAll(this.contentGrid, this.yearsListView);

        VBox contentHolder = new VBox();
        contentHolder.getChildren().setAll(this.createCalendarMonthLabelPane(), main);
        this.calendarPlaceHolder.getStyleClass().add("content");
        this.calendarPlaceHolder.getChildren().setAll(contentHolder, this.createCalendarArrowsPane());
        FXUtils.setOverflowHidden(this.calendarPlaceHolder);

        this.getChildren().add(this.calendarPlaceHolder);

        this.getChildren().add(this.createActions());

        this.refresh();
        this.addEventHandler(KeyEvent.ANY, (event) -> {
            Node node = this.getScene().getFocusOwner();
            if (node instanceof DateCell) {
                this.currentFocusedDayCell = (DateCell) node;
            }

            switch (event.getCode()) {
                case HOME -> {
                    this.init();
                    this.goToDate(LocalDate.now(), true);
                    event.consume();
                }
                case PAGE_UP -> {
                    if (!this.backMonthButton.isDisabled()) {
                        this.forward(-1, ChronoUnit.MONTHS, true, true);
                    }

                    event.consume();
                }
                case PAGE_DOWN -> {
                    if (!this.forwardMonthButton.isDisabled()) {
                        this.forward(1, ChronoUnit.MONTHS, true, true);
                    }

                    event.consume();
                }
                case ESCAPE -> {
                    fireEvent(new DialogCloseEvent());
                    event.consume();
                }
                default -> event.consume();
            }

        });
        this.showTransition = new CachedTransition(this.yearsListView, new Timeline(new KeyFrame(Duration.millis(0.0F), new KeyValue(this.yearsListView.opacityProperty(), 0, Interpolator.EASE_BOTH), new KeyValue(this.contentGrid.opacityProperty(), 1, Interpolator.EASE_BOTH)), new KeyFrame(Duration.millis(500.0F), new KeyValue(this.yearsListView.opacityProperty(), 0, Interpolator.EASE_BOTH), new KeyValue(this.contentGrid.opacityProperty(), 0, Interpolator.EASE_BOTH)), new KeyFrame(Duration.millis(1000.0F), new KeyValue(this.yearsListView.opacityProperty(), 1, Interpolator.EASE_BOTH), new KeyValue(this.contentGrid.opacityProperty(), 0, Interpolator.EASE_BOTH)))) {
            {
                this.setCycleDuration(Duration.millis(320.0F));
                this.setDelay(Duration.seconds(0.0F));
            }

            protected void starting() {
                super.starting();
                JFXDatePickerContent.this.yearsListView.setVisible(true);
            }
        };
        this.hideTransition = new CachedTransition(this.yearsListView, new Timeline(new KeyFrame(Duration.millis(0.0F), new KeyValue(this.yearsListView.opacityProperty(), 1, Interpolator.EASE_BOTH), new KeyValue(this.contentGrid.opacityProperty(), 0, Interpolator.EASE_BOTH)), new KeyFrame(Duration.millis(500.0F), new KeyValue(this.yearsListView.opacityProperty(), 0, Interpolator.EASE_BOTH), new KeyValue(this.contentGrid.opacityProperty(), 0, Interpolator.EASE_BOTH)), new KeyFrame(Duration.millis(1000.0F), new KeyValue(this.yearsListView.opacityProperty(), 0, Interpolator.EASE_BOTH), new KeyValue(this.contentGrid.opacityProperty(), 1, Interpolator.EASE_BOTH)))) {
            {
                this.setCycleDuration(Duration.millis(320.0F));
                this.setDelay(Duration.seconds(0.0F));
            }

            protected void stopping() {
                super.stopping();
                JFXDatePickerContent.this.yearsListView.setVisible(false);
            }
        };
    }

    ObjectProperty<YearMonth> displayedYearMonthProperty() {
        return this.selectedYearMonth;
    }

    private void createWeekDaysCells() {
        for (int i = 0; i < this.daysPerWeek; ++i) {
            DateCell cell = new DateCell();
            cell.getStyleClass().add("day-name-cell");
            cell.setFont(Font.font(ROBOTO, FontWeight.BOLD, 12.0F));
            cell.setAlignment(Pos.BASELINE_CENTER);
            this.weekDaysCells.add(cell);
        }

        for (int i = 0; i < 6; ++i) {
            DateCell cell = new DateCell();
            cell.getStyleClass().add("week-number-cell");
            cell.setFont(Font.font(ROBOTO, FontWeight.BOLD, 12.0F));
            this.weekNumberCells.add(cell);
        }

    }

    protected VBox createHeaderPane() {
        Label title = new Label(I18n.i18n("button.select_date"));
        title.getStyleClass().add("title");
        title.setFont(Font.font(ROBOTO, FontWeight.NORMAL, 12.0F));
        HBox titleContainer = new HBox(title);
        titleContainer.setAlignment(Pos.CENTER_LEFT);
        titleContainer.setPadding(new Insets(0, 0, 5, 0));
        this.selectedYearLabel = new Label();
        this.selectedYearLabel.getStyleClass().add(SPINNER_LABEL);
        this.selectedYearLabel.setFont(Font.font(ROBOTO, FontWeight.BOLD, 14.0F));
        HBox yearLabelContainer = new HBox();
        yearLabelContainer.getStyleClass().add("spinner");
        yearLabelContainer.getChildren().addAll(this.selectedYearLabel);
        yearLabelContainer.setAlignment(Pos.CENTER_LEFT);
        yearLabelContainer.setFillHeight(false);
        yearLabelContainer.setOnMouseClicked((click) -> {
            if (!this.yearsListView.isVisible()) {
                int yearIndex = Integer.parseInt(this.selectedYearLabel.getText()) - 1900 - 2;
                this.yearsListView.scrollTo(yearIndex >= 0 ? yearIndex : yearIndex + 2);
                this.hideTransition.stop();
                this.showTransition.play();
            }

        });
        this.selectedDateLabel = new Label();
        this.selectedDateLabel.getStyleClass().add(SPINNER_LABEL);
        this.selectedDateLabel.setFont(Font.font(ROBOTO, FontWeight.BOLD, 32.0F));
        HBox selectedDateContainer = new HBox();
        selectedDateContainer.getStyleClass().add("spinner");
        selectedDateContainer.getChildren().addAll(this.selectedDateLabel);
        selectedDateContainer.setAlignment(Pos.CENTER_LEFT);
        selectedDateContainer.setOnMouseClicked((click) -> {
            if (this.yearsListView.isVisible()) {
                this.showTransition.stop();
                this.hideTransition.play();
            }

        });
        VBox headerPanel = new VBox();
        headerPanel.getStyleClass().add("month-year-pane");
        headerPanel.setPadding(new Insets(12.0F, 24.0F, 12.0F, 24.0F));
        headerPanel.getChildren().addAll(titleContainer, yearLabelContainer, selectedDateContainer);
        return headerPanel;
    }

    protected BorderPane createCalendarArrowsPane() {
        SVGGlyph leftChevron = new SVGGlyph(0, "CHEVRON_LEFT", "M 742,-37 90,614 Q 53,651 53,704.5 53,758 90,795 l 652,651 q 37,37 90.5,37 53.5,0 90.5,-37 l 75,-75 q 37,-37 37,-90.5 0,-53.5 -37,-90.5 L 512,704 998,219 q 37,-38 37,-91 0,-53 -37,-90 L 923,-37 Q 886,-74 832.5,-74 779,-74 742,-37 z", Color.GRAY);
        SVGGlyph rightChevron = new SVGGlyph(0, "CHEVRON_RIGHT", "m 1099,704 q 0,-52 -37,-91 L 410,-38 q -37,-37 -90,-37 -53,0 -90,37 l -76,75 q -37,39 -37,91 0,53 37,90 l 486,486 -486,485 q -37,39 -37,91 0,53 37,90 l 76,75 q 36,38 90,38 54,0 90,-38 l 652,-651 q 37,-37 37,-90 z", Color.GRAY);
        leftChevron.setSize(6.0F, 11.0F);
        rightChevron.setSize(6.0F, 11.0F);
        this.backMonthButton = new JFXButton();
        this.backMonthButton.setMinSize(40.0F, 40.0F);
        this.backMonthButton.setBackground(new Background(new BackgroundFill(Color.TRANSPARENT, new CornerRadii(40.0F), Insets.EMPTY)));
        this.backMonthButton.getStyleClass().add("left-button");
        this.backMonthButton.setGraphic(leftChevron);
        this.backMonthButton.setRipplerFill(this.datePicker.getDefaultColor());
        this.backMonthButton.setOnAction((t) -> this.forward(-1, ChronoUnit.MONTHS, false, true));
        this.forwardMonthButton = new JFXButton();
        this.forwardMonthButton.setMinSize(40.0F, 40.0F);
        this.forwardMonthButton.setBackground(new Background(new BackgroundFill(Color.TRANSPARENT, new CornerRadii(40.0F), Insets.EMPTY)));
        this.forwardMonthButton.getStyleClass().add("right-button");
        this.forwardMonthButton.setGraphic(rightChevron);
        this.forwardMonthButton.setRipplerFill(this.datePicker.getDefaultColor());
        this.forwardMonthButton.setOnAction((t) -> this.forward(1, ChronoUnit.MONTHS, false, true));
        BorderPane arrowsContainer = new BorderPane();
        arrowsContainer.setLeft(this.backMonthButton);
        arrowsContainer.setRight(this.forwardMonthButton);
        arrowsContainer.setPadding(new Insets(4.0F, 12.0F, 2.0F, 12.0F));
        arrowsContainer.setPickOnBounds(false);
        return arrowsContainer;
    }

    protected BorderPane createCalendarMonthLabelPane() {
        this.monthYearLabel = new Label();
        this.monthYearLabel.getStyleClass().add(SPINNER_LABEL);
        this.monthYearLabel.setFont(Font.font(ROBOTO, FontWeight.BOLD, 13.0F));
        SVGContainer svgContainer = new SVGContainer(13);
        HBox box = new HBox(monthYearLabel, svgContainer);
        box.setSpacing(3);
        box.setAlignment(Pos.CENTER);
        FXUtils.onChangeAndOperate(this.yearsListView.visibleProperty(), b -> svgContainer.setIcon(b ? SVG.ARROW_DROP_UP : SVG.ARROW_DROP_DOWN));
        FXUtils.onClicked(box, () -> {
            if (!this.yearsListView.isVisible()) {
                int yearIndex = Integer.parseInt(this.selectedYearLabel.getText()) - 1900 - 2;
                this.yearsListView.scrollTo(yearIndex >= 0 ? yearIndex : yearIndex + 2);
                this.hideTransition.stop();
                this.showTransition.play();
            } else {
                this.showTransition.stop();
                this.hideTransition.play();
            }
        });
        BorderPane monthContainer = new BorderPane();
        monthContainer.setMinHeight(50.0F);
        monthContainer.setCenter(box);
        monthContainer.setPadding(new Insets(2.0F, 12.0F, 2.0F, 12.0F));
        return monthContainer;
    }

    protected HBox createActions() {
        JFXButton cancelButton = new JFXButton(I18n.i18n("button.cancel"));
        cancelButton.getStyleClass().add("dialog-cancel");
        FXUtils.onClicked(cancelButton, () -> fireEvent(new DialogCloseEvent()));

        JFXButton acceptButton = new JFXButton(I18n.i18n("button.ok"));
        acceptButton.getStyleClass().add("dialog-accept");
        FXUtils.onClicked(acceptButton, () -> {
            datePicker.setValue(valueProperty.get());
            fireEvent(new DialogCloseEvent());
        });

        HBox hBox = new HBox(cancelButton, acceptButton);
        hBox.setPadding(new Insets(0, 5, 3, 0));
        hBox.setSpacing(5);
        hBox.setAlignment(Pos.CENTER_RIGHT);
        return hBox;
    }

    void updateContentGrid() {
        this.contentGrid.getColumnConstraints().clear();
        this.contentGrid.getChildren().clear();
        int colsNumber = this.daysPerWeek + (this.datePicker.isShowWeekNumbers() ? 1 : 0);
        ColumnConstraints columnConstraints = new ColumnConstraints();
        columnConstraints.setPercentWidth(100.0F);

        for (int i = 0; i < colsNumber; ++i) {
            this.contentGrid.getColumnConstraints().add(columnConstraints);
        }

        for (int i = 0; i < this.daysPerWeek; ++i) {
            this.contentGrid.add(this.weekDaysCells.get(i), i + colsNumber - this.daysPerWeek, 1);
        }

        if (this.datePicker.isShowWeekNumbers()) {
            for (int i = 0; i < 6; ++i) {
                this.contentGrid.add(this.weekNumberCells.get(i), 0, i + 2);
            }
        }

        for (int row = 0; row < 6; ++row) {
            for (int col = 0; col < this.daysPerWeek; ++col) {
                this.contentGrid.add(this.dayCells.get(row * this.daysPerWeek + col), col + colsNumber - this.daysPerWeek, row + 2);
            }
        }

    }

    private void refresh() {
        this.updateDayNameCells();
        this.updateValues();
    }

    private void updateDayNameCells() {
        int weekFirstDay = WeekFields.of(this.getLocale()).getFirstDayOfWeek().getValue();
        LocalDate date = LocalDate.of(2009, 7, 12 + weekFirstDay);

        for (int i = 0; i < this.daysPerWeek; ++i) {
            String name = this.weekDayNameFormatter.withLocale(this.getLocale()).format(date.plusDays(i));
            if (!LocaleUtils.isChinese(this.weekNumberFormatter.getLocale())) {
                name = name.substring(0, 1).toUpperCase(Locale.ROOT);
            }

            this.weekDaysCells.get(i).setText(name);
        }

    }

    void updateValues() {
        this.updateWeekNumberDateCells();
        this.updateDayCells();
        this.updateMonthYearPane();
    }

    void updateWeekNumberDateCells() {
        if (this.datePicker.isShowWeekNumbers()) {
            Locale locale = this.getLocale();
            LocalDate firstDayOfMonth = this.selectedYearMonth.get().atDay(1);

            for (int i = 0; i < 6; ++i) {
                LocalDate date = firstDayOfMonth.plusWeeks(i);
                String weekNumber = this.weekNumberFormatter.withLocale(locale).withDecimalStyle(DecimalStyle.of(locale)).format(date);
                this.weekNumberCells.get(i).setText(weekNumber);
            }
        }

    }

    private void updateDayCells() {
        Locale locale = this.getLocale();
        Chronology chrono = this.getPrimaryChronology();
        int firstDayOfWeek = WeekFields.of(this.getLocale()).getFirstDayOfWeek().getValue();
        int firstOfMonthIndex = this.selectedYearMonth.get().atDay(1).getDayOfWeek().getValue() - firstDayOfWeek;
        firstOfMonthIndex += firstOfMonthIndex < 0 ? this.daysPerWeek : 0;
        YearMonth currentYearMonth = this.selectedYearMonth.get();
        int daysInCurMonth = -1;

        for (int i = 0; i < 6 * this.daysPerWeek; ++i) {
            DateCell dayCell = this.dayCells.get(i);
            try {
                if (daysInCurMonth == -1) {
                    daysInCurMonth = currentYearMonth.lengthOfMonth();
                }

                int dayIndex = i - firstOfMonthIndex + 1;
                LocalDate date;
                if (dayIndex <= 0) {
                    YearMonth previous = currentYearMonth.minusMonths(1);
                    date = previous.atDay(previous.lengthOfMonth() + dayIndex);
                } else if (dayIndex > daysInCurMonth) {
                    YearMonth next = currentYearMonth.plusMonths(1);
                    date = next.atDay(dayIndex - daysInCurMonth);
                } else {
                    date = currentYearMonth.atDay(dayIndex);
                }
                this.dayCellDates[i] = date;
                if (date.equals(LocalDate.now())) {
                    dayCell.getStyleClass().add("today");
                } else {
                    dayCell.getStyleClass().removeAll("today");
                }

                if (date.equals(valueProperty.get())) {
                    dayCell.getStyleClass().add("selected");
                    this.currentFocusedDayCell = dayCell;
                } else {
                    dayCell.getStyleClass().removeAll("selected");
                }

                ChronoLocalDate cDate = chrono.date(date);
                String cellText = this.dayCellFormatter.withLocale(locale).withChronology(chrono).withDecimalStyle(DecimalStyle.of(locale)).format(cDate);
                dayCell.setText(cellText);
                if (i < firstOfMonthIndex) {
                    dayCell.getStyleClass().add("previous-month");
                    dayCell.getStyleClass().removeAll("next-month");
                    dayCell.setDisable(true);
                } else if (i >= firstOfMonthIndex + daysInCurMonth) {
                    dayCell.getStyleClass().add("next-month");
                    dayCell.getStyleClass().removeAll("previous-month");
                    dayCell.setDisable(true);
                } else {
                    dayCell.getStyleClass().removeAll("previous-month", "next-month");
                    dayCell.setDisable(false);
                }

                dayCell.updateItem(date, false);
            } catch (DateTimeException var13) {
                dayCell.setText("");
                dayCell.setDisable(true);
            }
        }

    }

    protected void updateMonthYearPane() {
        YearMonth yearMonth = this.selectedYearMonth.get();
        this.selectedDateLabel.setText(DateTimeFormatter.ofPattern("EEE, MMM yyyy").format(Objects.requireNonNullElseGet(valueProperty.get(), LocalDate::now)));

        this.selectedYearLabel.setText(this.formatYear(yearMonth));
        this.monthYearLabel.setText(this.formatMonth(yearMonth) + " " + this.formatYear(yearMonth));
        Chronology chrono = this.datePicker.getChronology();
        LocalDate firstDayOfMonth = yearMonth.atDay(1);
        this.backMonthButton.setDisable(!this.isValidDate(chrono, firstDayOfMonth, -1, ChronoUnit.DAYS));
        this.forwardMonthButton.setDisable(!this.isValidDate(chrono, firstDayOfMonth, 1, ChronoUnit.MONTHS));
    }

    private String formatMonth(YearMonth yearMonth) {
        try {
            Chronology chrono = this.getPrimaryChronology();
            ChronoLocalDate cDate = chrono.date(yearMonth.atDay(1));
            return this.monthFormatter.withLocale(this.getLocale()).withChronology(chrono).format(cDate);
        } catch (DateTimeException var4) {
            return "";
        }
    }

    private String formatYear(YearMonth yearMonth) {
        try {
            Chronology chrono = this.getPrimaryChronology();
            ChronoLocalDate cDate = chrono.date(yearMonth.atDay(1));
            return this.yearFormatter.withLocale(this.getLocale()).withChronology(chrono).withDecimalStyle(DecimalStyle.of(this.getLocale())).format(cDate);
        } catch (DateTimeException var4) {
            return "";
        }
    }

    protected LocalDate dayCellDate(DateCell dateCell) {
        assert this.dayCellDates != null;

        return this.dayCellDates[this.dayCells.indexOf(dateCell)];
    }

    protected void forward(int offset, ChronoUnit unit, boolean focusDayCell, boolean withAnimation) {
        if (withAnimation && (this.tempImageTransition == null || this.tempImageTransition.getStatus() == Status.STOPPED)) {
            Pane monthContent = (Pane) this.calendarPlaceHolder.getChildren().get(0);
            this.getParent().setManaged(false);
            SnapshotParameters snapShotparams = new SnapshotParameters();
            snapShotparams.setFill(Color.TRANSPARENT);
            WritableImage temp = monthContent.snapshot(snapShotparams, new WritableImage((int) monthContent.getWidth(), (int) monthContent.getHeight()));
            ImageView tempImage = new ImageView(temp);
            this.calendarPlaceHolder.getChildren().add(this.calendarPlaceHolder.getChildren().size() - 2, tempImage);
            TranslateTransition imageTransition = new TranslateTransition(Duration.millis(160.0F), tempImage);
            imageTransition.setToX((double) (-offset) * this.calendarPlaceHolder.getWidth());
            imageTransition.setOnFinished((finish) -> this.calendarPlaceHolder.getChildren().remove(tempImage));
            monthContent.setTranslateX((double) offset * this.calendarPlaceHolder.getWidth());
            TranslateTransition contentTransition = new TranslateTransition(Duration.millis(160.0F), monthContent);
            contentTransition.setToX(0.0F);
            this.tempImageTransition = new ParallelTransition(imageTransition, contentTransition);
            this.tempImageTransition.setOnFinished((finish) -> {
                this.calendarPlaceHolder.getChildren().remove(tempImage);
                this.getParent().setManaged(true);
            });
            this.tempImageTransition.play();
        }

        YearMonth yearMonth = this.selectedYearMonth.get();
        DateCell dateCell = this.currentFocusedDayCell;
        if (dateCell == null || this.dayCellDate(dateCell).getMonth() != yearMonth.getMonth()) {
            dateCell = this.findDayCellOfDate(yearMonth.atDay(1));
        }

        this.goToDayCell(dateCell, offset, unit, focusDayCell);
    }

    private void goToDayCell(DateCell dateCell, int offset, ChronoUnit unit, boolean focusDayCell) {
        this.goToDate(this.dayCellDate(dateCell).plus(offset, unit), focusDayCell);
    }

    private void goToDate(LocalDate date, boolean focusDayCell) {
        if (this.isValidDate(this.datePicker.getChronology(), date)) {
            this.valueProperty.set(date);
            this.selectedYearMonth.set(YearMonth.from(date));
            if (focusDayCell) {
                this.findDayCellOfDate(date).requestFocus();
            }
        }

    }

    private void selectDayCell(DateCell dateCell) {
        this.valueProperty.set(this.dayCellDate(dateCell));
    }

    private DateCell findDayCellOfDate(LocalDate date) {
        for (int i = 0; i < this.dayCellDates.length; ++i) {
            if (date.equals(this.dayCellDates[i])) {
                return this.dayCells.get(i);
            }
        }

        return this.dayCells.get(this.dayCells.size() / 2 + 1);
    }

    void init() {
        this.contentGrid.setOpacity(1.0F);
        this.yearsListView.setOpacity(0.0F);
        this.yearsListView.setVisible(false);
    }

    void clearFocus() {
        LocalDate focusDate = this.valueProperty.get();
        if (focusDate == null) {
            focusDate = LocalDate.now();
        }

        if (YearMonth.from(focusDate).equals(this.selectedYearMonth.get())) {
            this.goToDate(focusDate, true);
        }

    }

    protected void createDayCells() {
        for (int row = 0; row < 6; ++row) {
            for (int col = 0; col < this.daysPerWeek; ++col) {
                DateCell dayCell = this.createDayCell();
                dayCell.addEventHandler(MouseEvent.MOUSE_CLICKED, (click) -> {
                    if (click.getButton() == MouseButton.PRIMARY) {
                        DateCell selectedDayCell = (DateCell) click.getSource();
                        this.selectDayCell(selectedDayCell);
                    }
                });
                dayCell.setOnMouseEntered((event) -> dayCell.getStyleClass().add("hovered"));
                dayCell.setOnMouseExited((event) -> dayCell.getStyleClass().removeAll("hovered"));
                dayCell.setAlignment(Pos.BASELINE_CENTER);
                dayCell.setBorder(new Border(new BorderStroke(Color.TRANSPARENT, BorderStrokeStyle.SOLID, CornerRadii.EMPTY, new BorderWidths(5.0F))));
                dayCell.setFont(Font.font(ROBOTO, FontWeight.BOLD, 12.0F));
                this.dayCells.add(dayCell);
            }
        }

        this.dayCellDates = new LocalDate[6 * this.daysPerWeek];
        this.updateContentGrid();
    }

    private DateCell createDayCell() {
        DateCell dayCell = null;
        if (this.datePicker.getDayCellFactory() != null) {
            dayCell = this.datePicker.getDayCellFactory().call(this.datePicker);
        }

        if (dayCell == null) {
            dayCell = new DateCell();
        }
        dayCell.getStyleClass().add("day-cell");
        dayCell.setPrefSize(36.0F, 36.0F);
        dayCell.setDisable(false);
        dayCell.setStyle(null);
        dayCell.setGraphic(null);
        dayCell.setTooltip(null);

        return dayCell;
    }

    protected Chronology getPrimaryChronology() {
        return this.datePicker.getChronology();
    }

    protected Locale getLocale() {
        return ConfigHolder.config().getLocalization().getLocale();
    }

    protected boolean isValidDate(Chronology chrono, LocalDate date, int offset, ChronoUnit unit) {
        return date != null && this.isValidDate(chrono, date.plus(offset, unit));
    }

    protected boolean isValidDate(Chronology chrono, LocalDate date) {
        try {
            if (date != null) {
                chrono.date(date);
            }

            return true;
        } catch (DateTimeException var4) {
            return false;
        }
    }
}
