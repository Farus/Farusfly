# FarusFly 0.8 — validación

## Implementado en código

- DJI Mobile SDK V4 y vista previa de video sólo lectura.
- Captura de cuadros y análisis en dispositivo con ML Kit Object Detection en modo de flujo.
- Superposición con cajas 2D normalizadas y zona virtual central con alerta visual al cruzarla.
- Micrófono local y simulador de movimiento sin comandos al dron.
- No hay llamadas de control de vuelo.

## No validado todavía

- Compilación Gradle/APK en Android SDK 35 y JDK 17.
- Descarga/inicialización del modelo, latencia y temperatura en un teléfono real.
- Registro DJI, enlace físico y video del Mini 2.
- Robustez ante obstáculos reales; no es un modelo dedicado ni certificable.
- Distancia y trayectoria; el sistema sólo compara cajas 2D con una región de pantalla.

## Uso seguro

DJI declara que Mini 2 no tiene evitación de obstáculos ni seguimiento automático. La alerta de FarusFly tampoco frena ni altera controles. Mantén vuelo manual, línea de vista y distancia; la falta de alerta debe asumirse como posible.

## Fuentes

- DJI Mini 2 FAQ: https://www.dji.com/mini-2
- Google ML Kit Android Object Detection: https://developers.google.com/ml-kit/vision/object-detection/android
