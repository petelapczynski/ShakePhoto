package petelap.shakephoto;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;

import androidx.fragment.app.Fragment;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class Camera2Fragment extends Fragment implements SurfaceHolder.Callback {

    private CameraControllerV2WithPreview ccv2WithPreview;
    private static final String TAG = "Camera2Fragment";

    public static Camera2Fragment newInstance() {
        return new Camera2Fragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_camera2, container, false);
        AutoFitTextureView textureView = view.findViewById(R.id.textureView);
        assert textureView != null;

        // set default camera
        if (CapturedPhotoManager.getCameraIDs() == null || CapturedPhotoManager.getCameraIDs().isEmpty()) {
            CapturedPhotoManager.setCameraIDs("0");
        }

        CircleProgressBar circleProgressBar = view.findViewById(R.id.custom_progressBar);
        circleProgressBar.setStrokeWidth(50f);
        circleProgressBar.setProgressWithAnimation(100, 100);

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
                ccv2WithPreview.switchCamera();
            }
        });

        //ImageButton btnRes = view.findViewById(R.id.btnRes);
        //btnRes.setOnClickListener(new View.OnClickListener() {
        //    @Override
        //    public void onClick(View v) {
        //        ccv2WithPreview.switchResolution();
        //    }
        //});

        ccv2WithPreview = new CameraControllerV2WithPreview(getActivity(), textureView);

        FloatingActionButton fab = view.findViewById(R.id.button);
        assert fab != null;
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Capture Image
                ccv2WithPreview.takePicture();
            }
        });

        return view;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        //startCameraPreview(holder);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        //stopCameraPreview();
        //startCameraPreview(holder);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        super.onDestroy();
        //stopCameraPreview();
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onPause() {
        Log.e(TAG, "onPause");
        super.onPause();
    }

}