package org.jackhuang.hmcl.ui;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;

public class CommonListPage<T> extends ListPageBase<T> {
    private CellMenuRequestSupportType cellMenuRequestSupportType = CellMenuRequestSupportType.SINGLE;

    public CommonListPage() {
        super();
    }

    private ObjectProperty<EventHandler<CellMenuRequestEvent<?>>> onSingleCellMenuRequest;

    public ObjectProperty<EventHandler<CellMenuRequestEvent<?>>> onSingleCellMenuRequestProperty() {
        if (onSingleCellMenuRequest == null) {
            onSingleCellMenuRequest = new SimpleObjectProperty<>(this, "onSingleCellMenuRequest") {
                @Override
                protected void invalidated() {
                    setEventHandler(CellMenuRequestEvent.SINGLE_CELL, get());
                }
            };

        }
        return onSingleCellMenuRequest;
    }

    public void setOnSingleCellMenuRequest(EventHandler<CellMenuRequestEvent<?>> onSingleCellMenuRequest) {
        onSingleCellMenuRequestProperty().set(onSingleCellMenuRequest);
    }

    private ObjectProperty<EventHandler<CellMenuRequestEvent<?>>> onMutiCellMenuRequest;

    public ObjectProperty<EventHandler<CellMenuRequestEvent<?>>> onMutiCellMenuRequestProperty() {
        if (onMutiCellMenuRequest == null) {
            onMutiCellMenuRequest = new SimpleObjectProperty<>(this, "onMutiCellMenuRequest") {
                @Override
                protected void invalidated() {
                    setEventHandler(CellMenuRequestEvent.MULTIPLE_CELL, get());
                }
            };
        }
        return onMutiCellMenuRequest;
    }

    public void setOnMutiCellMenuRequest(EventHandler<CellMenuRequestEvent<?>> onMutiCellMenuRequest) {
        onMutiCellMenuRequestProperty().set(onMutiCellMenuRequest);
    }

    public void setCellMenuRequestSupportType(CellMenuRequestSupportType cellMenuRequestSupportType) {
        this.cellMenuRequestSupportType = cellMenuRequestSupportType;
    }

    public CellMenuRequestSupportType getCellMenuRequestSupportType() {
        return cellMenuRequestSupportType;
    }

    public static class CellMenuRequestEvent<T> extends Event {

        public static final EventType<CellMenuRequestEvent<?>> ANY =
                new EventType<>(Event.ANY, "CELL_MENU_REQUEST");

        public static final EventType<CellMenuRequestEvent<?>> SINGLE_CELL =
                new EventType<>(ANY, "SINGLE_CELL");

        public static final EventType<CellMenuRequestEvent<?>> MULTIPLE_CELL =
                new EventType<>(ANY, "MULTIPLE_CELL");

        private final ListCell<T> listCell;
        private final ListView<T> listView;

        public CellMenuRequestEvent(EventType<? extends CellMenuRequestEvent<?>> eventType, ListCell<T> listCell, ListView<T> listView) {
            super(eventType);
            this.listCell = listCell;
            this.listView = listView;
        }

        public ListCell<T> getListCell() {
            return listCell;
        }

        public ListView<T> getListView() {
            return listView;
        }
    }

    public enum CellMenuRequestSupportType {
        SINGLE,
        MULTIPLE,
        BOTH
    }
}
