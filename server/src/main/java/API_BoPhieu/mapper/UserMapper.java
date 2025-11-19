package API_BoPhieu.mapper;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import API_BoPhieu.dto.user.UserRequestDTO;
import API_BoPhieu.dto.user.UserResponseDTO;
import API_BoPhieu.entity.User;

@Mapper(componentModel = "spring")
public interface UserMapper {
    @Mapping(target = "hashPassword", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "passwordResetToken", ignore = true)
    @Mapping(target = "passwordResetTokenExpiry", ignore = true)
    @Mapping(target = "roles", ignore = true)
    User toEntity(UserRequestDTO dto);

    @Mapping(target = "unit", ignore = true)
    UserResponseDTO toResponseDTO(User user);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "hashPassword", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "passwordResetToken", ignore = true)
    @Mapping(target = "passwordResetTokenExpiry", ignore = true)
    @Mapping(target = "roles", ignore = true)
    void updateEntityFromDto(UserRequestDTO dto, @MappingTarget User entity);
}
