# Mini-Calc

Mini-Calc analiza archivos de asignaciones y expresiones aritméticas. Muestra los tokens con su ubicación y verifica la sintaxis del archivo completo.

## Requisitos

- JDK 17 o superior.
- El comando `java` disponible en la terminal. Comprueba la instalación con `java -version`.
- Archivos `.calc` guardados en UTF-8 sin BOM.

Desde la carpeta del proyecto, ejecuta el analizador con el archivo de ejemplo.

```powershell
java MiniCalc.java ejemplo.calc
```

La primera línea del archivo produce estos tokens.

```text
=== INICIO DE ANÁLISIS LÉXICO ===
<ID, "x", 1, 1>
<OP_ASSIGN, "=", 1, 3>
<NUM, "10", 1, 5>
<OP_ADD, "+", 1, 8>
<NUM, "5", 1, 10>
<OP_MUL, "*", 1, 12>
<NUM, "2", 1, 14>
<PUNCT_SEMI, ";", 1, 15>
```

Si todo el archivo cumple la gramática, aparece `El análisis sintáctico finalizó exitosamente sin errores.`

## Ejecutar el programa analizado

```powershell
java MiniCalc.java ejemplo.calc --ejecutar
```

Después de validar la sintaxis, el ejemplo imprime `8`. En este modo las variables deben tener un valor asignado antes de usarse. La división es entera y trunca hacia cero. Una división por cero indica la posición del operador `/`.

## Pruebas de consola

| Comando | Resultado esperado |
| --- | --- |
| `java MiniCalc.java ejemplo.calc` | Análisis léxico y sintáctico exitoso |
| `java MiniCalc.java ejemplo.calc --ejecutar` | Imprime `8` después del análisis |
| `java MiniCalc.java pruebas/precedencia.calc --ejecutar` | Imprime `5`, `3`, `30` y `0` en ese orden |
| `java MiniCalc.java pruebas/error-lexico.calc` | Error por `@` en la línea 2, columna 9 |
| `java MiniCalc.java pruebas/error-sintactico.calc` | Falta `;` antes de `print` en la línea 2, columna 1 |

El código de salida es `0` si el análisis o la ejecución solicitada termina correctamente, `1` si hay un error en el código Mini-Calc y `2` si hay un error de uso o lectura.

El [informe técnico](informe-tecnico.md) documenta el AFD, la gramática y dos árboles de derivación.
