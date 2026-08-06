package com.cesarhotel.roomster.dtos;

import com.cesarhotel.roomster.model.Poste;
import com.cesarhotel.roomster.model.TypeContrat;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * DTO utilisé par le formulaire de création d'un employé.
 * dureeHebdoHeures est saisie en heures/semaine (ex: 35) puis convertie en Duration côté mapper,
 * car un champ HTML ne peut pas être lié directement à un java.time.Duration.
 */
@Data
public class EmployeFormDto {

    @NotBlank(message = "Le nom est obligatoire")
    private String nom;

    @NotBlank(message = "Le prénom est obligatoire")
    private String prenom;

    @NotBlank(message = "L'email est obligatoire")
    @Email(message = "L'email n'est pas valide")
    private String email;

    @NotNull(message = "Le poste est obligatoire")
    private Poste poste;

    @NotNull(message = "Le type de contrat est obligatoire")
    private TypeContrat typeContrat;

    @NotNull(message = "La date d'entrée est obligatoire")
    private LocalDate dateEntree;

    @NotNull(message = "La durée hebdomadaire est obligatoire")
    @DecimalMin(value = "0.0", inclusive = false, message = "La durée hebdomadaire doit être positive")
    private Double dureeHebdoHeures;
}
