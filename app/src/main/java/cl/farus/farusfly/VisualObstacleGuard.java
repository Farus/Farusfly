package cl.farus.farusfly;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.view.TextureView;
import com.google.mlkit.common.MlKitException;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.objects.DetectedObject;
import com.google.mlkit.vision.objects.ObjectDetection;
import com.google.mlkit.vision.objects.ObjectDetector;
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/** Analyzes decoded camera frames and displays visual alerts. */
public final class VisualObstacleGuard {
    public interface StatusListener { void onStatus(String message, boolean alert); }

    private static final long FRAME_INTERVAL_MS = 850;
    private static final int MAX_FRAME_DIMENSION = 640;
    private final TextureView preview;
    private final ObstacleOverlayView overlay;
    private final BooleanSupplier hasRecentFrame;
    private final StatusListener status;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable frameTask = this::analyzeFrame;
    private ObjectDetector detector;
    private int framesAnalyzed;

    public VisualObstacleGuard(TextureView preview, ObstacleOverlayView overlay,
                               BooleanSupplier hasRecentFrame, StatusListener status) {
        this.preview = preview;
        this.overlay = overlay;
        this.hasRecentFrame = hasRecentFrame;
        this.status = status;
    }

    public void start() {
        if (detector != null) return;
        try {
            ObjectDetectorOptions options = new ObjectDetectorOptions.Builder()
                    .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
                    .enableMultipleObjects()
                    .build();
            detector = ObjectDetection.getClient(options);
        } catch (RuntimeException error) {
            status.onStatus("No se pudo iniciar la IA: " + error.getClass().getSimpleName(), false);
            return;
        }
        framesAnalyzed = 0;
        status.onStatus("IA iniciando…", false);
        main.post(frameTask);
    }

    public void stop() {
        main.removeCallbacks(frameTask);
        ObjectDetector previous = detector;
        detector = null;
        if (previous != null) previous.close();
        overlay.setResult(java.util.Collections.emptyList(), false);
        status.onStatus("IA detenida.", false);
    }

    private void analyzeFrame() {
        ObjectDetector current = detector;
        if (current == null) return;

        int width = preview.getWidth();
        int height = preview.getHeight();
        if (!hasRecentFrame.getAsBoolean() || !preview.isAvailable() || width <= 0 || height <= 0) {
            clearAndRetry("IA en espera de imagen en directo.");
            return;
        }

        float scale = Math.min(1f, MAX_FRAME_DIMENSION / (float) Math.max(width, height));
        Bitmap frame;
        try {
            frame = preview.getBitmap(Math.max(1, Math.round(width * scale)),
                    Math.max(1, Math.round(height * scale)));
        } catch (RuntimeException error) {
            clearAndRetry("IA: no se pudo leer la imagen (" + error.getClass().getSimpleName() + ").");
            return;
        }
        if (frame == null) {
            clearAndRetry("IA: la vista está abierta, pero aún no entrega una imagen para analizar.");
            return;
        }

        try {
            current.process(InputImage.fromBitmap(frame, 0)).addOnCompleteListener(task -> {
                try {
                    if (detector != current) return;
                    if (!hasRecentFrame.getAsBoolean()) {
                        clearAndRetry("IA en espera de imagen en directo.");
                    } else if (task.isSuccessful()) {
                        showDetections(task.getResult(), frame.getWidth(), frame.getHeight());
                        main.postDelayed(frameTask, FRAME_INTERVAL_MS);
                    } else {
                        clearAndRetry(modelError(task.getException()));
                    }
                } finally {
                    frame.recycle();
                }
            });
        } catch (RuntimeException error) {
            frame.recycle();
            clearAndRetry(modelError(error));
        }
    }

    private void showDetections(List<DetectedObject> objects, int width, int height) {
        List<RectF> boxes = new ArrayList<>();
        for (DetectedObject object : objects) {
            Rect box = object.getBoundingBox();
            RectF normalized = new RectF(box.left / (float) width, box.top / (float) height,
                    box.right / (float) width, box.bottom / (float) height);
            if (normalized.intersect(0f, 0f, 1f, 1f)) boxes.add(normalized);
        }
        boolean alert = overlay.setResult(boxes, true);
        framesAnalyzed++;
        String result = alert ? "ALERTA: objeto en la zona central."
                : boxes.isEmpty() ? "Sin objetos detectados." : "Objeto detectado fuera de la zona central.";
        status.onStatus(result + " Imágenes analizadas: " + framesAnalyzed, alert);
    }

    private void clearAndRetry(String message) {
        overlay.setResult(java.util.Collections.emptyList(), true);
        status.onStatus(message, false);
        main.postDelayed(frameTask, FRAME_INTERVAL_MS);
    }

    private String modelError(Exception error) {
        if (error instanceof MlKitException) {
            return "IA: error del modelo (" + ((MlKitException) error).getErrorCode()
                    + "). Si es el primer uso, comprueba Internet.";
        }
        return "IA: no se pudo analizar la imagen"
                + (error == null ? "." : " (" + error.getClass().getSimpleName() + ").");
    }
}
