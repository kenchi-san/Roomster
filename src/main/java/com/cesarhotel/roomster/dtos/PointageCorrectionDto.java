package com.cesarhotel.roomster.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO utilisé par le manager pour corriger un pointage (oubli de badge).
 * Le commentaire est obligatoire : c'est le service qui l'impose, pas d'annotation
 * de validation ici pour rester cohérent avec editionEmploye (RequestBody + vérif manuelle).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PointageCorrectionDto {
    private LocalDateTime entree;
    private LocalDateTime sortie;
    private String commentaire;
}
