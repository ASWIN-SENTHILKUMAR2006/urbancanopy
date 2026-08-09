package com.urbancanopy.coefficient.mapper;

import com.urbancanopy.coefficient.dto.CoefficientResponse;
import com.urbancanopy.coefficient.entity.EnvironmentalCoefficient;
import com.urbancanopy.common.dto.EnumOption;
import org.springframework.stereotype.Component;

/**
 * Translates {@link EnvironmentalCoefficient} into its response DTO.
 *
 * <p>Pure translation. The two derived fields it carries across — {@code permittedRange} and
 * {@code modified} — are computed by the entity itself, not decided here.</p>
 */
@Component
public class CoefficientMapper {

    public CoefficientResponse toResponse(EnvironmentalCoefficient coefficient) {
        return new CoefficientResponse(
                coefficient.getId(),
                coefficient.getCoefficientKey(),
                coefficient.getLabel(),
                coefficient.getValue(),
                coefficient.getDefaultValue(),
                coefficient.getMinValue(),
                coefficient.getMaxValue(),
                coefficient.describeRange(),
                coefficient.getUnit(),
                new EnumOption(
                        coefficient.getCategory().name(),
                        coefficient.getCategory().getLabel(),
                        coefficient.getCategory().getDescription()),
                coefficient.getDescription(),
                coefficient.getSource(),
                coefficient.isModified(),
                coefficient.getUpdatedAt(),
                coefficient.getUpdatedBy());
    }
}
