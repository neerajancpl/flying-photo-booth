/*
 * Copyright (C) 2013 Benedict Lau
 * 
 * All rights reserved.
 */
package com.groundupworks.partyphotobooth.kiosk;

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnLongClickListener;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.Toast;
import com.groundupworks.partyphotobooth.R;
import com.groundupworks.partyphotobooth.fragments.CaptureFragment;
import com.groundupworks.partyphotobooth.fragments.ConfirmationFragment;
import com.groundupworks.partyphotobooth.fragments.ErrorDialogFragment;
import com.groundupworks.partyphotobooth.fragments.NoticeFragment;
import com.groundupworks.partyphotobooth.fragments.PhotoStripFragment;
import com.groundupworks.partyphotobooth.helpers.PreferencesHelper;
import com.groundupworks.partyphotobooth.kiosk.KioskModeHelper.State;

/**
 * {@link Activity} that puts the device in Kiosk mode. This should only be launched from the {@link KioskService}.
 * 
 * @author Benedict Lau
 */
public class KioskActivity extends FragmentActivity implements KioskSetupFragment.ICallbacks,
        PhotoStripFragment.ICallbacks, CaptureFragment.ICallbacks, ConfirmationFragment.ICallbacks,
        NoticeFragment.ICallbacks {

    /**
     * Package private flag to track whether the single instance {@link KioskActivity} is in foreground.
     */
    static boolean sIsInForeground = false;

    /**
     * The {@link KioskModeHelper}.
     */
    private KioskModeHelper mKioskModeHelper;

    //
    // Fragments.
    //

    private KioskSetupFragment mKioskSetupFragment = null;

    private PhotoStripFragment mPhotoStripFragment = null;

    private NoticeFragment mNoticeFragment = null;

    //
    // Views.
    //

    private View mFlashScreen;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mKioskModeHelper = new KioskModeHelper(this);

        // Show on top of lock screen.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);

        setContentView(R.layout.activity_kiosk);

        // Configure button to exit Kiosk mode.
        ImageView exitButton = (ImageView) findViewById(R.id.kiosk_exit_button);
        mFlashScreen = findViewById(R.id.flash_screen);
        exitButton.setOnLongClickListener(new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (mKioskModeHelper.isPasswordRequired()) {
                    showDialogFragment(KioskPasswordDialogFragment.newInstance());
                } else {
                    exitKioskMode();
                }
                return true;
            }
        });

    }

    @Override
    public void onResume() {
        super.onResume();
        sIsInForeground = true;

        // Choose fragments to start with based on whether Kiosk mode setup has completed.
        if (mKioskModeHelper.isSetupCompleted()) {
            launchPhotoStripFragment();
            launchCaptureFragment();

            // Dismiss the notice fragment after resume.
            dismissNoticeFragment();
        } else {
            Toast.makeText(this, getString(R.string.kiosk_mode__start_msg), Toast.LENGTH_SHORT).show();
            launchKioskSetupFragment();
        }
    }

    @Override
    public void onPause() {
        sIsInForeground = false;
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        // Do nothing.
    }

    @Override
    public boolean onSearchRequested() {
        // Block search.
        return false;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Block event.
        return true;
    }

    //
    // Implementation of the KioskSetupFragment callbacks.
    //

    @Override
    public void onKioskSetupComplete(String password) {
        // Set password if used.
        if (password != null) {
            mKioskModeHelper.setPassword(password);
        }

        // Transition to setup completed state.
        mKioskModeHelper.transitionState(State.SETUP_COMPLETED);

        // Remove Kiosk setup fragment.
        dismissKioskSetupFragment();

        // Launch photo booth ui.
        launchPhotoStripFragment();
        launchCaptureFragment();
    }

    //
    // Implementation of the PhotoStripFragment callbacks.
    //

    @Override
    public void onNewPhotoStarted() {
        // Fade out flash screen.
        Animation animation = AnimationUtils.loadAnimation(this, R.anim.fade_out);
        animation.setAnimationListener(new AnimationListener() {

            @Override
            public void onAnimationStart(Animation animation) {
                // Do nothing.
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
                // Do nothing.
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                mFlashScreen.setVisibility(View.GONE);
            }
        });
        mFlashScreen.startAnimation(animation);
    }

    @Override
    public void onNewPhotoEnded(boolean isPhotoStripComplete) {
        if (isPhotoStripComplete) {
            // Confirm submission of photo strip.
            launchConfirmationFragment();
        } else {
            // Capture next frame.
            launchCaptureFragment();
        }
    }

    @Override
    public void onPhotoRemoved() {
        // Capture next frame.
        launchCaptureFragment();
    }

    @Override
    public void onPhotoStripSubmitted(boolean facebookShared, boolean dropboxShared) {
        // Reset photo booth ui.
        launchPhotoStripFragment();
        launchCaptureFragment();

        if (facebookShared || dropboxShared) {
            PreferencesHelper preferencesHelper = new PreferencesHelper();
            if (preferencesHelper.getNoticeEnabled(this)) {
                // Show notice fragment.
                launchNoticeFragment(facebookShared, dropboxShared);
            }
        }
    }

    @Override
    public void onErrorNewPhoto() {
        // An error occurred while trying to add a new photo. Self-recover by relaunching capture fragment.
        Toast.makeText(this, getString(R.string.photostrip__error_new_photo), Toast.LENGTH_LONG).show();
        launchCaptureFragment();
    }

    @Override
    public void onErrorPhotoStripSubmit() {
        // An error occurred while trying to submit the current photo strip. Self-recover by relaunching photo booth ui.
        Toast.makeText(this, getString(R.string.photostrip__error_submission), Toast.LENGTH_LONG).show();
        launchPhotoStripFragment();
        launchCaptureFragment();
    }

    //
    // Implementation of the CaptureFragment callbacks.
    //

    @Override
    public void onPictureTaken(byte[] data, float rotation, boolean reflection) {
        if (mPhotoStripFragment != null) {
            mFlashScreen.setVisibility(View.VISIBLE);
            mPhotoStripFragment.addPhoto(data, rotation, reflection);
        }
    }

    @Override
    public void onErrorCameraNone() {
        String title = getString(R.string.capture__error_camera_dialog_title);
        String message = getString(R.string.capture__error_camera_dialog_message_none);
        showDialogFragment(ErrorDialogFragment.newInstance(title, message));
    }

    @Override
    public void onErrorCameraInUse() {
        String title = getString(R.string.capture__error_camera_dialog_title);
        String message = getString(R.string.capture__error_camera_dialog_message_in_use);
        showDialogFragment(ErrorDialogFragment.newInstance(title, message));
    }

    @Override
    public void onErrorCameraCrashed() {
        // The native camera crashes occasionally. Self-recover by relaunching capture fragment.
        Toast.makeText(this, getString(R.string.capture__error_camera_crash), Toast.LENGTH_SHORT).show();
        launchCaptureFragment();
    }

    //
    // Implementation of the ConfirmationFragment callbacks.
    //

    @Override
    public void onSubmit() {
        if (mPhotoStripFragment != null) {
            mPhotoStripFragment.submitPhotoStrip();
        }
    }

    //
    // Implementation of the NoticeFragment callbacks.
    //

    @Override
    public void onNoticeDismissRequested() {
        dismissNoticeFragment();
    }

    //
    // Private methods.
    //

    /**
     * Launches the {@link KioskSetupFragment}.
     */
    private void launchKioskSetupFragment() {
        mKioskSetupFragment = KioskSetupFragment.newInstance();
        replaceTopFragment(mKioskSetupFragment);
    }

    /**
     * Launches a new {@link PhotoStripFragment} in the left side container.
     */
    private void launchPhotoStripFragment() {
        mPhotoStripFragment = PhotoStripFragment.newInstance();
        replaceLeftFragment(mPhotoStripFragment);
    }

    /**
     * Launches a new {@link CaptureFragment} in the right side container.
     */
    private void launchCaptureFragment() {
        replaceRightFragment(CaptureFragment.newInstance());
    }

    /**
     * Launches a new {@link ConfirmationFragment} in the right side container.
     */
    private void launchConfirmationFragment() {
        replaceRightFragment(ConfirmationFragment.newInstance());
    }

    /**
     * Launches a new {@link NoticeFragment}.
     * 
     * @param facebookShared
     *            true if the photo strip is marked for Facebook sharing; false otherwise.
     * @param dropboxShared
     *            true if the photo strip is marked for Dropbox sharing; false otherwise.
     */
    private void launchNoticeFragment(boolean facebookShared, boolean dropboxShared) {
        mNoticeFragment = NoticeFragment.newInstance(facebookShared, dropboxShared);
        replaceTopFragment(mNoticeFragment);
    }

    /**
     * Dismisses the {@link KioskSetupFragment}.
     */
    private void dismissKioskSetupFragment() {
        if (mKioskSetupFragment != null) {
            removeFragment(mKioskSetupFragment);
            mKioskSetupFragment = null;
        }
    }

    /**
     * Dismisses the {@link NoticeFragment}.
     */
    private void dismissNoticeFragment() {
        if (mNoticeFragment != null) {
            removeFragment(mNoticeFragment);
            mNoticeFragment = null;
        }
    }

    /**
     * Replaces the {@link Fragment} in the top fullscreen container.
     * 
     * @param fragment
     *            the new {@link Fragment} used to replace the current.
     */
    private void replaceTopFragment(Fragment fragment) {
        final FragmentManager fragmentManager = getSupportFragmentManager();
        fragmentManager.popBackStack();
        final FragmentTransaction ft = fragmentManager.beginTransaction();
        ft.replace(R.id.fragment_container_top, fragment);
        ft.commit();
    }

    /**
     * Replaces the {@link Fragment} in the left side container.
     * 
     * @param fragment
     *            the new {@link Fragment} used to replace the current.
     */
    private void replaceLeftFragment(Fragment fragment) {
        final FragmentManager fragmentManager = getSupportFragmentManager();
        final FragmentTransaction ft = fragmentManager.beginTransaction();
        ft.replace(R.id.fragment_container_left, fragment);
        ft.commit();
    }

    /**
     * Replaces the {@link Fragment} in the left side container.
     * 
     * @param fragment
     *            the new {@link Fragment} used to replace the current.
     */
    private void replaceRightFragment(Fragment fragment) {
        final FragmentManager fragmentManager = getSupportFragmentManager();
        final FragmentTransaction ft = fragmentManager.beginTransaction();
        ft.replace(R.id.fragment_container_right, fragment);
        ft.commit();
    }

    /**
     * Shows a {@link DialogFragment}.
     * 
     * @param fragment
     *            the new {@link DialogFragment} to show.
     */
    private void showDialogFragment(DialogFragment fragment) {
        fragment.show(getSupportFragmentManager(), null);
    }

    /**
     * Removes the {@link Fragment}.
     * 
     * @param fragment
     *            the {@link Fragment} to remove.
     */
    private void removeFragment(Fragment fragment) {
        final FragmentManager fragmentManager = getSupportFragmentManager();
        final FragmentTransaction ft = fragmentManager.beginTransaction();
        ft.remove(fragment);
        ft.commit();
    }

    //
    // Package private methods.
    //

    /**
     * Exits Kiosk mode.
     */
    void exitKioskMode() {
        // Disable Kiosk mode.
        mKioskModeHelper.transitionState(State.DISABLED);

        // Finish KioskActivity.
        finish();
    }
}
