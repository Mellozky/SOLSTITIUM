package com.solstitium.solstitium.service;

import com.solstitium.solstitium.model.dto.RegistroBitacora;
import com.solstitium.solstitium.repository.BitacoraRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BitacoraService {
    private final BitacoraRepository repositorio;
    private final AdministradorSesion sesion;

    @Transactional(readOnly = true)
    public List<RegistroBitacora> consultar() {
        sesion.obtener();
        return repositorio.listarRecientes().stream().map(c -> new RegistroBitacora(c.getId(),
                c.getAdministrador().getUsuario(), c.getFecha(), c.getTablaAfectada(), c.getRegistroId(),
                c.getCampo(), c.getValorAnterior(), c.getValorNuevo())).toList();
    }
}
