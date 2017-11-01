package cn.georgeyang.floorrefreshlayout;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;

/**
 * bitmap居中纵向拉伸
 * Created by george.yang on 17/7/11.
 */

public class VerticalCenterDrawable extends Drawable {
    private Paint mPaint;
    private RectF bmpRect;
    private BitmapShader bitmapShader;
    private int bmWidth;

    public VerticalCenterDrawable(Bitmap bitmap) {
        mPaint = new Paint();

        bmWidth = bitmap.getWidth();
        //Shader.TileMode里有三种模式：CLAMP（拉伸）、MIRROR（镜像）、REPETA（重复）
        if (bitmapShader==null) {
            bitmapShader = new BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
            mPaint.setShader(bitmapShader); //设置BitmapShader之后相当于绘制了底层的图片背景
        }
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);

        float startX = bounds.width() / 2 - bmWidth / 2;
        bmpRect = new RectF(startX, 0, startX + bmWidth, bounds.height());
        Matrix matrix = new Matrix();
        matrix.postTranslate(startX,0);
        bitmapShader.setLocalMatrix(matrix);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        canvas.drawRect(bmpRect,mPaint);
    }

    @Override
    public void setAlpha(int alpha) {
        mPaint.setAlpha(alpha);
    }
    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        mPaint.setColorFilter(colorFilter);
    }
    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

}
