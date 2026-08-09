package com.urbancanopy.coefficient.repository;

import com.urbancanopy.coefficient.entity.CoefficientCategory;
import com.urbancanopy.coefficient.entity.EnvironmentalCoefficient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Persistence access for {@link EnvironmentalCoefficient}. */
public interface EnvironmentalCoefficientRepository
        extends JpaRepository<EnvironmentalCoefficient, Long> {

    Optional<EnvironmentalCoefficient> findByCoefficientKey(String coefficientKey);

    List<EnvironmentalCoefficient> findByCategoryOrderByCoefficientKeyAsc(CoefficientCategory category);

    List<EnvironmentalCoefficient> findAllByOrderByCategoryAscCoefficientKeyAsc();

    boolean existsByCoefficientKey(String coefficientKey);
}
