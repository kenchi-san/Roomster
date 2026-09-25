package com.cesarhotel.roomster.controller;

import com.cesarhotel.roomster.service.ExportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/**
 * Exports pour le comptable (fichiers CSV).
 * mois : valeur d'un champ HTML type="month", ex : "2026-08". Sans paramètre : le mois précédent.
 */
@Controller
public class ExportController {

    private final ExportService exportService;

    public ExportController(ExportService exportService) {
        this.exportService = exportService;
    }

    @GetMapping("/exports")
    public String page(Model model) {
        model.addAttribute("moisParDefaut", LocalDate.now().minusMonths(1).toString().substring(0, 7)); // ex : "2026-08"
        return "exports";
    }

    @GetMapping("/exports/recap-paie")
    public ResponseEntity<byte[]> recapPaie(@RequestParam(required = false) String mois) {
        LocalDate premierJour = premierJour(mois);
        return fichier(exportService.exporterRecapPaie(premierJour), "recap-paie", premierJour);
    }

    @GetMapping("/exports/pointages")
    public ResponseEntity<byte[]> pointages(@RequestParam(required = false) String mois) {
        LocalDate premierJour = premierJour(mois);
        return fichier(exportService.exporterPointages(premierJour), "pointages", premierJour);
    }

    @GetMapping("/exports/absences")
    public ResponseEntity<byte[]> absences(@RequestParam(required = false) String mois) {
        LocalDate premierJour = premierJour(mois);
        return fichier(exportService.exporterAbsences(premierJour), "absences", premierJour);
    }

    @GetMapping("/exports/compteurs")
    public ResponseEntity<byte[]> compteurs(@RequestParam(required = false) String mois) {
        LocalDate premierJour = premierJour(mois);
        return fichier(exportService.exporterCompteurs(premierJour), "compteurs", premierJour);
    }

    /** "2026-08" → 01/08/2026. Sans valeur : le 1er du mois précédent. */
    private LocalDate premierJour(String mois) {
        if (mois == null || mois.isBlank()) {
            return LocalDate.now().minusMonths(1).withDayOfMonth(1);
        }
        return LocalDate.parse(mois + "-01");
    }

    /** Réponse qui fait télécharger le fichier, ex : "recap-paie-2026-08.csv". */
    private ResponseEntity<byte[]> fichier(String contenu, String nom, LocalDate premierJour) {
        String nomDuFichier = nom + "-" + premierJour.toString().substring(0, 7) + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nomDuFichier + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(contenu.getBytes(StandardCharsets.UTF_8));
    }
}
