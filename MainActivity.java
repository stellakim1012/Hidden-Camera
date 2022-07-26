/*
 *  UVCCamera
 *  library and sample to access to UVC web camera on non-rooted Android device
 *
 * Copyright (c) 2014-2018 saki t_saki@serenegiant.com
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 *  All files in the folder are under this Apache License, Version 2.0.
 *  Files in the libjpeg-turbo, libusb, libuvc, rapidjson folder
 *  may have a different license, see the respective files.
 */

package com.serenegiant.opencvwithuvc;

import android.animation.Animator;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.graphics.Typeface;
import android.hardware.usb.UsbDevice;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Vibrator;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.serenegiant.common.BaseActivity;
import com.serenegiant.math.Vector;
import com.serenegiant.opencv.ImageProcessor;
import com.serenegiant.usb.CameraDialog;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.USBMonitor.OnDeviceConnectListener;
import com.serenegiant.usb.USBMonitor.UsbControlBlock;
import com.serenegiant.usb.UVCCamera;
//import com.serenegiant.usbcameracommon.UVCCameraHandlerMultiSurface;
import com.serenegiant.utils.CpuMonitor;
import com.serenegiant.utils.ViewAnimationHelper;
import com.serenegiant.widget.UVCCameraTextureView;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.InvalidMarkException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;


import static java.lang.Math.abs;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static org.opencv.core.CvType.CV_8U;
import static org.opencv.imgproc.Imgproc.ADAPTIVE_THRESH_MEAN_C;
import static org.opencv.imgproc.Imgproc.Canny;
import static org.opencv.imgproc.Imgproc.THRESH_BINARY;
import static org.opencv.imgproc.Imgproc.THRESH_BINARY_INV;
import static org.opencv.imgproc.Imgproc.adaptiveThreshold;
import static org.opencv.imgproc.Imgproc.minEnclosingTriangle;
import static org.opencv.imgproc.Imgproc.rectangle;
import static org.opencv.imgproc.Imgproc.threshold;
import static org.opencv.photo.Photo.fastNlMeansDenoising;

public final class MainActivity extends BaseActivity
        implements CameraDialog.CameraDialogParent {

    private static final boolean DEBUG = true;    // TODO set false on release
    private static final String TAG = "MainActivity";


    /**
     * set true if you want to record movie using MediaSurfaceEncoder
     * (writing frame data into Surface camera from MediaCodec
     * by almost same way as USBCameratest2)
     * set false if you want to record movie using MediaVideoEncoder
     */
    private static final boolean USE_SURFACE_ENCODER = false;

    /**
     * preview resolution(width)
     * if your camera does not support specific resolution and mode,
     * {@link UVCCamera#setPreviewSize(int, int, int)} throw exception
     */
    private static final int PREVIEW_WIDTH = 640;
    /**
     * preview resolution(height)
     * if your camera does not support specific resolution and mode,
     * {@link UVCCamera#setPreviewSize(int, int, int)} throw exception
     */
    private static final int PREVIEW_HEIGHT = 480;
    /**
     * preview mode
     * if your camera does not support specific resolution and mode,
     * {@link UVCCamera#setPreviewSize(int, int, int)} throw exception
     * 0:YUYV, other:MJPEG
     */
    private static final int PREVIEW_MODE = 1;

    protected static final int SETTINGS_HIDE_DELAY_MS = 2500;

    /**
     * for accessing USB
     */
    private USBMonitor mUSBMonitor;
    /**
     * Handler to execute camera related methods sequentially on private thread
     */
    private UVCCameraHandlerMultiSurface mCameraHandler;
    /**
     * for camera preview display
     */
    private UVCCameraTextureView mUVCCameraView;
    /**
     * for display resulted images
     */
    protected SurfaceView mResultView;
    /**
     * for open&start / stop&close camera preview
     */
    private ToggleButton mCameraButton;
    /**
     * button for start/stop recording
     */
    private ImageButton mCaptureButton;

    private View mSettingsButton;
    private View mResetButton;
    private View mToolsLayout, mValueLayout;
    private SeekBar mBrightnessSeekbar, mContrastSeekbar, mGammaSeekbar, mGainSeekbar, mSharpnessSeekbar;
    //private SeekBar mZoomSeekbar;

    protected ImageProcessor mImageProcessor;
    private TextView mbrightness, mcontrast, mgamma, mgain, msharpness;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (DEBUG) Log.v(TAG, "onCreate:");
        setContentView(R.layout.activity_main);
        mCameraButton = findViewById(R.id.camera_button);
        mCameraButton.setOnCheckedChangeListener(mOnCheckedChangeListener);
        mCaptureButton = findViewById(R.id.capture_button);
        mCaptureButton.setOnClickListener(mOnClickListener);
        mCaptureButton.setVisibility(View.INVISIBLE);

        mUVCCameraView = findViewById(R.id.camera_view);
        mUVCCameraView.setOnLongClickListener(mOnLongClickListener);
        mUVCCameraView.setAspectRatio(PREVIEW_WIDTH / (float) PREVIEW_HEIGHT);

        mResultView = findViewById(R.id.result_view);

        mSettingsButton = findViewById(R.id.settings_button);
        mSettingsButton.setOnClickListener(mOnClickListener);
        mResetButton = findViewById(R.id.reset_button);
        mResetButton.setOnClickListener(mOnClickListener);
        mBrightnessSeekbar = findViewById(R.id.brightness_seekbar);
        mBrightnessSeekbar.setOnSeekBarChangeListener(mOnSeekBarChangeListener);
        mContrastSeekbar = findViewById(R.id.contrast_seekbar);
        mContrastSeekbar.setOnSeekBarChangeListener(mOnSeekBarChangeListener);
        mGammaSeekbar = findViewById(R.id.gamma_seekbar);
        mGammaSeekbar.setOnSeekBarChangeListener(mOnSeekBarChangeListener);
        mGainSeekbar = findViewById(R.id.gain_seekbar);
        mGainSeekbar.setOnSeekBarChangeListener(mOnSeekBarChangeListener);
        mSharpnessSeekbar = findViewById(R.id.sharpness_seekbar);
        mSharpnessSeekbar.setOnSeekBarChangeListener(mOnSeekBarChangeListener);
        //mZoomSeekbar.setOnSeekBarChangeListener(mOnZoomSeekBarChangeListener);

        mToolsLayout = findViewById(R.id.tools_layout);
        mToolsLayout.setVisibility(View.INVISIBLE);
        mValueLayout = findViewById(R.id.value_layout);
        mValueLayout.setVisibility(View.INVISIBLE);

        mbrightness = findViewById(R.id.brightness_textview);
        mbrightness.setTypeface(Typeface.MONOSPACE);
        mcontrast = findViewById(R.id.contrast_textview);
        mcontrast.setTypeface(Typeface.MONOSPACE);
        mgamma = findViewById(R.id.gamma_textview);
        mgamma.setTypeface(Typeface.MONOSPACE);
        mgain = findViewById(R.id.gain_textview);
        mgain.setTypeface(Typeface.MONOSPACE);
        msharpness = findViewById(R.id.sharpness_textview);
        msharpness.setTypeface(Typeface.MONOSPACE);

        mUSBMonitor = new USBMonitor(this, mOnDeviceConnectListener);
        mCameraHandler = UVCCameraHandlerMultiSurface.createHandler(this, mUVCCameraView,
                USE_SURFACE_ENCODER ? 0 : 1, PREVIEW_WIDTH, PREVIEW_HEIGHT, PREVIEW_MODE);
        //hc--
        System.loadLibrary("opencv_java3");
        //----
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (DEBUG) Log.v(TAG, "onStart:");
        mUSBMonitor.register();
    }

    @Override
    protected void onStop() {
        if (DEBUG) Log.v(TAG, "onStop:");
        stopPreview();
        mCameraHandler.close();
        setCameraButton(false);
        super.onStop();
    }

    @Override
    public void onDestroy() {
        if (DEBUG) Log.v(TAG, "onDestroy:");
        if (mCameraHandler != null) {
            mCameraHandler.release();
            mCameraHandler = null;
        }
        if (mUSBMonitor != null) {
            mUSBMonitor.destroy();
            mUSBMonitor = null;
        }
        mUVCCameraView = null;
        mCameraButton = null;
        mCaptureButton = null;
        super.onDestroy();
    }

    /**
     * event handler when click camera / capture button
     */
    private final OnClickListener mOnClickListener = new OnClickListener() {
        @Override
        public void onClick(final View view) {
            switch (view.getId()) {
                case R.id.capture_button:
                    if (mCameraHandler.isOpened()) {
                        if (checkPermissionWriteExternalStorage() && checkPermissionAudio()) {
                            if (!mCameraHandler.isRecording()) {
                                mCaptureButton.setColorFilter(0xffff0000);    // turn red
                                mCameraHandler.startRecording();
                            } else {
                                mCaptureButton.setColorFilter(0);    // return to default color
                                mCameraHandler.stopRecording();
                            }
                        }
                    }
                    break;
                case R.id.settings_button:
                    showSettings();
                    break;
                case R.id.reset_button:
                    resetSettings();
                    break;
            }
        }
    };

    private final CompoundButton.OnCheckedChangeListener mOnCheckedChangeListener
            = new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(
                final CompoundButton compoundButton, final boolean isChecked) {

            switch (compoundButton.getId()) {
                case R.id.camera_button:
                    if (isChecked && !mCameraHandler.isOpened()) {
                        CameraDialog.showDialog(MainActivity.this);
                    } else {
                        stopPreview();
                    }
                    break;
            }
        }
    };

    /**
     * capture still image when you long click on preview image(not on buttons)
     */
    private final OnLongClickListener mOnLongClickListener = new OnLongClickListener() {
        @Override
        public boolean onLongClick(final View view) {
            switch (view.getId()) {
                case R.id.camera_view:
                    if (mCameraHandler.isOpened()) {
                        if (checkPermissionWriteExternalStorage()) {
                            mCameraHandler.captureStill();
                        }
                        return true;
                    }
            }
            return false;
        }
    };

    private void setCameraButton(final boolean isOn) {
        if (DEBUG) Log.v(TAG, "setCameraButton:isOn=" + isOn);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mCameraButton != null) {
                    try {
                        mCameraButton.setOnCheckedChangeListener(null);
                        mCameraButton.setChecked(isOn);
                    } finally {
                        mCameraButton.setOnCheckedChangeListener(mOnCheckedChangeListener);
                    }
                }
                if (!isOn && (mCaptureButton != null)) {
                    mCaptureButton.setVisibility(View.INVISIBLE);
                }
            }
        }, 0);
        updateItems();
    }

    private int mPreviewSurfaceId;

    private void startPreview() {
        if (DEBUG) Log.v(TAG, "startPreview:");
        mUVCCameraView.resetFps();
        mCameraHandler.startPreview();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    final SurfaceTexture st = mUVCCameraView.getSurfaceTexture();
                    if (st != null) {
                        final Surface surface = new Surface(st);
                        mPreviewSurfaceId = surface.hashCode();
                        mCameraHandler.addSurface(mPreviewSurfaceId, surface, false);
                    }
                    mCaptureButton.setVisibility(View.VISIBLE);
                    startImageProcessor(PREVIEW_WIDTH, PREVIEW_HEIGHT);
                } catch (final Exception e) {
                    Log.w(TAG, e);
                }
            }
        });
        updateItems();
    }

    private void stopPreview() {
        if (DEBUG) Log.v(TAG, "stopPreview:");
        stopImageProcessor();
        if (mPreviewSurfaceId != 0) {
            mCameraHandler.removeSurface(mPreviewSurfaceId);
            mPreviewSurfaceId = 0;
        }
        mCameraHandler.close();
        setCameraButton(false);
    }

    private final OnDeviceConnectListener mOnDeviceConnectListener
            = new OnDeviceConnectListener() {

        @Override
        public void onAttach(final UsbDevice device) {
            Toast.makeText(MainActivity.this,
                    "USB_DEVICE_ATTACHED", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onConnect(final UsbDevice device,
                              final UsbControlBlock ctrlBlock, final boolean createNew) {

            if (DEBUG) Log.v(TAG, "onConnect:");
            mCameraHandler.open(ctrlBlock);
            startPreview();
            updateItems();
        }

        @Override
        public void onDisconnect(final UsbDevice device,
                                 final UsbControlBlock ctrlBlock) {

            if (DEBUG) Log.v(TAG, "onDisconnect:");
            if (mCameraHandler != null) {
                queueEvent(new Runnable() {
                    @Override
                    public void run() {
                        stopPreview();
                    }
                }, 0);
                updateItems();
            }
        }

        @Override
        public void onDettach(final UsbDevice device) {
            Toast.makeText(MainActivity.this,
                    "USB_DEVICE_DETACHED", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onCancel(final UsbDevice device) {
            setCameraButton(false);
        }
    };

    /**
     * to access from CameraDialog
     *
     * @return
     */
    @Override
    public USBMonitor getUSBMonitor() {
        return mUSBMonitor;
    }

    @Override
    public void onDialogResult(boolean canceled) {
        if (DEBUG) Log.v(TAG, "onDialogResult:canceled=" + canceled);
        if (canceled) {
            setCameraButton(false);
        }
    }

    //================================================================================
    private boolean isActive() {
        return mCameraHandler != null && mCameraHandler.isOpened();
    }

    private boolean checkSupportFlag(final int flag) {
        return mCameraHandler != null && mCameraHandler.checkSupportFlag(flag);
    }

    private int getValue(final int flag) {
        return mCameraHandler != null ? mCameraHandler.getValue(flag) : 0;
    }

    private int setValue(final int flag, final int value) {
        return mCameraHandler != null ? mCameraHandler.setValue(flag, value) : 0;
    }

    private int resetValue(final int flag) {
        return mCameraHandler != null ? mCameraHandler.resetValue(flag) : 0;
    }

    private void updateItems() {
        runOnUiThread(mUpdateItemsOnUITask, 100);
    }

    private final Runnable mUpdateItemsOnUITask = new Runnable() {
        @Override
        public void run() {
            if (isFinishing()) return;
            final int visible_active = isActive() ? View.VISIBLE : View.INVISIBLE;
            mToolsLayout.setVisibility(visible_active);
            //gr_setting
            mCameraHandler.setValue(UVCCamera.PU_BRIGHTNESS, 30);  //기본값: 30
            mCameraHandler.setValue(UVCCamera.PU_CONTRAST, 60);  //기본값: 60
            mCameraHandler.setValue(UVCCamera.PU_GAMMA, 30);  //기본값: 30
            mCameraHandler.setValue(UVCCamera.PU_GAIN, 40);  //기본값: 40
            mCameraHandler.setValue(UVCCamera.PU_SHARPNESS, 70);  //기본값: 70
            mSettingsButton.setVisibility(
                    checkSupportFlag(UVCCamera.PU_BRIGHTNESS)
                            ? visible_active : View.INVISIBLE);
        }
    };

    private int mSettingMode = -1;

    /**
     * show setting view
     *
//     * @param mode
     */

    private final void showSettings() {
        if (DEBUG) Log.v(TAG, String.format("showSettings"));
        hideSetting(false);
        if (isActive()) {
            mSettingMode = 1;
            mBrightnessSeekbar.setProgress(getValue(UVCCamera.PU_BRIGHTNESS));
            mContrastSeekbar.setProgress(getValue(UVCCamera.PU_CONTRAST));
            mGammaSeekbar.setProgress(getValue(UVCCamera.PU_GAMMA));
            mGainSeekbar.setProgress(getValue(UVCCamera.PU_GAIN));
            mSharpnessSeekbar.setProgress(getValue(UVCCamera.PU_SHARPNESS));
            ViewAnimationHelper.fadeIn(mValueLayout, -1, 0, mViewAnimationListener);

            mbrightness.setText(String.format(Locale.US, "Brightness:%3d", getValue(UVCCamera.PU_BRIGHTNESS)));
            mcontrast.setText(String.format(Locale.US, "Contrast:%3d", getValue(UVCCamera.PU_CONTRAST)));
            mgamma.setText(String.format(Locale.US, "Gamma:%3d", getValue(UVCCamera.PU_GAMMA)));
            mgain.setText(String.format(Locale.US, "Gain:%3d", getValue(UVCCamera.PU_GAIN)));
            msharpness.setText(String.format(Locale.US, "Sharpness:%3d", getValue(UVCCamera.PU_SHARPNESS)));
        }
    }

    private void resetSettings() {
        if (isActive()) {
            mBrightnessSeekbar.setProgress(resetValue(UVCCamera.PU_BRIGHTNESS));
            mContrastSeekbar.setProgress(resetValue(UVCCamera.PU_CONTRAST));
            mGammaSeekbar.setProgress(resetValue(UVCCamera.PU_GAMMA));
            mGainSeekbar.setProgress(resetValue(UVCCamera.PU_GAIN));
            mSharpnessSeekbar.setProgress(resetValue(UVCCamera.PU_SHARPNESS));

            mbrightness.setText(String.format(Locale.US, "Brightness:%3d", getValue(UVCCamera.PU_BRIGHTNESS)));
            mcontrast.setText(String.format(Locale.US, "Contrast:%3d", getValue(UVCCamera.PU_CONTRAST)));
            mgamma.setText(String.format(Locale.US, "Gamma:%3d", getValue(UVCCamera.PU_GAMMA)));
            mgain.setText(String.format(Locale.US, "Gain:%3d", getValue(UVCCamera.PU_GAIN)));
            msharpness.setText(String.format(Locale.US, "Sharpness:%3d", getValue(UVCCamera.PU_SHARPNESS)));
        }
        mSettingMode = -1;
        ViewAnimationHelper.fadeOut(mValueLayout, -1, 0, mViewAnimationListener);
    }

    /**
     * hide setting view
     *
     * @param fadeOut
     */
    protected final void hideSetting(final boolean fadeOut) {
        removeFromUiThread(mSettingHideTask);
        if (fadeOut) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ViewAnimationHelper.fadeOut(mValueLayout, -1, 0, mViewAnimationListener);
                }
            }, 0);
        } else {
            try {
                mValueLayout.setVisibility(View.GONE);
            } catch (final Exception e) {
                // ignore
            }
            mSettingMode = -1;
        }
    }

    protected final Runnable mSettingHideTask = new Runnable() {
        @Override
        public void run() {
            hideSetting(true);
        }
    };

    /**
     * callback listener to change camera control values
     */

    //gr_zoom
//    private final SeekBar.OnSeekBarChangeListener mOnZoomSeekBarChangeListener
//            = new SeekBar.OnSeekBarChangeListener() {
//
//        @Override
//        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
//
//            int Zoom = progress * 6;
//
//            if(mResultView.params.getMaxZoom() < Zoom) {
//                Zoom = CameraPreview.params.getMaxZoom();
//            }
//            CameraPreview.params.setZoom(Zoom);
//            CameraPreview.mCamera.setParameters(CameraPreview.params);
//        }
//
//        @Override
//        public void onStartTrackingTouch(SeekBar seekBar) { }
//        @Override
//        public void onStopTrackingTouch(SeekBar seekBar) { }
//
//    };

    private final SeekBar.OnSeekBarChangeListener mOnSeekBarChangeListener
            = new SeekBar.OnSeekBarChangeListener() {

        @Override
        public void onProgressChanged(final SeekBar seekBar,
                                      final int progress, final boolean fromUser) {

            if (fromUser) {
                runOnUiThread(mSettingHideTask, SETTINGS_HIDE_DELAY_MS);
            }
        }

        @Override
        public void onStartTrackingTouch(final SeekBar seekBar) {
        }

        @Override
        public void onStopTrackingTouch(final SeekBar seekBar) {
            runOnUiThread(mSettingHideTask, SETTINGS_HIDE_DELAY_MS);
            if (isActive()) {
                setValue(UVCCamera.PU_BRIGHTNESS, mBrightnessSeekbar.getProgress());
                setValue(UVCCamera.PU_CONTRAST, mContrastSeekbar.getProgress());
                setValue(UVCCamera.PU_GAMMA, mGammaSeekbar.getProgress());
                setValue(UVCCamera.PU_GAIN, mGainSeekbar.getProgress());
                setValue(UVCCamera.PU_SHARPNESS, mSharpnessSeekbar.getProgress());

                mbrightness.setText(String.format(Locale.US, "Brightness:%3d", getValue(UVCCamera.PU_BRIGHTNESS)));
                mcontrast.setText(String.format(Locale.US, "Contrast:%3d", getValue(UVCCamera.PU_CONTRAST)));
                mgamma.setText(String.format(Locale.US, "Gamma:%3d", getValue(UVCCamera.PU_GAMMA)));
                mgain.setText(String.format(Locale.US, "Gain:%3d", getValue(UVCCamera.PU_GAIN)));
                msharpness.setText(String.format(Locale.US, "Sharpness:%3d", getValue(UVCCamera.PU_SHARPNESS)));
            }    // if (active)
        }
    };

    private final ViewAnimationHelper.ViewAnimationListener
            mViewAnimationListener = new ViewAnimationHelper.ViewAnimationListener() {
        @Override
        public void onAnimationStart(@NonNull final Animator animator,
                                     @NonNull final View target, final int animationType) {

//         if (DEBUG) Log.v(TAG, "onAnimationStart:");
        }

        @Override
        public void onAnimationEnd(@NonNull final Animator animator,
                                   @NonNull final View target, final int animationType) {

            final int id = target.getId();
            switch (animationType) {
                case ViewAnimationHelper.ANIMATION_FADE_IN:
                case ViewAnimationHelper.ANIMATION_FADE_OUT: {
                    final boolean fadeIn = animationType == ViewAnimationHelper.ANIMATION_FADE_IN;
                    if (id == R.id.value_layout) {
                        if (fadeIn) {
                            runOnUiThread(mSettingHideTask, SETTINGS_HIDE_DELAY_MS);
                        } else {
                            mValueLayout.setVisibility(View.GONE);
                            mSettingMode = -1;
                        }
                    } else if (!fadeIn) {
//               target.setVisibility(View.GONE);
                    }
                    break;
                }
            }
        }

        @Override
        public void onAnimationCancel(@NonNull final Animator animator,
                                      @NonNull final View target, final int animationType) {

//         if (DEBUG) Log.v(TAG, "onAnimationStart:");
        }
    };

    /////////////////////////////////////////////////2021-03-31

    //================================================================================
    private volatile boolean mIsRunning;
    private int mImageProcessorSurfaceId;

    /**
     * start image processing
     *
     * @param processing_width
     * @param processing_height
     */
    protected void startImageProcessor(final int processing_width, final int processing_height) {
        if (DEBUG) Log.v(TAG, "startImageProcessor:");
        mIsRunning = true;
        if (mImageProcessor == null) {
            mImageProcessor = new ImageProcessor(PREVIEW_WIDTH, PREVIEW_HEIGHT,    // src size
                    new MyImageProcessorCallback(processing_width, processing_height));    // processing size
            mImageProcessor.start(processing_width, processing_height);    // processing size
            final Surface surface = mImageProcessor.getSurface();
            mImageProcessorSurfaceId = surface != null ? surface.hashCode() : 0;
            if (mImageProcessorSurfaceId != 0) {
                mCameraHandler.addSurface(mImageProcessorSurfaceId, surface, false);
            }
        }
    }

    /**
     * stop image processing
     */
    protected void stopImageProcessor() {
        if (DEBUG) Log.v(TAG, "stopImageProcessor:");
        if (mImageProcessorSurfaceId != 0) {
            mCameraHandler.removeSurface(mImageProcessorSurfaceId);
            mImageProcessorSurfaceId = 0;
        }
        if (mImageProcessor != null) {
            mImageProcessor.release();
            mImageProcessor = null;
        }
    }

    /**
     * callback listener from `ImageProcessor`
     */

    public static Bitmap bitmapOutput = null;

    protected class MyImageProcessorCallback implements ImageProcessor.ImageProcessorCallback {

        private final int width, height;
        private final Matrix matrix = new Matrix();
        private Bitmap mFrame;

        protected MyImageProcessorCallback(
                final int processing_width, final int processing_height) {

            width = processing_width;
            height = processing_height;
        }

        @Override
        public void onFrame(final ByteBuffer frame) {
            if (mResultView != null) {
                final SurfaceHolder holder = mResultView.getHolder();
                if ((holder == null)
                        || (holder.getSurface() == null)
                        || (frame == null)) return;

//--------------------------------------------------------------------------------
// Using SurfaceView and Bitmap to draw resulted images is inefficient way,
// but functions onOpenCV are relatively heavy and expect slower than source
// frame rate. So currently just use the way to simply this sample app.
// If you want to use much efficient way, try to use as same way as
// UVCCamera class use to receive images from UVC camera.
//--------------------------------------------------------------------------------
                if (mFrame == null) {
                    mFrame = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                    final float scaleX = mResultView.getWidth() / (float) width;
                    final float scaleY = mResultView.getHeight() / (float) height;
                    matrix.reset();
                    matrix.postScale(scaleX, scaleY);
                }
                try {
                    frame.clear();
                    mFrame.copyPixelsFromBuffer(frame);

                    final Canvas canvas = holder.lockCanvas();
// mn
//2021-02-08
                    Mat img_output = new Mat();
                    Mat img_copy = new Mat();
                    Mat img_show = new Mat();
                    Utils.bitmapToMat(mFrame, img_output);
                    Imgproc.cvtColor(img_output, img_copy, Imgproc.COLOR_BGR2GRAY);
                    Imgproc.cvtColor(img_output, img_show, Imgproc.COLOR_BGR2RGB);

                    Mat se = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(5, 5));

                    Mat subimage_mor = new Mat();
                    org.opencv.core.Size s = new Size(5, 5);
                    Imgproc.GaussianBlur(img_copy, subimage_mor, s, 2);
                    Imgproc.adaptiveThreshold(subimage_mor, subimage_mor, 255, ADAPTIVE_THRESH_MEAN_C, THRESH_BINARY, 27, -15);  //bs27(odd). c-15

                    Imgproc.morphologyEx(subimage_mor, subimage_mor, Imgproc.MORPH_OPEN, se);
//
////##############################################################################
////
                    Mat hierarchy = new Mat();

                    List<MatOfPoint> contours = new ArrayList<MatOfPoint>();

                    Imgproc.findContours(subimage_mor, contours, hierarchy, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_NONE);

                    int[] current_hierarchy = new int[4];

                    for (int i = 0; i < contours.size(); i++) {
                        Mat contour = contours.get(i);
                        MatOfPoint2f mMOP2f1 = new MatOfPoint2f();
                        MatOfPoint2f mMOP2f2 = new MatOfPoint2f();
                        contours.get(i).convertTo(mMOP2f1, CvType.CV_32FC2);
                        Imgproc.approxPolyDP(mMOP2f1, mMOP2f2, 3, true);
                        mMOP2f2.convertTo(contours.get(i), CvType.CV_32S);
                        Rect rect = Imgproc.boundingRect(contours.get(i));
                        Rect rect2 = Imgproc.boundingRect(contours.get(i));
                        double contourArea = Imgproc.contourArea(contour);

                        hierarchy.get(0, i, current_hierarchy);
                        if (max(rect.height, rect.width) / min(rect.height, rect.width) > 2) {
                            try {
                                Thread.sleep(1);
                                // Do some stuff
                            } catch (Exception e) {
                                e.getLocalizedMessage();
                            }
                            continue;
                        }
                        if (contourArea < 50) {
                            try {
                                Thread.sleep(1);
                                // Do some stuff
                            } catch (Exception e) {
                                e.getLocalizedMessage();
                            }
                            continue;
                        }
                        if (contourArea > 80) {
                            try {
                                Thread.sleep(1);
                                // Do some stuff
                            } catch (Exception e) {
                                e.getLocalizedMessage();
                            }
                            continue;
                        }

                        Mat checkimg = new Mat(subimage_mor, rect);
                        Mat se2 = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(5, 5));

                        Imgproc.morphologyEx(checkimg, checkimg, Imgproc.MORPH_DILATE, se2);

                        Mat hierarchy_temp = new Mat();

                        List<MatOfPoint> contours_temp = new ArrayList<MatOfPoint>();
                        Imgproc.findContours(checkimg, contours_temp, hierarchy_temp, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_NONE);

                        if(contours_temp.size()>1){
                            continue;
                        }
                        if(contours_temp.size() == 0){
                            continue;
                        }

                        //나사 제외 검출
                        if (current_hierarchy[3] == -1) {
                            Mat crop_image = new Mat(img_show, rect2);

                            double sum_pix = 0;

                            int pix = (rect2.height + 2) * (rect2.width + 2);

                            Mat bCnl = new Mat();
                            Core.extractChannel(crop_image, bCnl, 0);
                            Mat gCnl = new Mat();
                            Core.extractChannel(crop_image, gCnl, 1);
                            Mat rCnl = new Mat();
                            Core.extractChannel(crop_image, rCnl, 2);
                            double bMean = Core.mean(bCnl).val[0];
                            double gMean = Core.mean(gCnl).val[0];
                            double rMean = Core.mean(rCnl).val[0];
                            sum_pix  = bMean + gMean + rMean ;

                            int average_pix = (int)sum_pix / 3;

                            //if ((average_pix >= 0 && average_pix <= 20) || (average_pix >= 100 && average_pix <= 190) || (average_pix >= 220)) {
                            if ((average_pix < 100) || (average_pix > 150)) {
                                try {
                                    Thread.sleep(1);
                                    // Do some stuff
                                } catch (Exception e) {
                                    e.getLocalizedMessage();
                                }
                                continue;
                            }

                            int circle_x = ((rect2.x * 2) + rect.width) / 2;
                            int circle_y = ((rect2.y * 2) + rect.height) / 2;

                            Imgproc.putText(img_show, Integer.toString(average_pix), new Point(rect.x , rect.y ), 2, 1, new Scalar(0, 0, 255));
                            Imgproc.circle(img_show, new Point(circle_x, circle_y), 10, new Scalar(0, 255, 0), 4);
                        }
                        // 여기까지 나사 제외 검출

                        if (current_hierarchy[3] == -1) {
                            // random colour
                            continue;
                        }
                        Imgproc.circle(img_show, new Point((rect.x + rect.x + rect.width)/2, (rect.y+rect.y+rect.height)/2), 5, new Scalar(255,0,0), 4);

//						Vibrator vib = (Vibrator) getSystemService(VIBRATOR_SERVICE);
//						vib.vibrate(20);
                    }

//##############################################################################

                    //Imgproc.rectangle(img_show, new Point(160, 120), new Point(480, 360), new Scalar(255, 255, 0), 4);
                    bitmapOutput = Bitmap.createBitmap(img_output.cols(), img_output.rows(), Bitmap.Config.ARGB_8888);
                    Utils.matToBitmap(img_show, bitmapOutput);

                    try {
                        Thread.sleep(10);
                        // Do some stuff
                    } catch (Exception e) {
                        e.getLocalizedMessage();
                    }

//###################################################################

                    if (canvas != null) {
                        try {
                            Thread.sleep(1);
                            canvas.drawBitmap(bitmapOutput, matrix, null);//여기에 원래 첫번째에 mFrame 있었다-mn
                        } catch (final Exception e) {
                            Log.w(TAG, e);

                        } finally {
                            holder.unlockCanvasAndPost(canvas);
                        }
                    }

                } catch (final Exception e) {
                    Log.w(TAG, e);
                }
            }
        }

        @Override
        public void onResult(final int type, final float[] result) {
            // do something
        }
    }
}