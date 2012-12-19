/*
 * Copyright (C) 2012 Benedict Lau
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.groundupworks.flyingphotobooth.arrangements;

import android.graphics.Canvas;
import android.graphics.Paint;
import com.groundupworks.flyingphotobooth.helpers.ImageHelper.Arrangement;

/**
 * Base class where other {@link Arrangement} implementations extend from.
 * 
 * @author Benedict Lau
 */
public abstract class BaseArrangement implements Arrangement {

    /**
     * Comic panel padding.
     */
    public static final int PHOTO_STRIP_PANEL_PADDING = 50;

    //
    // Private methods.
    //

    /**
     * Draws the outline of a rectangle.
     * 
     * @param canvas
     *            the canvas to draw on.
     * @param left
     *            the left side of the rectangle to be drawn.
     * @param top
     *            the top of the rectangle to be drawn.
     * @param right
     *            the right side of the rectangle to be drawn.
     * @param bottom
     *            the bottom of the rectangle to be drawn.
     * @param paint
     *            the {@link Paint} to use for drawing.
     */
    protected static void drawRectOutline(Canvas canvas, float left, float top, float right, float bottom, Paint paint) {
        canvas.drawLine(left, top, right, top, paint);
        canvas.drawLine(right, top, right, bottom, paint);
        canvas.drawLine(right, bottom, left, bottom, paint);
        canvas.drawLine(left, bottom, left, top, paint);
    }
}