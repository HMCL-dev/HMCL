/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com>
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
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hmcl.event;

import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.util.ToStringBuilder;

import java.util.Collection;
import java.util.Collections;

/**
 * This event gets fired when loading profiles.
 * <br>
 * This event is fired on the {@link org.jackhuang.hmcl.event.EventBus#EVENT_BUS}
 *
 * @author huangyuhui
 */
public class ProfileLoadingEvent extends Event {

    private final Collection<Profile> profiles;

    /**
     * Constructor.
     *
     * @param source {@link org.jackhuang.hmcl.setting.Settings}
     */
    public ProfileLoadingEvent(Object source, Collection<Profile> profiles) {
        super(source);

        this.profiles = Collections.unmodifiableCollection(profiles);
    }

    public Collection<Profile> getProfiles() {
        return profiles;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("source", source)
                .append("profiles", profiles)
                .toString();
    }
}

