package org.fernice.reflare.internal.impl;

import javax.swing.JComponent;
import javax.swing.plaf.ComponentUI;
import org.fernice.reflare.internal.DefaultLookup;
import org.fernice.reflare.internal.DefaultLookupDelegate;
import org.fernice.reflare.internal.DefaultLookupHelper.DefaultLookupAccessor;

public class DefaultLookupAccessorImpl implements DefaultLookupAccessor {

    @Override
    public void setDefaultLookup(DefaultLookup defaultLookup) {
        sun.swing.DefaultLookup.setDefaultLookup(new DefaultLookupWrapper(defaultLookup));
    }

    private static final class DefaultLookupWrapper extends sun.swing.DefaultLookup {

        private final DefaultLookup lookup;
        private final DefaultLookupDelegate delegate;

        DefaultLookupWrapper(DefaultLookup lookup) {
            this.lookup = lookup;
            this.delegate = super::getDefault;
        }

        @Override
        public Object getDefault(JComponent c, ComponentUI ui, String key) {
            return lookup.getDefault(c, ui, key, delegate);
        }
    }
}
