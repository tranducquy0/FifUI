package dev.rmx3834.circularbattery;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

public final class CircularBatteryDrawable extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arc = new RectF();
    private int level;
    private int color;
    private boolean charging;
    private boolean powerSave;

    public CircularBatteryDrawable() {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        color = 0xffffffff;
    }

    public void setLevel(int level) {
        this.level = Math.max(0, Math.min(100, level));
    }

    public void setColor(int color) {
        this.color = color;
    }

    public void setCharging(boolean charging) {
        this.charging = charging;
    }

    public void setPowerSave(boolean powerSave) {
        this.powerSave = powerSave;
    }

    @Override
    public void draw(Canvas canvas) {
        float inset = Math.max(1f, Math.min(getBounds().width(), getBounds().height()) * 0.12f);
        arc.set(getBounds().left + inset, getBounds().top + inset,
                getBounds().right - inset, getBounds().bottom - inset);
        paint.setStrokeWidth(Math.max(2f, arc.width() * 0.15f));
        paint.setColor(color);
        paint.setAlpha(80);
        canvas.drawArc(arc, -90f, 360f, false, paint);
        paint.setAlpha(255);
        canvas.drawArc(arc, -90f, 360f * level / 100f, false, paint);
        if (charging) {
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(arc.centerX(), arc.centerY(), paint.getStrokeWidth() * 0.7f, paint);
            paint.setStyle(Paint.Style.STROKE);
        } else if (powerSave) {
            paint.setStrokeWidth(Math.max(2f, arc.width() * 0.1f));
            canvas.drawLine(arc.centerX() - arc.width() * 0.18f, arc.centerY(),
                    arc.centerX() + arc.width() * 0.18f, arc.centerY(), paint);
        }
    }

    @Override
    public int getIntrinsicWidth() {
        return 20;
    }

    @Override
    public int getIntrinsicHeight() {
        return 20;
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return android.graphics.PixelFormat.TRANSLUCENT;
    }
}
