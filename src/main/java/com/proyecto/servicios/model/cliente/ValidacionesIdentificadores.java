package com.proyecto.servicios.model.cliente;

import com.proyecto.servicios.entity.cliente.Sexo;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class ValidacionesIdentificadores {

    private static final DateTimeFormatter FECHA_RFC = DateTimeFormatter.ofPattern("yyMMdd", Locale.ROOT);
    private static final DateTimeFormatter FECHA_CURP = DateTimeFormatter.ofPattern("yyMMdd", Locale.ROOT);

    private ValidacionesIdentificadores() {
    }

    public static String detalleErrorCurp(String curp, LocalDate fechaNacimiento, Sexo sexo) {
        if (curp == null || fechaNacimiento == null || curp.length() != 18) {
            return null;
        }
        String valor = curp.toUpperCase(Locale.ROOT);
        String fechaEsperada = FECHA_CURP.format(fechaNacimiento);
        if (!valor.substring(4, 10).equals(fechaEsperada)) {
            return "La fecha en la CURP (" + valor.substring(4, 10) + ") no coincide con la fecha de nacimiento (" + fechaEsperada + ")";
        }
        if (sexo == Sexo.M && valor.charAt(10) != 'H') {
            return "El sexo en la CURP debe ser 'H' para sexo Masculino";
        }
        if (sexo == Sexo.F && valor.charAt(10) != 'M') {
            return "El sexo en la CURP debe ser 'M' para sexo Femenino";
        }
        int suma = 0;
        for (int i = 0; i < 17; i++) {
            int valorCaracter = valorCaracterCurp(valor.charAt(i));
            if (valorCaracter < 0) {
                return "La CURP contiene un carácter no reconocido en la posición " + (i + 1);
            }
            suma += valorCaracter * (18 - i);
        }
        int digitoVerificador = (10 - suma % 10) % 10;
        char esperado = (char) ('0' + digitoVerificador);
        if (valor.charAt(17) != esperado) {
            return "El dígito verificador de la CURP no es válido";
        }
        return null;
    }

    public static String detalleErrorRfc(String rfc, LocalDate fechaNacimiento) {
        if (rfc == null || fechaNacimiento == null) {
            return null;
        }
        String valor = rfc.toUpperCase(Locale.ROOT);
        int inicioFecha = valor.length() == 13 ? 4 : 3;
        int finFecha = inicioFecha + 6;
        if (valor.length() == 12 || valor.length() == 13) {
            String fechaEnRfc = valor.substring(inicioFecha, finFecha);
            String fechaEsperada = FECHA_RFC.format(fechaNacimiento);
            if (!fechaEnRfc.equals(fechaEsperada)) {
                return "La fecha en el RFC (" + fechaEnRfc + ") no coincide con la fecha de nacimiento (" + fechaEsperada + ")";
            }
        }
        return null;
    }

    public static boolean curpConsistente(String curp, LocalDate fechaNacimiento, Sexo sexo) {
        return detalleErrorCurp(curp, fechaNacimiento, sexo) == null;
    }

    public static boolean rfcConsistente(String rfc, LocalDate fechaNacimiento) {
        return detalleErrorRfc(rfc, fechaNacimiento) == null;
    }

    private static int valorCaracterCurp(char caracter) {
        if (caracter >= '0' && caracter <= '9') {
            return caracter - '0';
        }
        if (caracter >= 'A' && caracter <= 'N') {
            return caracter - 'A' + 10;
        }
        if (caracter == 'Ñ') {
            return 24;
        }
        if (caracter >= 'O' && caracter <= 'Z') {
            return caracter - 'A' + 11;
        }
        return -1;
    }
}
