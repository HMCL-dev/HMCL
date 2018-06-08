package org.jackhuang.hmcl.ui.construct;

import com.jfoenix.validation.base.ValidatorBase;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.TextInputControl;

import java.net.MalformedURLException;
import java.net.URL;

public class URLValidator extends ValidatorBase {

    private final ObservableList<String> protocols = FXCollections.observableArrayList();

    public URLValidator() {
        super();
    }

    public ObservableList<String> getProtocols() {
        return protocols;
    }

    @Override
    protected void eval() {
        if (srcControl.get() instanceof TextInputControl) {
            try {
                URL url = new URL(((TextInputControl) srcControl.get()).getText());
                if (protocols.isEmpty())
                    hasErrors.set(false);
                else
                    hasErrors.set(!protocols.contains(url.getProtocol()));
            } catch (MalformedURLException e) {
                hasErrors.set(true);
            }
        }
    }
}
