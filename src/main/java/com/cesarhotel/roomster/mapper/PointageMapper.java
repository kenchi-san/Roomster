package com.cesarhotel.roomster.mapper;

import com.cesarhotel.roomster.dtos.PointageDto;
import com.cesarhotel.roomster.model.Pointage;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PointageMapper {

    @Mapping(source = "employe.id", target = "employeId")
    @Mapping(target = "employe", ignore = true)
    PointageDto toDto(Pointage pointage);
}
