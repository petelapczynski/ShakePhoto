package petelap.shakephoto;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Bundle;
import android.view.View;

import android.view.Window;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ShowCaptureActivity extends AppCompatActivity {
    // The following are used for the shake detection
    private SensorManager mSensorManager;
    private Sensor mAccelerometer;
    private ShakeDetector mShakeDetector;
    private int ShakeCount;

    private Runnable runnable;
    private Handler handler;

    private Bitmap pictureImage;
    private Bitmap displayImage;
    private ImageView image;
    private CircleProgressBar circleProgressBar;
    private TextView btnText;

    private int brightness;
    private Boolean isLayerRefresh;
    private long startTime;
    private long shakeTime;
    private final int THREAD_TIME_MS = 33;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getSupportActionBar().hide();

        setContentView(R.layout.activity_show_capture);
        circleProgressBar = findViewById(R.id.custom_progressBar);

        btnText = findViewById(R.id.btnText);

        FloatingActionButton bSave = findViewById(R.id.button);
        bSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String bTxt = btnText.getText().toString();
                if (bTxt.equals("Save Photo")) {
                    if (SaveImage(displayImage)) {
                        Toast.makeText(ShowCaptureActivity.this, "Photo Saved",Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(ShowCaptureActivity.this, "Error Saving Photo",Toast.LENGTH_LONG).show();
                    }
                }
            }
        });

        ImageButton btnNavMain = findViewById(R.id.btnNavMain);
        btnNavMain.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getApplicationContext(),MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
            }
        });


        ImageButton btnNavSettings = findViewById(R.id.btnNavSettings);
        btnNavSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getApplicationContext(),SettingsActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
            }
        });

        // SharedPreferences
        SharedPreferences pref = getApplicationContext().getSharedPreferences("appPref", Context.MODE_PRIVATE);
        float shakeThresholdGravity = pref.getFloat("shakeThresholdGravity", 2.0f);
        int shakeSlopTime =  pref.getInt("shakeSlopTime", 50);
        int shakeCountResetTime = pref.getInt("shakeCountResetTime", 500);
        int countDown = pref.getInt("countdown", 3000);
        ShakeDetector.setShakeThresholdGravity(shakeThresholdGravity);
        ShakeDetector.setShakeSlopTime(shakeSlopTime);
        ShakeDetector.setShakeCountResetTime(shakeCountResetTime);
        shakeTime = countDown;

        // ShakeDetector initialization
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        assert mSensorManager != null;
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mShakeDetector = new ShakeDetector();
        mShakeDetector.setOnShakeListener(new ShakeDetector.OnShakeListener() {
            @Override
            public void onShake(int count) {
                handleShakeEvent(count);
            }
        });

        image = findViewById(R.id.imageCaptured);
        // Set brightness
        brightness = 240;

        // Handle captured image to variable
        pictureImage = CapturedPhotoManager.getImage();
        displayImage = overlay(pictureImage);

        // Set image to layout
        image.setImageBitmap(displayImage);

        // photo brightness loop
        isLayerRefresh = true;
        handler = new Handler();
        startTime = System.currentTimeMillis();
        runnable = new Runnable() {
            @Override
            public void run() {
                if (isLayerRefresh) {
                    if (System.currentTimeMillis() < (startTime + shakeTime)) {
                        handler.postDelayed(this, THREAD_TIME_MS);
                        // Start progress bar countdown;
                        float progress = (100f * (1.0f - (float)(System.currentTimeMillis() - startTime) / (float)((startTime + shakeTime) - startTime)));
                        circleProgressBar.setProgressWithAnimation(progress, THREAD_TIME_MS);
                    } else {
                        isLayerRefresh = false;

                        btnText.setText(R.string.photo_Save);

                        FloatingActionButton fab = findViewById(R.id.button);
                        fab.setBackgroundTintList(ColorStateList.valueOf(ResourcesCompat.getColor(getResources(), R.color.colorAccent, null)));
                        circleProgressBar.setColor(Color.WHITE);
                        circleProgressBar.setProgressWithAnimation(0, THREAD_TIME_MS);

                        ImageButton btnNavMain = findViewById(R.id.btnNavMain);
                        btnNavMain.setVisibility(View.VISIBLE);

                        ImageButton btnNavSettings = findViewById(R.id.btnNavSettings);
                        btnNavSettings.setVisibility(View.VISIBLE);
                    }
                }
            }
        };
        // start handler
        handler.postDelayed(runnable, THREAD_TIME_MS);
    }

    @Override
    public void onPause() {
        // unregister the Sensor Manager onPause
        mSensorManager.unregisterListener(mShakeDetector);
        handler.removeCallbacksAndMessages(null);
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        // register the Session Manager Listener onResume
        mSensorManager.registerListener(mShakeDetector, mAccelerometer,	SensorManager.SENSOR_DELAY_UI);
    }

    private void handleShakeEvent(int count) {
        ShakeCount += count;
        brightness -= count;
        if (isLayerRefresh) {
            displayImage = overlay(pictureImage);
            image.setImageBitmap(displayImage);
        }
    }

    private Bitmap overlay(Bitmap bmp1) {
        Bitmap bmOverlay = Bitmap.createBitmap(bmp1.getWidth(), bmp1.getHeight(), bmp1.getConfig());
        Canvas canvas = new Canvas(bmOverlay);
        canvas.drawBitmap(bmp1, new Matrix(), null);
        Paint paint = new Paint();
        paint.setColor(Color.BLACK);
        if (brightness > 240) {
            paint.setAlpha(240);
        } else if (brightness < 0) {
            paint.setAlpha(0);
        } else {
            paint.setAlpha(brightness);
        }
        canvas.drawRect(0,0, bmOverlay.getWidth(), bmOverlay.getHeight(), paint);
        return bmOverlay;
    }

    private boolean SaveImage(Bitmap finalBitmap) {
        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString() + "/Shake Photo");
        // Create the storage directory if it does not exist
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                return false;
            }
        }

        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String mediaFileName = "img_"+ timeStamp +".jpg";
        File mediaFile = new File (mediaStorageDir, mediaFileName);
        if (mediaFile.exists ()) mediaFile.delete ();
        try {
            FileOutputStream out = new FileOutputStream(mediaFile);
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
            out.flush();
            out.close();

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        // Update media scanner
        MediaScannerConnection.scanFile(this, new String[]{mediaFile.toString()}, null,
            new MediaScannerConnection.OnScanCompletedListener() {
                public void onScanCompleted(String path, Uri uri) {
                    // scan complete
                }
            }
        );
        return true;
    }
}