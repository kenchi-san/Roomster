package com.cesarhotel.roomster.dtos;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChangementMotDePasseDto {

    @NotBlank(message = "Le mot de passe actuel est obligatoire")
    private String ancienMotDePasse;

    @NotBlank(message = "Le nouveau mot de passe est obligatoire")
    private String nouveauMotDePasse;

    @NotBlank(message = "La confirmation est obligatoire")
    private String confirmationMotDePasse;
}
