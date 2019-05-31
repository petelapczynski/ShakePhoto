package petelap.shakephoto;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

public class CameraFragment extends Fragment implements SurfaceHolder.Callback {

    private Camera mCamera;
    private int mCameraId;
    private int previewRotation;
    private Camera.PictureCallback jpegCallback;

    private SurfaceView mSurfaceView;
    private SurfaceHolder mSurfaceHolder;

    public static CameraFragment newInstance(){
        return new CameraFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_camera, container, false);
        mSurfaceView = view.findViewById(R.id.surfaceView);
        mSurfaceHolder = mSurfaceView.getHolder();
        // Set default camera and clear photo
        mCameraId = CapturedPhotoManager.getCameraID();

        CircleProgressBar circleProgressBar = view.findViewById(R.id.custom_progressBar);
        circleProgressBar.setStrokeWidth(50f);
        circleProgressBar.setProgressWithAnimation(100, 100);

        jpegCallback = new Camera.PictureCallback() {
            @Override
            public void onPictureTaken(byte[] data, Camera camera) {
                // Save captured image to variable
                CapturedPhotoManager.setImage( rotate(BitmapFactory.decodeByteArray(data, 0, data.length)) );

                Intent intent = new Intent(getActivity(), ShowCaptureActivity.class);
                startActivity(intent);
            }
        };

        if (ActivityCompat.checkSelfPermission(Objects.requireNonNull(getContext()), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            mSurfaceHolder.addCallback(this);
            mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        }

        FloatingActionButton fab = view.findViewById(R.id.button);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                captureImage();
            }
        });

        ImageButton btnNavSettings = view.findViewById(R.id.btnNavSettings);
        btnNavSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getActivity(), SettingsActivity.class);
                startActivity(intent);
            }
        });

        ImageButton btnSwitchCamera = view.findViewById(R.id.btnSwitchCamera);
        btnSwitchCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mCameraId == 0) {
                    mCameraId = 1;
                } else {
                    mCameraId = 0;
                }
                CapturedPhotoManager.setCameraID(mCameraId);
                // restart camera preview
                stopCameraPreview();
                startCameraPreview(mSurfaceHolder);
            }
        });

        return view;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        startCameraPreview(holder);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        stopCameraPreview();
        startCameraPreview(holder);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        stopCameraPreview();

    }

    private Bitmap rotate(Bitmap decodedBitmap) {
        int w = decodedBitmap.getWidth();
        int h = decodedBitmap.getHeight();

        Matrix matrix = new Matrix();
        matrix.setRotate(90);

        return Bitmap.createBitmap(decodedBitmap, 0, 0, w, h, matrix, true);
    }

    private void captureImage() {
        mCamera.takePicture(null, null, jpegCallback);
    }

    private void startCameraPreview(SurfaceHolder holder) {
        // get camera
        mCamera = Camera.open(mCameraId);
        // get camera parameters
        Camera.Parameters parameters = mCamera.getParameters();
        // set framerate
       List<int[]> frameRates = parameters.getSupportedPreviewFpsRange();
       int[] frameRate = null;
        for (int i = 0; i < frameRates.size(); i++) {
            if (frameRates.get(i)[1] > 1000 && frameRates.get(i)[1] <= 30000)  {
                frameRate = frameRates.get(i);
            }
        }
        parameters.setPreviewFpsRange(frameRate[0], frameRate[1]);

        // set focus mode
        List<String> focusModes = parameters.getSupportedFocusModes();
        String focusMode = focusModes.get(0);
        for (int i = 0; i < focusModes.size(); i++) {
            if (focusModes.get(i).equals(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                focusMode = focusModes.get(i);
            }
        }
        parameters.setFocusMode(focusMode);

        // set preview size
        Camera.Size bestSize;
        List<Camera.Size> sizeList = mCamera.getParameters().getSupportedPreviewSizes();
        bestSize = sizeList.get(0);
        for (int i = 1; i < sizeList.size(); i++) {
            if ((sizeList.get(i).width * sizeList.get(i).height) > (bestSize.width * bestSize.height)) {
                bestSize = sizeList.get(i);
            }
        }
        parameters.setPreviewSize(bestSize.width, bestSize.height);

        mCamera.setParameters(parameters);

        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(mCameraId, info);

        int rotation = getActivity().getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }

        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            // compensate the mirror
            previewRotation = (info.orientation + degrees) % 360;
            previewRotation = (360 - previewRotation) % 360;
        } else {
            // back-facing
            previewRotation = (info.orientation - degrees + 360) % 360;
        }
        mCamera.setDisplayOrientation(previewRotation);

        // set preview
        try {
            mCamera.setPreviewDisplay(holder);
        } catch (IOException e) {
            e.printStackTrace();
        }
        mCamera.startPreview();
    }

    private void stopCameraPreview() {
        // stop preview and release camera
        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }
    }
}