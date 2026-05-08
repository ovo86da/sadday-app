# Mejorar el Registro de Errores en el Backend

## Objetivo
Al igual que en el frontend, el backend (Spring Boot) tiene múltiples bloques `catch` donde se captura una excepción pero solo se registra su mensaje (`e.getMessage()`), o se envuelve en una `BusinessException` perdiendo la causa original, o simplemente se ignora por completo (`ignored`). Esto dificulta el diagnóstico de problemas en producción. 
El objetivo es asegurar que **todas las causas reales y el stack trace** queden registrados o encapsulados.

## Análisis de la Situación Actual
1. **GlobalExceptionHandler**: Está bien configurado y para excepciones genéricas (`Exception.class`) ya registra el stack trace completo (`log.error("...", ex.getMessage(), ex)`).
2. **BusinessException**: No cuenta con un constructor que reciba un mensaje personalizado y la causa (solo recibe uno o el otro). Por lo tanto, cuando un servicio falla al leer un archivo y lanza un `BusinessException("No se pudo leer...", e)`, no compila, y actualmente se desecha la causa `e`.
3. **Loggers con solo el mensaje**: Se encontraron múltiples incidencias donde se usa `log.error("...", e.getMessage())` omitiendo pasar `e` al final, lo que evita que SLF4J imprima el stack trace de la causa real.
4. **Excepciones ignoradas**: Existen bloques `catch (Exception ignored) {}` en `ConfiguracionSistemaService` y `AuditAspect` que silencian errores por completo.

## Propuesta de Cambios

### 1. Modificar `BusinessException.java`
Añadir el constructor faltante para permitir envolver excepciones manteniendo la causa real y un mensaje personalizado:
```java
public BusinessException(ErrorCode errorCode, String customMessage, Throwable cause) {
    super(customMessage, cause);
    this.errorCode = errorCode;
}
```

### 2. Modificar `GlobalExceptionHandler.java`
Mejorar el logging de `BusinessException` para que, si existe una causa interna (`ex.getCause() != null`), la imprima a nivel de debug o warn para facilitar la depuración sin exponerla al cliente HTTP.

### 3. Actualizar los Loggers en los Servicios
Modificar los archivos detectados para que los métodos `log.warn` y `log.error` reciban el objeto de la excepción completa.
Se modificarán, entre otros:
- `SecurityAlertMailSender.java`
- `PasswordResetMailSender.java`
- `GeoIpService.java`
- `TotpService.java`
- `SecurityEventService.java`
- `DocumentoService.java`
- `PdfInformeService.java`
- `CsvHabilitacionService.java`
- `CsvSocioImportService.java`
- `JwtAuthFilter.java`

### 4. Preservar la causa en envoltorios (Wrappers)
Modificar los servicios que lanzan `BusinessException` tras capturar una excepción de I/O o validación para que pasen `e` al nuevo constructor.
- `ActaMdFileValidator.java`
- `CsvSocioImportService.java`
- `CsvHabilitacionService.java`

### 5. Registrar excepciones silenciosas
Añadir un `log.debug` o `log.trace` en los bloques `catch (Exception ignored)` de `AuditAspect.java` y `ConfiguracionSistemaService.java` para que el error no sea completamente invisible.

---

# Implementación Realizada

## ¿Cómo se arregló el problema?

1. **Nuevo constructor en BusinessException**: Se agregó exitosamente el constructor `BusinessException(ErrorCode, String, Throwable)` para que cualquier servicio pueda lanzar errores de negocio adjuntando la causa que los originó.
2. **GlobalExceptionHandler**: Se actualizó el método `handleBusinessException` para imprimir un `log.warn` especial con el *stack trace* completo (`ex.getCause()`) si la excepción lo contiene.
3. **Paso de excepciones al Logger**: Utilizando un script para procesar de manera segura el código Java, se transformaron de forma masiva 11 archivos de servicio donde se utilizaba `log.warn` o `log.error` omitiendo la causa. Por ejemplo, la línea:
   ```java
   log.error("GeoIpService: error cargando GeoLite2 '{}': {}", dbPath, e.getMessage());
   ```
   Pasó a ser:
   ```java
   log.error("GeoIpService: error cargando GeoLite2 '{}': {}", dbPath, e.getMessage(), e);
   ```
4. **Preservar envoltorios**: En `ActaMdFileValidator.java`, `CsvSocioImportService.java` y `CsvHabilitacionService.java`, se actualizó el `throw new BusinessException` dentro de los bloques `catch (IOException e)` para que pasen `e` como tercer argumento, utilizando el nuevo constructor.
5. **Erradicación de excepciones silenciosas**: En `AuditAspect.java` y `ConfiguracionSistemaService.java` se añadió lógica de logging a los bloques `catch (Exception ignored) {}` imprimiendo en niveles bajos (trace/warn) para que un operador pueda descubrir problemas si ocurren.
6. **Verificación**: Finalmente se ejecutó la compilación de todo el backend (Spring Boot / Maven) y finalizó exitosamente ("BUILD SUCCESS"), confirmando que el código modificado es totalmente compatible.
