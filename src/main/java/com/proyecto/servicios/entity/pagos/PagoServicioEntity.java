package com.proyecto.servicios.entity.pagos;

import com.proyecto.servicios.entity.cliente.CuentaBancaria;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "pagos_servicios")
@Getter
@Setter
public class PagoServicioEntity {

    @Id
    @UuidGenerator
    @Column(name = "id_pago", nullable = false, updatable = false)
    private UUID idPago;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "cuenta_id", nullable = false)
    private CuentaBancaria cuenta;

    @Column(name = "id_servicio", nullable = false, length = 30)
    private String idServicio;

    @Column(name = "id_producto", nullable = false, length = 30)
    private String idProducto;

    @Column(name = "nombre_servicio", length = 150)
    private String nombreServicio;

    @Column(name = "nombre_producto", length = 150)
    private String nombreProducto;

    @Column(name = "referencia", nullable = false, length = 100)
    private String referencia;

    @Column(name = "monto", nullable = false, precision = 15, scale = 2)
    private BigDecimal monto;

    @Column(name = "upc", nullable = false, unique = true, length = 10)
    private String upc;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "estado", nullable = false, columnDefinition = "estado_pago_servicio_enum")
    private EstadoPagoServicio estado = EstadoPagoServicio.PENDIENTE;

    @Column(name = "codigo_respuesta_proveedor", length = 20)
    private String codigoRespuestaProveedor;

    @Column(name = "mensaje_proveedor", columnDefinition = "TEXT")
    private String mensajeProveedor;

    @Column(name = "folio_autorizacion", length = 100)
    private String folioAutorizacion;

    @Column(name = "folio_transaccion_gp", length = 100)
    private String folioTransaccionGp;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private OffsetDateTime creadoEn;

    @Column(name = "fecha_actualizacion", nullable = false)
    private OffsetDateTime fechaActualizacion;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        this.creadoEn = now;
        this.fechaActualizacion = now;
        if (this.estado == null) {
            this.estado = EstadoPagoServicio.PENDIENTE;
        }
    }

    @PreUpdate
    void onUpdate() {
        this.fechaActualizacion = OffsetDateTime.now(ZoneOffset.UTC);
    }
}

