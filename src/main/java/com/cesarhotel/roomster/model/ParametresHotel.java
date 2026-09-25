package com.cesarhotel.roomster.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Réglages de l'hôtel, modifiables par le manager (page /parametres).
 * Une seule ligne en base, d'id 1 (voir ParametresService).
 */
@Entity
@Getter
@NoArgsConstructor
@Table(name = "parametres_hotel")
public class ParametresHotel {

    public static final Long ID_UNIQUE = 1L;

    @Id
    private Long id;

    /**
     * L'hôtel accorde-t-il des RTT ? La convention HCR n'en prévoit pas : ils n'existent que si un accord
     * d'entreprise les met en place. Non par défaut ; sur "non", le type RTT et les colonnes RTT sont masqués.
     */
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean rttActives;

    public ParametresHotel(Long id) {
        this.id = id;
    }

    public void definirRttActives(boolean rttActives) {
        this.rttActives = rttActives;
    }
}
