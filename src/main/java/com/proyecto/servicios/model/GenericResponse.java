package com.proyecto.servicios.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL) 
public class GenericResponse {

    private Integer codigo;
    private String mensaje;
    private Map<String, String> errores; 
    /** Carga útil de las respuestas exitosas (DTO de respuesta, página, etc.). */
    private Object datos;

    /** Respuesta exitosa uniforme: {@code codigo = 0}. */
    public static GenericResponse ok(String mensaje, Object datos) {
        GenericResponse response = new GenericResponse();
        response.setCodigo(0);
        response.setMensaje(mensaje);
        response.setDatos(datos);
        return response;
    }
}