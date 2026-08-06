package com.cesarhotel.roomster.dtos;

import com.cesarhotel.roomster.model.Employe;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class PointageDto {
    private Long id;
    private Long employeId;
    private Employe employe;
    private LocalDateTime entree;
    private LocalDateTime sortie;
    private String commentaire;
}
