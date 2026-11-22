package cl.farus.farusfly;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

/** Draws the central detection zone and the objects reported in the current frame. */
public final class ObstacleOverlayView extends View {
    private static final RectF ZONE = new RectF(0.25f, 0.25f, 0.75f, 0.75f);
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<RectF> boxes = new ArrayList<>();
    private boolean active;
    private boolean alert;

    public ObstacleOverlayView(Context context) {
        super(context);
    }

    public boolean setResult(List<RectF> detections, boolean enabled) {
        boxes.clear();
        boxes.addAll(detections);
        active = enabled;
        alert = false;
        for (RectF box : boxes) {
            if (RectF.intersects(box, ZONE)) {
                alert = true;
                break;
            }
        }
        invalidate();
        return alert;
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!active) return;
        float width = getWidth();
        float height = getHeight();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(3f, width * 0.006f));
        paint.setColor(alert ? Color.RED : Color.rgb(255, 190, 40));
        drawBox(canvas, ZONE, width, height);
        for (RectF box : boxes) {
            paint.setColor(RectF.intersects(box, ZONE) ? Color.RED : Color.rgb(50, 230, 170));
            drawBox(canvas, box, width, height);
        }
    }

    private void drawBox(Canvas canvas, RectF box, float width, float height) {
        canvas.drawRect(box.left * width, box.top * height, box.right * width, box.bottom * height, paint);
    }
}
