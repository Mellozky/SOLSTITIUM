package com.solstitium.solstitium.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.solstitium.solstitium.apoyo.SolicitudesPrueba;
import com.solstitium.solstitium.exception.ConflictoCatalogoException;
import com.solstitium.solstitium.model.dto.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Sin transacción envolvente: comprueba commits, rollback real y bloqueos entre conexiones. */
@SpringBootTest
@WithMockUser(username = "prueba_transaccion_paneles", roles = "ADMIN")
class TransaccionPanelesTest {
    private static final String USUARIO = "prueba_transaccion_paneles";
    @Autowired private PanelService servicio;
    @Autowired private ParametroService parametros;
    @Autowired private SimulacionService simulaciones;
    @Autowired private JdbcTemplate consultas;
    @Autowired private PlatformTransactionManager transacciones;
    private List<Map<String, Object>> panelesOriginales;
    private List<Map<String, Object>> parametrosOriginales;

    @BeforeEach
    void preparar() {
        panelesOriginales = consultas.queryForList("select * from panel order by id");
        parametrosOriginales = consultas.queryForList("select * from parametro order by clave");
        consultas.update("insert into administrador (usuario, contrasena_hash, nombre, activo) values (?, ?, ?, true)",
                USUARIO, "hash-no-utilizado-en-login", "Prueba transacción catálogo");
    }

    @AfterEach
    void limpiar() {
        consultas.execute("drop trigger if exists fallo_bitacora_paneles on bitacora_parametro");
        consultas.execute("drop function if exists provocar_fallo_bitacora_paneles()");
        consultas.update("delete from bitacora_parametro where administrador_id=(select id from administrador where usuario=?)", USUARIO);
        consultas.update("update panel set es_referencia=false where es_referencia");
        for (var fila : panelesOriginales) {
            consultas.update("update panel set marca=?, modelo=?, potencia_wp=?, area_m2=?, activo=?, es_referencia=? where id=?",
                    fila.get("marca"), fila.get("modelo"), fila.get("potencia_wp"), fila.get("area_m2"), fila.get("activo"), fila.get("es_referencia"), fila.get("id"));
        }
        var idsOriginales = panelesOriginales.stream().map(f -> ((Number) f.get("id")).longValue()).toList();
        for (Long id : consultas.queryForList("select id from panel", Long.class)) {
            if (!idsOriginales.contains(id)) consultas.update("delete from panel where id=?", id);
        }
        for (var fila : parametrosOriginales) {
            consultas.update("update parametro set valor=?, fecha_actualizacion=? where clave=?", fila.get("valor"), fila.get("fecha_actualizacion"), fila.get("clave"));
        }
        consultas.update("delete from administrador where usuario=?", USUARIO);
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @ValueSource(strings = {"registrar", "editar", "estado", "referencia"})
    void errorRealDeBitacoraRevierteTodaOperacionInclusoLaReferenciaSincronizadaCv28(String operacion) {
        var antes = consultas.queryForList("select * from bitacora_parametro order by id");
        // Alta/edición fallan en el segundo campo; referencia falla después de auditar la anterior.
        String condicion = switch (operacion) {
            case "registrar", "editar" -> "new.campo = 'modelo'";
            case "referencia" -> "new.campo = 'es_referencia' and new.valor_nuevo = 'true'";
            default -> "new.campo = 'activo'";
        };
        consultas.execute("create function provocar_fallo_bitacora_paneles() returns trigger language plpgsql as $$ begin if "
                + condicion + " then raise exception 'FALLO_REAL_BITACORA_PANELES'; end if; return new; end; $$");
        consultas.execute("create trigger fallo_bitacora_paneles before insert on bitacora_parametro for each row execute function provocar_fallo_bitacora_paneles()");
        assertThatThrownBy(() -> {
            switch (operacion) {
                case "registrar" -> servicio.registrar(datos("Alta revertida"));
                case "editar" -> servicio.editar(2L, datos("Edición revertida"));
                case "estado" -> servicio.cambiarEstado(2L, new SolicitudEstadoPanel(false));
                default -> servicio.designarReferencia(2L, new SolicitudReferenciaPanel(true));
            }
        }).isInstanceOf(DataAccessException.class).hasStackTraceContaining("FALLO_REAL_BITACORA_PANELES");
        assertThat(consultas.queryForList("select * from panel order by id")).isEqualTo(panelesOriginales);
        assertThat(consultas.queryForList("select * from bitacora_parametro order by id")).isEqualTo(antes);
        assertThat(consultas.queryForObject("select id from panel where activo and es_referencia", Long.class)).isEqualTo(1);
    }

    @Test
    void cambioConfirmadoModificaPotenciaAreaYDimensionamientoDeLaSiguienteSimulacionRf04() {
        var solicitud = SolicitudesPrueba.crear(4, "600", "10", 1, 1, 1);
        var antes = simulaciones.simular(solicitud);
        assertThat(antes.getPanelReferencia()).isEqualTo("DEMOSTRACIÓN Referencia ERS 550");
        assertThat(antes.getResultados().getFirst().getPaneles()).isEqualTo(2);
        assertThat(antes.getResultados().getFirst().getPotenciaKwp()).isEqualByComparingTo("1.1");
        servicio.designarReferencia(2L, new SolicitudReferenciaPanel(true));
        var despues = simulaciones.simular(solicitud);
        assertThat(despues.getPanelReferencia()).isEqualTo("DEMOSTRACIÓN Alterno ERS 450");
        assertThat(despues.getResultados().getFirst().getPaneles()).isEqualTo(3);
        assertThat(despues.getResultados().getFirst().getPotenciaKwp()).isEqualByComparingTo("1.35");
        servicio.editar(2L, new SolicitudPanel("Panel", "Actualizado", new BigDecimal("600"), new BigDecimal("3")));
        var editado = simulaciones.simular(solicitud);
        assertThat(editado.getPanelReferencia()).isEqualTo("Panel Actualizado");
        assertThat(editado.getResultados().getFirst().getPaneles()).isEqualTo(2);
        assertThat(editado.getResultados().getFirst().getPotenciaKwp()).isEqualByComparingTo("1.2");
    }

    @Test
    void dosReferenciasConcurrentesConservanUnaYAuditanAmbosCambios() throws Exception {
        var tercero = servicio.registrar(datos("Tercero"));
        try (var ejecutor = Executors.newFixedThreadPool(2)) {
            var inicio = new CountDownLatch(1);
            var primero = ejecutor.submit(() -> ejecutar(inicio, () -> servicio.designarReferencia(2L, new SolicitudReferenciaPanel(true))));
            var segundo = ejecutor.submit(() -> ejecutar(inicio, () -> servicio.designarReferencia(tercero.id(), new SolicitudReferenciaPanel(true))));
            inicio.countDown();
            assertThat(primero.get(20, TimeUnit.SECONDS)).isTrue();
            assertThat(segundo.get(20, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(consultas.queryForObject("select count(*) from panel where es_referencia and activo", Integer.class)).isEqualTo(1);
        assertThat(consultas.queryForObject("select id from panel where es_referencia", Long.class)).isIn(2L, tercero.id());
        assertThat(consultas.queryForObject("select count(*) from bitacora_parametro where campo='es_referencia' and valor_anterior <> '(sin registro)'", Integer.class)).isEqualTo(4);
    }

    @Test
    void desactivarYElegirMismoPanelConcurrentementeNoDejaReferenciaInactiva() throws Exception {
        try (var ejecutor = Executors.newFixedThreadPool(2)) {
            var inicio = new CountDownLatch(1);
            var referencia = ejecutor.submit(() -> ejecutar(inicio, () -> servicio.designarReferencia(2L, new SolicitudReferenciaPanel(true))));
            var estado = ejecutor.submit(() -> ejecutar(inicio, () -> servicio.cambiarEstado(2L, new SolicitudEstadoPanel(false))));
            inicio.countDown();
            assertThat(List.of(referencia.get(20, TimeUnit.SECONDS), estado.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        assertThat(consultas.queryForObject("select count(*) from panel where es_referencia and activo", Integer.class)).isEqualTo(1);
        assertThat(consultas.queryForObject("select count(*) from panel where es_referencia and not activo", Integer.class)).isZero();
    }

    @Test
    void altasConcurrentesDuplicadasSoloConfirmanUnPanelYSuBitacora() throws Exception {
        try (var ejecutor = Executors.newFixedThreadPool(2)) {
            var inicio = new CountDownLatch(1);
            var primero = ejecutor.submit(() -> ejecutar(inicio, () -> servicio.registrar(datos("Duplicado concurrente"))));
            var segundo = ejecutor.submit(() -> ejecutar(inicio, () -> servicio.registrar(datos("Duplicado concurrente"))));
            inicio.countDown();
            assertThat(List.of(primero.get(20, TimeUnit.SECONDS), segundo.get(20, TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
        }
        assertThat(consultas.queryForObject("select count(*) from panel where modelo='Duplicado concurrente'", Integer.class)).isEqualTo(1);
        assertThat(consultas.queryForObject("select count(*) from bitacora_parametro where administrador_id=(select id from administrador where usuario=?)", Integer.class, USUARIO)).isEqualTo(6);
    }

    @Test
    void catalogoEsperaElMismoBloqueoQueAdministracionDeParametros() throws Exception {
        var liberacion = new CountDownLatch(1);
        var pidBloqueante = new CompletableFuture<Integer>();
        try (var ejecutor = Executors.newFixedThreadPool(2)) {
            var administradorParametros = ejecutor.submit(() -> ejecutar(new CountDownLatch(0), () ->
                    new TransactionTemplate(transacciones).execute(estado -> {
                        parametros.actualizar(new SolicitudActualizacionParametros(List.of(
                                new CambioParametro("parametro", "valor", "hsp", new BigDecimal("5")))));
                        pidBloqueante.complete(consultas.queryForObject("select pg_backend_pid()", Integer.class));
                        try {
                            if (!liberacion.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("No se liberó la transacción de prueba.");
                        } catch (InterruptedException excepcion) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(excepcion);
                        }
                        return true;
                    })));
            try {
                int pid = pidBloqueante.get(10, TimeUnit.SECONDS);
                var catalogo = ejecutor.submit(() -> ejecutar(new CountDownLatch(0), () -> servicio.designarReferencia(2L, new SolicitudReferenciaPanel(true))));
                boolean bloqueoObservado = false;
                long plazo = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (System.nanoTime() < plazo && !bloqueoObservado) {
                    bloqueoObservado = Boolean.TRUE.equals(consultas.queryForObject(
                            "select exists(select 1 from pg_stat_activity where ? = any(pg_blocking_pids(pid)))", Boolean.class, pid));
                    if (!bloqueoObservado) LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(20));
                }
                assertThat(bloqueoObservado).as("PostgreSQL debe mostrar la conexión de catálogo bloqueada por parámetros").isTrue();
                assertThat(catalogo.isDone()).isFalse();
                liberacion.countDown();
                assertThat(administradorParametros.get(10, TimeUnit.SECONDS)).isTrue();
                assertThat(catalogo.get(10, TimeUnit.SECONDS)).isTrue();
            } finally {
                liberacion.countDown();
            }
        }
        assertThat(consultas.queryForObject("select valor from parametro where clave='hsp'", BigDecimal.class)).isEqualByComparingTo("5");
        assertThat(consultas.queryForObject("select id from panel where es_referencia and activo", Long.class)).isEqualTo(2);
    }

    private boolean ejecutar(CountDownLatch inicio, Supplier<?> accion) throws InterruptedException {
        inicio.await();
        var contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(new UsernamePasswordAuthenticationToken(USUARIO, "", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        SecurityContextHolder.setContext(contexto);
        try {
            accion.get();
            return true;
        } catch (ConflictoCatalogoException excepcion) {
            return false;
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private SolicitudPanel datos(String modelo) {
        return new SolicitudPanel("Marca de prueba", modelo, new BigDecimal("600"), new BigDecimal("2.8"));
    }
}
