package com.cesarhotel.roomster.controller;

import com.cesarhotel.roomster.dtos.PointageDto;
import com.cesarhotel.roomster.mapper.PointageMapper;
import com.cesarhotel.roomster.service.PointageService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class PointageController {

    private final PointageService pointageService;
    private final PointageMapper pointageMapper;

    public PointageController(PointageService pointageService, PointageMapper pointageMapper) {
        this.pointageService = pointageService;
        this.pointageMapper = pointageMapper;
    }

    @GetMapping("/pointages")
    public String listePointages(@RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("employes", pointageService.getEmployesActifs());
        model.addAttribute("pointagesOuverts", pointageService.getPointagesOuverts());
        model.addAttribute("historique", pointageService.getHistorique(page));
        return "pointage/liste";
    }

    @PostMapping("/pointer-employe/{employeId}")
    public ResponseEntity<PointageDto> pointer(@PathVariable Long employeId) {
        return ResponseEntity.of(pointageService.pointer(employeId).map(pointageMapper::toDto));
    }
}
