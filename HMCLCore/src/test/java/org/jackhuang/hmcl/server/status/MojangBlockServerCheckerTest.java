// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.server.status;

import org.jackhuang.hmcl.util.DigestUtils;
import org.jackhuang.hmcl.util.HostAndPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@Execution(ExecutionMode.SAME_THREAD)
class MojangBlockServerCheckerTest {

    private static String hash(String server) {
        return DigestUtils.digestToString(MojangBlockServerChecker.SHA_1, server.toLowerCase(Locale.ROOT)
                .getBytes(StandardCharsets.ISO_8859_1)
        );
    }

    private static void assertBlocked(HostAndPort hostAndPort) {
        assertThrows(MojangBlockServerChecker.ServerBlockedByMojangException.class,
                () -> MojangBlockServerChecker.check(hostAndPort));
    }

    private static void assertNotBlocked(HostAndPort hostAndPort) {
        assertDoesNotThrow(() -> MojangBlockServerChecker.check(hostAndPort));
    }

    private static void assertBlocked(InetSocketAddress address) {
        assertThrows(MojangBlockServerChecker.ServerBlockedByMojangException.class,
                () -> MojangBlockServerChecker.check(address));
    }

    private static void assertNotBlocked(InetSocketAddress address) {
        assertDoesNotThrow(() -> MojangBlockServerChecker.check(address));
    }

    @BeforeEach
    void setUp() {
        MojangBlockServerChecker.setTestCheckContent(Set.of());
    }

    @AfterEach
    void tearDown() {
        MojangBlockServerChecker.setTestCheckContent(Set.of());
    }

    @Test
    void testIsIPv4() {
        assertAll(
                () -> assertTrue(MojangBlockServerChecker.isIPv4(new String[]{"1", "2", "3", "4"})),
                () -> assertTrue(MojangBlockServerChecker.isIPv4(new String[]{"0", "0", "0", "0"})),
                () -> assertTrue(MojangBlockServerChecker.isIPv4(new String[]{"255", "255", "255", "255"})),

                () -> assertFalse(MojangBlockServerChecker.isIPv4(new String[]{"1", "2", "3"})),
                () -> assertFalse(MojangBlockServerChecker.isIPv4(new String[]{"1", "2", "3", "4", "5"})),
                () -> assertFalse(MojangBlockServerChecker.isIPv4(new String[]{})),

                () -> assertFalse(MojangBlockServerChecker.isIPv4(new String[]{"256", "1", "1", "1"})),
                () -> assertFalse(MojangBlockServerChecker.isIPv4(new String[]{"-1", "1", "1", "1"})),
                () -> assertFalse(MojangBlockServerChecker.isIPv4(new String[]{"a", "1", "1", "1"})),
                () -> assertFalse(MojangBlockServerChecker.isIPv4(new String[]{"", "1", "1", "1"}))
        );
    }

    @Test
    void testHashHelperIsCaseInsensitive() {
        assertEquals(hash("EXAMPLE.COM"), hash("example.com"));
    }

    @Test
    void testSpecifiedExactMatch() {
        MojangBlockServerChecker.setTestCheckContent(Set.of(hash("example.com")));

        assertBlocked(new HostAndPort("example.com", 25565));
        assertBlocked(new HostAndPort("Example.COM", 25565));
        assertBlocked(new HostAndPort("example.com.", 25565));

        assertNotBlocked(new HostAndPort("a.example.com.", 25565));
        assertNotBlocked(new HostAndPort("example.com.evil.org", 25565));
        assertNotBlocked(new HostAndPort("notexample.com", 25565));
    }

    @Test
    void testPortIsIrrelevant() {
        MojangBlockServerChecker.setTestCheckContent(Set.of(hash("example.com")));
        assertBlocked(new HostAndPort("example.com", 1));
        assertBlocked(new HostAndPort("example.com", 65535));
    }

    @Test
    void testMinecraftSrvPrefixViaHostAndPort() {
        MojangBlockServerChecker.setTestCheckContent(Set.of(hash("example.com")));

        assertBlocked(new HostAndPort("_minecraft._tcp.example.com", 25565));
        assertNotBlocked(new HostAndPort("_minecraft._tcp.a.example.com.", 25565));
    }

    @Test
    void testWildcardMatchesRootDomain_currentBehavior() {
        MojangBlockServerChecker.setTestCheckContent(Set.of(hash("*.example.com")));
        assertBlocked(new HostAndPort("example.com", 25565));
    }

    @Test
    void testWildcardMatchesSubdomain() {
        MojangBlockServerChecker.setTestCheckContent(Set.of(hash("*.example.com")));

        assertBlocked(new HostAndPort("sub.example.com", 25565));
        assertBlocked(new HostAndPort("a.b.example.com", 25565));
        assertBlocked(new HostAndPort("SUB.Example.COM.", 25565));
    }

    @Test
    void testWildcardDoesNotMatchSimilarSuffix() {
        MojangBlockServerChecker.setTestCheckContent(Set.of(hash("*.example.com")));

        assertNotBlocked(new HostAndPort("notexample.com", 25565));
        assertNotBlocked(new HostAndPort("example.com.evil.org", 25565));
        assertNotBlocked(new HostAndPort("example.org", 25565));
    }

    @Test
    void testIpv4Exact() {
        MojangBlockServerChecker.setTestCheckContent(Set.of(hash("192.168.1.1")));
        assertBlocked(new HostAndPort("192.168.1.1", 25565));
    }

    @Test
    void testIpv4Wildcard() {
        MojangBlockServerChecker.setTestCheckContent(Set.of(hash("192.168.*")));

        assertBlocked(new HostAndPort("192.168.1.1", 25565));
        assertBlocked(new HostAndPort("192.168.255.255", 25565));
        assertNotBlocked(new HostAndPort("192.169.1.1", 25565));
    }

    @Test
    void testIpv4ShortWildcard() {
        MojangBlockServerChecker.setTestCheckContent(Set.of(hash("1.*")));
        assertBlocked(new HostAndPort("1.2.3.4", 25565));
    }

    @Test
    void testIpv4ShortWildcardNegative() {
        MojangBlockServerChecker.setTestCheckContent(Set.of(hash("1.*")));

        assertNotBlocked(new HostAndPort("2.3.4.5", 25565));
        assertNotBlocked(new HostAndPort("11.2.3.4", 25565));
    }

    @Test
    void testIdnHost() {
        MojangBlockServerChecker.setTestCheckContent(Set.of(hash("xn--bcher-kva.example")));
        assertBlocked(new HostAndPort("bücher.example", 25565));
    }

    @Test
    void testDotsOnlyHost() {
        MojangBlockServerChecker.setTestCheckContent(Set.of(hash("example.com")));
        // IDN.toASCII("...") 可能抛异常，也可能返回原字符串；
        // 无论哪种路径，最终都不应被判定为 blocked。
        assertNotBlocked(new HostAndPort("...", 25565));
    }

    @Test
    void testInetSocketAddressBlockedByIp() throws Exception {
        MojangBlockServerChecker.setTestCheckContent(Set.of(hash("1.2.3.4")));
        InetAddress addr = InetAddress.getByAddress("some.host", new byte[]{1, 2, 3, 4});
        InetSocketAddress isa = new InetSocketAddress(addr, 25565);

        assertBlocked(isa);
    }

    @Test
    void testInetSocketAddressBlockedByHostname() throws Exception {
        MojangBlockServerChecker.setTestCheckContent(Set.of(hash("*.example.com")));
        InetAddress addr = InetAddress.getByAddress("sub.example.com", new byte[]{1, 2, 3, 4});
        InetSocketAddress isa = new InetSocketAddress(addr, 25565);

        assertBlocked(isa);
    }

    @Test
    void testInetSocketAddressWithMinecraftSrvPrefix() throws Exception {
        MojangBlockServerChecker.setTestCheckContent(Set.of(hash("example.com")));
        InetAddress addr = InetAddress.getByAddress("_minecraft._tcp.example.com", new byte[]{1, 2, 3, 4});
        InetSocketAddress isa = new InetSocketAddress(addr, 25565);

        assertBlocked(isa);
    }

    @Test
    void testUnresolvedInetSocketAddress_currentBehavior() {
        MojangBlockServerChecker.setTestCheckContent(Set.of(hash("example.com")));
        InetSocketAddress unresolved = InetSocketAddress.createUnresolved("example.com", 25565);

        assertNotBlocked(unresolved);
    }

    @Test
    void testMultipleRules() {
        MojangBlockServerChecker.setTestCheckContent(Set.of(
                hash("example.com"),
                hash("*.foo.org"),
                hash("10.0.*")
        ));

        assertBlocked(new HostAndPort("example.com", 25565));
        assertBlocked(new HostAndPort("a.foo.org", 25565));
        assertBlocked(new HostAndPort("10.0.1.1", 25565));
        assertNotBlocked(new HostAndPort("bar.org", 25565));
    }

    @Test
    void testEmptyCheckerNeverBlocks() throws Exception {
        MojangBlockServerChecker.setTestCheckContent(Set.of());

        assertNotBlocked(new HostAndPort("example.com", 25565));
        assertNotBlocked(new HostAndPort("1.2.3.4", 25565));

        InetAddress addr = InetAddress.getByAddress("example.com", new byte[]{1, 2, 3, 4});
        assertNotBlocked(new InetSocketAddress(addr, 25565));
    }
}
