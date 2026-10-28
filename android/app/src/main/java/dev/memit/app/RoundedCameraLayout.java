package dev.memit.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.util.AttributeSet;
import android.widget.FrameLayout;

/** Clip TextureView, review and permission states identically in hardware and software rendering. */
public final class RoundedCameraLayout extends FrameLayout {
    private final Path outline = new Path();
    public RoundedCameraLayout(Context context, AttributeSet attributes) { super(context, attributes); }
    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        float radius = 28 * getResources().getDisplayMetrics().density;
        outline.reset();
        outline.addRoundRect(0, 0, width, height, radius, radius, Path.Direction.CW);
    }
    @Override protected void dispatchDraw(Canvas canvas) {
        int checkpoint = canvas.save();
        canvas.clipPath(outline);
        super.dispatchDraw(canvas);
        canvas.restoreToCount(checkpoint);
    }
}
