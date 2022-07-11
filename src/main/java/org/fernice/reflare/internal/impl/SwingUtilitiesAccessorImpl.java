package org.fernice.reflare.internal.impl;

import java.awt.FontMetrics;
import java.awt.Graphics;
import javax.swing.JComponent;
import org.fernice.reflare.internal.SwingUtilitiesHelper.SwingUtilitiesAccessor;
import sun.swing.SwingUtilities2;

public class SwingUtilitiesAccessorImpl implements SwingUtilitiesAccessor {

    @Override
    public Object getBASICMENUITEMUI_MAX_TEXT_OFFSET() {
        return SwingUtilities2.BASICMENUITEMUI_MAX_TEXT_OFFSET;
    }

    @Override
    public FontMetrics getFontMetrics(JComponent var0, Graphics var1) {
        return SwingUtilities2.getFontMetrics(var0, var1);
    }

    @Override
    public void drawString(JComponent c, Graphics g, String text, int x, int y) {
        SwingUtilities2.drawString(c, g, text, x, y);
    }

    @Override
    public void drawStringUnderlineCharAt(JComponent var0, Graphics var1, String var2, int var3, int var4, int var5) {
        SwingUtilities2.drawStringUnderlineCharAt(var0, var1, var2, var3, var4, var5);
    }
}
