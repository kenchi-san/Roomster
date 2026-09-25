package com.cesarhotel.roomster.model;

public enum TypeAbsence {
    CONGE_PAYE("Congé payé"),
    RTT("RTT"),
    MALADIE("Maladie"),
    SANS_SOLDE("Sans solde"),
    RECUP_JOUR_FERIE("Récup. jour férié");

    private final String libelle;

    TypeAbsence(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }
}
