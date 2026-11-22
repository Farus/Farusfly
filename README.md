# FarusFly 0.8.3 — alertas visuales IA (sin control de vuelo)

Aplicación Android Java, identificador `cl.farus.farusfly`. La versión 0.8.3 declara Android API 35 como objetivo para incorporar protecciones modernas de Android.

## Funciones incluidas

- Vista previa de video DJI SDK V4 en modo de solo lectura.
- Análisis local de imagen con Google ML Kit Object Detection en modo de flujo.
- Cuadros sobre objetos detectados y una zona virtual central. El cuadro se vuelve rojo y aparece una alerta cuando un objeto cruza esa zona.
- Botones para iniciar y detener el análisis.
- Grabación de audio con el micrófono del teléfono, selección de video local y simulación de seguimiento sin transmitir órdenes.

## Límites de la alerta

Es una ayuda visual experimental, no un sistema certificado de detección o evitación. El detector genérico busca objetos prominentes en la imagen; no fue entrenado para obstáculos. Puede pasar por alto cables, ramas finas, vidrio, objetos pequeños, escenas oscuras o elementos fuera del encuadre. La zona sólo comprueba si el cuadro 2D del objeto cruza una región de pantalla: no estima distancia, profundidad, velocidad de aproximación ni trayectoria. La alerta no hace que el dron frene o esquive.

DJI confirma que el Mini 2 no admite evitación de obstáculos ni seguimiento automático de objetos: https://www.dji.com/mini-2. Mantén control manual, línea de vista y distancia prudente.

## Privacidad y modelo

El análisis se ejecuta en el teléfono; FarusFly no envía imágenes a un servidor propio. ML Kit puede requerir que Google Play services instale o descargue el modelo antes del primer uso; el primer inicio puede tardar y requiere conectividad disponible. La detección se limita a cerca de 1,4 cuadros por segundo y al objeto más prominente para reducir carga. No hay un modelo entrenado específicamente para cables u obstáculos.

## Compilación

El APK debug v0.8.3 se compiló correctamente en GitHub Actions. El paquete excluye la base Fly Safe para reducir su tamaño; DJI puede descargarla al registrar el SDK y requiere Internet en ese momento.

Requiere Android Studio, Android SDK 35, JDK 17 y acceso a repositorios Maven/Gradle. Abrir el proyecto y configurar `DJI_APP_KEY` como propiedad local de Gradle; no guardar ni compartir esa clave en el repositorio. Ejecutar `gradle assembleDebug`. Este paquete no incorpora el wrapper Gradle. La compatibilidad del teléfono, inicialización DJI, video e IA aún requieren pruebas en equipo real.

## Estado de validación

La compilación pasó en CI y el APK contiene el identificador `cl.farus.farusfly`. La App Key se entrega al proceso de compilación mediante un secreto de GitHub Actions y no se guarda en el repositorio. Falta instalar la app y validar el registro, la cámara y la detección IA con un teléfono, control y Mini 2 reales.
