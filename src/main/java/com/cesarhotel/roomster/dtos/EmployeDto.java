package com.cesarhotel.roomster.dtos;

import com.cesarhotel.roomster.model.Poste;
import com.cesarhotel.roomster.model.TypeContrat;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
@AllArgsConstructor
@NoArgsConstructor
@Data
public class EmployeDto {
    private Long id;

    private String nom;

    private String prenom;


    private String email;

//    @Enumerated(EnumType.STRING)
    private Poste poste;

//    @Enumerated(EnumType.STRING)
    private TypeContrat typeContrat;

    private LocalDate dateEntree;

    private LocalDate dateSortie;

    private Boolean actif;

}
