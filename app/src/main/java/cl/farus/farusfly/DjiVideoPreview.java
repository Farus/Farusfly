package cl.farus.farusfly;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.os.SystemClock;
import android.view.TextureView;
import java.util.function.Consumer;
import dji.sdk.camera.VideoFeeder;
import dji.sdk.codec.DJICodecManager;

/** Displays live DJI video on the texture used by visual detection. */
public final class DjiVideoPreview implements TextureView.SurfaceTextureListener {
    public interface Listener { void onStatus(String message); }
    private final Context context;
    private final TextureView view;
    private final Listener listener;
    private final Consumer<String> stage;
    private final Object decoderLock = new Object();
    private DJICodecManager codec;
    private VideoFeeder.VideoDataListener feedListener;
    private VideoFeeder.VideoFeed feed;
    private boolean active;
    private boolean connected;
    private boolean rendered;
    private long lastFrameAt;
    private volatile boolean receivedData;
    private final Runnable frameTimeout = this::showFrameTimeout;

    public DjiVideoPreview(Context context, TextureView view, Listener listener,
                           Consumer<String> stage) {
        this.context = context;
        this.view = view;
        this.listener = listener;
        this.stage = stage;
        view.setSurfaceTextureListener(this);
    }

    public void start() {
        if (!active) active = true;
        if (feed != null && !rendered) detach();
        attach();
    }

    public void setProductConnected(boolean connected) {
        this.connected = connected;
        if (!connected) {
            detach();
            listener.onStatus("Cámara en espera de conexión DJI.");
        } else if (active) {
            attach();
        } else {
            listener.onStatus("Mini 2 conectado. Pulsa Activar vista DJI.");
        }
    }

    private void attach() {
        if (!active || feed != null) return;
        if (!connected) {
            listener.onStatus("Cámara en espera de conexión DJI.");
            return;
        }
        if (!view.isAvailable() || view.getWidth() <= 0 || view.getHeight() <= 0) return;

        try {
            stage.accept("selección de la señal de cámara");
            VideoFeeder feeder = VideoFeeder.getInstance();
            if (feeder != null) {
                feed = feeder.isLensDistortionCalibrationNeeded() || feeder.isFetchKeyFrameNeeded()
                        ? feeder.provideTranscodedVideoFeed() : feeder.getPrimaryVideoFeed();
            }
            if (feed == null) {
                listener.onStatus("DJI conectado, pero la cámara aún no ofrece video. Pulsa Activar vista DJI para reintentar.");
                return;
            }

            stage.accept("inicio del decodificador de video DJI");
            DJICodecManager decoder = new DJICodecManager(context, view.getSurfaceTexture(),
                    view.getWidth(), view.getHeight());
            synchronized (decoderLock) {
                codec = decoder;
            }
            feedListener = (bytes, size) -> {
                if (bytes == null || size <= 0) return;
                synchronized (decoderLock) {
                    if (codec != decoder) return;
                    receivedData = true;
                    decoder.sendDataToDecoder(bytes, size);
                }
            };
            stage.accept("recepción y decodificación del video DJI");
            feed.addVideoDataListener(feedListener);
            listener.onStatus("Esperando imagen de la cámara DJI…");
            view.postDelayed(frameTimeout, 8000);
        } catch (RuntimeException | LinkageError error) {
            detach();
            listener.onStatus("Error de cámara DJI: " + error);
        }
    }

    private void showFrameTimeout() {
        if (!active || rendered || feed == null) return;
        listener.onStatus(receivedData
                ? "DJI envía video, pero el teléfono aún no muestra la imagen. Pulsa Activar vista DJI para reintentar."
                : "DJI conectado, pero sin datos de cámara. Revisa el enlace del control con el Mini 2.");
    }

    public void stop() {
        active = false;
        detach();
        listener.onStatus("Cámara detenida.");
    }

    boolean hasRecentFrame() {
        return rendered && SystemClock.elapsedRealtime() - lastFrameAt <= 2000;
    }

    private void detach() {
        view.removeCallbacks(frameTimeout);
        if (feed != null && feedListener != null) feed.removeVideoDataListener(feedListener);
        feed = null;
        feedListener = null;
        DJICodecManager decoder;
        synchronized (decoderLock) {
            decoder = codec;
            codec = null;
            receivedData = false;
        }
        rendered = false;
        if (decoder != null) {
            decoder.cleanSurface();
            decoder.destroyCodec();
        }
    }

    @Override public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
        attach();
    }

    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
        if (feed == null) attach();
    }

    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
        detach();
        return true;
    }

    @Override public void onSurfaceTextureUpdated(SurfaceTexture texture) {
        if (feed == null || !receivedData) return;
        lastFrameAt = SystemClock.elapsedRealtime();
        if (!rendered) {
            rendered = true;
            view.removeCallbacks(frameTimeout);
            stage.accept("cámara DJI en directo");
            listener.onStatus("Cámara DJI en directo.");
        }
    }
}
