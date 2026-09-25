package com.cesarhotel.roomster.model;

public enum StatutDemande {
    SOUMISE("En attente"),
    VALIDEE("Validée"),
    REFUSEE("Refusée"),
    ANNULEE("Annulée");

    private final String libelle;

    StatutDemande(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }
}
