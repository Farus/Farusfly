# FarusFly 0.8.2 — compilar APK desde Android

1. Sube el contenido del proyecto a un repositorio privado de GitHub, incluyendo la carpeta .github.
2. Abre Actions > Compilar FarusFly APK > Run workflow.
3. Descarga FarusFly-debug-apk desde Artifacts cuando termine.
4. Descomprime el APK en el teléfono y abre app-debug.apk para instalar.
5. Para que se registre con DJI, configura tu App Key existente como secreto de GitHub Actions llamado DJI_APP_KEY. El flujo de compilación la pasa a Gradle como propiedad sin escribirla en los archivos del proyecto. No publiques la clave.

La versión 0.7 incluye detección de objetos de ML Kit sobre la imagen DJI y alerta visual en una zona virtual. La API puede requerir la instalación/descarga inicial de su modelo. La alerta no estima distancia ni controla o evita obstáculos. El Mini 2 no tiene evitación de obstáculos.

Este entorno no tiene Gradle, Java ni Android SDK instalados, por lo que aquí no pude compilar ni producir un APK. El workflow de GitHub debe confirmar la compilación; el APK necesitará prueba supervisada con teléfono, control y dron reales. No actives vuelos autónomos.
