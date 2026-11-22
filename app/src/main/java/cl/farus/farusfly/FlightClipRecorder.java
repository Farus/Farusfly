package cl.farus.farusfly;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.view.Surface;
import android.view.TextureView;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.function.BooleanSupplier;

/** Saves the decoded DJI preview and microphone as a single MP4. */
final class FlightClipRecorder {
    interface Listener { void onStatus(String message, Uri savedClip); }

    private enum State { IDLE, STARTING, RECORDING, FINISHING }

    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final long FRAME_INTERVAL_MS = 83;
    private final Context context;
    private final TextureView preview;
    private final BooleanSupplier hasRecentFrame;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final HandlerThread encoderThread = new HandlerThread("FarusFly-clip");
    private final Handler encoder;
    private final Paint imagePaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Runnable captureTask = this::captureFrame;
    private State state = State.IDLE;
    private boolean closed;
    private long startedAt;
    private long lastShownSecond = -1;

    // Accessed only on the encoder thread.
    private MediaRecorder recorder;
    private Surface surface;
    private File pendingFile;
    private int framesWritten;

    FlightClipRecorder(Context context, TextureView preview, BooleanSupplier hasRecentFrame, Listener listener) {
        this.context = context.getApplicationContext();
        this.preview = preview;
        this.hasRecentFrame = hasRecentFrame;
        this.listener = listener;
        encoderThread.start();
        encoder = new Handler(encoderThread.getLooper());
    }

    boolean isBusy() { return state != State.IDLE; }
    boolean isRecording() { return state == State.RECORDING; }
    boolean isFinishing() { return state == State.STARTING || state == State.FINISHING; }

    void start() {
        if (closed || isBusy() || !hasRecentFrame.getAsBoolean()) return;
        state = State.STARTING;
        listener.onStatus("Preparando grabación…", null);
        encoder.post(this::prepare);
    }

    private void prepare() {
        try {
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.ROOT).format(new Date());
            pendingFile = new File(context.getFilesDir(), "FarusFly_" + timestamp + ".mp4");
            recorder = Build.VERSION.SDK_INT >= 31 ? new MediaRecorder(context) : new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            recorder.setVideoSize(WIDTH, HEIGHT);
            recorder.setVideoFrameRate(12);
            recorder.setVideoEncodingBitRate(3_000_000);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioChannels(1);
            recorder.setAudioSamplingRate(44_100);
            recorder.setAudioEncodingBitRate(96_000);
            recorder.setOutputFile(pendingFile.getAbsolutePath());
            recorder.prepare();
            surface = recorder.getSurface();
            recorder.start();
            framesWritten = 0;
            main.post(() -> {
                if (state != State.STARTING || closed) return;
                state = State.RECORDING;
                startedAt = SystemClock.elapsedRealtime();
                lastShownSecond = -1;
                captureFrame();
            });
        } catch (IOException | RuntimeException failure) {
            discard();
            complete("No se pudo iniciar la grabación: " + failure.getClass().getSimpleName(), null);
        }
    }

    private void captureFrame() {
        if (state != State.RECORDING) return;
        if (!hasRecentFrame.getAsBoolean() || !preview.isAvailable()) {
            finish("Se perdió la imagen DJI. ");
            return;
        }
        long frameStart = SystemClock.elapsedRealtime();
        long seconds = (frameStart - startedAt) / 1000;
        if (seconds != lastShownSecond) {
            lastShownSecond = seconds;
            listener.onStatus(String.format(Locale.ROOT, "● Grabando %02d:%02d · video y micrófono",
                    seconds / 60, seconds % 60), null);
        }
        Bitmap frame;
        try {
            int previewWidth = preview.getWidth();
            int previewHeight = preview.getHeight();
            float ratio = Math.min(1f, Math.min(WIDTH / (float) previewWidth, HEIGHT / (float) previewHeight));
            frame = preview.getBitmap(Math.max(1, Math.round(previewWidth * ratio)),
                    Math.max(1, Math.round(previewHeight * ratio)));
        } catch (RuntimeException failure) {
            finish("Se interrumpió la lectura de imagen. ");
            return;
        }
        if (frame == null) {
            finish("Se interrumpió la lectura de imagen. ");
            return;
        }
        encoder.post(() -> writeFrame(frame, frameStart));
    }

    private void writeFrame(Bitmap frame, long frameStart) {
        try {
            Canvas canvas = surface.lockHardwareCanvas();
            try {
                canvas.drawColor(Color.BLACK);
                float scale = Math.min(canvas.getWidth() / (float) frame.getWidth(),
                        canvas.getHeight() / (float) frame.getHeight());
                int width = Math.round(frame.getWidth() * scale);
                int height = Math.round(frame.getHeight() * scale);
                int left = (canvas.getWidth() - width) / 2;
                int top = (canvas.getHeight() - height) / 2;
                canvas.drawBitmap(frame, null, new Rect(left, top, left + width, top + height), imagePaint);
            } finally {
                surface.unlockCanvasAndPost(canvas);
            }
            framesWritten++;
            main.postDelayed(captureTask,
                    Math.max(0, FRAME_INTERVAL_MS - (SystemClock.elapsedRealtime() - frameStart)));
        } catch (RuntimeException failure) {
            main.post(() -> finish("El codificador de video se interrumpió. "));
        } finally {
            frame.recycle();
        }
    }

    void stop() { finish(""); }

    private void finish(String prefix) {
        if (state != State.RECORDING && state != State.STARTING) return;
        state = State.FINISHING;
        main.removeCallbacks(captureTask);
        listener.onStatus("Guardando MP4…", null);
        encoder.post(() -> save(prefix));
    }

    private void save(String prefix) {
        if (recorder == null) return;
        File clip = pendingFile;
        try {
            recorder.stop();
        } catch (RuntimeException failure) {
            discard();
            complete(prefix + "No se pudo guardar. Intenta grabar al menos tres segundos.", null);
            return;
        }
        release();
        try {
            if (framesWritten == 0 || !hasAudioAndVideo(clip)) {
                throw new IOException("Pistas incompletas");
            }
        } catch (IOException | RuntimeException failure) {
            discard();
            complete(prefix + "El archivo quedó incompleto. Intenta grabar al menos tres segundos.", null);
            return;
        }
        pendingFile = null;
        try {
            Uri uri = saveToGallery(clip);
            if (uri != null) {
                clip.delete();
                complete(prefix + "MP4 guardado en Galería → FarusFly.", uri);
                return;
            }
        } catch (IOException | RuntimeException failure) {
            // Retain the verified MP4 locally if the gallery is unavailable.
        }
        Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".provider", clip);
        complete(prefix + "MP4 guardado. Pulsa Compartir último MP4.", uri);
    }

    private boolean hasAudioAndVideo(File clip) throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(clip.getAbsolutePath());
            boolean hasAudio = false;
            boolean hasVideo = false;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime == null) continue;
                if (mime.startsWith("audio/")) hasAudio = true;
                if (mime.startsWith("video/")) hasVideo = true;
            }
            return hasAudio && hasVideo;
        } finally {
            extractor.release();
        }
    }

    private Uri saveToGallery(File clip) throws IOException {
        if (Build.VERSION.SDK_INT < 29) return null;
        ContentResolver resolver = context.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.Video.Media.DISPLAY_NAME, clip.getName());
        values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
        values.put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/FarusFly");
        values.put(MediaStore.Video.Media.IS_PENDING, 1);
        Uri uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) return null;
        boolean published = false;
        try {
            try (FileInputStream in = new FileInputStream(clip); OutputStream out = resolver.openOutputStream(uri)) {
                if (out == null) throw new IOException("Galería no disponible");
                byte[] buffer = new byte[64 * 1024];
                for (int count; (count = in.read(buffer)) != -1;) out.write(buffer, 0, count);
            }
            values.clear();
            values.put(MediaStore.Video.Media.IS_PENDING, 0);
            if (resolver.update(uri, values, null, null) != 1) throw new IOException("No se publicó el video");
            published = true;
            return uri;
        } finally {
            if (!published) resolver.delete(uri, null, null);
        }
    }

    private void complete(String message, Uri uri) {
        main.post(() -> {
            main.removeCallbacks(captureTask);
            state = State.IDLE;
            if (!closed) listener.onStatus(message, uri);
        });
    }

    private void discard() {
        release();
        if (pendingFile != null) pendingFile.delete();
        pendingFile = null;
    }

    private void release() {
        if (surface != null) surface.release();
        if (recorder != null) recorder.release();
        surface = null;
        recorder = null;
    }

    void close() {
        stop();
        closed = true;
        encoderThread.quitSafely();
    }
}
