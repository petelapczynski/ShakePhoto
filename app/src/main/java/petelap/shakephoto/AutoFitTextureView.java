package petelap.shakephoto;

import android.app.Activity;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.Surface;
import android.view.TextureView;
import android.util.Size;

public class AutoFitTextureView extends TextureView {

    int maxwidth = 0;
    int maxheight = 0;
    private int mRatioWidth = 0;
    private int mRatioHeight = 0;
    private Size previewSize;

    public AutoFitTextureView(Context context) {
        this(context, null);
    }

    public AutoFitTextureView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AutoFitTextureView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setAspectRatio(int width, int height, int maxwidth, int maxheight, Size preview) {
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("Size cannot be negative.");
        }
        mRatioWidth = width;
        mRatioHeight = height;
        this.maxwidth = maxwidth;
        this.maxheight = maxheight;
        this.previewSize = preview;
        enterTheMatrix();
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        boolean isFullBleed = true;
        if (0 == mRatioWidth || 0 == mRatioHeight) {
            setMeasuredDimension(width, height);
        } else {
            setMeasuredDimension(height * mRatioWidth / mRatioHeight,height);
        }

    }

    private void adjustAspectRatio(int previewWidth, int previewHeight, int rotation) {
        Matrix txform = new Matrix();
        int viewWidth = getWidth();
        int viewHeight = getHeight();
        RectF rectView = new RectF(0, 0, viewWidth, viewHeight);
        float viewCenterX = rectView.centerX();
        float viewCenterY = rectView.centerY();
        RectF rectPreview = new RectF(0, 0, previewHeight, previewWidth);
        float previewCenterX = rectPreview.centerX();
        float previewCenterY = rectPreview.centerY();

        if (Surface.ROTATION_90 == rotation ||
                Surface.ROTATION_270 == rotation) {
            rectPreview.offset(viewCenterX - previewCenterX,
                    viewCenterY - previewCenterY);

            txform.setRectToRect(rectView, rectPreview,
                    Matrix.ScaleToFit.FILL);

            float scale = Math.max((float) viewHeight / previewHeight,
                    (float) viewWidth / previewWidth);

            txform.postScale(scale, scale, viewCenterX, viewCenterY);
            txform.postRotate(90 * (rotation - 2), viewCenterX,
                    viewCenterY);
        } else {
            if (Surface.ROTATION_180 == rotation) {
                txform.postRotate(180, viewCenterX, viewCenterY);
            }
        }

        if (CapturedPhotoManager.isForwardFacingLens()) {
            txform.postScale(-1, 1, viewCenterX, viewCenterY);
        }

        setTransform(txform);
    }

    private void enterTheMatrix() {
        if (previewSize != null) {
            adjustAspectRatio(mRatioWidth, mRatioHeight,
                    ((Activity) getContext()).getWindowManager().getDefaultDisplay().getRotation());
        }
    }
}