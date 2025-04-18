/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.fernice.reflare.internal.impl;

import org.fernice.reflare.internal.SunFontHelper.SunFontAccessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.font.CompositeFont;
import sun.font.Font2D;
import sun.font.FontAccess;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public class SunFontAccessorImpl implements SunFontAccessor {

    private static final String[] WEIGHT_NAMES = { //
            "thin", "light", "ultralight", "ultra light", "ultra-light", //
            "extralight", "extlt", "extra light", "extra-light", //
            "demilight", "demi light", "demi-light", //
            "normal", "regular", "medium", "med", //
            "bold", "heavy", "black", "blk", //
            "semibold", "sembd", "semi bold", "semi-bold", //
            "demibold", "demi", "demi bold", "demi-bold", //
            "extrabold", "extbd", "extra bold", "extra-bold", //
            "ultrabold", "ultra bold", "ultra-bold", //
    };

    private static final int MERGING_STRATEGY_COMBINE = 0;
    private static final int MERGING_STRATEGY_KEEP = 1;
    private static final int MERGING_STRATEGY_OVERRIDE = 2;

    private static final AtomicBoolean initialized = new AtomicBoolean();
    private static final CountDownLatch initializationLatch = new CountDownLatch(1);

    private static volatile Map<String, List<FontPeer>> fontFamilies = new HashMap<>();

    private static final Set<Font> fontExtensions = new HashSet<>();
    private static final Set<Font> fontExtensionOverrides = new HashSet<>();

    @Nullable
    @Override
    public Font findFont(@NotNull String family, int weight, boolean italic) {
        if (!ensureInitialized(family)) {
            return null;
        }

        List<FontPeer> fontFamily = fontFamilies.get(family.toLowerCase());

        if (fontFamily != null && !fontFamily.isEmpty()) {
            int weightDistance = Integer.MAX_VALUE;
            Font matchingFont = null;

            for (FontPeer font : fontFamily) {
                boolean fontItalic = font.font2D.getStyle() == 2 || font.font2D.getStyle() == 3;

                if (fontItalic != italic) {
                    continue;
                }

                int distance = Math.abs(weight - getFontWeight(font.font2D));

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

        Map<String, List<FontPeer>> fontFamilies = new HashMap<>();

        Font[] fonts = GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts();
        targetResult = mergeFontFamilies(fontFamilies, fonts, MERGING_STRATEGY_COMBINE, target);

        Font[] fontExtensions = SunFontAccessorImpl.fontExtensions.toArray(new Font[0]);
        targetResult |= mergeFontFamilies(fontFamilies, fontExtensions, MERGING_STRATEGY_KEEP, target);

        Font[] fontExtensionOverrides = SunFontAccessorImpl.fontExtensionOverrides.toArray(new Font[0]);
        targetResult |= mergeFontFamilies(fontFamilies, fontExtensionOverrides, MERGING_STRATEGY_OVERRIDE, target);

        SunFontAccessorImpl.fontFamilies = fontFamilies;

        return targetResult;
    }

    private boolean mergeFontFamilies(@NotNull Map<String, List<FontPeer>> fontFamilies, @NotNull Font[] fonts, int mergingStrategy, @Nullable Font target) {
        boolean targetResult = false;

        for (Font font : fonts) {
            String familyName = font.getFamily().toLowerCase();

            List<FontPeer> fontFamily = fontFamilies.computeIfAbsent(familyName, (s) -> new ArrayList<>());
            targetResult |= mergeFontFamily(fontFamily, font, mergingStrategy) && font == target;

            for (String weightName : WEIGHT_NAMES) {
                int index = familyName.indexOf(weightName);

                if (index >= 0) {
                    String strippedFamilyName = familyName.substring(0, index).trim();

                    List<FontPeer> additionalFontFamily = fontFamilies.computeIfAbsent(strippedFamilyName, (s) -> new ArrayList<>());
                    targetResult |= mergeFontFamily(additionalFontFamily, font, mergingStrategy) && font == target;
                }
            }
        }

        return targetResult;
    }

    private boolean mergeFontFamily(@NotNull List<FontPeer> fonts, @NotNull Font font, int mergingStrategy) {
        FontAccess fontAccess = FontAccess.getFontAccess();
        Font2D font2D = fontAccess.getFont2D(font);

        int fontWeight = getFontWeight(font2D);
        boolean fontItalic = font2D.getStyle() == 2 || font2D.getStyle() == 3;

        while (true) {
            FontPeer matchingFont = null;
            for (FontPeer candidateFont : fonts) {
                Font2D candidateFont2D = candidateFont.font2D;

                int candidateFontWeight = getFontWeight(candidateFont2D);
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
                    continue;
                }
            }
            break;
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

        @NotNull
        @Override
        public String toString() {
            //            return "Font[" + font2D.toString() + " weight=" + font2D.getWeight() + "]";
            return "Font[family=" + font.getFamily() + " name=" + font.getName() + " style=" + font2D.getStyle() + " weight=" + getFontWeight(font2D) + "]";
        }
    }

    @Override
    public int getFontWeight(@NotNull Font font) {
        Font2D font2D = FontAccess.getFontAccess().getFont2D(font);
        return getFontWeight(font2D);
    }

    @Override
    public boolean isFontItalic(@NotNull Font font) {
        Font2D font2D = FontAccess.getFontAccess().getFont2D(font);
        int style = font2D.getStyle();
        return style == 2 || style == 3;
    }

    private static int getFontWeight(@NotNull Font2D font2D) {
        // CompositeFonts do not propagate the weight of their
        // slot fonts, not even the primary slot font, which
        // makes every CompositeFont either 400 or 700 in weight.
        if (font2D instanceof CompositeFont) {
            CompositeFont compositeFont = (CompositeFont) font2D;

            return getFontWeight(compositeFont.getSlotFont(0));
        }
        return font2D.getWeight();
    }
}
