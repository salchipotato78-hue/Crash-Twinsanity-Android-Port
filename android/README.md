# Crash Twinsanity para Android

App Android para TwinsanityEvolved. La capa de pantalla, touch y ajustes viene del port anterior; el motor será el de
este repositorio (wasm2c sobre NativeEE, SDL2 y GLES 3).

## Estado (hito 0)

- **App Kotlin completa:** selector de disco solo la primera vez, pantalla sin estirar (incluido ultrawide), controles
  táctiles editables (posición, tamaño, opacidad; restablecer) y menú con pausa, editar controles, pantalla, cambiar
  disco y salir. El layout y los botones son los del port anterior (`Layouts.kt`, `TouchOverlay.kt`, `res/drawable`).
- **Puente nativo (`native/`):** `libtwinsanityevolved.so` abre el disco por descriptor, comprueba `SLES_525.68` y pinta.
  Todavía no ejecuta el juego.

## Próximos hitos

1. **Motor para NDK.** Generar el C de wasm2c (`tools/fetch_toolchain.sh`, sin disco) y compilar `tegame` y el runner
   para `aarch64-linux-android`, con un punto de entrada `te::android::Startup`, como el de Vita.
2. **SDL2 en Android.** Glue de SDLActivity y ventana GLES 3; hoy el runner usa SDL2 con GL 4.1, así que hay que
   revisar lo que usa el renderer.
3. **Disco por descriptor.** `/proc/self/fd/<n>` del descriptor del selector, igual que en el plan anterior.
4. **Pad.** `NativeBridge.setPad` (bits de `Pad` y stick) entra al `PlatformInput` del motor.

## Compilar

Con JDK 17, Android SDK (API 34) y Gradle 8.7:

    cd android && gradle :app:assembleDebug

El APK queda en `app/build/outputs/apk/debug/`. También lo compila el workflow manual **Android** en GitHub Actions.
