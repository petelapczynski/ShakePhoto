package petelap.shakephoto;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.TextureView;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class CameraControllerV2WithPreview {

    private Activity mActivity;

    /* Conversion from screen rotation to JPEG orientation */
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private static final String TAG = "CCV2WithPreview";
    private static final int STATE_PREVIEW = 0;
    private static final int STATE_WAITING_LOCK = 1;
    private static final int STATE_WAITING_PRECAPTURE = 2;
    private static final int STATE_WAITING_NON_PRECAPTURE = 3;
    private static final int STATE_PICTURE_TAKEN = 4;
    private static final int MAX_PREVIEW_WIDTH = 1920;
    private static final int MAX_PREVIEW_HEIGHT = 1080;

    private final CameraManager mCameraManager;

    private AutoFitTextureView mTextureView;

    private String mCameraId;
    private CameraCharacteristics mCameraCharacteristics;
    private CameraDevice mCameraDevice;
    private CameraCaptureSession mCaptureSession;

    private Size mPreviewSize;
    private Size mPictureSize;

    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    private ImageReader mImageReader;

    private CaptureRequest.Builder mPreviewRequestBuilder;
    private CaptureRequest mPreviewRequest;
    private int mState = STATE_PREVIEW;
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);
    private boolean mFlashSupported;
    private int mSensorOrientation;
    private OrientationEventListener mOrientationListener;


    public CameraControllerV2WithPreview(Activity activity, AutoFitTextureView textureView) {
        mActivity = activity;
        mTextureView = textureView;
        mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);

        //Set initial camera
        mCameraManager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        mCameraId = CapturedPhotoManager.getCameraIDs();
        if (mCameraId.equals("")) {
            CapturedPhotoManager.setCameraIDs("0");
            mCameraId = CapturedPhotoManager.getCameraIDs();
        }


        mOrientationListener = new OrientationEventListener(mActivity, SensorManager.SENSOR_DELAY_NORMAL) {
            @Override
            public void onOrientationChanged(int orientation) {
                if (mTextureView != null && mTextureView.isAvailable()) {
                    configureTransform(mTextureView.getWidth(), mTextureView.getHeight());
                }
            }
        };
    }

    private final TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
            openCamera(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
            configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture texture) {
            //Save Image Every Frame
            // try {
            //    CapturedPhotoManager.setImage(mTextureView.getBitmap());
            //} catch (Exception e) {
            //    e.printStackTrace();
            //}
        }

    };

    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            // Open camera, start preview session
            mCameraOpenCloseLock.release();
            mCameraDevice = cameraDevice;
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
            if(mActivity != null) {
                mActivity.finish();
            }
        }

    };

    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader reader) {
            Log.d(TAG, "ImageAvailable");
            // image is now available callback - handle the image/file
            //backgroundHandler.post(new ImageSaver(reader.acquireNextImage(), file));

            ByteBuffer buffer = reader.acquireNextImage().getPlanes()[0].getBuffer();
            byte[] data = new byte[buffer.remaining()];
            buffer.get(data);

            // Save captured image to variable
            Bitmap capturedBitmap = BitmapFactory.decodeByteArray(data,0,data.length);

            if (CapturedPhotoManager.isForwardFacingLens()) {
                capturedBitmap = rotateBitmap(capturedBitmap, "ORIENTATION_FLIP_VERTICAL");
            }

            // Resize Bitmap Aspect
            capturedBitmap = resizeBitmap(capturedBitmap, mTextureView.getWidth(), mTextureView.getHeight());
            //capturedBitmap = resizeBitmap(capturedBitmap, 1080, 1920);

            CapturedPhotoManager.setImage(capturedBitmap);

            // Close image
            reader.close();

            // Start Show Capture Activity and handle shake events
            Intent intent = new Intent(mActivity, ShowCaptureActivity.class);
            mActivity.startActivity(intent);
        }

    };

    private CameraCaptureSession.CaptureCallback mCaptureCallback = new CameraCaptureSession.CaptureCallback() {

        private void process(CaptureResult result) {
            switch (mState) {
                case STATE_PREVIEW: {
                    // do nothing when the camera preview is working normally.
                    Log.d(TAG, "STATE_PREVIEW");
                    break;
                }
                case STATE_WAITING_LOCK: {
                    Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
                    if (afState == null) {
                        captureStillPicture();
                    } else if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState ||
                            CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState) {
                        // CONTROL_AE_STATE can be null on some devices
                        Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                        if (aeState == null || aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                            mState = STATE_PICTURE_TAKEN;
                            captureStillPicture();
                        } else {
                            runPrecaptureSequence();
                        }
                    }
                    Log.d(TAG, "STATE_WAITING_LOCK");
                    break;
                }
                case STATE_WAITING_PRECAPTURE: {
                    // CONTROL_AE_STATE can be null on some devices
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null || aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE || aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
                        mState = STATE_WAITING_NON_PRECAPTURE;
                    }
                    Log.d(TAG, "STATE_WAITING_PRECAPTURE");
                    break;
                }
                case STATE_WAITING_NON_PRECAPTURE: {
                    // CONTROL_AE_STATE can be null on some devices
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                        mState = STATE_PICTURE_TAKEN;
                        captureStillPicture();
                    }
                    Log.d(TAG, "STATE_WAITING_NON_PRECAPTURE");
                    break;
                }
            }
        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureResult partialResult) {
            process(partialResult);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            process(result);
        }

    };

    private static Size chooseOptimalSize(Size[] choices, int textureViewWidth, int textureViewHeight, int maxWidth, int maxHeight, Size aspectRatio) {
        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        // Collect the supported resolutions that are smaller than the preview Surface
        List<Size> notBigEnough = new ArrayList<>();

        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();

        for (Size option : choices) {
            if (option.getWidth() <= maxWidth && option.getHeight() <= maxHeight
                    && option.getHeight() == option.getWidth() * h / w ){
                if (option.getWidth() >= textureViewWidth && option.getHeight() >= textureViewHeight) {
                    bigEnough.add(option);
                } else {
                    notBigEnough.add(option);
                }
            }
        }

        // Pick the smallest of those big enough. If there is no one big enough, pick the largest of those not big enough.
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else if (notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, new CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    private void setUpCameraOutputs(int viewWidth, int viewHeight) {

        try {
            for (String cameraId : mCameraManager.getCameraIdList()) {

                if (cameraId.equals(CapturedPhotoManager.getCameraIDs())) {
                    mCameraId = cameraId;
                } else {
                    continue;
                }

                mCameraCharacteristics = mCameraManager.getCameraCharacteristics(mCameraId);

                StreamConfigurationMap map = mCameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) {
                    continue;
                }

                // For still image captures, use the largest available to match View Aspect Ratio
                // else select largest available
                Size[] imgSizes = map.getOutputSizes(ImageFormat.JPEG);
                List<Size> sameRatio = new ArrayList<>();
                float viewRatio1 = (float)viewWidth / (float)viewHeight;
                float viewRatio2 = (float)viewHeight / (float)viewWidth;
                for ( Size size: imgSizes) {
                    // Same as View Ratio
                    float imgRation = ((float)size.getWidth() / (float)size.getHeight());
                    if ( viewRatio1 ==  imgRation || viewRatio2 ==  imgRation ) {
                        sameRatio.add(size);
                    }
                }
                if (sameRatio.size() > 0) {
                    mPictureSize = Collections.max(sameRatio, new CompareSizesByArea());
                } else {
                    mPictureSize = Collections.max(Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)), new CompareSizesByArea());
                }

                mImageReader = ImageReader.newInstance(mPictureSize.getWidth(), mPictureSize.getHeight(), ImageFormat.JPEG, /*maxImages*/2);
                mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, backgroundHandler);

                // Find out if we need to swap dimension to get the preview size relative to sensor coordinate.
                mSensorOrientation = mCameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);

                // Find the rotation of the device relative to the native device orientation.
                Point displaySize = new Point();
                mActivity.getWindowManager().getDefaultDisplay().getSize(displaySize);

                int deviceRotation = mActivity.getWindowManager().getDefaultDisplay().getRotation();
                deviceRotation = ORIENTATIONS.get(deviceRotation);

                // Reverse device orientation for front-facing cameras
                if (mCameraCharacteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
                    deviceRotation = -deviceRotation;
                }

                // the image upright relative to the device orientation
                int totalRotation = (mSensorOrientation + deviceRotation + 360) % 360;

                boolean swappedDimensions = totalRotation == 90 || totalRotation == 270;

                int rotatedViewWidth = viewWidth;
                int rotatedViewHeight = viewHeight;
                int maxPreviewWidth = displaySize.x;
                int maxPreviewHeight = displaySize.y;

                if (swappedDimensions) {
                    rotatedViewWidth = viewHeight;
                    rotatedViewHeight = viewWidth;
                    maxPreviewWidth = displaySize.y;
                    maxPreviewHeight = displaySize.x;
                }

                if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
                    maxPreviewWidth = MAX_PREVIEW_WIDTH;
                }

                if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
                    maxPreviewHeight = MAX_PREVIEW_HEIGHT;
                }

                // large preview size could exceed the camera bus and width limitation
                Size[] sizes = map.getOutputSizes(SurfaceTexture.class);
                mPreviewSize = chooseOptimalSize(sizes, rotatedViewWidth, rotatedViewHeight, maxPreviewWidth, maxPreviewHeight, mPictureSize);

                // We fit the aspect ratio of TextureView to the size of preview we picked.
                int orientation = mActivity.getResources().getConfiguration().orientation;
                if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    mTextureView.setAspectRatio(mPreviewSize.getWidth(), mPreviewSize.getHeight(), maxPreviewWidth, maxPreviewHeight, mPictureSize);
                } else {
                    mTextureView.setAspectRatio(mPreviewSize.getHeight(), mPreviewSize.getWidth(), maxPreviewWidth, maxPreviewHeight, mPictureSize);
                }

                // if the flash is supported.
                Boolean available = mCameraCharacteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                mFlashSupported = available == null ? false : available;

                // Check if forward facing lens.
                boolean facing = mCameraCharacteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT;
                CapturedPhotoManager.setforwardFacingLens(facing);

                return;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the device this code runs.
            e.printStackTrace();
        }
    }


    public void openCamera(int width, int height) {
        if (ContextCompat.checkSelfPermission(mActivity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        setUpCameraOutputs(width, height);
        configureTransform(width, height);

        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            startBackgroundThread();
            mCameraManager.openCamera(mCameraId, mStateCallback, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }
    }

    public void closeCamera() {
        try {
            mCameraOpenCloseLock.acquire();
            if (null != mCaptureSession) {
                mCaptureSession.close();
                mCaptureSession = null;
            }
            if (null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
            if (null != mImageReader) {
                mImageReader.close();
                mImageReader = null;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            mCameraOpenCloseLock.release();
            stopBackgroundThread();
        }
    }

    public void reopenCamera() {
        if (mTextureView.isAvailable()) {
            openCamera(mTextureView.getWidth(), mTextureView.getHeight());
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        backgroundThread.quitSafely();
        try {
            backgroundThread.join();
            backgroundThread = null;
            backgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


    private void createCameraPreviewSession() {
        try {
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;

            // configure the size of default buffer to be the size of camera preview
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());

            // This is the output Surface we need to start preview.
            Surface surface = new Surface(texture);

            // set up a CaptureRequest.Builder with the output Surface.
            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(surface);

            // create a CameraCaptureSession for camera preview.
            mCameraDevice.createCaptureSession(Arrays.asList(surface, mImageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                            // The camera is already closed
                            if (null == mCameraDevice) {
                                return;
                            }

                            // session is ready, start displaying the preview
                            mCaptureSession = cameraCaptureSession;
                            try {
                                // Auto focus should be continuous for camera preview
                                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                // Flash is automatically enabled when necessary.
                                setAutoFlash(mPreviewRequestBuilder);

                                // start displaying the camera preview
                                mPreviewRequest = mPreviewRequestBuilder.build();
                                mCaptureSession.setRepeatingRequest(mPreviewRequest, mCaptureCallback, backgroundHandler);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                            Log.d(TAG, "Configuration Failed");
                        }
                    }, null
            );
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void configureTransform(int viewWidth, int viewHeight) {
        if (null == mTextureView || null == mPreviewSize) {
            return;
        }
        int rotation = mActivity.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / mPreviewSize.getHeight(),
                    (float) viewWidth / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }
        mTextureView.setTransform(matrix);
    }

    public void takePicture() {
        try {
            // lock focus
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
            // wait for the lock
            mState = STATE_WAITING_LOCK;
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void runPrecaptureSequence() {
        try {
            // tell the camera to trigger
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);

            // wait for the precapture sequence to be set
            mState = STATE_WAITING_PRECAPTURE;
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void captureStillPicture() {
        try {
            if (null == mCameraDevice) {
                return;
            }
            // create CaptureRequest.Builder that we use to take a picture
            final CaptureRequest.Builder captureBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(mImageReader.getSurface());

            // Use same AE and AF modes as the preview.
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            setAutoFlash(captureBuilder);

            // Orientation
            int deviceRotation = sensorToDeviceRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, deviceRotation );

            CameraCaptureSession.CaptureCallback CaptureCallback = new CameraCaptureSession.CaptureCallback() {

                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                    try {
                        // Reset the auto-focus trigger
                        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
                        setAutoFlash(mPreviewRequestBuilder);
                        mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback, backgroundHandler);
                        // go back to the normal state of preview.
                        mState = STATE_PREVIEW;
                        mCaptureSession.setRepeatingRequest(mPreviewRequest, mCaptureCallback, backgroundHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }
            };

            mCaptureSession.stopRepeating();
            mCaptureSession.capture(captureBuilder.build(), CaptureCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private int sensorToDeviceRotation() {
        int deviceRotation = mActivity.getWindowManager().getDefaultDisplay().getRotation();
        deviceRotation = ORIENTATIONS.get(deviceRotation);

        // Reverse device orientation for front-facing cameras
        if (mCameraCharacteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
            deviceRotation = -deviceRotation;
        }

        // Calculate desired JPEG orientation relative to camera orientation to make
        // the image upright relative to the device orientation
        return (mSensorOrientation + deviceRotation + 270) % 360;
    }

    private void setAutoFlash(CaptureRequest.Builder requestBuilder) {
        if (mFlashSupported) {
            requestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
        }
    }

    public void switchCamera() {
        if (CapturedPhotoManager.getCameraIDs().equals("0")) {
            CapturedPhotoManager.setCameraIDs("1");
        } else {
            CapturedPhotoManager.setCameraIDs("0");
        }
        mCameraId = CapturedPhotoManager.getCameraIDs();
        closeCamera();
        reopenCamera();
    }

    public void switchResolution() {
        // TODO handle different resolutions
        closeCamera();
        reopenCamera();
    }

    private static Bitmap resizeBitmap(Bitmap image, int maxWidth, int maxHeight) {
        if (maxHeight > 0 && maxWidth > 0) {
            int width = image.getWidth();
            int height = image.getHeight();
            float ratioBitmap = (float) width / (float) height;
            float ratioMax = (float) maxWidth / (float) maxHeight;

            if (ratioBitmap != ratioMax) {
                int finalWidth = width;
                if (maxWidth > finalWidth) {
                    finalWidth = maxWidth;
                }
                int finalHeight = (int) ( (float)finalWidth / ratioMax);
                image = Bitmap.createScaledBitmap(image, finalWidth, finalHeight, true);
            }
            return image;
        } else {
            return image;
        }
    }

    private static Bitmap rotateBitmap(Bitmap bitmap, String orientation) {
        /*
        ORIENTATION_FLIP_HORIZONTAL
        ORIENTATION_FLIP_VERTICAL
        ORIENTATION_NORMAL
        ORIENTATION_ROTATE_90
        ORIENTATION_ROTATE_180
        ORIENTATION_ROTATE_270
        ORIENTATION_TRANSPOSE
        ORIENTATION_TRANSVERSE
        ORIENTATION_UNDEFINED
        */
        Matrix matrix = new Matrix();
        switch (orientation) {
            case "ORIENTATION_TRANSPOSE":
                Log.d(TAG, "rotateBitmap: transpose");
                matrix.setRotate(90);
                matrix.postScale(-1, 1);
                break;
            case "ORIENTATION_NORMAL":
                Log.d(TAG, "rotateBitmap: normal.");
                return bitmap;
            case "ORIENTATION_FLIP_HORIZONTAL":
                Log.d(TAG, "rotateBitmap: flip horizontal");
                matrix.setScale(-1, 1);
                break;
            case "ORIENTATION_ROTATE_180":
                Log.d(TAG, "rotateBitmap: rotate 180");
                matrix.setRotate(180);
                break;
            case "ORIENTATION_FLIP_VERTICAL":
                Log.d(TAG, "rotateBitmap: rotate vertical");
                matrix.setRotate(180);
                matrix.postScale(-1, 1);
                break;
            case "ORIENTATION_ROTATE_90":
                Log.d(TAG, "rotateBitmap: rotate 90");
                matrix.setRotate(90);
                break;
            case "ORIENTATION_TRANSVERSE":
                Log.d(TAG, "rotateBitmap: transverse");
                matrix.setRotate(-90);
                matrix.postScale(-1, 1);
                break;
            case "ORIENTATION_ROTATE_270":
                Log.d(TAG, "rotateBitmap: rotate 270");
                matrix.setRotate(-90);
                break;
        }
        try {
            Bitmap bmRotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            bitmap.recycle();

            return bmRotated;
        } catch (OutOfMemoryError e) {
            e.printStackTrace();
            return null;
        }
    }

    static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() - (long) rhs.getWidth() * rhs.getHeight());
        }

    }

}