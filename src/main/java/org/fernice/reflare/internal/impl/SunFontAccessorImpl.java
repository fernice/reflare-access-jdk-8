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

    private static final int MERGING_STRATEGY_COMBINE = 0;
    private static final int MERGING_STRATEGY_KEEP = 1;
    private static final int MERGING_STRATEGY_OVERRIDE = 2;

    private static final AtomicBoolean initialized = new AtomicBoolean();
    private static final CountDownLatch initializationLatch = new CountDownLatch(1);

    private static volatile Map<String, Set<FontPeer>> fontFamilies = new HashMap<>();

    private static final Set<Font> fontExtensions = new HashSet<>();
    private static final Set<Font> fontExtensionOverrides = new HashSet<>();

    @Nullable
    @Override
    public Font findFont(@NotNull String family, int weight, boolean italic) {
        if (!ensureInitialized(family)) {
            return null;
        }

        Set<FontPeer> fontFamily = fontFamilies.get(family.toLowerCase());

        if (fontFamily != null && !fontFamily.isEmpty()) {
            int weightDistance = Integer.MAX_VALUE;
            Font matchingFont = null;

            for (FontPeer font : fontFamily) {
                boolean fontItalic = font.font2D.getStyle() == 2 || font.font2D.getStyle() == 3;

                if (fontItalic != italic) {
                    continue;
                }

                int distance = Math.abs(weight - font.font2D.getWeight());

                if (distance < weightDistance) {
                    weightDistance = distance;
                    matchingFont = font.font;
                }
            }

            return matchingFont;
        } else {
            return null;
        }
    }

    private boolean ensureInitialized(@NotNull String family) {
        // Force the full initialization of the FontManager
        if (!initialized.getAndSet(true)) {
            new Font(family, Font.BOLD | Font.ITALIC, 12);

            refresh();

            initializationLatch.countDown();
        } else {
            try {
                initializationLatch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean registerFontExtension(@NotNull Font font, boolean override) {
        if (override) {
            fontExtensions.remove(font);
            if (!fontExtensionOverrides.add(font)) {
                return false;
            }
        } else {
            fontExtensionOverrides.remove(font);
            if (!fontExtensions.add(font)) {
                return false;
            }
        }
        return recomputeFontFamilies(font);
    }

    @Override
    public boolean unregisterFontExtension(@NotNull Font font) {
        boolean result = fontExtensions.remove(font);
        result |= fontExtensionOverrides.remove(font);
        return result;
    }

    @Override
    public void refresh() {
        recomputeFontFamilies(null);
    }

    private boolean recomputeFontFamilies(@Nullable Font target) {
        boolean targetResult;

        Map<String, Set<FontPeer>> fontFamilies = new HashMap<>();

        Font[] fonts = GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts();
        targetResult = mergeFontFamilies(fontFamilies, fonts, MERGING_STRATEGY_COMBINE, target);

        Font[] fontExtensions = SunFontAccessorImpl.fontExtensions.toArray(new Font[0]);
        targetResult |= mergeFontFamilies(fontFamilies, fontExtensions, MERGING_STRATEGY_KEEP, target);

        Font[] fontExtensionOverrides = SunFontAccessorImpl.fontExtensionOverrides.toArray(new Font[0]);
        targetResult |= mergeFontFamilies(fontFamilies, fontExtensionOverrides, MERGING_STRATEGY_OVERRIDE, target);

        SunFontAccessorImpl.fontFamilies = fontFamilies;

        return targetResult;
    }

    private boolean mergeFontFamilies(@NotNull Map<String, Set<FontPeer>> fontFamilies, @NotNull Font[] fonts, int mergingStrategy, @Nullable Font target) {
        boolean targetResult = false;

        for (Font font : fonts) {
            String familyName = font.getFamily().toLowerCase();

            Set<FontPeer> fontFamily = fontFamilies.computeIfAbsent(familyName, (s) -> new HashSet<>());
            targetResult |= mergeFontFamily(fontFamily, font, mergingStrategy) && font == target;

            for (String weightName : WEIGHT_NAMES) {
                int index = familyName.indexOf(weightName);

                if (index >= 0) {
                    String strippedFamilyName = familyName.substring(0, index).trim();

                    Set<FontPeer> additionalFontFamily = fontFamilies.computeIfAbsent(strippedFamilyName, (s) -> new HashSet<>());
                    targetResult |= mergeFontFamily(additionalFontFamily, font, mergingStrategy) && font == target;
                }
            }
        }

        return targetResult;
    }

    private boolean mergeFontFamily(@NotNull Set<FontPeer> fonts, @NotNull Font font, int mergingStrategy) {
        FontAccess fontAccess = FontAccess.getFontAccess();
        Font2D font2D = fontAccess.getFont2D(font);

        int fontWeight = font2D.getWeight();
        boolean fontItalic = font2D.getStyle() == 2 || font2D.getStyle() == 3;

        FontPeer matchingFont = null;
        for (FontPeer candidateFont : fonts) {
            Font2D candidateFont2D = candidateFont.font2D;

            int candidateFontWeight = candidateFont2D.getWeight();
            boolean candidateFontItalic = candidateFont2D.getStyle() == 2 || candidateFont2D.getStyle() == 3;

            if (candidateFontWeight == fontWeight && candidateFontItalic == fontItalic) {
                matchingFont = candidateFont;
                break;
            }
        }

        if (matchingFont != null) {
            if (mergingStrategy == MERGING_STRATEGY_KEEP) {
                return false;
            }

            if (mergingStrategy == MERGING_STRATEGY_OVERRIDE) {
                fonts.remove(matchingFont);
            }
        }
        fonts.add(new FontPeer(font, font2D));

        return true;
    }

    private static final class FontPeer {

        @NotNull
        final Font font;
        @NotNull
        final Font2D font2D;

        private FontPeer(@NotNull Font font, @NotNull Font2D font2D) {
            this.font = font;
            this.font2D = font2D;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != FontPeer.class) {
                return false;
            }
            FontPeer other = (FontPeer) obj;

            return other.font.getFamily().equalsIgnoreCase(font.getFamily()) && other.font2D.getStyle() == font2D.getStyle() &&
                    other.font2D.getWeight() == font2D.getWeight();
        }

        @Override
        public int hashCode() {
            return super.hashCode();
        }

        @NotNull
        @Override
        public String toString() {
            //            return "Font[" + font2D.toString() + " weight=" + font2D.getWeight() + "]";
            return "Font[family=" + font.getFamily() + " name=" + font.getName() + " style=" + font2D.getStyle() + " weight=" + font2D.getWeight() + "]";
        }
    }

    @Override
    public int getFontWeight(@NotNull Font font) {
        return FontAccess.getFontAccess().getFont2D(font).getWeight();
    }

    @Override
    public boolean isFontItalic(@NotNull Font font) {
        int style = FontAccess.getFontAccess().getFont2D(font).getStyle();
        return style == 2 || style == 3;
    }
}
