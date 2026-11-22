# FarusFly 0.8.2 — integración y estado verificable

## Incluido en el código

- Registro/conexión DJI SDK V4 y vista previa de video en modo de solo lectura.
- Detector ML Kit en modo de flujo, con captura a baja resolución y frecuencia reducida.
- Zona virtual central; el recuadro se pone rojo y se muestra una alerta visual cuando un objeto la cruza.
- Controles para activar/desactivar IA y detener cámara.
- Micrófono local, video local y simulador sin comandos de vuelo.
- Identificador Android cl.farus.farusfly; versión 0.8.2.

## Pendiente de verificación

- No se ha confirmado un APK compilado en este entorno.
- No se ha probado la detección sobre video real del Mini 2, el rendimiento en el teléfono ni la inicialización real del SDK.
- El primer uso de ML Kit puede depender de la disponibilidad/instalación del modelo de Google Play services.
- El detector genérico no está entrenado para obstáculos y devuelve cajas 2D. No infiere distancia, profundidad o riesgo de colisión y puede omitir cables delgados.
- No se implementó control de vuelo, frenado, seguimiento autónomo ni evitación automática.

## Cómo usar la capa visual

1. Configura localmente la DJI App Key para cl.farus.farusfly; no publiques la clave.
2. Conecta el teléfono y control según el procedimiento DJI normal.
3. Pulsa Activar vista DJI y confirma que aparece video.
4. Pulsa Iniciar alertas visuales IA (zona virtual). Espera a que el modelo esté disponible y revisa los cuadros sobre la imagen.
5. Detén la IA y la vista antes de cerrar la aplicación.

Empieza con el dron apagado; después, prueba la imagen con la aeronave inmovilizada y hélices retiradas. Mantén vigilancia manual: la superposición no es un equipo de seguridad.

## Referencias

- ML Kit Object Detection Android: https://developers.google.com/ml-kit/vision/object-detection/android
- DJI Mini 2 FAQ: https://www.dji.com/mini-2
