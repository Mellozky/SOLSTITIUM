package com.solstitium.solstitium.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.solstitium.solstitium.model.entity.Administrador;
import com.solstitium.solstitium.model.entity.CambioBitacora;
import com.solstitium.solstitium.model.entity.Configuracion;
import com.solstitium.solstitium.model.entity.Estrato;
import com.solstitium.solstitium.model.entity.Panel;
import com.solstitium.solstitium.model.entity.TipoAjuste;
import com.solstitium.solstitium.model.entity.TramoSombra;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

/**
 * Comprueba que las entidades y los repositorios funcionan contra el esquema real de PostgreSQL.
 * Cada prueba corre en una transacción que se revierte al terminar, así que no deja datos.
 * Usa identificadores y nombres propios de prueba para no chocar con los datos iniciales.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PersistenciaTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PanelRepository panelRepository;

    @Autowired
    private AdministradorRepository administradorRepository;

    @Autowired
    private BitacoraRepository bitacoraRepository;

    @Test
    void estratoGuardaYLeeElTipoEnumeradoDePostgres() {
        Estrato existente = entityManager.find(Estrato.class, 1);
        Estrato estrato = existente != null ? existente : new Estrato();
        estrato.setNumero(1);
        estrato.setTipoAjuste(TipoAjuste.SUBSIDIO);
        estrato.setPorcentajeAjuste(new BigDecimal("50.00"));
        if (existente == null) {
            entityManager.persist(estrato);
        }
        sincronizar();

        Estrato leido = entityManager.find(Estrato.class, 1);

        assertThat(leido.getTipoAjuste()).isEqualTo(TipoAjuste.SUBSIDIO);
        assertThat(leido.getPorcentajeAjuste()).isEqualByComparingTo("50");
        Object enBase = entityManager
                .createNativeQuery("select tipo_ajuste::text from estrato where numero = 1")
                .getSingleResult();
        assertThat(enBase).isEqualTo("subsidio");
    }

    @Test
    void panelRecibeIdYSeEncuentraComoReferencia() {
        boolean yaHayReferencia = panelRepository.buscarReferencia().isPresent();
        Panel panel = nuevoPanel("MARCA-PRUEBA", "MODELO-PRUEBA", !yaHayReferencia);

        Panel guardado = panelRepository.guardar(panel);
        sincronizar();

        assertThat(guardado.getId()).isNotNull();
        assertThat(panelRepository.buscarTodos())
                .extracting(Panel::getModelo)
                .contains("MODELO-PRUEBA");
        assertThat(panelRepository.buscarReferencia())
                .get()
                .matches(Panel::isEsReferencia)
                .matches(Panel::isActivo);
    }

    @Test
    void tramoSombraCargaSusConfiguraciones() {
        Configuracion central = nuevaConfiguracion(9001L, "PRUEBA-CENTRAL", "0.000");
        Configuracion mlpe = nuevaConfiguracion(9002L, "PRUEBA-MLPE", "0.360");
        entityManager.persist(central);
        entityManager.persist(mlpe);

        TramoSombra tramo = new TramoSombra();
        tramo.setId(9001L);
        tramo.setNombre("tramo de prueba");
        tramo.setOrden(99);
        tramo.setFactorMinimo(new BigDecimal("0.550"));
        tramo.setFactorMaximo(new BigDecimal("0.750"));
        tramo.setMuestraAdvertencia(true);
        tramo.getConfiguraciones().addAll(List.of(central, mlpe));
        entityManager.persist(tramo);
        sincronizar();

        TramoSombra leido = entityManager.find(TramoSombra.class, 9001L);

        assertThat(leido.isMuestraAdvertencia()).isTrue();
        assertThat(leido.getConfiguraciones())
                .extracting(Configuracion::getCodigo)
                .containsExactly("PRUEBA-CENTRAL", "PRUEBA-MLPE");
    }

    @Test
    void bitacoraListaLosCambiosConSuAdministrador() {
        Administrador administrador = new Administrador();
        administrador.setUsuario("admin_prueba");
        administrador.setContrasenaHash("$2a$10$hashDePruebaQueNoEsUnaContrasenaReal");
        administrador.setNombre("Administrador de prueba");
        administrador.setActivo(true);
        administrador.setFechaCreacion(LocalDateTime.now());
        entityManager.persist(administrador);

        CambioBitacora cambio = new CambioBitacora();
        cambio.setAdministrador(administrador);
        cambio.setFecha(LocalDateTime.now());
        cambio.setTablaAfectada("parametro");
        cambio.setRegistroId("registro_prueba");
        cambio.setCampo("valor");
        cambio.setValorAnterior("5");
        cambio.setValorNuevo("6");
        bitacoraRepository.save(cambio);
        sincronizar();

        assertThat(administradorRepository.buscarPorUsuario("admin_prueba")).isPresent();
        assertThat(administradorRepository.buscarPorUsuario("no_existe")).isEmpty();
        assertThat(bitacoraRepository.listarRecientes())
                .filteredOn(c -> c.getRegistroId().equals("registro_prueba"))
                .singleElement()
                .satisfies(c -> {
                    assertThat(c.getValorAnterior()).isEqualTo("5");
                    assertThat(c.getValorNuevo()).isEqualTo("6");
                    assertThat(c.getAdministrador().getUsuario()).isEqualTo("admin_prueba");
                });
    }

    /** Envía los cambios a la base y vacía la caché, para que la lectura siguiente salga de PostgreSQL. */
    private void sincronizar() {
        entityManager.flush();
        entityManager.clear();
    }

    private static Panel nuevoPanel(String marca, String modelo, boolean esReferencia) {
        Panel panel = new Panel();
        panel.setMarca(marca);
        panel.setModelo(modelo);
        panel.setPotenciaWp(new BigDecimal("550.00"));
        panel.setAreaM2(new BigDecimal("2.600"));
        panel.setEsReferencia(esReferencia);
        panel.setActivo(true);
        return panel;
    }

    private static Configuracion nuevaConfiguracion(Long id, String codigo, String coeficiente) {
        Configuracion configuracion = new Configuracion();
        configuracion.setId(id);
        configuracion.setCodigo(codigo);
        configuracion.setNombre("Configuración " + codigo);
        configuracion.setCostoKwp(new BigDecimal("4000000.00"));
        configuracion.setCoeficienteRecuperacion(new BigDecimal(coeficiente));
        return configuracion;
    }
}
