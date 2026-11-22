package cl.farus.farusfly;

import android.content.Context;
import dji.common.error.DJIError;
import dji.common.error.DJISDKError;
import dji.sdk.base.BaseProduct;
import dji.sdk.sdkmanager.DJISDKManager;
import dji.sdk.sdkmanager.DJISDKInitEvent;

/** Read-only DJI V4 registration and product connectivity; NO flight commands. */
public final class DjiConnection {
    public interface Listener { void onStatus(String message); }
    private final Context context;
    private final Listener listener;
    private boolean registering;
    public DjiConnection(Context context, Listener listener) {
        this.context = context.getApplicationContext(); this.listener = listener;
    }
    public void register() {
        if (registering) return;
        registering = true;
        listener.onStatus("Registrando DJI SDK V4 (solo lectura)...");
        try {
            DJISDKManager.getInstance().registerApp(context, new DJISDKManager.SDKManagerCallback() {
                @Override public void onRegister(DJIError error) {
                    if (error == DJISDKError.REGISTRATION_SUCCESS) {
                        listener.onStatus("SDK registrado. Buscando control remoto DJI...");
                        DJISDKManager.getInstance().startConnectionToProduct();
                    } else {
                        listener.onStatus("Error de registro DJI: " + String.valueOf(error));
                        registering = false;
                    }
                }
                @Override public void onProductDisconnect() {
                    listener.onStatus("DJI desconectado. Control de vuelo BLOQUEADO.");
                }
                @Override public void onProductConnect(BaseProduct product) {
                    listener.onStatus("DJI conectado: " + (product == null ? "desconocido" : product.getModel())
                            + ". Control de vuelo BLOQUEADO.");
                }
                @Override public void onProductChanged(BaseProduct product) {
                    listener.onStatus("DJI cambió: " + (product == null ? "desconectado" : product.getModel()));
                }
                @Override public void onComponentChange(BaseProduct.ComponentKey key,
                        dji.sdk.base.BaseComponent oldComponent, dji.sdk.base.BaseComponent newComponent) { }
                @Override public void onInitProcess(DJISDKInitEvent event, int totalProcess) { }
                @Override public void onDatabaseDownloadProgress(long current, long total) { }
            });
        } catch (Throwable t) {
            registering = false;
            listener.onStatus("SDK no disponible: " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }
}
