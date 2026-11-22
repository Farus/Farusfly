package cl.farus.farusfly;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Process;

/** Retains only the last DJI step locally so a process exit can be diagnosed on reopening. */
final class DjiDiagnostics {
    private final Context context;
    private final SharedPreferences preferences;

    DjiDiagnostics(Context context) {
        this.context = context;
        preferences = context.getSharedPreferences("dji_diagnostics", Context.MODE_PRIVATE);
    }

    void record(String stage) {
        // Commit before the next SDK call, which may end this process.
        preferences.edit()
                .putString("stage", stage)
                .putInt("pid", Process.myPid())
                .putLong("timestamp", System.currentTimeMillis())
                .commit();
    }

    void installCrashRecorder() {
        Thread.UncaughtExceptionHandler androidHandler = Thread.getDefaultUncaughtExceptionHandler();
        if (androidHandler == null) return;
        Thread.setDefaultUncaughtExceptionHandler((thread, failure) -> {
            try {
                if (preferences.getInt("pid", 0) == Process.myPid()) {
                    Throwable cause = failure;
                    for (int depth = 0; depth < 8 && cause.getCause() != null; depth++) {
                        cause = cause.getCause();
                    }
                    StackTraceElement[] frames = cause.getStackTrace();
                    String location = frames.length > 0
                            ? frames[0].getClassName() + "." + frames[0].getMethodName() : "";
                    // Retain only names from the stack, never exception messages or identifiers.
                    preferences.edit()
                            .putInt("exceptionPid", Process.myPid())
                            .putString("exception", cause.getClass().getName() + " · " + location)
                            .commit();
                }
            } finally {
                androidHandler.uncaughtException(thread, failure);
            }
        });
    }

    String previousInterruption() {
        int pid = preferences.getInt("pid", 0);
        String stage = preferences.getString("stage", null);
        if (pid == 0 || pid == Process.myPid() || stage == null) return null;
        String lastStep = "Último paso DJI: " + stage + ".";
        if (preferences.getInt("exceptionPid", 0) == pid) {
            String exception = preferences.getString("exception", "");
            if (!exception.isEmpty()) lastStep += "\nError: " + exception;
        }
        if (Build.VERSION.SDK_INT < 30) return lastStep;

        ActivityManager manager = context.getSystemService(ActivityManager.class);
        if (manager == null) return lastStep;
        try {
            long timestamp = preferences.getLong("timestamp", 0);
            for (ApplicationExitInfo exit : manager.getHistoricalProcessExitReasons(null, pid, 5)) {
                if (exit.getTimestamp() < timestamp) continue;
                switch (exit.getReason()) {
                    case ApplicationExitInfo.REASON_CRASH_NATIVE:
                        return "Android registró un fallo nativo. " + lastStep;
                    case ApplicationExitInfo.REASON_CRASH:
                        return "Android registró un cierre inesperado. " + lastStep;
                    case ApplicationExitInfo.REASON_ANR:
                        return "Android detectó que FarusFly dejó de responder. " + lastStep;
                    default:
                        return lastStep;
                }
            }
        } catch (RuntimeException error) {
            // The saved phase remains useful when the system history is unavailable.
        }
        return lastStep;
    }
}
