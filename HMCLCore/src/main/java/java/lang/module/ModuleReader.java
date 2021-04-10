package java.lang.module;

import java.io.Closeable;
import java.io.IOException;
import java.util.stream.Stream;

/**
 * Dummy java compatibility class
 *
 * @author xxDark
 */
public interface ModuleReader extends Closeable {
    //CHECKSTYLE:OFF
    Stream<String> list() throws IOException;
    @Override
    void close() throws IOException;
    //CHECKSTYLE:ON
}
