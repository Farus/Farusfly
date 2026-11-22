package cl.farus.farusfly;

import android.app.Application;
import android.content.Context;
import androidx.multidex.MultiDex;
import com.cySdkyc.clx.Helper;

/** Required DJI V4 native library initialization before any SDK access. */
public final class FarusApplication extends Application {
    @Override public void onCreate() {
        super.onCreate();
        new DjiDiagnostics(this).installCrashRecorder();
    }

    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        MultiDex.install(this);
        Helper.install(this);
    }
}
