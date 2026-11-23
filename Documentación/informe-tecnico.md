# Informe técnico de Mini-Calc

Mini-Calc reconoce asignaciones a variables de una letra y sentencias `print`. El analizador léxico convierte el texto en tokens con coordenadas. El analizador sintáctico consume esos tokens mediante descenso recursivo. Ambos están implementados en Java con la biblioteca estándar.

## Autómata finito determinista

El estado inicial es `INICIO`. Los estados de aceptación son `ID` y `P` para `ID`, `PRINT` para `KW_PRINT`, `NUM` para `NUM` y los ocho estados de la última fila para sus respectivos operadores y delimitadores. Los demás estados son de rechazo.

La matriz describe la función de transición `δ: Q × Σ → Q`. `D` representa un dígito ASCII de 0 a 9. La columna «Otros» cubre cualquier carácter fuera de las categorías anteriores.

| Estado | `p` | `r` | `i` | `n` | `t` | Otra letra a–z | D | `=` | `+` | `-` | `*` | `/` | `(` | `)` | `;` | Otros |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| INICIO | P | ID | ID | ID | ID | ID | NUM | ASIGNACION | SUMA | RESTA | MULTIPLICACION | DIVISION | ABRE_PARENTESIS | CIERRA_PARENTESIS | PUNTO_Y_COMA | FALLO |
| ID | INV | INV | INV | INV | INV | INV | FALLO | FALLO | FALLO | FALLO | FALLO | FALLO | FALLO | FALLO | FALLO | FALLO |
| P | INV | PR | INV | INV | INV | INV | FALLO | FALLO | FALLO | FALLO | FALLO | FALLO | FALLO | FALLO | FALLO | FALLO |
| PR | INV | INV | PRI | INV | INV | INV | FALLO | FALLO | FALLO | FALLO | FALLO | FALLO | FALLO | FALLO | FALLO | FALLO |
| PRI | INV | INV | INV | PRIN | INV | INV | FALLO | FALLO | FALLO | FALLO | FALLO | FALLO | FALLO | FALLO | FALLO | FALLO |
| PRIN | INV | INV | INV | INV | PRINT | INV | FALLO | FALLO | FALLO | FALLO | FALLO | FALLO | FALLO | FALLO | FALLO | FALLO |
| PRINT / INV | INV | INV | INV | INV | INV | INV | FALLO | FALLO | FALLO | FALLO | FALLO | FALLO | FALLO | FALLO | FALLO | FALLO |
| NUM | FALLO | FALLO | FALLO | FALLO | FALLO | FALLO | NUM | FALLO | FALLO | FALLO | FALLO | FALLO | FALLO | FALLO | FALLO | FALLO |
| FALLO y cada estado de operador o delimitador | FALLO | FALLO | FALLO | FALLO | FALLO | FALLO | FALLO | FALLO | FALLO | FALLO | FALLO | FALLO | FALLO | FALLO | FALLO | FALLO |

`INV` abrevia `PALABRA_INVALIDA`. El lexer lee cada tramo continuo de letras minúsculas como una palabra. La letra `p` puede ser una variable por sí sola. La secuencia `print` se acepta como palabra reservada, y secuencias como `pr` y `printx` se rechazan desde su primera columna. En los números, la regla `[0-9]+` admite `0` y ceros iniciales. La división `/` figura en el alfabeto y en la gramática de la consigna y se reconoce como `OP_DIV`.

Al intentar una transición a `FALLO` después de haber leído parte de un token, el lexer decide si el estado anterior era de aceptación y conserva el carácter siguiente para el próximo análisis. Desde `INICIO`, `FALLO` indica que el carácter no pertenece a ningún token. Antes de aplicar el autómata, se omiten espacios, tabulaciones y saltos de línea. Las coordenadas comienzan en 1. Cada tabulación cuenta como una columna y CRLF cuenta como un salto de línea.

En `Lexer.transicion`, el estado numérico permanece en `NUM` mientras recibe dígitos.

~~~java
case NUM -> esDigito(caracter) ? Estado.NUM : Estado.FALLO;
~~~

## Gramática para descenso recursivo

Las reglas originales de `expresion` y `termino` tenían recursión directa por la izquierda. Se extrajo cada sufijo de operación a una regla con alternativa vacía.

~~~text
programa         → lista_sentencias
lista_sentencias → sentencia lista_sentencias | ε
sentencia        → ID "=" expresion ";" | KW_PRINT expresion ";"
expresion        → termino resto_expresion
resto_expresion  → "+" termino resto_expresion | "-" termino resto_expresion | ε
termino          → factor resto_termino
resto_termino    → "*" factor resto_termino | "/" factor resto_termino | ε
factor           → "(" expresion ")" | ID | NUM
~~~

Cada no terminal tiene su método en `Parser`. Los métodos `listaSentencias`, `restoExpresion` y `restoTermino` recorren con bucles las repeticiones de sus reglas. El parser exige el fin del archivo tras la última sentencia. La alternativa vacía de `lista_sentencias` permite un archivo vacío o con solo espacios en blanco.

En `Parser.restoTermino`, se lee un factor por cada operador multiplicativo y se agrega el operador después de ambos operandos en la representación posfija.

~~~java
while (actual().tipo() == Tipo.OP_MUL || actual().tipo() == Tipo.OP_DIV) {
    Token operador = avanzar();
    factor(posfija);
    posfija.add(operador);
}
~~~

Como `expresion` lee cada término completo antes de aplicar la suma o la resta, multiplicación y división tienen mayor precedencia. La salida posfija mantiene la asociación a la izquierda. Por ejemplo, `10 - 3 - 2` se convierte en `10 3 - 2 -` y da `5`.

## Árboles de derivación

En los árboles, `E` es `expresion`, `E'` es `resto_expresion`, `T` es `termino`, `T'` es `resto_termino` y `F` es `factor`.

### Expresión 10 + 5 * 2

~~~text
E
├── T
│   ├── F → NUM(10)
│   └── T' → ε
└── E'
    ├── +
    ├── T
    │   ├── F → NUM(5)
    │   └── T'
    │       ├── *
    │       ├── F → NUM(2)
    │       └── T' → ε
    └── E' → ε
~~~

### Expresión (x - 4) / 2

~~~text
E
├── T
│   ├── F
│   │   ├── (
│   │   ├── E
│   │   │   ├── T
│   │   │   │   ├── F → ID(x)
│   │   │   │   └── T' → ε
│   │   │   └── E'
│   │   │       ├── -
│   │   │       ├── T
│   │   │       │   ├── F → NUM(4)
│   │   │       │   └── T' → ε
│   │   │       └── E' → ε
│   │   └── )
│   └── T'
│       ├── /
│       ├── F → NUM(2)
│       └── T' → ε
└── E' → ε
~~~

## Salida y errores

Los tokens se muestran como `<TIPO, "lexema", línea, columna>`. El primer error detiene la fase en curso. Un error sintáctico indica la posición del token inesperado o del fin de archivo si falta un símbolo al final.

~~~text
Error [Línea 2, Columna 1]: Se esperaba ';' y se encontró "print".
~~~

Con `--ejecutar`, el programa evalúa la representación posfija con una pila después de validar toda la sintaxis. Los valores son enteros de precisión arbitraria. La división trunca hacia cero.
