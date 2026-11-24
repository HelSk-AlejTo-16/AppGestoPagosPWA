package com.proyecto.servicios.util;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Utilería centralizada de sanitización y detección de cargas de inyección
 * (SQLi, XSS, SSTI/JNDI, NoSQLi, CRLF y caracteres de control).
 */
public final class InputSanitizer {

    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\p{Cntrl}]");
    private static final Pattern MULTIPLE_SPACES = Pattern.compile("\\s+");
    private static final Pattern FORBIDDEN_METACHARS = Pattern.compile("[<>\"`;\\\\{}\\[\\]\\$=*|\\^~\\r\\n\\u0000]");
    private static final Pattern SQL_TAUTOLOGY_OR_COMMENTS = Pattern.compile(
            "(?i)(--|/\\*|\\*/|'\\s*(or|and)\\s*['\\d]|'\\s*=\\s*'|\\b(union\\s+(all\\s+)?select|drop\\s+(table|database)|insert\\s+into|delete\\s+from|update\\s+\\w+\\s+set|xp_cmdshell|pg_sleep|waitfor\\s+delay)\\b)"
    );
    private static final Pattern XSS_OR_SCRIPT_PATTERNS = Pattern.compile(
            "(?i)(javascript:|vbscript:|data:text/html|onerror\\s*=|onload\\s*=|<\\s*script|<\\s*iframe|<\\s*img|<\\s*svg)"
    );

    private InputSanitizer() {
    }

    /**
     * Normaliza en Unicode NFC, elimina caracteres de control / null bytes y colapsa espacios.
     */
    public static String sanitizarTexto(String valor) {
        if (valor == null) {
            return null;
        }
        String normalizado = Normalizer.normalize(valor, Normalizer.Form.NFC);
        String sinControles = CONTROL_CHARS.matcher(normalizado).replaceAll("");
        return MULTIPLE_SPACES.matcher(sinControles.trim()).replaceAll(" ");
    }

    /**
     * Elimina saltos de línea (CRLF) para cabeceras SMTP o registros de auditoría (Log Injection).
     */
    public static String sanitizarParaLog(String valor) {
        if (valor == null) {
            return "";
        }
        String limpio = CONTROL_CHARS.matcher(valor).replaceAll("_").trim();
        return limpio.length() > 120 ? limpio.substring(0, 120) : limpio;
    }

    /**
     * Escapa caracteres especiales HTML para evitar HTML Injection / XSS en plantillas de correo.
     */
    public static String escaparHtml(String valor) {
        if (valor == null) {
            return "";
        }
        return valor
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    /**
     * Evalúa si una cadena contiene metacaracteres o patrones típicos de inyección.
     */
    public static boolean contieneCargaMaliciosa(String valor) {
        if (valor == null || valor.isEmpty()) {
            return false;
        }
        String nfc = Normalizer.normalize(valor, Normalizer.Form.NFC);
        return FORBIDDEN_METACHARS.matcher(nfc).find()
                || CONTROL_CHARS.matcher(nfc).find()
                || SQL_TAUTOLOGY_OR_COMMENTS.matcher(nfc).find()
                || XSS_OR_SCRIPT_PATTERNS.matcher(nfc).find();
    }
}
