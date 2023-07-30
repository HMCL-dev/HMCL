package org.jackhuang.hmcl.util.gson;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

public class DateTypeAdapterTest {

    @Test
    public void parse() {
        assertEquals(
                LocalDateTime.of(2017, 6, 8, 4, 26, 33)
                        .atOffset(ZoneOffset.UTC).toInstant(),
                DateTypeAdapter.deserializeToDate("2017-06-08T04:26:33+0000").toInstant());

        assertEquals(
                LocalDateTime.of(2021, 1, 3, 0, 53, 34)
                        .atOffset(ZoneOffset.UTC).toInstant(),
                DateTypeAdapter.deserializeToDate("2021-01-03T00:53:34+00:00").toInstant());

        assertEquals(
                LocalDateTime.of(2021, 1, 3, 0, 53, 34)
                        .atZone(ZoneId.systemDefault()).toInstant(),
                DateTypeAdapter.deserializeToDate("2021-01-03T00:53:34").toInstant());
    }
}
