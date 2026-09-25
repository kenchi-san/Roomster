package com.cesarhotel.roomster.controller;

import com.cesarhotel.roomster.service.TableauDeBordService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class TableauDeBordController {

    private final TableauDeBordService tableauDeBordService;

    public TableauDeBordController(TableauDeBordService tableauDeBordService) {
        this.tableauDeBordService = tableauDeBordService;
    }

    @GetMapping("/tableau-de-bord")
    public String tableauDeBord(Model model) {
        model.addAttribute("tableau", tableauDeBordService.getTableauDeBord());
        return "tableau-de-bord";
    }
}
