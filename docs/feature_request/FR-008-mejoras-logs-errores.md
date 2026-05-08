# Mejorar el Registro de Errores en el Frontend

## Objetivo
Actualizar el manejo de errores en el frontend para asegurar que las excepciones capturadas queden registradas adecuadamente en la consola. Actualmente hay 45 bloques `catch {` que ignoran el error subyacente y solo muestran un mensaje genérico. Estos bloques serán actualizados a `catch (error) { console.error(error); ... }`.

## Propuesta de Cambios

### [Frontend Components & Pages]
Vamos a iterar sobre los archivos en `frontend/src/` y reemplazar las capturas vacías por capturas con parámetro de error y el respectivo `console.error`.

Se afectarán aproximadamente 22 archivos:
#### [MODIFY] `src/pages/aprobaciones/aprobaciones-page.tsx`
#### [MODIFY] `src/pages/admin/admin-page.tsx`
#### [MODIFY] `src/pages/actas/acta-import-dialog.tsx`
#### [MODIFY] `src/pages/actas/acta-form-dialog.tsx`
#### [MODIFY] `src/pages/actas/actas-page.tsx`
#### [MODIFY] `src/pages/informes/informe-jefe-dialog.tsx`
#### [MODIFY] `src/pages/informes/informes-page.tsx`
#### [MODIFY] `src/pages/rutas/ruta-detail-dialog.tsx`
#### [MODIFY] `src/pages/rutas/rutas-page.tsx`
#### [MODIFY] `src/pages/salidas/salidas-page.tsx`
#### [MODIFY] `src/pages/salidas/salida-form-dialog.tsx`
#### [MODIFY] `src/pages/salidas/salida-detail-dialog.tsx`
#### [MODIFY] `src/pages/contactos/contactos-page.tsx`
#### [MODIFY] `src/pages/socios/socios-page.tsx`
#### [MODIFY] `src/pages/socios/socio-detail-dialog.tsx`
#### [MODIFY] `src/pages/socios/invitaciones-tab.tsx`
#### [MODIFY] `src/pages/acceso-nivel/acceso-nivel-page.tsx`
#### [MODIFY] `src/pages/montanas/acceso-nivel-dialog.tsx`
#### [MODIFY] `src/components/layout/app-layout.tsx`
#### [MODIFY] `src/components/auth/auth-initializer.tsx`
#### [MODIFY] `src/pages/forgot-password.tsx`

Usaremos un script en Node.js que realice este reemplazo de forma segura en todos los archivos `.tsx` detectados para optimizar el tiempo.

## Plan de Verificación

### Verificación Automatizada
- Ejecutar la compilación de TypeScript (`pnpm tsc --noEmit`) para confirmar que ningún cambio introduzca errores de sintaxis.
- Verificar con el linter (`pnpm lint`) que la regla esté conforme.

### Verificación Manual
- Indicar al usuario que observe la consola del navegador al forzar un error para confirmar que se registra el log.

---

# Resumen de Implementación (Realizada)

## ¿Qué se hizo?
Se ha completado la actualización masiva de los bloques de captura de errores (`catch`) en todo el código fuente del frontend.

1. **Escaneo del código**: Identificamos 21 archivos y aproximadamente 45 lugares donde se capturaban errores (excepciones) pero no se registraba la causa subyacente. En estos casos, solo se mostraba una alerta visual (toast) genérica al usuario.
2. **Reemplazo seguro**: Mediante un script automatizado en Node.js, transformamos todos los bloques de `catch {` sin parámetros a `catch (error) { console.error(error); ... }`.
3. **Verificación**: Ejecutamos la compilación de TypeScript y el linter para asegurarnos de que esta adición no introdujera ningún error de sintaxis y que cumpliera con las reglas del proyecto. Todo compiló sin problemas asociados a los cambios introducidos.

## Beneficio
A partir de ahora, cuando un usuario experimente un error (como un fallo al aprobar un historial, al guardar una salida o al eliminar un acta), no solo verá el aviso genérico en pantalla ("Error al..."), sino que la consola de desarrollo de su navegador (`F12`) documentará exactamente **el porqué** del error (por ejemplo, *500 Internal Server Error*, *403 Forbidden* o problemas de conectividad de red). Esto facilitará enormemente futuras sesiones de diagnóstico y depuración.
