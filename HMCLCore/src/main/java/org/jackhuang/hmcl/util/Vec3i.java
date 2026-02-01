package org.jackhuang.hmcl.util;

public record Vec3i(int x, int y, int z) {

    public static Vec3i ZERO = new Vec3i(0, 0, 0);

}
