# Crash Twinsanity (Android)

Capa Android para el port nativo de Crash Twinsanity. Esta versión entrega la app completa **excepto el motor**: el
motor nativo (C++ de `Crash-Twinsanity-Windows`) todavía no está enlazado, así que la app avisa en pantalla y los
controles y menús funcionan igual.

## Qué hace ya

- **Selector de disco solo la primera vez.** Al abrir la app por primera vez pide el `.iso`, `.bin` o `.img` con el
  selector del sistema (Storage Access Framework). Después entra directo al juego. El disco se puede cambiar desde
  el menú táctil ("Cambiar disco").
- **Sin estirar, también en ultrawide.** La imagen nunca se deforma. "Sin estirar (pantalla)" le da al motor la
  relación de aspecto real de la pantalla (20:9, 21:9...), así que en un ultrawide se ve más escena, no una imagen
  estirada. 16:9, 21:9 y 4:3 se centran con franjas.
- **Controles táctiles editables.** Cada control tiene posición, tamaño y opacidad, guardados en el dispositivo.
  Menú ≡ → "Editar controles": arrastra un botón, y las barras ajustan tamaño y opacidad. "Restablecer" vuelve al
  diseño original.
- **Dos perfiles** según las imágenes de referencia: *Cortex* (L1 y R1) y *Crash y Nina* (solo R1, a la izquierda).
- **Menús táctiles:** pausa (Start), editar controles, perfil, pantalla, cambiar disco y salir.
- **CI en GitHub Actions:** compila el APK de depuración en cada push y publica el APK de release al crear una
  etiqueta `v*`. Falla si se sube un `.iso`, `.bin`, `.img` o `.cue`.

## Qué falta para que el juego arranque

1. **Motor para Android (NDK + CMake).** `native/CMakeLists.txt` compila el motor para escritorio. Hay que añadir un
   objetivo `libcrashtwin.so` con su `src/platform/android/` y que `NativeBridge` reciba las funciones JNI
   (`nativeStart`, `nativeSurface`, `nativeSetPad`, `nativeStop`).
2. **Gráficos.** El backend actual usa OpenGL 3.3 (`graphics/hw/gl.cpp`). Android necesita GLES 3.0 (shaders
   `#version 300 es`) o Vulkan a través de SDL GPU.
3. **Disco por descriptor de archivo.** `platform/native/disc.cpp` abre la imagen por ruta. Con el selector de
   Android solo hay un descriptor (`ParcelFileDescriptor`), así que hay que leer con `pread` sobre el `isoFd`.
4. **Datos del juego (`build/native/data.cpp`).** Se genera a partir de `asm/data`, que sale de dividir **tu** disco
   PAL, con la herramienta del proyecto. Ese archivo no puede estar en el repositorio ni en CI: el propio repo lo
   prohíbe (`check_game_data.py`). Opciones: generarlo en tu máquina y compilar el `.so` localmente, o usar un
   runner propio donde tengas el disco. Solo el APK de la capa Android se compila en GitHub Actions.
5. **Audio.** El port usa SDL3 para el audio; SDL3 funciona en Android, pero hay que revisar la latencia.
6. **Guardados y ajustes del motor.** Hoy van a `~/.config/...`. En Android deben ir al almacenamiento interno de la app.

## Compilar

Con JDK 17, Android SDK (API 34) y Gradle 8.7:

    gradle :app:assembleDebug

El APK queda en `app/build/outputs/apk/debug/`. También lo compila GitHub Actions y lo deja como artefacto.
