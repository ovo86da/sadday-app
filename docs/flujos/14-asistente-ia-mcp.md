# Flujo 14 — Asistente de IA para planificación de salidas (MCP)

## ¿Qué es esto?

El asistente de IA te permite hacerle preguntas en lenguaje natural a Claude sobre las montañas, rutas, salidas e informes del club. Claude consulta directamente la base de datos del sistema y responde con información real.

Ejemplos de lo que puedes preguntar:
- *"¿Qué rutas de trekking tenemos disponibles para nivel básico?"*
- *"¿Cuánto costó la última salida al Cayambe y cómo estuvo la ruta?"*
- *"Planifica una salida de ciclismo para junio, máximo nivel 3, con cupo para 8 personas."*
- *"¿Qué acordamos en la última reunión de socios sobre las salidas de invierno?"*

El asistente **solo lee datos** — no puede crear salidas, inscribir socios ni modificar nada. Las acciones se siguen haciendo desde la aplicación web.

---

## Requisitos previos

- Tener una cuenta activa en el sistema con rol Socio o superior
- Tener instalado [Claude Desktop](https://claude.ai/download), [Cursor](https://cursor.com) o usar Claude Code

---

## Paso 1 — Generar tu API Key

La API Key es la contraseña que el asistente usa para consultar los datos del club en tu nombre. Tienes un máximo de 5 keys activas.

1. Inicia sesión en la aplicación web
2. Ve a tu **Perfil** (esquina superior derecha)
3. Busca la sección **API Keys**
4. Haz clic en **Nueva API Key**
5. Escribe un nombre descriptivo, por ejemplo: `MCP Claude Desktop`
6. Haz clic en **Generar**
7. **Copia la key inmediatamente** — solo se muestra una vez y no se puede recuperar después

> ⚠️ Guarda la key en un lugar seguro. Si la pierdes, deberás revocar esa y crear una nueva.

---

## Paso 2 — Descargar el servidor MCP

El servidor MCP se distribuye como un **ejecutable único** — no necesitas instalar Node.js ni clonar el repositorio.

1. Ve a la página de [Releases del repositorio](../../releases) y descarga el archivo correspondiente a tu sistema operativo:

   | Sistema | Archivo a descargar |
   |---|---|
   | macOS Apple Silicon (M1/M2/M3) | `sadday-mcp-vX.X.X-macos-arm64` |
   | macOS Intel | `sadday-mcp-vX.X.X-macos-x64` |
   | Windows 64-bit | `sadday-mcp-vX.X.X-win-x64.exe` |
   | Linux 64-bit | `sadday-mcp-vX.X.X-linux-x64` |

2. Guarda el binario en una carpeta permanente (por ejemplo, `~/sadday-mcp/` o `C:\sadday-mcp\`).

3. En macOS, dale permisos de ejecución:
   ```bash
   chmod +x ~/sadday-mcp/sadday-mcp-vX.X.X-macos-arm64
   ```

> **macOS — GateKeeper:** La primera vez que ejecutes el binario, macOS puede bloquearlo porque no está firmado con una cuenta de Apple Developer. Para autorizarlo: haz clic derecho sobre el archivo → **Abrir** → **Abrir** en el diálogo de confirmación. Solo debes hacerlo una vez. Alternativamente, en la terminal: `xattr -c /ruta/al/binario`.

---

## Paso 3 — Configurar tu cliente

Usa la **ruta completa al binario** que descargaste.

### Claude Desktop

Abre (o crea) el archivo de configuración:
- **macOS:** `~/Library/Application Support/Claude/claude_desktop_config.json`
- **Windows:** `%APPDATA%\Claude\claude_desktop_config.json`
- **Linux:** `~/.config/claude/claude_desktop_config.json`

```json
{
  "mcpServers": {
    "sadday": {
      "command": "/ruta/completa/al/sadday-mcp-vX.X.X-macos-arm64",
      "env": {
        "SADDAY_API_URL": "https://app.el-sadday.com",
        "SADDAY_API_KEY": "sk-sadday-tu-key-aqui"
      }
    }
  }
}
```

Reinicia Claude Desktop. Verás el ícono de herramientas (🔧) junto al campo de texto si la conexión fue exitosa.

### Cursor

Abre (o crea) `~/.cursor/mcp.json` para configuración global, o `.cursor/mcp.json` dentro del proyecto:

```json
{
  "mcpServers": {
    "sadday": {
      "command": "/ruta/completa/al/sadday-mcp-vX.X.X-macos-arm64",
      "env": {
        "SADDAY_API_URL": "https://app.el-sadday.com",
        "SADDAY_API_KEY": "sk-sadday-tu-key-aqui"
      }
    }
  }
}
```

### Claude Code (terminal)

```bash
claude mcp add sadday \
  --command "/ruta/completa/al/sadday-mcp-vX.X.X-macos-arm64" \
  --env SADDAY_API_URL=https://app.el-sadday.com \
  --env SADDAY_API_KEY=sk-sadday-tu-key-aqui
```

---

## Alternativa — Instalar desde el código fuente

Si prefieres compilar el servidor tú mismo (requiere Node.js 20+ y acceso al repositorio):

```bash
cd mcp
npm ci --ignore-scripts
npm run build
```

En ese caso, usa `node /ruta/a/sadday-app/mcp/dist/index.js` como `command` en lugar del binario.

---

## Paso 4 — Usar el asistente

Una vez configurado, simplemente escribe tu pregunta en lenguaje natural. No necesitas saber qué "tool" usar — Claude decide solo.

### Consultas de rutas y montañas

```
¿Qué montañas tenemos registradas en la Sierra norte?

¿Cuáles son las rutas de alpinismo disponibles para nivel técnico 3 o menos?

Muéstrame las rutas del Cotopaxi con todos sus detalles.
```

### Planificación de salidas

```
Planifica una salida de trekking para el próximo mes.
El grupo tiene nivel básico (máximo nivel 2) y somos 10 personas.

¿Qué salidas tenemos programadas para las próximas 8 semanas?
¿Cuáles tienen plazas disponibles?

¿Cuándo fue la última vez que fuimos al Chimborazo?
```

### Consulta de informes

```
¿Cómo estuvo la ruta la última vez que hicimos la normal del Cotopaxi?
¿Llegaron a la cumbre? ¿Cuánto costó?

Dame un resumen de todas las salidas al Antisana:
tasa de éxito, costo promedio y condiciones más frecuentes.

¿Qué guías o transportistas hemos usado en salidas de ciclismo?
```

### Actas de reuniones

```
¿Qué se discutió en la última reunión de socios sobre salidas?

Busca en las actas si hay algún acuerdo sobre el calendario de salidas de este año.

¿Cuántas reuniones de socios hemos tenido en 2025?
```

### Planificación combinada

```
Quiero organizar una salida de escalada para agosto.
¿Qué rutas tenemos disponibles? ¿Cuándo fue la última vez que fuimos?
¿Cómo estuvieron las condiciones según el informe?
Sugiere una fecha y estima el costo basándote en salidas anteriores.
```

---

## Cómo funciona por detrás

Cuando escribes una pregunta, Claude:

1. Analiza qué información necesita
2. Llama las consultas necesarias en el orden correcto (puede encadenar varias)
3. Recibe los datos reales de la base de datos del club
4. Redacta una respuesta en lenguaje natural con esa información

Todo ocurre en segundos y de forma transparente. Puedes pedirle a Claude que te muestre qué consultas hizo si tienes curiosidad.

---

## Revocar una API Key

Si sospechas que tu key fue expuesta, o simplemente ya no la necesitas:

1. Ve a tu **Perfil** en la aplicación web
2. Sección **API Keys**
3. Haz clic en **Revocar** junto a la key que quieres eliminar
4. La key deja de funcionar inmediatamente

Después deberás actualizar la configuración de tu cliente con una nueva key.

---

## Solución de problemas

**El ícono de herramientas no aparece en Claude Desktop**
- Verifica que la ruta en `command` sea correcta y absoluta
- En macOS, verifica que el binario tenga permisos de ejecución: `ls -l /ruta/al/binario`
- En macOS, autoriza el binario si GateKeeper lo bloqueó (clic derecho → Abrir)
- Reinicia Claude Desktop completamente

**Error "Unauthorized" o "403" al hacer preguntas**
- La API key está mal copiada, revocada o expirada
- Genera una nueva key desde el perfil y actualiza la config

**Respuestas vacías o "no encontré información"**
- Verifica que `SADDAY_API_URL` apunte al servidor correcto
- Comprueba que el servidor de la app esté funcionando

**"Has alcanzado el límite de 5 API Keys"**
- Revoca alguna key que ya no uses antes de crear una nueva
