/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.game;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

@NotNullByDefault
public class HMCLGameInstance extends DefaultGameInstance {
    protected HMCLGameInstance(DefaultGameRepository.Status status, DefaultGameRepository repository, DefaultGameRepositoryLayout layout, GameInstanceID id, GameInstanceManifest manifest) {
        super(status, repository, layout, id, manifest);
    }

    @Override
    public HMCLGameRepository getRepository() {
        return (HMCLGameRepository) super.getRepository();
    }

    @NotNullByDefault
    public static final class Optional {
        private final HMCLGameRepository repository;
        private final @Nullable HMCLGameInstance instance;

        public Optional(HMCLGameRepository repository) {
            this.repository = repository;
            this.instance = null;
        }

        public Optional(HMCLGameInstance instance) {
            this.repository = instance.getRepository();
            this.instance = instance;
        }

        public HMCLGameRepository repository() {
            return repository;
        }

        @Contract(pure = true)
        public @Nullable HMCLGameInstance instance() {
            return instance;
        }

        @Contract(pure = true)
        public @Nullable GameInstanceID instanceId() {
            return instance != null ? instance.getId() : null;
        }

        public boolean isPresent() {
            return instance != null;
        }

        public boolean isEmpty() {
            return instance == null;
        }
    }
}
