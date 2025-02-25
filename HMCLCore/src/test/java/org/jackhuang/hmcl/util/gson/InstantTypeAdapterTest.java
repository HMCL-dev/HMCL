package org.jackhuang.hmcl.util.gson;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

public class InstantTypeAdapterTest {

    @Test
    public void testDeserialize() {
        assertEquals(
                LocalDateTime.of(2017, 6, 8, 4, 26, 33)
                        .atOffset(ZoneOffset.UTC).toInstant(),
                InstantTypeAdapter.deserializeToInstant("2017-06-08T04:26:33+0000"));

        assertEquals(
                LocalDateTime.of(2021, 1, 3, 0, 53, 34)
                        .atOffset(ZoneOffset.UTC).toInstant(),
                InstantTypeAdapter.deserializeToInstant("2021-01-03T00:53:34+00:00"));

        assertEquals(
                LocalDateTime.of(2021, 1, 3, 0, 53, 34)
                        .atZone(ZoneId.systemDefault()).toInstant(),
                InstantTypeAdapter.deserializeToInstant("2021-01-03T00:53:34"));
    }

    @Test
    public void testSerialize() {
        assertEquals(
                "2024-02-13T15:11:06+08:00",
                InstantTypeAdapter.serializeToString(Instant.ofEpochMilli(1707808266154L), ZoneOffset.ofHours(8))
        );
    }
}
