/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.fernice.reflare.internal.impl;

import java.awt.Image;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.fernice.reflare.internal.ImageHelper.ImageAccessor;

public class ImageAccessorImpl implements ImageAccessor {

    @Override
    public Image getMultiResolutionImageResource(String resource) throws IOException {
        return ImageIO.read(ImageAccessorImpl.class.getResourceAsStream(resource));
    }
}
