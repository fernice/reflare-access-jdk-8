/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.fernice.reflare.internal.impl;

import java.lang.reflect.Constructor;
import java.security.AccessController;
import java.security.PrivilegedAction;
import javax.swing.PopupFactory;
import org.fernice.reflare.internal.PopupFactoryHelper.PopupFactoryAccessor;
import org.jetbrains.annotations.NotNull;

public final class PopupFactoryAccessorImpl implements PopupFactoryAccessor {

    @NotNull
    @Override
    public PopupFactory createScreenPopupFactory() {
        return AccessController.doPrivileged((PrivilegedAction<PopupFactory>) () -> {
            try {
                Class<?> screenPopupFactoryClass = Class.forName("com.apple.laf.ScreenPopupFactory");

                Constructor<?> constructor = screenPopupFactoryClass.getDeclaredConstructor();
                boolean wasAccessible = constructor.isAccessible();
                constructor.setAccessible(true);

                try {
                    return (PopupFactory) constructor.newInstance();
                } finally {
                    constructor.setAccessible(wasAccessible);
                }
            } catch (Exception e) {
                throw new RuntimeException("cannot create ScreenPopupFactory", e);
            }
        });
    }
}
