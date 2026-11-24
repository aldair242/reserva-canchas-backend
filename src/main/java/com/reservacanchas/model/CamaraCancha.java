package com.reservacanchas.model;

import com.reservacanchas.config.ClockConfig;
import com.reservacanchas.model.enums.EstadoGrabacion;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "camaras_cancha")
@DiscriminatorValue("CAMARA_CANCHA")
@Getter
@Setter
public class CamaraCancha extends Dispositivo {

    @Column(name = "url_stream")
    private String urlStream;

    @Column(length = 20)
    private String resolucion;

    @Column
    private int fps = 30;

    @OneToMany(mappedBy = "camara", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Grabacion> grabaciones = new ArrayList<>();

    public Grabacion iniciarGrabacion(Partido p) {
        Grabacion g = new Grabacion();
        g.setCamara(this);
        g.setPartido(p);
        g.setInicio(LocalDateTime.now(ClockConfig.ZONA_HORARIA));
        g.setEstado(EstadoGrabacion.GRABANDO);
        g.setUrlBase(determinarUrlBase());
        return g;
    }

    private String determinarUrlBase() {
        if (this.urlStream != null && !this.urlStream.isBlank()) {
            return this.urlStream;
        }
        String idCamara = this.id != null ? String.valueOf(this.id) : "nueva";
        return "stream://simulado/camara/" + idCamara;
    }

    @Override
    public String getTipo() {
        return "CAMARA_CANCHA";
    }
}

