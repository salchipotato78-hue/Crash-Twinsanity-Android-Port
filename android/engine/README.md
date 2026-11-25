# Motor para Android (hito 1: compilar y enlazar)

`libcrashtwin_engine.so` es el motor de escritorio (`src/`, `src/platform/native/`) compilado para arm64 con el NDK.
Está en un job aparte (`.github/workflows/android-engine.yml`, manual) para que la APK de `android.yml` siga compilando.

## Decisiones

- **Disco:** no hace falta cambiar `disc.cpp`. `Native::Disc::Open` ya abre con `open()`. La app pasa `/proc/self/fd/<n>`
  del descriptor del selector de archivos, y el motor lo lee en sitio.
- **Punto de entrada:** `entry.cpp` se compila con `-Dmain=twin_engine_main`; `engine_jni.cpp` lo llama.
- **Gráficos:** Vulkan a través de SDL GPU (`hw/sdlgpu.cpp`), que ya existe en el motor. No se usa en este hito.
- **Stubs:** lo que no esté portado sale como stub débil (`native/tools/make_stubs.py`), igual que en escritorio.

## Qué queda fuera de este hito

1. Movies (FFmpeg para Android) y el update check: fuera del build.
2. Glue de SDL para Android (`SDLActivity`): sin él SDL no crea ventana. Siguiente hito.
3. Glslang para el backend Vulkan: sin él solo funciona OpenGL, que no existe en Android. Siguiente hito.
4. El workflow aún no sube el `.so` a `jniLibs` ni lo llama desde la app.
