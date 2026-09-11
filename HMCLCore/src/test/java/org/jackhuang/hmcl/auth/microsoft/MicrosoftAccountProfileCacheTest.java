/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026  huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.auth.microsoft;

import org.jackhuang.hmcl.auth.AccountID;
import org.jackhuang.hmcl.auth.AuthenticationException;
import org.jackhuang.hmcl.auth.OAuth;
import org.jackhuang.hmcl.auth.ServerDisconnectException;
import org.jackhuang.hmcl.util.io.ResponseCodeException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/// Unit tests for the MicrosoftAccount Minecraft Services profile cache, its
/// in-flight deduplication, and its 429 cooldown.
public final class MicrosoftAccountProfileCacheTest {

    private static MicrosoftSession newSession() {
        return new MicrosoftSession(
                "Bearer", "access-token", Long.MAX_VALUE, "refresh-token",
                new MicrosoftSession.User("user-id"),
                new MicrosoftSession.GameProfile(UUID.randomUUID(), "Player"));
    }

    private static MicrosoftService.MinecraftProfileResponse newProfile() {
        MicrosoftService.MinecraftProfileResponse profile = new MicrosoftService.MinecraftProfileResponse();
        profile.id = UUID.randomUUID();
        profile.name = "Player";
        profile.skins = List.of();
        profile.capes = List.of();
        return profile;
    }

    /// Stubs the network layer so cache/cooldown behavior can be exercised without HTTP.
    private static final class StubService extends MicrosoftService {
        final MicrosoftService.MinecraftProfileResponse profile;
        final AtomicInteger attempts = new AtomicInteger();
        volatile boolean rateLimited;
        volatile long fetchDelayMillis;

        StubService(MicrosoftService.MinecraftProfileResponse profile) {
            super(new OAuth.Callback() {
                @Override
                public OAuth.Session startServer() {
                    return null;
                }

                @Override
                public void grantDeviceCode(String userCode, String verificationURI) {
                }

                @Override
                public void loginCompletedDeviceCode() {
                }

                @Override
                public void openBrowser(OAuth.GrantFlow grantFlow, String url) {
                }

                @Override
                public String getClientId() {
                    return "test-client-id";
                }
            });
            this.profile = profile;
        }

        @Override
        public boolean validate(long notAfter, String tokenType, String accessToken) {
            return true;
        }

        @Override
        public Optional<MicrosoftService.MinecraftProfileResponse> getCompleteProfile(String authorization) throws AuthenticationException {
            attempts.incrementAndGet();
            if (fetchDelayMillis > 0) {
                try {
                    Thread.sleep(fetchDelayMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new ServerDisconnectException(e);
                }
            }
            if (rateLimited) {
                throw new ServerDisconnectException(new ResponseCodeException("https://api.minecraftservices.com/minecraft/profile", 429));
            }
            return Optional.ofNullable(profile);
        }
    }

    @Test
    public void testProfileIsCachedWithinTtl() throws Exception {
        StubService service = new StubService(newProfile());
        MicrosoftAccount account = new MicrosoftAccount(AccountID.generate(), service, newSession());

        assertEquals(0, account.getCapes().size());
        assertEquals(0, account.getCapes().size());
        assertEquals(1, service.attempts.get());
    }

    @Test
    public void testConcurrentLoadsShareOneRequest() throws Exception {
        StubService service = new StubService(newProfile());
        service.fetchDelayMillis = 150;
        MicrosoftAccount account = new MicrosoftAccount(AccountID.generate(), service, newSession());

        CyclicBarrier barrier = new CyclicBarrier(2);
        AtomicInteger failures = new AtomicInteger();
        Runnable task = () -> {
            try {
                barrier.await();
                account.getCapes();
            } catch (Exception e) {
                failures.incrementAndGet();
            }
        };

        Thread t1 = new Thread(task);
        Thread t2 = new Thread(task);
        t1.start();
        t2.start();
        t1.join();
        t2.join();

        assertEquals(0, failures.get());
        assertEquals(1, service.attempts.get());
    }

    @Test
    public void testRateLimitWithoutCacheEntersCooldown() throws Exception {
        StubService service = new StubService(newProfile());
        service.rateLimited = true;
        MicrosoftAccount account = new MicrosoftAccount(AccountID.generate(), service, newSession());

        assertThrows(MicrosoftService.MinecraftServicesRateLimitException.class, account::getCapes);
        assertEquals(1, service.attempts.get());

        // Still within the cooldown: no further request is attempted.
        assertThrows(MicrosoftService.MinecraftServicesRateLimitException.class, account::getCapes);
        assertEquals(1, service.attempts.get());
    }

    @Test
    public void testFreshCacheIsUsedDuringRateLimit() throws Exception {
        StubService service = new StubService(newProfile());
        MicrosoftAccount account = new MicrosoftAccount(AccountID.generate(), service, newSession());

        account.getCapes();
        assertEquals(1, service.attempts.get());

        // Even if the server would now rate-limit, the fresh cache short-circuits.
        service.rateLimited = true;
        assertEquals(0, account.getCapes().size());
        assertEquals(1, service.attempts.get());
    }
}
