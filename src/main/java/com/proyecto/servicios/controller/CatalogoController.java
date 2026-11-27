package com.proyecto.servicios.controller;

import com.proyecto.servicios.model.GenericResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/catalogos")
public class CatalogoController {

    private static final List<String> OCUPACIONES = List.of(
            "Abogado/a",
            "Actor / Actriz",
            "Administrador/a de empresas",
            "Agente de ventas",
            "Agente de seguros",
            "Agricultor/a",
            "Albañil",
            "Analista de datos",
            "Analista financiero/a",
            "Arquitecto/a",
            "Artesano/a",
            "Asistente administrativo/a",
            "Auxiliar contable",
            "Bibliotecario/a",
            "Biólogo/a",
            "Cajero/a",
            "Camarero/a / Mesero/a",
            "Carpintero/a",
            "Chofer / Conductor/a",
            "Cocinero/a / Chef",
            "Comerciante",
            "Contador/a",
            "Controlador/a de calidad",
            "Coordinador/a de proyectos",
            "Cosmetólogo/a / Estilista",
            "Dentista / Odontólogo/a",
            "Desarrollador/a de software",
            "Diseñador/a gráfico/a",
            "Diseñador/a industrial",
            "Docente de educación básica",
            "Docente de educación media superior",
            "Docente universitario/a",
            "Ejecutivo/a de cuenta",
            "Electricista",
            "Empleado/a de mostrador",
            "Empleado/a de limpieza",
            "Enfermero/a",
            "Escritor/a",
            "Estudiante",
            "Farmacéutico/a",
            "Fisioterapeuta",
            "Fotógrafo/a",
            "Gerente general",
            "Gerente de ventas",
            "Guardia de seguridad",
            "Hogar (ama de casa / amo de casa)",
            "Informático/a / Técnico/a en sistemas",
            "Ingeniero/a civil",
            "Ingeniero/a en computación",
            "Ingeniero/a eléctrico/a",
            "Ingeniero/a industrial",
            "Ingeniero/a mecánico/a",
            "Ingeniero/a químico/a",
            "Investigador/a científico/a",
            "Jardinero/a",
            "Jubilado/a / Pensionado/a",
            "Locutor/a",
            "Maestro/a de obra",
            "Mecánico/a automotriz",
            "Médico/a general",
            "Médico/a especialista",
            "Mensajero/a / Repartidor/a",
            "Mercadólogo/a",
            "Militar",
            "Mesero/a",
            "Músico/a",
            "Notario/a",
            "Nutriólogo/a",
            "Obrero/a",
            "Operador/a de maquinaria",
            "Panadero/a",
            "Peluquero/a",
            "Periodista",
            "Pintor/a",
            "Plomero/a",
            "Policía",
            "Profesional independiente",
            "Programador/a",
            "Psicólogo/a",
            "Recepcionista",
            "Representante de atención a clientes",
            "Servidor/a público/a",
            "Soldador/a",
            "Supervisor/a de producción",
            "Taxista",
            "Técnico/a en electrónica",
            "Técnico/a en mantenimiento",
            "Trabajador/a social",
            "Trabajador/a de la construcción",
            "Trabajador/a del campo",
            "Traductor/a",
            "Transportista",
            "Veterinario/a",
            "Vendedor/a ambulante",
            "Vendedor/a de comercio",
            "Otra ocupación"
    );

    private static final List<String> NACIONALIDADES = List.of(
            "Mexicana",
            "Alemana",
            "Argentina",
            "Australiana",
            "Austriaca",
            "Belga",
            "Beliceña",
            "Boliviana",
            "Brasileña",
            "Británica",
            "Canadiense",
            "Chilena",
            "China",
            "Colombiana",
            "Coreana",
            "Costarricense",
            "Cubana",
            "Danesa",
            "Dominicana",
            "Ecuatoriana",
            "Egipcia",
            "Salvadoreña",
            "Española",
            "Estadounidense",
            "Filipina",
            "Finlandesa",
            "Francesa",
            "Griega",
            "Guatemalteca",
            "Haitiana",
            "Hondureña",
            "India",
            "Indonesa",
            "Irlandesa",
            "Israelí",
            "Italiana",
            "Jamaicana",
            "Japonesa",
            "Marroquí",
            "Neerlandesa",
            "Neozelandesa",
            "Nicaragüense",
            "Noruega",
            "Panameña",
            "Paraguaya",
            "Peruana",
            "Polaca",
            "Portuguesa",
            "Puertorriqueña",
            "Rusa",
            "Sueca",
            "Suiza",
            "Tailandesa",
            "Turca",
            "Ucraniana",
            "Uruguaya",
            "Venezolana",
            "Vietnamita",
            "Otra"
    );

    @GetMapping("/ocupaciones")
    public ResponseEntity<GenericResponse> listarOcupaciones(
            @RequestParam(required = false) String busqueda) {
        List<String> resultado = filtrarPorBusqueda(OCUPACIONES, busqueda);
        return ResponseEntity.ok(GenericResponse.ok(
                "Catálogo de ocupaciones consultado exitosamente.",
                resultado
        ));
    }

    @GetMapping("/nacionalidades")
    public ResponseEntity<GenericResponse> listarNacionalidades(
            @RequestParam(required = false) String busqueda) {
        List<String> resultado = filtrarPorBusqueda(NACIONALIDADES, busqueda);
        return ResponseEntity.ok(GenericResponse.ok(
                "Catálogo de nacionalidades consultado exitosamente.",
                resultado
        ));
    }

    private List<String> filtrarPorBusqueda(List<String> origen, String busqueda) {
        if (busqueda == null || busqueda.isBlank()) {
            return origen;
        }
        String q = normalizar(busqueda);
        return origen.stream()
                .filter(item -> normalizar(item).contains(q))
                .toList();
    }

    private String normalizar(String input) {
        return Normalizer.normalize(input.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toLowerCase(Locale.ROOT);
    }
}
