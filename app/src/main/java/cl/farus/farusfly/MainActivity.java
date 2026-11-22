package cl.farus.farusfly;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.TextureView;
import android.widget.FrameLayout;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.VideoView;
import androidx.core.content.FileProvider;
import java.io.File;
import java.util.Locale;

/** FarusFly offline prototype: local microphone, video preview, safe simulator. No DJI control. */
public class MainActivity extends Activity {
    private static final int MIC_PERMISSION=100, PICK_VIDEO=101;
    private MediaRecorder recorder;
    private TextView status, videoLabel;
    private Button recordButton;
    private VideoView videoView;
    private TextureView djiPreview;
    private DjiVideoPreview djiVideo;
    private ObstacleOverlayView obstacleOverlay;
    private VisualObstacleGuard visualGuard;
    private boolean recording=false;
    private long startElapsed;
    private File lastRecording;
    private final FollowSimulator simulator=new FollowSimulator();
    private DjiConnection djiConnection;
    private int simulationStep=0;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScrollView scroll=new ScrollView(this);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(30,40,30,40);
        root.setBackgroundColor(Color.rgb(12,35,43));
        scroll.addView(root);
        label(root,"FarusFly",32,Color.WHITE);
        label(root,"DJI Mini 2 · IA visual · Audio y video local",15,0xFF8BE0CB);
        status=label(root,"SIN CONEXIÓN DJI · CONTROL REAL BLOQUEADO",16,Color.WHITE);
        status.setPadding(0,30,0,24);
        djiConnection = new DjiConnection(this, message -> runOnUiThread(() -> status.setText(message)));
        button(root,"Conectar DJI Mini 2 (SDK V4 · solo lectura)",v -> {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},102);
            } else { djiConnection.register(); }
        });
        label(root,"CÁMARA DJI EN DIRECTO (solo lectura)",18,0xFF8BE0CB);
        FrameLayout previewFrame=new FrameLayout(this);
        djiPreview=new TextureView(this);
        obstacleOverlay=new ObstacleOverlayView(this);
        previewFrame.addView(djiPreview,new FrameLayout.LayoutParams(-1,-1));
        previewFrame.addView(obstacleOverlay,new FrameLayout.LayoutParams(-1,-1));
        root.addView(previewFrame,new LinearLayout.LayoutParams(-1,520));
        djiVideo=new DjiVideoPreview(this,djiPreview, message -> runOnUiThread(() -> status.setText(message)));
        visualGuard=new VisualObstacleGuard(djiPreview,obstacleOverlay);
        button(root,"Activar vista DJI",v -> djiVideo.start());
        button(root,"Desactivar vista DJI",v -> {visualGuard.stop();djiVideo.stop();});
        button(root,"Iniciar alertas visuales IA (zona virtual)",v -> visualGuard.start());
        button(root,"Detener alertas visuales IA",v -> visualGuard.stop());
        recordButton=button(root,"● Iniciar grabación de audio ambiente",v->{if(recording)stopAudio();else requestAudio();});
        button(root,"Compartir última grabación M4A",v->shareAudio());
        videoLabel=label(root,"Video: ninguno seleccionado",14,0xFFB7C9CB);
        videoView=new VideoView(this);
        root.addView(videoView,new LinearLayout.LayoutParams(-1,520));
        button(root,"Abrir video local (MP4)",v->{
            Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("video/*");
            startActivityForResult(intent,PICK_VIDEO);
        });
        button(root,"▶ Reproducir video",v->{if(videoView.getTag()!=null)videoView.start();});
        button(root,"■ Pausar video",v->videoView.pause());
        label(root,"SEGUIMIENTO TRASERO · SOLO SIMULACIÓN",18,0xFF8BE0CB);
        button(root,"Simular objetivo moviéndose",v->simulate());
        button(root,"DETENER SIMULACIÓN",v->{simulationStep=0;status.setText("Simulación detenida. No se han enviado comandos al dron.");});
        label(root,"La IA analiza visualmente la imagen de la cámara y dibuja cuadros de objetos con una zona virtual central de alerta.\n"
            +"No calcula distancia ni trayectoria, no detecta todos los obstáculos y no mueve ni detiene el dron.\n"
            +"Mini 2: sin evitación frontal/lateral/trasera. Mantén control manual y vigilancia directa.\n"
            +"Micrófono junto al teléfono; audio y video local no se sincronizan.",13,0xFFB7C9CB);
        setContentView(scroll);
    }
    private TextView label(LinearLayout root,String value,int size,int color){
        TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);
        t.setPadding(0,9,0,9);root.addView(t);return t;
    }
    private Button button(LinearLayout root,String title,View.OnClickListener listener){
        Button b=new Button(this);b.setText(title);b.setOnClickListener(listener);root.addView(b);return b;
    }
    private void simulate(){
        double[] positions={0.5,0.65,0.8,0.7,0.45,0.3,0.5};
        double x=positions[simulationStep++%positions.length];
        FollowSimulator.Result r=simulator.update(x,0.12,true,SystemClock.elapsedRealtime());
        status.setText(String.format(Locale.US,"%s\nObjetivo X: %.2f\nGiro calculado: %.3f (solo simulación)\nAvance: %.1f\nCOMANDOS REALES: BLOQUEADOS",r.state,x,r.yaw,r.forward));
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==PICK_VIDEO && result==RESULT_OK && data!=null && data.getData()!=null){
            Uri uri=data.getData();videoView.setVideoURI(uri);videoView.setTag(uri);
            videoLabel.setText("Video local cargado. Audio y video NO están sincronizados automáticamente.");
        }
    }
    private void requestAudio(){
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},MIC_PERMISSION);
        else startAudio();
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results){
        super.onRequestPermissionsResult(code,permissions,results);
        if(code==102 && results.length>0 && results[0]==PackageManager.PERMISSION_GRANTED) djiConnection.register();
        else if(code==MIC_PERMISSION && results.length>0 && results[0]==PackageManager.PERMISSION_GRANTED)startAudio();
        else status.setText("Permiso no concedido.");
    }
    private void startAudio(){
        try{
            lastRecording=new File(getFilesDir(),"farusfly_audio_"+System.currentTimeMillis()+".m4a");
            recorder=Build.VERSION.SDK_INT>=31?new MediaRecorder(this):new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioEncodingBitRate(128000);
            recorder.setAudioSamplingRate(44100);
            recorder.setOutputFile(lastRecording.getAbsolutePath());
            recorder.prepare();recorder.start();
            startElapsed=SystemClock.elapsedRealtime();recording=true;
            recordButton.setText("■ Detener grabación ambiente");
            status.setText("GRABANDO AUDIO AMBIENTE · El vuelo autónomo está deshabilitado.");
        }catch(Exception e){releaseRecorder();status.setText("Error de grabación: "+e.getClass().getSimpleName());}
    }
    private void stopAudio(){
        boolean saved=false;
        try{recorder.stop();saved=true;}catch(Exception e){if(lastRecording!=null)lastRecording.delete();}
        releaseRecorder();recording=false;recordButton.setText("● Iniciar grabación de audio ambiente");
        status.setText(saved?"Audio guardado: "+lastRecording.getName()+" ("+((SystemClock.elapsedRealtime()-startElapsed)/1000)+" s)":"Grabación interrumpida o demasiado corta.");
    }
    private void shareAudio(){
        if(lastRecording==null || !lastRecording.exists() || recording){status.setText("Primero termina una grabación para compartirla.");return;}
        Uri uri=FileProvider.getUriForFile(this,getPackageName()+".provider",lastRecording);
        Intent intent=new Intent(Intent.ACTION_SEND);
        intent.setType("audio/mp4");intent.putExtra(Intent.EXTRA_STREAM,uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent,"Compartir audio FarusFly"));
    }
    private void releaseRecorder(){if(recorder!=null){try{recorder.reset();}catch(Exception ignored){}recorder.release();recorder=null;}}
    @Override protected void onDestroy(){if(visualGuard!=null)visualGuard.stop();if(djiVideo!=null)djiVideo.stop();if(recording)stopAudio();super.onDestroy();}
}
