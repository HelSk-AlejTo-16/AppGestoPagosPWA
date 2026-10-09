package com.proyecto.servicios.entity.cliente;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "cuenta_bancaria")
@Getter
@Setter
public class CuentaBancaria {

    @Id
    @UuidGenerator
    @Column(name = "id_bancaria", nullable = false, updatable = false)
    private UUID idBancaria;

    @ManyToOne(optional = false)
    @JoinColumn(name = "persona_id", nullable = false)
    private Cliente cliente;

    @Column(name = "activa", nullable = false)
    private boolean activa = true;

    @Column(name = "clabe", nullable = false, unique = true, length = 18)
    private String clabe;

    @Column(name = "numero_cuenta", nullable = false, unique = true, length = 20)
    private String numeroCuenta;

    @Column(name = "saldo", nullable = false, precision = 15, scale = 2)
    private BigDecimal saldo;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private OffsetDateTime creadoEn;

    @jakarta.persistence.PrePersist
    void onCreate() {
        creadoEn = OffsetDateTime.now(ZoneOffset.UTC);
    }
}
