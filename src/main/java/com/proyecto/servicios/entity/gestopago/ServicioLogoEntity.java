package com.proyecto.servicios.entity.gestopago;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "gestopago_service_logos")
@Getter
@Setter
public class ServicioLogoEntity {

    @Id
    @Column(name = "id_servicio", nullable = false, updatable = false)
    private Integer idServicio;

    @Column(name = "nombre_servicio", nullable = false, length = 150)
    private String nombreServicio;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType = "image/png";

    @Column(name = "logo_data", nullable = false, columnDefinition = "bytea")
    private byte[] logoData;

    @Column(name = "fuente", nullable = false, length = 50)
    private String fuente = "LOCAL";

    @Column(name = "actualizado_en", nullable = false)
    private OffsetDateTime actualizadoEn;

    @PrePersist
    @PreUpdate
    void touch() {
        actualizadoEn = OffsetDateTime.now(ZoneOffset.UTC);
    }
}
