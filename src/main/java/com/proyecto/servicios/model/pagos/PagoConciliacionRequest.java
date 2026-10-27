package com.proyecto.servicios.model.pagos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Solicitud para conciliar una transacción que quedó en estado {@code PENDIENTE_CONCILIACION}
 * tras un tiempo de espera agotado (timeout) con el proveedor externo GestoPago.
 * <p>
 * Si {@code procesadaPorProveedor} se envía como {@code null}, el sistema ejecuta una
 * verificación idempotente con el proveedor reutilizando el código {@code upc} original.
 * Si se indica explícitamente {@code true} o {@code false} (tras dictamen del portal del proveedor),
 * liquida la operación como {@code EXITOSO} o reintegra los fondos marcándola como {@code REVERTIDO}.
 */
public record PagoConciliacionRequest(
        @NotBlank(message = "El código único de operación (UPC) es obligatorio para conciliar la transacción.")
        @Size(max = 20, message = "El código UPC no puede exceder los 20 caracteres.")
        String upc,

        Boolean procesadaPorProveedor,

        @Size(max = 50, message = "El folio del proveedor no puede exceder los 50 caracteres.")
        String folioTransaccionGp,

        @Size(max = 50, message = "El código de autorización no puede exceder los 50 caracteres.")
        String folioAutorizacion,

        @Size(max = 250, message = "La observación de conciliación no puede exceder los 250 caracteres.")
        String observacion
) {
}

