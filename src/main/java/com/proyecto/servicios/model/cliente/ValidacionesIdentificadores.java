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

    public static boolean curpConsistente(String curp, LocalDate fechaNacimiento, Sexo sexo) {
        if (curp == null || fechaNacimiento == null || curp.length() != 18) {
            return false;
        }
        String valor = curp.toUpperCase(Locale.ROOT);
        if (!valor.substring(4, 10).equals(FECHA_CURP.format(fechaNacimiento))) {
            return false;
        }
        if (sexo == Sexo.M && valor.charAt(10) != 'H') {
            return false;
        }
        if (sexo == Sexo.F && valor.charAt(10) != 'M') {
            return false;
        }
        int suma = 0;
        for (int i = 0; i < 17; i++) {
            int valorCaracter = valorCaracterCurp(valor.charAt(i));
            if (valorCaracter < 0) {
                return false;
            }
            suma += valorCaracter * (18 - i);
        }
        int digitoVerificador = (10 - suma % 10) % 10;
        return valor.charAt(17) == (char) ('0' + digitoVerificador);
    }

    public static boolean rfcConsistente(String rfc, LocalDate fechaNacimiento) {
        if (rfc == null || fechaNacimiento == null) {
            return false;
        }
        String valor = rfc.toUpperCase(Locale.ROOT);
        int inicioFecha = valor.length() == 13 ? 4 : 3;
        int finFecha = inicioFecha + 6;
        return valor.length() == 12 || valor.length() == 13
                ? valor.substring(inicioFecha, finFecha).equals(FECHA_RFC.format(fechaNacimiento))
                : false;
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
