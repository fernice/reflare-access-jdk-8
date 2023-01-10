/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.fernice.reflare.internal.impl;

import java.awt.Component;
import java.awt.Window;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import javax.swing.JRootPane;
import javax.swing.Popup;
import javax.swing.PopupFactory;
import javax.swing.RootPaneContainer;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.jetbrains.annotations.NotNull;

public final class ScreenPopupFactory extends PopupFactory {

    private static final Float TRANSLUCENT = 245f / 255f;
    private static final Float OPAQUE = 1f;

    private static final @NotNull Method heavyWeightPopupFactoryMethod;

    static {
        try {
            heavyWeightPopupFactoryMethod = AccessController.doPrivileged((PrivilegedExceptionAction<Method>) () -> {
                Method method = PopupFactory.class.getDeclaredMethod("getHeavyWeightPopup", Component.class, Component.class, int.class, int.class);
                method.setAccessible(true);
                return method;
            });
        } catch (PrivilegedActionException e) {
            throw new IllegalStateException("failed to access necessary resources for ScreenPopupFactory", e);
        }
    }

    private boolean active = true;

    public void setActive(boolean var1) {
        this.active = var1;
    }

    private static Window getWindow(Component c) {
        Component w = c;
        while (!(w instanceof Window) && (w != null)) {
            w = w.getParent();
        }
        return (Window) w;
    }

    private Popup getHeavyWeightPopup(Component comp, Component invoker, int x, int y) {
        try {
            return (Popup) heavyWeightPopupFactoryMethod.invoke(this, comp, invoker, x, y);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("failed to invoke PopupFactory for heavy weight popup", e);
        }
    }

    public Popup getPopup(Component comp, Component invoker, int x, int y) {
        if (invoker == null) {
            throw new IllegalArgumentException("Popup.getPopup must be passed non-null contents");
        }

        final Popup popup;
        if (active) {
            popup = getHeavyWeightPopup(comp, invoker, x, y);
        } else {
            popup = super.getPopup(comp, invoker, x, y);
        }

        // Make the popup semi-translucent if it is a heavy weight
        // see <rdar://problem/3547670> JPopupMenus have incorrect background
        final Window w = getWindow(invoker);
        if (w == null) {
            return popup;
        }

        if (!(w instanceof RootPaneContainer)) {
            return popup;
        }
        final JRootPane popupRootPane = ((RootPaneContainer) w).getRootPane();

        // we need to set every time, because PopupFactory caches the heavy weight
        // TODO: CPlatformWindow constants?
        if (active) {
            popupRootPane.putClientProperty("Window.alpha", OPAQUE);
            popupRootPane.putClientProperty("Window.shadow", Boolean.TRUE);
            popupRootPane.putClientProperty("apple.awt._windowFadeDelegate", invoker);

            w.setBackground(UIManager.getColor("PopupMenu.translucentBackground"));
            popupRootPane.putClientProperty("apple.awt.draggableWindowBackground", Boolean.FALSE);
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    popupRootPane.putClientProperty("apple.awt.windowShadow.revalidateNow", Math.random());
                }
            });
        } else {
            popupRootPane.putClientProperty("Window.alpha", OPAQUE);
            popupRootPane.putClientProperty("Window.shadow", Boolean.FALSE);
        }

        return popup;
    }
}
