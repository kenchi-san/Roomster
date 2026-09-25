package com.cesarhotel.roomster.service;

import com.cesarhotel.roomster.model.ParametresHotel;
import com.cesarhotel.roomster.repository.ParametresHotelRepository;
import org.springframework.stereotype.Service;

/**
 * Réglages de l'hôtel. La ligne est créée avec les valeurs par défaut la première fois qu'on la lit.
 */
@Service
public class ParametresService {

    private final ParametresHotelRepository parametresHotelRepository;

    public ParametresService(ParametresHotelRepository parametresHotelRepository) {
        this.parametresHotelRepository = parametresHotelRepository;
    }

    /** L'hôtel accorde-t-il des RTT ? Non par défaut (convention HCR). */
    public boolean rttActives() {
        return getParametres().isRttActives();
    }

    public void definirRttActives(boolean rttActives) {
        ParametresHotel parametres = getParametres();
        parametres.definirRttActives(rttActives);
        parametresHotelRepository.save(parametres);
    }

    private ParametresHotel getParametres() {
        return parametresHotelRepository.findById(ParametresHotel.ID_UNIQUE)
                .orElseGet(() -> parametresHotelRepository.save(new ParametresHotel(ParametresHotel.ID_UNIQUE)));
    }
}
