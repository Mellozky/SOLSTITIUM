package com.solstitium.solstitium.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.solstitium.solstitium.model.dto.CambioParametro;
import com.solstitium.solstitium.model.dto.SolicitudActualizacionParametros;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;

/** Sin @Transactional: las comprobaciones posteriores usan transacciones nuevas, fuera de la fallida. */
@SpringBootTest
@WithMockUser(username = "prueba_transaccion_parametros", roles = "ADMIN")
class TransaccionParametrosTest {
    private static final String USUARIO = "prueba_transaccion_parametros";
    @Autowired private ParametroService servicio;
    @Autowired private JdbcTemplate consultas;
    @Autowired private PasswordEncoder codificador;
    private List<Map<String, Object>> parametrosOriginales;

    @BeforeEach
    void preparar() {
        parametrosOriginales = consultas.queryForList("select clave, valor, fecha_actualizacion from parametro order by clave");
        consultas.update("insert into administrador (usuario, contrasena_hash, nombre, activo) values (?, ?, ?, true)",
                USUARIO, codificador.encode("ClaveDePrueba-789"), "Prueba de transacción");
    }

    @AfterEach
    void limpiar() {
        consultas.execute("drop trigger if exists fallo_auditoria_prueba on bitacora_parametro");
        consultas.execute("drop function if exists provocar_fallo_auditoria_prueba()");
        // Restaura incluso si una aserción detecta una regresión.
        for (var fila : parametrosOriginales) {
            consultas.update("update parametro set valor=?, fecha_actualizacion=? where clave=?",
                    fila.get("valor"), fila.get("fecha_actualizacion"), fila.get("clave"));
        }
        consultas.update("update estrato set porcentaje_ajuste=50 where numero=1");
        consultas.update("delete from bitacora_parametro where administrador_id=(select id from administrador where usuario=?)", USUARIO);
        consultas.update("delete from administrador where usuario=?", USUARIO);
        SecurityContextHolder.clearContext();
    }

    @Test
    void falloRealEnSegundaAuditoriaRevierteParametrosFechasYPrimeraAuditoriaCv27Cv28() {
        var estratosAntes = consultas.queryForList("select * from estrato order by numero");
        var bitacoraAntes = consultas.queryForList("select * from bitacora_parametro order by id");
        consultas.execute("""
                create function provocar_fallo_auditoria_prueba() returns trigger language plpgsql as $$
                begin
                    if new.campo = 'porcentaje_ajuste' then
                        raise exception 'FALLO_PROVOCADO_AUDITORIA';
                    end if;
                    return new;
                end; $$
                """);
        consultas.execute("""
                create trigger fallo_auditoria_prueba before insert on bitacora_parametro
                for each row execute function provocar_fallo_auditoria_prueba()
                """);

        assertThatThrownBy(() -> servicio.actualizar(new SolicitudActualizacionParametros(List.of(
                cambio("parametro", "costo_unitario_kwh", "valor", "1100"),
                cambio("estrato", "1", "porcentaje_ajuste", "45")))))
                .isInstanceOf(DataAccessException.class).hasStackTraceContaining("FALLO_PROVOCADO_AUDITORIA");

        assertThat(consultas.queryForList("select clave, valor, fecha_actualizacion from parametro order by clave"))
                .isEqualTo(parametrosOriginales);
        assertThat(consultas.queryForList("select * from estrato order by numero")).isEqualTo(estratosAntes);
        assertThat(consultas.queryForList("select * from bitacora_parametro order by id")).isEqualTo(bitacoraAntes);
    }

    @Test
    void actualizacionCorrectaSeConfirmaJuntoConAuditoria() {
        assertThat(servicio.actualizar(new SolicitudActualizacionParametros(List.of(
                cambio("parametro", "costo_unitario_kwh", "valor", "1100"))))).isEqualTo(1);
        assertThat(consultas.queryForObject("select valor from parametro where clave='costo_unitario_kwh'", BigDecimal.class))
                .isEqualByComparingTo("1100");
        assertThat(consultas.queryForObject("select count(*) from bitacora_parametro where administrador_id=(select id from administrador where usuario=?)", Integer.class, USUARIO))
                .isEqualTo(1);
    }

    @Test
    void lotesConcurrentesNoDejanAreasIncoherentes() throws Exception {
        try (var ejecutor = Executors.newFixedThreadPool(2)) {
            var inicio = new CountDownLatch(1);
            var minimo = ejecutor.submit(() -> actualizarArea(inicio, "area_minima", "450"));
            var maximo = ejecutor.submit(() -> actualizarArea(inicio, "area_maxima", "100"));
            inicio.countDown();
            assertThat(List.of(minimo.get(20, TimeUnit.SECONDS), maximo.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        var minimo = consultas.queryForObject("select valor from parametro where clave='area_minima'", BigDecimal.class);
        var maximo = consultas.queryForObject("select valor from parametro where clave='area_maxima'", BigDecimal.class);
        assertThat(minimo).isLessThanOrEqualTo(maximo);
    }

    private boolean actualizarArea(CountDownLatch inicio, String clave, String valor) throws InterruptedException {
        inicio.await();
        var contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(new UsernamePasswordAuthenticationToken(USUARIO, "", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        SecurityContextHolder.setContext(contexto);
        try {
            servicio.actualizar(new SolicitudActualizacionParametros(List.of(cambio("parametro", clave, "valor", valor))));
            return true;
        } catch (IllegalArgumentException excepcion) {
            return false;
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private CambioParametro cambio(String tabla, String id, String campo, String valor) {
        return new CambioParametro(tabla, campo, id, new BigDecimal(valor));
    }
}
