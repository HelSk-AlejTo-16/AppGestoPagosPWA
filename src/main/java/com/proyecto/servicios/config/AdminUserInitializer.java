package com.proyecto.servicios.config;

import com.proyecto.servicios.entity.cliente.Cliente;
import com.proyecto.servicios.entity.cliente.Contacto;
import com.proyecto.servicios.entity.cliente.Direccion;
import com.proyecto.servicios.entity.cliente.EstadoCivil;
import com.proyecto.servicios.entity.cliente.InformacionLaboral;
import com.proyecto.servicios.entity.cliente.Sexo;
import com.proyecto.servicios.entity.cliente.Usuario;
import com.proyecto.servicios.repositorys.cliente.ClienteRepository;
import com.proyecto.servicios.repositorys.cliente.UsuarioRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.admin.seed-enabled", havingValue = "true", matchIfMissing = true)
public class AdminUserInitializer implements CommandLineRunner {

    private final UsuarioRepository usuarioRepository;
    private final ClienteRepository clienteRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;

    public AdminUserInitializer(UsuarioRepository usuarioRepository,
                                ClienteRepository clienteRepository,
                                PasswordEncoder passwordEncoder,
                                @Value("${app.admin.email:admin@banco.com}") String adminEmail,
                                @Value("${app.admin.password:Admin#123456}") String adminPassword) {
        this.usuarioRepository = usuarioRepository;
        this.clienteRepository = clienteRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(String... args) {
        if (usuarioRepository.findByCorreoIgnoreCase(adminEmail).isEmpty()) {
            log.info("Inicializando usuario administrador ({})", adminEmail);
            Cliente adminCliente = new Cliente();
            adminCliente.setNombre("Administrador");
            adminCliente.setApellidoPaterno("Sistema");
            adminCliente.setFechaNacimiento(LocalDate.of(1990, 1, 1));
            adminCliente.setSexo(Sexo.M);
            adminCliente.setEstadoCivil(EstadoCivil.Soltero);
            adminCliente.setCurp("AAMI900101HDFRRN01");
            adminCliente.setRfc("ADMI9001011A1");
            adminCliente.setNacionalidad("Mexicana");

            Contacto contacto = new Contacto();
            contacto.setTelefonoMovil("5512345678");
            adminCliente.setContacto(contacto);

            Direccion direccion = new Direccion();
            direccion.setCalle("Av. Reforma");
            direccion.setNumeroExterior("100");
            direccion.setColonia("Centro");
            direccion.setMunicipio("Cuauhtémoc");
            direccion.setEstado("CDMX");
            direccion.setCodigoPostal("06000");
            adminCliente.setDireccion(direccion);

            InformacionLaboral laboral = new InformacionLaboral();
            laboral.setIngresoMensual(new BigDecimal("50000.00"));
            laboral.setOcupacion("Administrador de Sistemas");
            laboral.setEmpresa("Banco Central");
            adminCliente.setInformacionLaboral(laboral);

            Usuario adminUsuario = new Usuario();
            adminUsuario.setCorreo(adminEmail);
            adminUsuario.setPassword(passwordEncoder.encode(adminPassword));
            adminUsuario.setActivo(true);
            adminUsuario.setRol("ROLE_ADMIN");
            adminCliente.setUsuario(adminUsuario);

            clienteRepository.save(adminCliente);
            log.info("Usuario administrador creado exitosamente con correo: {}", adminEmail);
        }
    }
}

