package org.jackhuang.hmcl.game;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import org.jackhuang.hmcl.download.DefaultCacheRepository;

import java.nio.file.Paths;

public class HMCLCacheRepository extends DefaultCacheRepository {

    private final StringProperty directory = new SimpleStringProperty();

    public HMCLCacheRepository() {
        directory.addListener((a, b, t) -> changeDirectory(Paths.get(t)));
    }

    public String getDirectory() {
        return directory.get();
    }

    public StringProperty directoryProperty() {
        return directory;
    }

    public void setDirectory(String directory) {
        this.directory.set(directory);
    }

    public static final HMCLCacheRepository REPOSITORY = new HMCLCacheRepository();
}
