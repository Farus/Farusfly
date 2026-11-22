package cl.farus.farusfly;

import android.content.Context;
import android.content.Intent;
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
    public interface ProductListener { void onConnectionChanged(boolean connected); }
    private final ProductListener productListener;
    private volatile boolean registering;
    private volatile boolean registered;

    public DjiConnection(Context context, Listener listener, ProductListener productListener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        this.productListener = productListener;
    }

    public void register() {
        if (registered) {
            BaseProduct product = DJISDKManager.getInstance().getProduct();
            if (product != null && product.isConnected()) {
                productListener.onConnectionChanged(true);
                return;
            }
            context.sendBroadcast(new Intent(DJISDKManager.USB_ACCESSORY_ATTACHED)
                    .setPackage(context.getPackageName()));
            listener.onStatus("SDK registrado. Buscando el control DJI por USB…");
            return;
        }
        if (registering) return;
        registering = true;
        listener.onStatus("Registrando DJI SDK V4 (solo lectura)...");
        try {
            DJISDKManager.getInstance().registerApp(context, new DJISDKManager.SDKManagerCallback() {
                @Override public void onRegister(DJIError error) {
                    if (error == DJISDKError.REGISTRATION_SUCCESS) {
                        registered = true;
                        registering = false;
                        listener.onStatus("SDK registrado. Buscando el control DJI por USB…");
                        try {
                            DJISDKManager.getInstance().startConnectionToProduct();
                        } catch (RuntimeException e) {
                            registered = false;
                            listener.onStatus("Error al conectar DJI: " + e);
                        }
                    } else {
                        listener.onStatus("Error de registro DJI: " + String.valueOf(error));
                        registering = false;
                    }
                }
                @Override public void onProductDisconnect() {
                    productListener.onConnectionChanged(false);
                    listener.onStatus("DJI desconectado. Revisa el cable USB y que el Mini 2 esté encendido.");
                }
                @Override public void onProductConnect(BaseProduct product) {
                    listener.onStatus("DJI conectado: " + (product == null ? "modelo desconocido" : product.getModel()));
                    productListener.onConnectionChanged(product != null);
                }
                @Override public void onProductChanged(BaseProduct product) {
                    if (product != null && product.isConnected()) onProductConnect(product);
                    else onProductDisconnect();
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
