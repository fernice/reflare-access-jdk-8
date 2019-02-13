/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.fernice.reflare.internal.impl;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import org.fernice.reflare.internal.SunFontHelper.SunFontAccessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.font.Font2D;
import sun.font.FontAccess;

public class SunFontAccessorImpl implements SunFontAccessor {

    private static final String[] WEIGHT_NAMES = { //
            "light", "ultralight", "ultra light", "ultra-light", //
            "extralight", "extra light", "extra-light", //
            "demilight", "demi light", "demi-light", //
            "normal", "regular", "medium", //
            "bold", "heavy", "black", //
            "semibold", "semi bold", "semi-bold", //
            "demibold", "demi bold", "demi-bold", //
            "extrabold", "extra bold", "extra-bold", //
            "ultrabold", "ultra bold", "ultra-bold", //
    };

    private static final AtomicBoolean initialized = new AtomicBoolean();
    private static final CountDownLatch initializationLatch = new CountDownLatch(1);

    private static final Map<String, Set<Font>> fontFamilies = new HashMap<>();

    @Nullable
    @Override
    public Font findFont(@NotNull String family, int weight, boolean italic) {
        // Force the full initialization of the FontManager
        if (!initialized.getAndSet(true)) {
            new Font(family, Font.BOLD | Font.ITALIC, 12);

            Font[] fonts = GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts();

            for (Font font : fonts) {
                String familyName = font.getFamily().toLowerCase();

                Set<Font> fontFamily = fontFamilies.computeIfAbsent(familyName, (s) -> new HashSet<>());
                fontFamily.add(font);

                for (String weightName : WEIGHT_NAMES) {
                    int index = familyName.indexOf(weightName);

                    if (index >= 0) {
                        String strippedFamilyName = familyName.substring(0, index).trim();

                        Set<Font> additionalFontFamily = fontFamilies.computeIfAbsent(strippedFamilyName, (s) -> new HashSet<>());
                        additionalFontFamily.add(font);
                    }
                }
            }

            initializationLatch.countDown();
        } else {
            try {
                initializationLatch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
                return null;
            }
        }

        Set<Font> fontFamily = fontFamilies.get(family.toLowerCase());

        if (fontFamily != null && !fontFamily.isEmpty()) {
            int weightDistance = Integer.MAX_VALUE;
            Font font = null;

            for (Font f : fontFamily) {
                Font2D font2D = FontAccess.getFontAccess().getFont2D(f);

                boolean fontItalic = font2D.getStyle() == 2 || font2D.getStyle() == 3;

                if (fontItalic != italic) {
                    continue;
                }

                int distance = Math.abs(weight - font2D.getWeight());

                if (distance < weightDistance) {
                    weightDistance = distance;
                    font = f;
                }
            }

            return font;
        } else {
            return null;
        }
    }
}
