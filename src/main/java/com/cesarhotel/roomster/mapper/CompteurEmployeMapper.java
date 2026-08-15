package com.cesarhotel.roomster.mapper;

import com.cesarhotel.roomster.dtos.CompteurEmployeDto;
import com.cesarhotel.roomster.model.CompteurEmploye;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CompteurEmployeMapper {

    @Mapping(source = "employe.user.nom", target = "nom")
    @Mapping(source = "employe.user.prenom", target = "prenom")
    @Mapping(source = "employe.poste", target = "poste")
    CompteurEmployeDto toDto(CompteurEmploye compteurEmploye);

}
