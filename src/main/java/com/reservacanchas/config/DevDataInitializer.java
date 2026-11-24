package com.reservacanchas.config;

import com.reservacanchas.model.Administrador;
import com.reservacanchas.model.AdministradorSede;
import com.reservacanchas.repository.AdministradorRepository;
import com.reservacanchas.repository.AdministradorSedeRepository;
import com.reservacanchas.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@Profile("dev")
public class DevDataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataInitializer.class);

    private static final String CORREO_ADMIN_SEDE = "admin@prueba.com";
    private static final String CORREO_ADMIN_GENERAL = "admingeneral@prueba.com";

    private final UsuarioRepository usuarioRepository;
    private final AdministradorSedeRepository administradorSedeRepository;
    private final AdministradorRepository administradorRepository;
    private final PasswordEncoder passwordEncoder;
    private final String passwordAdminSede;
    private final String passwordAdminGeneral;

    public DevDataInitializer(UsuarioRepository usuarioRepository,
                              AdministradorSedeRepository administradorSedeRepository,
                              AdministradorRepository administradorRepository,
                              PasswordEncoder passwordEncoder,
                              @Value("${dev.data.admin-sede-password:admin123}") String passwordAdminSede,
                              @Value("${dev.data.admin-general-password:admin123}") String passwordAdminGeneral) {
        this.usuarioRepository = usuarioRepository;
        this.administradorSedeRepository = administradorSedeRepository;
        this.administradorRepository = administradorRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordAdminSede = passwordAdminSede;
        this.passwordAdminGeneral = passwordAdminGeneral;
    }

    @Override
    public void run(String... args) {
        crearAdminSedeSiNoExiste();
        crearAdminGeneralSiNoExiste(CORREO_ADMIN_GENERAL, passwordAdminGeneral);
        crearAdminGeneralSiNoExiste("admin.general@prueba.com", passwordAdminGeneral);
    }

    private void crearAdminSedeSiNoExiste() {
        if (!usuarioRepository.existsByCorreo(CORREO_ADMIN_SEDE)) {
            AdministradorSede admin = new AdministradorSede();
            admin.setNombre("Admin");
            admin.setApellido("Sede");
            admin.setTelefono("999999999");
            admin.setCorreo(CORREO_ADMIN_SEDE);
            admin.setPasswordHash(passwordEncoder.encode(passwordAdminSede));
            admin.setFechaRegistro(LocalDateTime.now(ClockConfig.ZONA_HORARIA));
            admin.setActivo(true);

            administradorSedeRepository.save(admin);
            log.info("Usuario AdministradorSede de prueba creado: {} (password: {})", CORREO_ADMIN_SEDE, passwordAdminSede);
        }
    }

    private void crearAdminGeneralSiNoExiste(String correo, String password) {
        if (!usuarioRepository.existsByCorreo(correo)) {
            Administrador admin = new Administrador();
            admin.setNombre("Admin");
            admin.setApellido("General");
            admin.setTelefono("999888777");
            admin.setCorreo(correo);
            admin.setPasswordHash(passwordEncoder.encode(password));
            admin.setFechaRegistro(LocalDateTime.now(ClockConfig.ZONA_HORARIA));
            admin.setActivo(true);

            administradorRepository.save(admin);
            log.info("Usuario Administrador (Admin General) de prueba creado: {} (password: {})", correo, password);
        }
    }
}
