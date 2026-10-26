package com.proyecto.servicios.service;

import com.proyecto.servicios.model.cliente.PaginaResponse;
import com.proyecto.servicios.model.pagos.PagoBusquedaRequest;
import com.proyecto.servicios.model.pagos.PagoServicioRequest;
import com.proyecto.servicios.model.pagos.PagoServicioResponse;
import com.proyecto.servicios.model.pagos.RecargaTelefonicaRequest;

public interface PagoServicioService {

    /**
     * Procesa una transacción de pago de servicios públicos o privados (CFE, Telmex, Agua, etc.)
     * implementando el patrón de reserva atómica de saldo con compensación inmediata (Rollback)
     * en caso de rechazo del proveedor.
     *
     * @param request                  Datos del servicio (cuenta, PIN, servicio, producto, referencia del recibo, monto)
     * @param correoUsuarioAutenticado Correo extraído del JWT del usuario
     * @return Comprobante con folio de autorización, UPC y saldo actualizado
     */
    PagoServicioResponse procesarPago(PagoServicioRequest request, String correoUsuarioAutenticado);

    /**
     * Procesa una transacción de recarga de tiempo aire o paquete de telefonía móvil
     * (Telcel, Movistar, AT&T, etc.) validando el número celular a 10 dígitos y el monto del paquete.
     *
     * @param request                  Datos de la recarga (cuenta, PIN, compañía, paquete, teléfono a 10 dígitos, monto)
     * @param correoUsuarioAutenticado Correo extraído del JWT del usuario
     * @return Comprobante con folio de autorización, UPC y saldo actualizado
     */
    PagoServicioResponse procesarRecarga(RecargaTelefonicaRequest request, String correoUsuarioAutenticado);

    /**
     * Consulta el historial paginado de pagos de servicios y recargas.
     * Si el usuario no es ADMIN, se limita estrictamente a los pagos de sus propias cuentas.
     *
     * @param request                  Criterios de filtro y paginación
     * @param correoUsuarioAutenticado Correo del usuario en sesión
     * @param esAdmin                  Indica si el usuario posee rol administrativo
     * @return Página de resultados
     */
    PaginaResponse<PagoServicioResponse> consultarHistorial(
            PagoBusquedaRequest request,
            String correoUsuarioAutenticado,
            boolean esAdmin
    );
}
