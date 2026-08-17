# Radio Vertical - Manual Single-File Boomerang Walkthrough

¡He simplificado y perfeccionado el motor de video! Ahora la aplicación genera el efecto de ida y vuelta (Boomerang) utilizando exclusivamente el archivo de video original, eliminando la necesidad de archivos extra y asegurando un funcionamiento impecable.

## Cambios Realizados

### Nuevo Motor Boomerang de Un Solo Archivo
- **Eliminación de Dependencias:** He borrado la búsqueda del archivo `_reverse`. Ahora el sistema es mucho más ligero y robusto al no depender de recursos externos que podrían faltar.
- **Retroceso Manual por Código:** Dado que Android no permite reproducir videos hacia atrás de forma nativa, he programado un controlador que realiza "micro-saltos" de retroceso.
    - **Fase Normal:** El video se reproduce hacia adelante hasta llegar al final.
    - **Fase Boomerang:** En cuanto llega a la meta, la app entra en modo "Rewind", retrocediendo 40 milisegundos cada 25 milisegundos. El resultado es un efecto de retroceso rítmico muy similar al de un DJ.
- **Cero Cortes:** El paso de adelante hacia atrás es instantáneo y mecánico, eliminando cualquier parpadeo en negro y manteniendo la inmersión visual.

### Optimización de Recursos
- **Un Solo Jugador:** Hemos pasado de usar dos reproductores (Dual Player) a uno solo. Esto reduce drásticamente el consumo de memoria RAM y de batería del teléfono.
- **Sincronización Total:** El video sigue respetando la pausa táctil de 1 segundo sobre el vinilo, deteniendo el retroceso o avance de forma inmediata.

## Cómo disfrutar del efecto Boomerang
1. Abre la aplicación y mira el video de fondo.
2. **Observa:** El video avanzará normalmente. Al llegar al final de la toma, verás cómo retrocede de forma fluida hasta el principio.
3. El ciclo se repetirá infinitamente sin errores de carga, ya que solo estamos usando un archivo.

**¡Dale a "Run" y experimenta la fluidez mecánica del nuevo motor Boomerang de tu Radio Vertical!** 🎬🔄⏩⏪✨🚀
