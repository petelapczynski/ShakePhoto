package petelap.shakephoto;

import android.graphics.Bitmap;

public class CapturedPhotoManager {
    private static Bitmap image;
    private static int cameraID;
    private static String cameraIDs;
    private static boolean forwardFacingLens;

    public static Bitmap getImage() { return image; }

    public static void setImage(Bitmap img) {
        image = img;
    }

    public static int getCameraID() {
        return cameraID;
    }

    public static void setCameraID(int camID) {
        cameraID = camID;
    }

    public static String getCameraIDs() { return cameraIDs; }

    public static void setCameraIDs(String camID) { cameraIDs = camID; }

    public static boolean isForwardFacingLens() { return forwardFacingLens; }

    public static void setforwardFacingLens(boolean facingForward) { forwardFacingLens = facingForward; }

}
