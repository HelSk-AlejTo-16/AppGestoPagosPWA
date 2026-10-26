package com.proyecto.servicios.util;

import com.proyecto.servicios.exception.ErrorValidacionException;
import com.proyecto.servicios.service.ClabeUtils;

public final class ReferenciaValidator {

    private ReferenciaValidator() {
    }

    /**
     * Valida el formato de la referencia o teléfono según las reglas del tipoFront y metadatos de GestoPago.
     *
     * @param referencia            Cadena con el teléfono o número de servicio
     * @param tipoFront             Clasificador de GestoPago (1=Telefonía, 2=Servicios, 40=Remesas/Transferencias, etc.)
     * @param hasDigitoVerificador  Indica si el carrier exige validación de dígito verificador
     * @throws ErrorValidacionException Si el formato no cumple con las reglas de negocio
     */
    public static void validarReferencia(String referencia, Integer tipoFront, Boolean hasDigitoVerificador) {
        if (referencia == null || referencia.trim().isEmpty()) {
            throw new ErrorValidacionException("La referencia o dato de pago no puede estar vacío.");
        }

        String ref = referencia.trim();

        if (tipoFront == null) {
            // Validación general básica si no se especifica tipoFront
            if (ref.length() < 3 || ref.length() > 60) {
                throw new ErrorValidacionException("La referencia debe tener entre 3 y 60 caracteres.");
            }
            return;
        }

        switch (tipoFront) {
            case 1 -> validarTelefonia(ref);
            case 2, 4 -> validarServicioPublico(ref, hasDigitoVerificador);
            case 40 -> validarTransferencia(ref);
            case 5, 30 -> validarPinDigital(ref);
            default -> {
                if (ref.length() < 3 || ref.length() > 60) {
                    throw new ErrorValidacionException("La longitud de la referencia no es válida para el tipo de servicio.");
                }
            }
        }
    }

    private static void validarTelefonia(String ref) {
        if (!ref.matches("^\\d{10}$")) {
            throw new ErrorValidacionException(
                    "Para recargas de tiempo aire, debes ingresar un número celular válido de 10 dígitos numéricos."
            );
        }
    }

    private static void validarServicioPublico(String ref, Boolean hasDigitoVerificador) {
        if (ref.length() < 4 || ref.length() > 50) {
            throw new ErrorValidacionException(
                    "La referencia de pago de servicio debe contener entre 4 y 50 caracteres."
            );
        }

        // Si el servicio especifica dígito verificador, se valida con algoritmo de Luhn / Módulo 10
        if (Boolean.TRUE.equals(hasDigitoVerificador) && ref.matches("^\\d+$")) {
            if (!esLuhnValido(ref)) {
                throw new ErrorValidacionException(
                        "El dígito verificador de la referencia del servicio no es válido. Por favor verifica los datos en tu recibo."
                );
            }
        }
    }

    private static void validarTransferencia(String ref) {
        if (ref.matches("^\\d{16}$")) {
            if (!esLuhnValido(ref)) {
                throw new ErrorValidacionException(
                        "El número de tarjeta ingresado no es válido. Por favor verifica los 16 dígitos."
                );
            }
        } else if (ref.matches("^\\d{18}$")) {
            if (!ClabeUtils.esValida(ref)) {
                throw new ErrorValidacionException(
                        "El número de cuenta CLABE ingresado no es válido. Por favor verifica los 18 dígitos."
                );
            }
        } else {
            throw new ErrorValidacionException(
                    "Para transferencias o pagos bancarios, debes ingresar una tarjeta válida de 16 dígitos o una cuenta CLABE de 18 dígitos numéricos."
            );
        }
    }

    private static void validarPinDigital(String ref) {
        if (ref.length() < 3 || ref.length() > 60) {
            throw new ErrorValidacionException(
                    "El código o pin digital debe contener entre 3 y 60 caracteres."
            );
        }
    }

    /**
     * Valida números de control mediante el algoritmo de Luhn (Módulo 10).
     */
    public static boolean esLuhnValido(String numero) {
        if (numero == null || numero.isEmpty() || !numero.matches("^\\d+$")) {
            return false;
        }

        int suma = 0;
        boolean alternate = false;
        for (int i = numero.length() - 1; i >= 0; i--) {
            int n = Integer.parseInt(numero.substring(i, i + 1));
            if (alternate) {
                n *= 2;
                if (n > 9) {
                    n = (n % 10) + 1;
                }
            }
            suma += n;
            alternate = !alternate;
        }
        return (suma % 10 == 0);
    }
}
