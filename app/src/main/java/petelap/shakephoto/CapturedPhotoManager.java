package petelap.shakephoto;

import android.graphics.Bitmap;

public class CapturedPhotoManager {
    private static Bitmap image;
    private static int cameraID;

    public static Bitmap getImage() {
        return image;
    }

    public static void setImage(Bitmap img) {
        image = img;
    }

    public static int getCameraID() {
        return cameraID;
    }

    public static void setCameraID(int camID) {
        cameraID = camID;
    }
}
