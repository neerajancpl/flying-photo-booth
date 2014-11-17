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
package com.groundupworks.flyingphotobooth.controllers;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.os.Bundle;
import android.os.Message;

import com.groundupworks.flyingphotobooth.MyApplication;
import com.groundupworks.flyingphotobooth.R;
import com.groundupworks.flyingphotobooth.fragments.ShareFragment;
import com.groundupworks.lib.photobooth.arrangements.BoxArrangement;
import com.groundupworks.lib.photobooth.arrangements.HorizontalArrangement;
import com.groundupworks.lib.photobooth.arrangements.VerticalArrangement;
import com.groundupworks.lib.photobooth.filters.BlackAndWhiteFilter;
import com.groundupworks.lib.photobooth.filters.LineArtFilter;
import com.groundupworks.lib.photobooth.filters.SepiaFilter;
import com.groundupworks.lib.photobooth.framework.BaseController;
import com.groundupworks.lib.photobooth.helpers.ImageHelper;
import com.groundupworks.lib.photobooth.helpers.ImageHelper.Arrangement;
import com.groundupworks.lib.photobooth.helpers.ImageHelper.ImageFilter;
import com.groundupworks.wings.Wings;
import com.groundupworks.wings.dropbox.DropboxEndpoint;
import com.groundupworks.wings.facebook.FacebookEndpoint;
import com.groundupworks.wings.gcp.GoogleCloudPrintEndpoint;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Controller class for the {@link ShareFragment}.
 *
 * @author Benedict Lau
 */
public class ShareController extends BaseController {

    //
    // Controller events. The ui should be notified of these events.
    //

    public static final int ERROR_OCCURRED = -1;

    public static final int THUMB_READY = 0;

    public static final int JPEG_SAVED = 1;

    public static final int GCP_SHARE_MARKED = 2;

    public static final int FACEBOOK_SHARE_MARKED = 3;

    public static final int DROPBOX_SHARE_MARKED = 4;

    private String mJpegPath = null;

    private Bitmap mThumb = null;

    private boolean mIsGcpShareActive = true;

    private boolean mIsFacebookShareActive = true;

    private boolean mIsDropboxShareActive = true;

    //
    // BaseController implementation.
    //

    @Override
    protected void handleEvent(Message msg) {
        final Context context = MyApplication.getContext();
        switch (msg.what) {
            case ShareFragment.IMAGE_VIEW_READY:

                /*
                 * Create an image bitmap from Jpeg data.
                 */
                Bundle bundle = msg.getData();

                int jpegDataLength = 0;
                for (int i = 0; i < ShareFragment.MESSAGE_BUNDLE_KEY_JPEG_DATA.length; i++) {
                    if (bundle.containsKey(ShareFragment.MESSAGE_BUNDLE_KEY_JPEG_DATA[i])) {
                        jpegDataLength++;
                    } else {
                        break;
                    }
                }

                byte[][] jpegData = new byte[jpegDataLength][];
                for (int i = 0; i < jpegDataLength; i++) {
                    jpegData[i] = bundle.getByteArray(ShareFragment.MESSAGE_BUNDLE_KEY_JPEG_DATA[i]);
                }

                float rotation = bundle.getFloat(ShareFragment.MESSAGE_BUNDLE_KEY_ROTATION);
                boolean reflection = bundle.getBoolean(ShareFragment.MESSAGE_BUNDLE_KEY_REFLECTION);
                String filterPref = bundle.getString(ShareFragment.MESSAGE_BUNDLE_KEY_FILTER);
                String arrangementPref = bundle.getString(ShareFragment.MESSAGE_BUNDLE_KEY_ARRANGEMENT);
                int thumbMaxWidth = bundle.getInt(ShareFragment.MESSAGE_BUNDLE_KEY_MAX_THUMB_WIDTH);
                int thumbMaxHeight = bundle.getInt(ShareFragment.MESSAGE_BUNDLE_KEY_MAX_THUMB_HEIGHT);

                // Select filter.
                ImageFilter[] filters = new ImageFilter[ShareFragment.MESSAGE_BUNDLE_KEY_JPEG_DATA.length];
                if (filterPref.equals(context.getString(R.string.pref__filter_bw))) {
                    filters[0] = new BlackAndWhiteFilter();
                    filters[1] = new BlackAndWhiteFilter();
                    filters[2] = new BlackAndWhiteFilter();
                    filters[3] = new BlackAndWhiteFilter();
                } else if (filterPref.equals(context.getString(R.string.pref__filter_bw_mixed))) {
                    if (arrangementPref.equals(context.getString(R.string.pref__arrangement_box))) {
                        filters[0] = new BlackAndWhiteFilter();
                        filters[3] = new BlackAndWhiteFilter();
                    } else {
                        filters[0] = new BlackAndWhiteFilter();
                        filters[2] = new BlackAndWhiteFilter();
                    }
                } else if (filterPref.equals(context.getString(R.string.pref__filter_sepia))) {
                    filters[0] = new SepiaFilter();
                    filters[1] = new SepiaFilter();
                    filters[2] = new SepiaFilter();
                    filters[3] = new SepiaFilter();
                } else if (filterPref.equals(context.getString(R.string.pref__filter_sepia_mixed))) {
                    if (arrangementPref.equals(context.getString(R.string.pref__arrangement_box))) {
                        filters[0] = new SepiaFilter();
                        filters[3] = new SepiaFilter();
                    } else {
                        filters[0] = new SepiaFilter();
                        filters[2] = new SepiaFilter();
                    }
                } else if (filterPref.equals(context.getString(R.string.pref__filter_line_art))) {
                    filters[0] = new LineArtFilter();
                    filters[1] = new LineArtFilter();
                    filters[2] = new LineArtFilter();
                    filters[3] = new LineArtFilter();
                } else {
                    // No filter. Keep filter as null.
                }

                // Select arrangement.
                Arrangement arrangement = null;
                if (arrangementPref.equals(context.getString(R.string.pref__arrangement_horizontal))) {
                    arrangement = new HorizontalArrangement();
                } else if (arrangementPref.equals(context.getString(R.string.pref__arrangement_box))) {
                    arrangement = new BoxArrangement();
                } else {
                    arrangement = new VerticalArrangement();
                }

                // Do the image processing.
                Bitmap[] bitmaps = new Bitmap[jpegDataLength];
                boolean isFramesValid = true;
                for (int i = 0; i < jpegDataLength; i++) {
                    // Create frame.
                    Bitmap frame = ImageHelper.createImage(jpegData[i], rotation, reflection, filters[i]);

                    // Ensure frame is non-null.
                    if (frame != null) {
                        bitmaps[i] = frame;
                    } else {
                        isFramesValid = false;
                        break;
                    }
                }

                // Create photo strip if all frames are valid.
                Bitmap photoStrip = null;
                if (isFramesValid) {
                    photoStrip = ImageHelper.createPhotoStrip(bitmaps, arrangement);
                }

                // Recycle original bitmaps.
                for (Bitmap bitmap : bitmaps) {
                    if (bitmap != null) {
                        bitmap.recycle();
                    }
                }
                bitmaps = null;

                // Notify ui.
                if (photoStrip != null) {
                    // Create thumbnail.
                    Point fittedSize = ImageHelper.getAspectFitSize(thumbMaxWidth, thumbMaxHeight,
                            photoStrip.getWidth(), photoStrip.getHeight());
                    mThumb = Bitmap.createScaledBitmap(photoStrip, fittedSize.x, fittedSize.y, true);
                    if (mThumb != null) {
                        // Thumbnail bitmap is ready.
                        Message uiMsg = Message.obtain();
                        uiMsg.what = THUMB_READY;
                        uiMsg.obj = mThumb;
                        sendUiUpdate(uiMsg);
                    } else {
                        // An error has occurred.
                        reportError();
                    }
                } else {
                    // An error has occurred.
                    reportError();
                }

                /*
                 * Save image bitmap as Jpeg.
                 */
                try {
                    String imageDirectory = ImageHelper.getCapturedImageDirectory(context
                            .getString(R.string.image_helper__image_folder_name));
                    if (imageDirectory != null) {
                        String imageName = ImageHelper.generateCapturedImageName(context
                                .getString(R.string.image_helper__image_filename_prefix));
                        File file = new File(imageDirectory, imageName);
                        final OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(file));

                        // Convert to Jpeg and writes to file.
                        boolean isSuccessful = ImageHelper.writeJpeg(photoStrip, outputStream);
                        outputStream.flush();
                        outputStream.close();

                        if (isSuccessful) {
                            mJpegPath = file.getPath();

                            // Notify ui the Jpeg is saved.
                            Message uiMsg = Message.obtain();
                            uiMsg.what = JPEG_SAVED;
                            uiMsg.obj = mJpegPath;
                            sendUiUpdate(uiMsg);
                        } else {
                            reportError();
                        }
                    } else {
                        // Invalid external storage state or failed directory creation.
                        reportError();
                    }
                } catch (FileNotFoundException e) {
                    reportError();
                } catch (IOException e) {
                    reportError();
                }

                /*
                 * Recycle photo strip bitmap if it is not the same object referenced by mThumb.
                 */
                if (photoStrip != null && photoStrip != mThumb) {
                    photoStrip.recycle();
                }
                photoStrip = null;

                break;
            case ShareFragment.GCP_SHARE_REQUESTED:
                // Create record in Wings.
                if (mIsGcpShareActive) {
                    if (mJpegPath != null && Wings.share(mJpegPath, GoogleCloudPrintEndpoint.DestinationId.PRINT_QUEUE, GoogleCloudPrintEndpoint.class)) {
                        // Disable to ensure we only make one share request.
                        mIsGcpShareActive = false;

                        // Notify ui.
                        Message uiMsg = Message.obtain();
                        uiMsg.what = GCP_SHARE_MARKED;
                        sendUiUpdate(uiMsg);
                    } else {
                        reportError();
                    }
                }
                break;
            case ShareFragment.FACEBOOK_SHARE_REQUESTED:
                // Create record in Wings.
                if (mIsFacebookShareActive) {
                    if (mJpegPath != null && Wings.share(mJpegPath, FacebookEndpoint.DestinationId.PROFILE, FacebookEndpoint.class)) {
                        // Disable to ensure we only make one share request.
                        mIsFacebookShareActive = false;

                        // Notify ui.
                        Message uiMsg = Message.obtain();
                        uiMsg.what = FACEBOOK_SHARE_MARKED;
                        sendUiUpdate(uiMsg);
                    } else {
                        reportError();
                    }
                }
                break;
            case ShareFragment.DROPBOX_SHARE_REQUESTED:
                // Create record in Wings.
                if (mIsDropboxShareActive) {
                    if (mJpegPath != null && Wings.share(mJpegPath, DropboxEndpoint.DestinationId.APP_FOLDER, DropboxEndpoint.class)) {
                        // Disable to ensure we only make one share request.
                        mIsDropboxShareActive = false;

                        // Notify ui.
                        Message uiMsg = Message.obtain();
                        uiMsg.what = DROPBOX_SHARE_MARKED;
                        sendUiUpdate(uiMsg);
                    } else {
                        reportError();
                    }
                }
                break;
            case ShareFragment.FRAGMENT_DESTROYED:
                /*
                 * Recycle thumb bitmap.
                 */
                if (mThumb != null) {
                    mThumb.recycle();
                    mThumb = null;
                }
                break;
            default:
                break;
        }
    }

    //
    // Private methods.
    //

    /**
     * Reports an error event to ui.
     */
    private void reportError() {
        Message uiMsg = Message.obtain();
        uiMsg.what = ERROR_OCCURRED;
        sendUiUpdate(uiMsg);
    }
}
