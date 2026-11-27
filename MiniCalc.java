import java.io.IOException;
import java.io.PrintStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path ;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class MiniCalc {
    public static void main(String[] args) {
        int resultado = procesar(args, System.out, System.err);
        if (resultado != 0) {
            System.exit(resultado);
        }
    }

    private static int procesar(String[] args, PrintStream salida, PrintStream errores) {
        if (args.length == 0 || args.length > 2
                || (args.length == 2 && !args[1].equals("--ejecutar"))) {
            errores.println("Uso: java MiniCalc.java <archivo.calc> [--ejecutar]");
            return 2;
        }

        try {
            String codigo = Files.readString(Path.of(args[0]));
            salida.println("=== INICIO DE ANÁLISIS LÉXICO ===");
            List<Token> tokens = new ArrayList<>();
            Lexer lexer = new Lexer(codigo);
            Token token;
            do {
                token = lexer.siguiente();
                tokens.add(token);
                if (token.tipo() != Tipo.FIN) {
                    salida.println(token);
                }
            } while (token.tipo() != Tipo.FIN);

            salida.println("=== INICIO DE ANÁLISIS SINTÁCTICO ===");
            List<Sentencia> programa = new Parser(tokens).programa();
            salida.println("El análisis sintáctico finalizó exitosamente sin errores.");
            if (args.length == 2) {
                salida.println("=== INICIO DE EJECUCIÓN ===");
                ejecutar(programa, salida);
            }
            return 0;
        } catch (ErrorMiniCalc error) {
            errores.println(error.getMessage());
            return 1;
        } catch (IOException | InvalidPathException error) {
            errores.printf("No se pudo leer el archivo %s. %s%n", citar(args[0]), error.getMessage());
            return 2;
        }
    }

    private enum Tipo {
        KW_PRINT, ID, NUM, OP_ASSIGN, OP_ADD, OP_SUB, OP_MUL, OP_DIV,
        PUNCT_LPAREN, PUNCT_RPAREN, PUNCT_SEMI, FIN
    }

    private record Token(Tipo tipo, String lexema, int linea, int columna) {
        @Override
        public String toString() {
            return "<%s, %s, %d, %d>".formatted(tipo, citar(lexema), linea, columna);
        }

        String encontrado() {
            return tipo == Tipo.FIN ? "fin de archivo" : citar(lexema);
        }
    }

    private static String citar(String texto) {
        return "\"" + texto.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static final class ErrorMiniCalc extends RuntimeException {
        ErrorMiniCalc(int linea, int columna, String mensaje) {
            super("Error [Línea %d, Columna %d]: %s".formatted(linea, columna, mensaje));
        }

        ErrorMiniCalc(Token token, String mensaje) {
            this(token.linea(), token.columna(), mensaje);
        }
    }

    private enum Estado {
        INICIO(null), FALLO(null), PALABRA_INVALIDA(null),
        ID(Tipo.ID), P(Tipo.ID), PR(null), PRI(null), PRIN(null),
        PRINT(Tipo.KW_PRINT), NUM(Tipo.NUM),
        ASIGNACION(Tipo.OP_ASSIGN), SUMA(Tipo.OP_ADD), RESTA(Tipo.OP_SUB),
        MULTIPLICACION(Tipo.OP_MUL), DIVISION(Tipo.OP_DIV),
        ABRE_PARENTESIS(Tipo.PUNCT_LPAREN), CIERRA_PARENTESIS(Tipo.PUNCT_RPAREN),
        PUNTO_Y_COMA(Tipo.PUNCT_SEMI);

        private final Tipo tipo;

        Estado(Tipo tipo) {
            this.tipo = tipo;
        }
    }

    private static final class Lexer {
        private final String codigo;
        private int indice;
        private int linea = 1;
        private int columna = 1;

        Lexer(String codigo) {
            this.codigo = codigo;
        }

        Token siguiente() {
            ignorarEspacios();
            if (indice == codigo.length()) {
                return new Token(Tipo.FIN, "", linea, columna);
            }

            int inicio = indice;
            int columnaInicial = columna;
            Estado estado = transicion(Estado.INICIO, codigo.charAt(indice));
            if (estado == Estado.FALLO) {
                String caracter = Character.toString(codigo.codePointAt(indice));
                throw new ErrorMiniCalc(linea, columna, "Carácter no válido: " + citar(caracter) + ".");
            }

            do {
                indice++;
                columna++;
                if (indice == codigo.length()) {
                    break;
                }
                Estado siguiente = transicion(estado, codigo.charAt(indice));
                if (siguiente == Estado.FALLO) {
                    break;
                }
                estado = siguiente;
            } while (true);

            String lexema = codigo.substring(inicio, indice);
            if (estado.tipo == null) {
                throw new ErrorMiniCalc(linea, columnaInicial,
                        "Palabra no válida: " + citar(lexema)
                                + ". Usa una variable de una letra o la palabra reservada print.");
            }
            return new Token(estado.tipo, lexema, linea, columnaInicial);
        }

        private void ignorarEspacios() {
            while (indice < codigo.length()) {
                char caracter = codigo.charAt(indice);
                if (caracter == ' ' || caracter == '\t') {
                    indice++;
                    columna++;
                } else if (caracter == '\r' || caracter == '\n') {
                    indice++;
                    if (caracter == '\r' && indice < codigo.length() && codigo.charAt(indice) == '\n') {
                        indice++;
                    }
                    linea++;
                    columna = 1;
                } else {
                    return;
                }
            }
        }

        private static Estado transicion(Estado estado, char caracter) {
            return switch (estado) {
                case INICIO -> transicionInicial(caracter);
                case NUM -> esDigito(caracter) ? Estado.NUM : Estado.FALLO;
                case P -> continuarPalabra(caracter, 'r', Estado.PR);
                case PR -> continuarPalabra(caracter, 'i', Estado.PRI);
                case PRI -> continuarPalabra(caracter, 'n', Estado.PRIN);
                case PRIN -> continuarPalabra(caracter, 't', Estado.PRINT);
                case ID, PRINT, PALABRA_INVALIDA ->
                        esLetra(caracter) ? Estado.PALABRA_INVALIDA : Estado.FALLO;
                default -> Estado.FALLO;
            };
        }

        private static Estado transicionInicial(char caracter) {
            if (esDigito(caracter)) {
                return Estado.NUM;
            }
            if (esLetra(caracter)) {
                return caracter == 'p' ? Estado.P : Estado.ID;
            }
            return switch (caracter) {
                case '=' -> Estado.ASIGNACION;
                case '+' -> Estado.SUMA;
                case '-' -> Estado.RESTA;
                case '*' -> Estado.MULTIPLICACION;
                case '/' -> Estado.DIVISION;
                case '(' -> Estado.ABRE_PARENTESIS;
                case ')' -> Estado.CIERRA_PARENTESIS;
                case ';' -> Estado.PUNTO_Y_COMA;
                default -> Estado.FALLO;
            };
        }

        private static Estado continuarPalabra(char caracter, char esperado, Estado destino) {
            if (caracter == esperado) {
                return destino;
            }
            return esLetra(caracter) ? Estado.PALABRA_INVALIDA : Estado.FALLO;
        }

        private static boolean esLetra(char caracter) {
            return caracter >= 'a' && caracter <= 'z';
        }

        private static boolean esDigito(char caracter) {
            return caracter >= '0' && caracter <= '9';
        }
    }

    private record Sentencia(Token inicio, List<Token> expresionPosfija) {}

    private static final class Parser {
        private final List<Token> tokens;
        private int indice;

        Parser(List<Token> tokens) {
            this.tokens = tokens;
        }

        List<Sentencia> programa() {
            List<Sentencia> sentencias = listaSentencias();
            exigir(Tipo.FIN, "el fin de archivo");
            return sentencias;
        }

        private List<Sentencia> listaSentencias() {
            List<Sentencia> sentencias = new ArrayList<>();
            while (actual().tipo() != Tipo.FIN) {
                sentencias.add(sentencia());
            }
            return sentencias;
        }

        private Sentencia sentencia() {
            Token inicio = actual();
            switch (inicio.tipo()) {
                case ID -> {
                    avanzar();
                    exigir(Tipo.OP_ASSIGN, "'='");
                }
                case KW_PRINT -> avanzar();
                default -> throw errorEsperado("una variable o 'print'");
            }

            List<Token> posfija = new ArrayList<>();
            expresion(posfija);
            exigir(Tipo.PUNCT_SEMI, "';'");
            return new Sentencia(inicio, List.copyOf(posfija));
        }

        private void expresion(List<Token> posfija) {
            termino(posfija);
            restoExpresion(posfija);
        }

        private void restoExpresion(List<Token> posfija) {
            while (actual().tipo() == Tipo.OP_ADD || actual().tipo() == Tipo.OP_SUB) {
                Token operador = avanzar();
                termino(posfija);
                posfija.add(operador);
            }
        }

        private void termino(List<Token> posfija) {
            factor(posfija);
            restoTermino(posfija);
        }

        private void restoTermino(List<Token> posfija) {
            while (actual().tipo() == Tipo.OP_MUL || actual().tipo() == Tipo.OP_DIV) {
                Token operador = avanzar();
                factor(posfija);
                posfija.add(operador);
            }
        }

        private void factor(List<Token> posfija) {
            switch (actual().tipo()) {
                case NUM, ID -> posfija.add(avanzar());
                case PUNCT_LPAREN -> {
                    avanzar();
                    expresion(posfija);
                    exigir(Tipo.PUNCT_RPAREN, "')'");
                }
                default -> throw errorEsperado("un número, una variable o '('");
            }
        }

        private void exigir(Tipo tipo, String esperado) {
            if (actual().tipo() != tipo) {
                throw errorEsperado(esperado);
            }
            avanzar();
        }

        private Token actual() {
            return tokens.get(indice);
        }

        private Token avanzar() {
            return tokens.get(indice++);
        }

        private ErrorMiniCalc errorEsperado(String esperado) {
            Token token = actual();
            return new ErrorMiniCalc(token,
                    "Se esperaba " + esperado + " y se encontró " + token.encontrado() + ".");
        }
    }

    private static void ejecutar(List<Sentencia> programa, PrintStream salida) {
        Map<String, BigInteger> variables = new HashMap<>();
        for (Sentencia sentencia : programa) {
            BigInteger valor = evaluar(sentencia.expresionPosfija(), variables);
            if (sentencia.inicio().tipo() == Tipo.KW_PRINT) {
                salida.println(valor);
            } else {
                variables.put(sentencia.inicio().lexema(), valor);
            }
        }
    }

private static BigInteger evaluar(List<Token> posfija, Map<String, BigInteger> variables) {
    ArrayDeque<BigInteger> pila = new ArrayDeque<>();

    for (Token token : posfija) {
        switch (token.tipo()) {
            case NUM -> pila.push(new BigInteger(token.lexema()));

            case ID -> {
                BigInteger valor = variables.get(token.lexema());
                if (valor == null) {
                    throw new ErrorMiniCalc(token,
                            "Variable sin asignar: " + citar(token.lexema()) + ".");
                }
                pila.push(valor);
            }

            case OP_ADD, OP_SUB, OP_MUL, OP_DIV -> {
                // Verifica que existan dos operandos antes de operar.
                if (pila.size() < 2) {
                    throw new ErrorMiniCalc(token,
                            "Expresión inválida durante la evaluación.");
                }

                BigInteger derecha = pila.pop();
                BigInteger izquierda = pila.pop();

                pila.push(operar(token, izquierda, derecha));
            }

            default -> throw new IllegalStateException(
                    "Token inesperado en la expresión: " + token);
        }
    }

    // Verifica que la expresión haya generado un único resultado.
    if (pila.size() != 1) {
        Token referencia = posfija.get(posfija.size() - 1);
        throw new ErrorMiniCalc(referencia,
                "Expresión inválida durante la evaluación.");
    }

    return pila.pop();
}

    private static BigInteger operar(Token operador, BigInteger izquierda, BigInteger derecha) {
        return switch (operador.tipo()) {
            case OP_ADD -> izquierda.add(derecha);
            case OP_SUB -> izquierda.subtract(derecha);
            case OP_MUL -> izquierda.multiply(derecha);
            case OP_DIV -> {
                if (derecha.signum() == 0) {
                    throw new ErrorMiniCalc(operador, "División por cero.");
                }
                yield izquierda.divide(derecha);
            }
            default -> throw new IllegalStateException("Operador inesperado: " + operador);
        };
    }
}
