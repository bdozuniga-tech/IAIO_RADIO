#!/bin/bash
# ROBOT DESPLEGADOR DE IAIO RADIO V2.0 🚀

echo "--- INICIANDO DESPLIEGUE GALÁCTICO ---"

# 1. Compilar la APK
echo "🏗️  Compilando APK..."
./gradlew app:assembleDebug

if [ $? -eq 0 ]; then
    echo "✅  APK Compilada con éxito."

    # 2. Mover y renombrar APK
    cp app/build/outputs/apk/debug/app-debug.apk test.apk
    cp app/build/outputs/apk/debug/app-debug.apk iaio_radio.apk

    # 3. Subir a GitHub
    echo "📤  Subiendo mambo a GitHub..."
    git add .
    git commit -m "Actualización automática de IAIO RADIO: $(date +'%Y-%m-%d %H:%M:%S')"
    git push origin main

    echo "🚀  ¡DESPLIEGUE COMPLETADO! Tu amigo ya puede actualizar."
else
    echo "❌  Error en la compilación. Revisa el código, Comandante."
    exit 1
fi