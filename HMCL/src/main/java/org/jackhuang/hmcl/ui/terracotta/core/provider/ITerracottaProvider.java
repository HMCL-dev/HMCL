package org.jackhuang.hmcl.ui.terracotta.core.provider;

import javafx.beans.property.DoubleProperty;
import org.jackhuang.hmcl.task.Task;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface ITerracottaProvider {
    boolean exist();

    Task<?> install(DoubleProperty progress) throws IOException;

    List<String> launch(Path path);
}
