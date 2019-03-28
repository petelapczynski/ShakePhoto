package petelap.shakephoto;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

public class ShakeDetector implements SensorEventListener {
    // Gravity threshold
    private static float SHAKE_THRESHOLD_GRAVITY;
    // Time between shake events
    private static int SHAKE_SLOP_TIME_MS;
    // Time reset with no shake activity
    private static int SHAKE_COUNT_RESET_TIME_MS;

    private OnShakeListener mListener;
    private long mShakeTimestamp;
    private int mShakeCount;

    public void setOnShakeListener(OnShakeListener listener) {
        this.mListener = listener;
    }

    public interface OnShakeListener {
        void onShake(int count);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // ignore
    }

    @Override
    public void onSensorChanged(SensorEvent event) {

        if (mListener != null) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];

            float gX = x / SensorManager.GRAVITY_EARTH;
            float gY = y / SensorManager.GRAVITY_EARTH;
            float gZ = z / SensorManager.GRAVITY_EARTH;

            // gForce will be close to 1 when there is no movement.
            float gForce = (float)Math.sqrt(gX * gX + gY * gY + gZ * gZ);

            if (gForce > SHAKE_THRESHOLD_GRAVITY) {
                final long now = System.currentTimeMillis();
                // ignore shake events too close to each other
                if (mShakeTimestamp + SHAKE_SLOP_TIME_MS > now) {
                    return;
                }

                // reset the shake count after time with no shakes
                if (mShakeTimestamp + SHAKE_COUNT_RESET_TIME_MS < now) {
                    mShakeCount = 0;
                }

                mShakeTimestamp = now;
                mShakeCount++;

                mListener.onShake(mShakeCount);
            }
        }
    }

    public float getShakeThresholdGravity() {
        return SHAKE_THRESHOLD_GRAVITY;
    }

    public static void setShakeThresholdGravity(float shakeThresholdGravity) {
        SHAKE_THRESHOLD_GRAVITY = shakeThresholdGravity;
    }

    public int getShakeSlopTime() {
        return SHAKE_SLOP_TIME_MS;
    }

    public static void setShakeSlopTime(int shakeSlopTime) {
        SHAKE_SLOP_TIME_MS = shakeSlopTime;
    }

    public int getShakeCountResetTime() {
        return SHAKE_COUNT_RESET_TIME_MS;
    }

    public static void setShakeCountResetTime(int shakeCountResetTime) {
        SHAKE_COUNT_RESET_TIME_MS = shakeCountResetTime;
    }
}