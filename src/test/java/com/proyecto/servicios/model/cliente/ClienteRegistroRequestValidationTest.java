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
