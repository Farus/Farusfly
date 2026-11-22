package cl.farus.farusfly;

import android.graphics.Bitmap;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.view.TextureView;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.objects.ObjectDetection;
import com.google.mlkit.vision.objects.ObjectDetector;
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions;
import java.util.ArrayList;
import java.util.List;

/**
 * On-device image-only object detection over the DJI preview.
 * Alerts are visual only; this class has no DJI control API.
 */
public final class VisualObstacleGuard {
    private static final long FRAME_INTERVAL_MS = 700;
    private static final int MAX_FRAME_WIDTH = 320;
    private final TextureView preview;
    private final ObstacleOverlayView overlay;
    private final Handler main = new Handler(Looper.getMainLooper());
    private ObjectDetector detector;
    private boolean active;
    private boolean processing;

    public VisualObstacleGuard(TextureView preview, ObstacleOverlayView overlay) {
        this.preview = preview;
        this.overlay = overlay;
    }

    public void start() {
        if (active) return;
        try {
            ObjectDetectorOptions options = new ObjectDetectorOptions.Builder()
                    .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
                    .build();
            detector = ObjectDetection.getClient(options);
        } catch (RuntimeException error) {
            detector = null;
            overlay.setResult(null, "No se pudo iniciar el modelo IA", false);
            return;
        }
        active = true;
        overlay.setResult(null, "IA visual iniciando · alerta solamente", true);
        main.post(frameTask);
    }

    public void stop() {
        active = false;
        main.removeCallbacks(frameTask);
        processing = false;
        if (detector != null) {
            detector.close();
            detector = null;
        }
        overlay.setResult(null, "IA visual detenida", false);
    }

    private final Runnable frameTask = new Runnable() {
        @Override public void run() {
            if (!active) return;
            if (!processing && preview.isAvailable() && preview.getWidth() > 0 && preview.getHeight() > 0) {
                int width = Math.min(MAX_FRAME_WIDTH, preview.getWidth());
                int height = Math.max(1, Math.round(width * preview.getHeight() / (float) preview.getWidth()));
                Bitmap frame = preview.getBitmap(width, height);
                if (frame != null && detector != null) {
                    processing = true;
                    ObjectDetector current = detector;
                    current.process(InputImage.fromBitmap(frame, 0))
                            .addOnSuccessListener(objects -> {
                                if (!active || detector != current) return;
                                List<RectF> boxes = new ArrayList<>();
                                for (com.google.mlkit.vision.objects.DetectedObject object : objects) {
                                    android.graphics.Rect r = object.getBoundingBox();
                                    boxes.add(new RectF(
                                            Math.max(0f, r.left / (float) frame.getWidth()),
                                            Math.max(0f, r.top / (float) frame.getHeight()),
                                            Math.min(1f, r.right / (float) frame.getWidth()),
                                            Math.min(1f, r.bottom / (float) frame.getHeight())));
                                }
                                String text = boxes.isEmpty()
                                        ? "IA visual activa · no se detectan objetos"
                                        : "IA visual activa · " + boxes.size() + " objeto(s) visible(s)";
                                overlay.setResult(boxes, text, true);
                            })
                            .addOnFailureListener(error -> {
                                if (active && detector == current)
                                    overlay.setResult(null, "Error IA visual · revisa modelo/permisos", true);
                            })
                            .addOnCompleteListener(task -> {
                                processing = false;
                                frame.recycle();
                            });
                } else if (frame != null) {
                    frame.recycle();
                }
            } else if (active && !preview.isAvailable()) {
                overlay.setResult(null, "Activa la vista DJI para analizar imagen", true);
            }
            if (active) main.postDelayed(this, FRAME_INTERVAL_MS);
        }
    };
}