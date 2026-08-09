package com.urbancanopy.plan.mapper;

import com.urbancanopy.calculation.model.CalculationInput;
import com.urbancanopy.common.dto.EnumOption;
import com.urbancanopy.plan.dto.PlanDetailResponse;
import com.urbancanopy.plan.dto.PlanItemResponse;
import com.urbancanopy.plan.dto.PlanSummary;
import com.urbancanopy.plan.entity.LandAreaUnit;
import com.urbancanopy.plan.entity.PlanItem;
import com.urbancanopy.plan.entity.PlanStatus;
import com.urbancanopy.plan.entity.PlantationPlan;
import com.urbancanopy.plan.entity.PlantationPurpose;
import com.urbancanopy.species.entity.TreeSpecies;
import com.urbancanopy.species.mapper.TreeSpeciesMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/**
 * Translates {@link PlantationPlan} and its items into DTOs, and into the calculation
 * engine's input type.
 *
 * <p>Pure translation. The one thing that might look like a decision — reading
 * {@code plan.isEditable()} into the response — is the entity answering a question about
 * itself; this class does not decide what "editable" means.</p>
 *
 * <p>Every method here must be called inside the caller's transaction: the plan's items and
 * their species are lazy associations, and {@code open-in-view} is disabled by design. The
 * services that call it are all {@code @Transactional}, which is what makes that safe.</p>
 */
@Component
public class PlantationPlanMapper {

    private final TreeSpeciesMapper speciesMapper;

    public PlantationPlanMapper(TreeSpeciesMapper speciesMapper) {
        this.speciesMapper = speciesMapper;
    }

    // ------------------------------------------------------------------- enum options

    public static EnumOption option(PlanStatus status) {
        return EnumOption.of(status.name(), status.getLabel());
    }

    public static EnumOption option(PlantationPurpose purpose) {
        return new EnumOption(purpose.name(), purpose.getLabel(), purpose.getDescription());
    }

    /** The unit's symbol is its description, so a form can show "Hectares (ha)". */
    public static EnumOption option(LandAreaUnit unit) {
        return new EnumOption(unit.name(), unit.getLabel(), unit.getSymbol());
    }

    // ------------------------------------------------------------------- entity → DTO

    /**
     * A list row (FR-PLAN-07).
     *
     * <p>The aggregates are passed in rather than read from {@code plan.getItems()}: they are
     * computed for the whole page in one grouped query, because touching the items collection
     * per row is precisely the N+1 the listing query exists to avoid.</p>
     *
     * @param annualCo2Kg from the plan's result snapshot, or {@code null} when it has none
     * @param impactScore likewise — a draft has no frozen score and must not display a zero
     */
    public PlanSummary toSummary(PlantationPlan plan, int speciesCount, int totalTrees,
                                 BigDecimal annualCo2Kg, BigDecimal impactScore) {
        return new PlanSummary(
                plan.getId(),
                plan.getTitle(),
                plan.getSiteName(),
                option(plan.getPurpose()),
                option(plan.getStatus()),
                plan.getLandAreaInput(),
                option(plan.getLandAreaUnit()),
                plan.getLandAreaSquareMetres(),
                plan.getTargetStartYear(),
                plan.getProjectionYears(),
                speciesCount,
                totalTrees,
                annualCo2Kg,
                impactScore,
                plan.getCreatedAt(),
                plan.getUpdatedAt());
    }

    /**
     * The full plan with its composition.
     *
     * @param ownedByCaller {@code false} when an administrator is viewing someone else's plan
     *                      (FR-PLAN-11) — the interface uses it to render read-only
     */
    public PlanDetailResponse toDetail(PlantationPlan plan, boolean ownedByCaller) {
        List<PlanItemResponse> items = plan.getItems().stream()
                // Stable order: heaviest contribution first, then alphabetical. Without an
                // explicit order the editor's rows would shuffle between saves.
                .sorted(Comparator.comparingInt((PlanItem item) -> item.getQuantity()).reversed()
                        .thenComparing(item -> item.getSpecies().getCommonName()))
                .map(this::toItemResponse)
                .toList();

        List<EnumOption> allowedStatuses = plan.getStatus().allowedTransitions().stream()
                .sorted(Comparator.comparing((PlanStatus status) -> status.name()))
                .map(PlantationPlanMapper::option)
                .toList();

        return new PlanDetailResponse(
                plan.getId(),
                plan.getTitle(),
                plan.getDescription(),
                plan.getSiteName(),
                plan.getLandAreaInput(),
                option(plan.getLandAreaUnit()),
                plan.getLandAreaSquareMetres(),
                option(plan.getPurpose()),
                plan.getTargetStartYear(),
                plan.getProjectionYears(),
                option(plan.getStatus()),
                plan.isEditable(),
                plan.isCalculable(),
                allowedStatuses,
                items.size(),
                plan.getTotalTrees(),
                items,
                plan.getOwner().getFullName(),
                ownedByCaller,
                plan.getCreatedAt(),
                plan.getUpdatedAt());
    }

    public PlanItemResponse toItemResponse(PlanItem item) {
        TreeSpecies species = item.getSpecies();
        return new PlanItemResponse(
                item.getId(),
                species.getId(),
                species.getCommonName(),
                species.getScientificName(),
                speciesMapper.resolveImageUrl(species),
                item.getQuantity(),
                species.getCo2SequestrationKgPerYear(),
                species.getOxygenProductionKgPerYear(),
                species.getTotalPollutantRemovalGramsPerYear(),
                new EnumOption(species.getGrowthRate().name(), species.getGrowthRate().getLabel(), null),
                species.getCanopyRadiusMetres(),
                species.getSpacingMetres(),
                species.isActive());
    }

    // ----------------------------------------------------------- entity → engine input

    /**
     * Builds the engine's input from a persistent plan.
     *
     * <p>Copies the species' reference values out of the entity graph, so what reaches the
     * engine cannot lazily load anything and cannot behave differently depending on whether a
     * transaction is open (FR-CALC-12).</p>
     */
    public CalculationInput toCalculationInput(PlantationPlan plan) {
        return new CalculationInput(
                plan.getLandAreaSquareMetres(),
                plan.getProjectionYears(),
                plan.getItems().stream()
                        .map(speciesMapper::toCalculationInput)
                        .toList());
    }
}
