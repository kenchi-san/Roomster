package com.cesarhotel.roomster.mapper;

import com.cesarhotel.roomster.dtos.EmployeDto;
import com.cesarhotel.roomster.dtos.EmployeFormDto;
import com.cesarhotel.roomster.model.Employe;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.time.Duration;
import java.util.List;

@Mapper(componentModel = "spring")
public interface EmployeMapper {

    EmployeDto toDto(Employe employe);

    List<EmployeDto> toDtoList(List<Employe> employes);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "actif", ignore = true)
    @Mapping(target = "dateSortie", ignore = true)
    @Mapping(target = "dureeHebdoContrat", source = "dureeHebdoHeures", qualifiedByName = "heuresToDuration")
    Employe toEntity(EmployeFormDto dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "actif", ignore = true)
    @Mapping(target = "dureeHebdoContrat", ignore = true)
    void updateEntityFromDto(EmployeDto dto, @MappingTarget Employe employe);

    @org.mapstruct.Named("heuresToDuration")
    default Duration heuresToDuration(Double heures) {
        return Duration.ofMinutes(Math.round(heures * 60));
    }
}
