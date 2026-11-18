package com.solstitium.solstitium.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.solstitium.solstitium.model.dto.ConjuntoParametros;
import com.solstitium.solstitium.model.entity.Configuracion;
import com.solstitium.solstitium.model.entity.FranjaHoraria;
import com.solstitium.solstitium.model.entity.PorcionAfectada;
import com.solstitium.solstitium.model.entity.RangoViabilidad;
import com.solstitium.solstitium.model.entity.TramoSombra;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;

/** Prueba la carga real de PostgreSQL; cada escenario se revierte al terminar. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CargaParametrosTest {

    @Autowired
    private ParametroRepository repositorio;

    @Autowired
    private JdbcTemplate consultas;

    @Autowired
    private EntityManager gestorEntidades;

    @BeforeEach
    void prepararDatos() {
        // DELETE es transaccional: los datos anteriores se recuperan con el rollback del test.
        consultas.execute("""
                delete from tramo_configuracion;
                delete from tramo_sombreado;
                delete from configuracion;
                delete from estrato;
                delete from franja_horaria;
                delete from porcion_afectada;
                delete from rango_viabilidad;
                delete from limite_parametro;
                delete from parametro;
                delete from panel;
                insert into parametro(clave, valor, unidad, descripcion) values
                    ('hsp', 4.5678, 'h', 'Horas de prueba');
                insert into estrato values (4, 'ninguno', 0), (1, 'subsidio', 50);
                insert into franja_horaria values
                    (9102, 'tarde de prueba', 2, '14:00', '18:00', 0.3),
                    (9101, 'mañana de prueba', 1, '06:00', '14:00', 0.7);
                insert into porcion_afectada values
                    (9102, 'borde de prueba', 2, 0.1), (9101, 'nada de prueba', 1, 0);
                insert into configuracion values
                    (9102, 'PRUEBA-MLPE', 'MLPE de prueba', 4600000, 0.36),
                    (9101, 'PRUEBA-CENTRAL', 'Central de prueba', 4000000, 0);
                insert into tramo_sombreado values
                    (9102, 'sombra de prueba', 2, 0, 0.9, false),
                    (9101, 'sin sombra de prueba', 1, 0.9, 1, false);
                insert into tramo_configuracion values (9101, 9101), (9102, 9101), (9102, 9102);
                insert into rango_viabilidad values
                    (9102, 'no viable de prueba', 2, 6, null),
                    (9101, 'viable de prueba', 1, 0, 6);
                insert into panel(marca, modelo, potencia_wp, area_m2, es_referencia) values
                    ('PRUEBA', 'REFERENCIA', 550, 2.6, true),
                    ('PRUEBA', 'ALTERNO', 450, 2.1, false);
                insert into limite_parametro(tabla, campo, clave, valor_minimo, valor_maximo) values
                    ('parametro', 'valor', 'hsp', 1, 10), ('panel', 'area_m2', null, 1, 5);
                """);
    }

    @Test
    void cargaTodosLosGruposConPrecisionYOrden() {
        ConjuntoParametros conjunto = repositorio.obtenerParametros();

        assertThat(conjunto.getParametros().get("hsp")).isEqualByComparingTo("4.5678");
        assertThat(conjunto.getEstratos()).extracting(estrato -> estrato.getNumero()).containsExactly(1, 4);
        assertThat(conjunto.getFranjas()).extracting(FranjaHoraria::getOrden).containsExactly(1, 2);
        assertThat(conjunto.getPorciones()).extracting(PorcionAfectada::getOrden).containsExactly(1, 2);
        assertThat(conjunto.getTramos()).extracting(TramoSombra::getOrden).containsExactly(1, 2);
        assertThat(conjunto.getConfiguraciones()).extracting(Configuracion::getId).containsExactly(9101L, 9102L);
        assertThat(conjunto.getRangos()).extracting(RangoViabilidad::getOrden).containsExactly(1, 2);
        assertThat(conjunto.getPanelReferencia().getModelo()).isEqualTo("REFERENCIA");
        assertThat(conjunto.getLimites()).hasSize(2);
    }

    @Test
    void mantieneLasConfiguracionesDisponiblesTrasDesvincularLasEntidades() {
        ConjuntoParametros conjunto = repositorio.obtenerParametros();
        gestorEntidades.clear();

        assertThat(conjunto.getTramos().get(1).getConfiguraciones())
                .extracting(Configuracion::getCodigo).containsExactly("PRUEBA-CENTRAL", "PRUEBA-MLPE");
    }

    @Test
    void unaNuevaCargaReflejaLosCambiosSinValoresEnCache() {
        assertThat(repositorio.obtenerParametros().getParametros().get("hsp")).isEqualByComparingTo("4.5678");
        consultas.update("update parametro set valor = 5.1234 where clave = 'hsp'");
        gestorEntidades.clear();

        assertThat(repositorio.obtenerParametros().getParametros().get("hsp")).isEqualByComparingTo("5.1234");
    }

    @Test
    void informaQueFaltaUnPanelDeReferenciaActivo() {
        consultas.update("update panel set es_referencia = false");

        assertThatThrownBy(repositorio::obtenerParametros)
                .hasMessageContaining("Debe existir exactamente un panel de referencia activo");
    }

    @Test
    void informaQueFaltaUnGrupoDeParametros() {
        consultas.update("delete from limite_parametro");

        assertThatThrownBy(repositorio::obtenerParametros)
                .hasMessageContaining("Faltan datos de configuración: límites de parámetros");
    }

    @Test
    void conservaTramosSinConfiguracionesParaSuPosteriorValidacion() {
        consultas.update("delete from tramo_configuracion where tramo_id = 9102");

        assertThat(repositorio.obtenerParametros().getTramos().get(1).getConfiguraciones()).isEmpty();
    }
}
