package org.fernice.reflare.internal.impl;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Locale;
import java.util.Map;
import org.fernice.reflare.internal.AATextInfoHelper.AATextInfoAccessor;
import sun.security.action.GetPropertyAction;
import sun.swing.SwingUtilities2;

public class AATextInfoAccessorImpl implements AATextInfoAccessor {

    public static final Object AA_TEXT_INFO_KEY = SwingUtilities2.AA_TEXT_PROPERTY_KEY;

    @Override
    public void aaTextInfo(Map<Object, Object> defaults) {
        defaults.put(AA_TEXT_INFO_KEY, getAATextInfo());
    }

    public static Object getAATextInfo() {
        String language = Locale.getDefault().getLanguage();
        String desktop = AccessController.doPrivileged(new GetPropertyAction("sun.desktop"));

        boolean isCjkLocale = (Locale.CHINESE.getLanguage().equals(language) || Locale.JAPANESE.getLanguage().equals(language) ||
                Locale.KOREAN.getLanguage().equals(language));
        boolean isGnome = "gnome".equals(desktop);
        boolean isLocal = SwingUtilities2.isLocalDisplay();

        boolean setAA = isLocal && (!isGnome || !isCjkLocale);

        return SwingUtilities2.AATextInfo.getAATextInfo(setAA);
    }
}
