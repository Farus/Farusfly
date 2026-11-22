package cl.farus.farusfly;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

/** Read-only visual overlay. It never controls the aircraft. */
public final class ObstacleOverlayView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<RectF> boxes = new ArrayList<>();
    private String message = "IA visual detenida";
    private boolean active;

    public ObstacleOverlayView(Context context) { super(context); setWillNotDraw(false); }

    public void setResult(List<RectF> detections, String status, boolean enabled) {
        boxes.clear();
        if (detections != null) for (RectF r : detections) boxes.add(new RectF(r));
        message = status == null ? "" : status;
        active = enabled;
        postInvalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (getWidth() <= 0 || getHeight() <= 0) return;
        float w = getWidth(), h = getHeight();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2f, w * 0.006f));
        paint.setColor(active ? Color.rgb(255, 190, 40) : Color.argb(140, 220, 230, 230));
        RectF zone = new RectF(w * 0.25f, h * 0.25f, w * 0.75f, h * 0.75f);
        canvas.drawRect(zone, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(210, 0, 0, 0));
        canvas.drawRoundRect(new RectF(8, 8, w - 8, Math.max(44, h * 0.09f)), 8, 8, paint);
        paint.setColor(Color.WHITE);
        paint.setTextSize(Math.max(13f, w * 0.038f));
        canvas.drawText(message, 18, Math.max(31, h * 0.055f), paint);

        boolean alert = false;
        for (RectF box : boxes) {
            RectF mapped = new RectF(box.left * w, box.top * h, box.right * w, box.bottom * h);
            boolean inZone = RectF.intersects(mapped, zone);
            alert |= inZone;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(3f, w * 0.008f));
            paint.setColor(inZone ? Color.RED : Color.rgb(50, 230, 170));
            canvas.drawRect(mapped, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setTextSize(Math.max(12f, w * 0.034f));
            canvas.drawText(inZone ? "OBJETO EN ZONA" : "OBJETO VISIBLE",
                    Math.max(6, mapped.left), Math.max(58, mapped.top - 6), paint);
        }
        if (active) {
            String warning = alert ? "ALERTA VISUAL: objeto dentro de zona virtual" : "ZONA VIRTUAL CENTRAL · solo alerta visual";
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(alert ? Color.RED : Color.rgb(255, 190, 40));
            paint.setTextSize(Math.max(12f, w * 0.034f));
            canvas.drawText(warning, 12, h - 16, paint);
        }
    }
}