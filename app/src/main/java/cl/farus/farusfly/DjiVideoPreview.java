package cl.farus.farusfly;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.view.TextureView;
import dji.sdk.camera.VideoFeeder;
import dji.sdk.codec.DJICodecManager;

/** DJI SDK V4 video preview; read-only. Never transmits flight commands. */
public final class DjiVideoPreview implements TextureView.SurfaceTextureListener {
    public interface Listener { void onStatus(String message); }
    private final Context context;
    private final TextureView view;
    private final Listener listener;
    private DJICodecManager codec;
    private VideoFeeder.VideoDataListener feedListener;
    private VideoFeeder.VideoFeed feed;
    private boolean active;
    public DjiVideoPreview(Context context, TextureView view, Listener listener) {
        this.context=context; this.view=view; this.listener=listener;
    }
    public void start() {
        if(active) return;
        active=true;
        view.setSurfaceTextureListener(this);
        if(view.isAvailable()) attach(view.getSurfaceTexture(),view.getWidth(),view.getHeight());
    }
    private void attach(SurfaceTexture texture,int width,int height) {
        if(!active || codec!=null || width<=0 || height<=0) return;
        try {
            codec=new DJICodecManager(context,texture,width,height);
            VideoFeeder feeder=VideoFeeder.getInstance();
            if(feeder==null || feeder.getPrimaryVideoFeed()==null) {
                listener.onStatus("Video DJI no disponible. Comprueba la conexión.");return;
            }
            feed=feeder.getPrimaryVideoFeed();
            feedListener=(bytes,size)->{ if(codec!=null && bytes!=null && size>0) codec.sendDataToDecoder(bytes,size); };
            feed.addVideoDataListener(feedListener);
            listener.onStatus("Vista DJI iniciada (sin comandos de vuelo).");
        } catch(Throwable error) {listener.onStatus("Error de video DJI: "+error.getClass().getSimpleName());stop();}
    }
    public void stop() {
        active=false;
        if(feed!=null && feedListener!=null) feed.removeVideoDataListener(feedListener);
        feed=null;feedListener=null;
        if(codec!=null) {codec.cleanSurface();codec.destroyCodec();codec=null;}
    }
    @Override public void onSurfaceTextureAvailable(SurfaceTexture texture,int width,int height) {attach(texture,width,height);}
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture texture,int width,int height) {}
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {stop();return true;}
    @Override public void onSurfaceTextureUpdated(SurfaceTexture texture) {}
}
