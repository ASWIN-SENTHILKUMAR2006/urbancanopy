package com.urbancanopy.plan.entity;

import com.urbancanopy.common.domain.AuditableEntity;
import com.urbancanopy.species.entity.TreeSpecies;
import com.urbancanopy.user.entity.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A planned urban plantation: a site, a purpose, and a species composition.
 *
 * <p>This is the aggregate root of the planning model. {@link PlanItem} instances are a
 * composition — they have no meaning outside their plan and are cascaded and orphan-removed
 * accordingly. The invariant that a species appears at most once per plan (BR-08) is
 * enforced here in {@link #addSpecies}, and again by a unique constraint in the database so
 * it survives concurrent requests.</p>
 *
 * <p>Land area is stored twice on purpose: {@link #landAreaSquareMetres} is the normalised
 * figure every query and calculation uses, while {@link #landAreaInput} and
 * {@link #landAreaUnit} preserve what the planner actually typed. Without the second pair,
 * a plan entered as "1.2 hectares" would redisplay as "12000 m²" and look broken.</p>
 */
@Entity
@Table(
        name = "plantation_plans",
        indexes = {
                @Index(name = "idx_plans_user_deleted", columnList = "user_id,deleted"),
                @Index(name = "idx_plans_status", columnList = "status"),
                @Index(name = "idx_plans_purpose", columnList = "purpose")
        })
public class PlantationPlan extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false,
            foreignKey = @jakarta.persistence.ForeignKey(name = "fk_plans_user"))
    private User owner;

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "site_name", length = 180)
    private String siteName;

    /** Normalised available land, in square metres. The value all calculations use. */
    @Column(name = "land_area_sqm", nullable = false, precision = 14, scale = 2)
    private BigDecimal landAreaSquareMetres;

    /** The figure the planner entered, preserved for faithful redisplay. */
    @Column(name = "land_area_input", nullable = false, precision = 14, scale = 4)
    private BigDecimal landAreaInput;

    @Enumerated(EnumType.STRING)
    @Column(name = "land_area_unit", nullable = false, length = 20)
    private LandAreaUnit landAreaUnit = LandAreaUnit.SQUARE_METRE;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 40)
    private PlantationPurpose purpose;

    @Column(name = "target_start_year", nullable = false)
    private Integer targetStartYear;

    /** Number of years the multi-year projection covers (FR-CALC-07). */
    @Column(name = "projection_years", nullable = false)
    private Integer projectionYears;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PlanStatus status = PlanStatus.DRAFT;

    /** Soft delete (BR-21). Deleted plans vanish from listings and every aggregate. */
    @Column(name = "deleted", nullable = false)
    private boolean deleted = false;

    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<PlanItem> items = new ArrayList<>();

    protected PlantationPlan() {
        // Required by JPA.
    }

    public PlantationPlan(User owner, String title, BigDecimal landAreaInput,
                          LandAreaUnit landAreaUnit, PlantationPurpose purpose,
                          Integer targetStartYear, Integer projectionYears) {
        this.owner = owner;
        this.title = title;
        this.purpose = purpose;
        this.targetStartYear = targetStartYear;
        this.projectionYears = projectionYears;
        setLandArea(landAreaInput, landAreaUnit);
    }

    // ------------------------------------------------------------------- behaviour

    /** Sets the land area from a user-entered value and unit, normalising to m² (BR-07). */
    public void setLandArea(BigDecimal input, LandAreaUnit unit) {
        this.landAreaInput = input;
        this.landAreaUnit = unit == null ? LandAreaUnit.SQUARE_METRE : unit;
        this.landAreaSquareMetres = this.landAreaUnit.toSquareMetres(input);
    }

    /**
     * Adds a species, or increases the quantity if it is already present (BR-08).
     *
     * @return the item that now represents this species in the plan
     */
    public PlanItem addSpecies(TreeSpecies species, int quantity) {
        return findItemForSpecies(species)
                .map(existing -> {
                    existing.increaseQuantityBy(quantity);
                    return existing;
                })
                .orElseGet(() -> {
                    PlanItem item = new PlanItem(this, species, quantity);
                    items.add(item);
                    return item;
                });
    }

    public void removeItem(PlanItem item) {
        items.remove(item);
        item.detach();
    }

    public Optional<PlanItem> findItemForSpecies(TreeSpecies species) {
        return items.stream()
                .filter(item -> item.getSpecies() != null && item.getSpecies().equals(species))
                .findFirst();
    }

    public Optional<PlanItem> findItemById(Long itemId) {
        return items.stream()
                .filter(item -> itemId != null && itemId.equals(item.getId()))
                .findFirst();
    }

    /** Total number of trees across all items. */
    public int getTotalTrees() {
        return items.stream().mapToInt(PlanItem::getQuantity).sum();
    }

    public boolean isOwnedBy(Long userId) {
        return owner != null && owner.getId() != null && owner.getId().equals(userId);
    }

    /** {@code true} when the plan has enough substance to be calculated (BR-10). */
    public boolean isCalculable() {
        return !items.isEmpty()
                && landAreaSquareMetres != null
                && landAreaSquareMetres.signum() > 0;
    }

    public boolean isEditable() {
        return !deleted && status != null && status.isEditable();
    }

    public void markDeleted() {
        this.deleted = true;
    }

    // ------------------------------------------------------------------ accessors

    public User getOwner() {
        return owner;
    }

    public void setOwner(User owner) {
        this.owner = owner;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSiteName() {
        return siteName;
    }

    public void setSiteName(String siteName) {
        this.siteName = siteName;
    }

    public BigDecimal getLandAreaSquareMetres() {
        return landAreaSquareMetres;
    }

    public BigDecimal getLandAreaInput() {
        return landAreaInput;
    }

    public LandAreaUnit getLandAreaUnit() {
        return landAreaUnit;
    }

    public PlantationPurpose getPurpose() {
        return purpose;
    }

    public void setPurpose(PlantationPurpose purpose) {
        this.purpose = purpose;
    }

    public Integer getTargetStartYear() {
        return targetStartYear;
    }

    public void setTargetStartYear(Integer targetStartYear) {
        this.targetStartYear = targetStartYear;
    }

    public Integer getProjectionYears() {
        return projectionYears;
    }

    public void setProjectionYears(Integer projectionYears) {
        this.projectionYears = projectionYears;
    }

    public PlanStatus getStatus() {
        return status;
    }

    public void setStatus(PlanStatus status) {
        this.status = status;
    }

    public boolean isDeleted() {
        return deleted;
    }

    /** Unmodifiable view; mutate through {@link #addSpecies} and {@link #removeItem}. */
    public List<PlanItem> getItems() {
        return List.copyOf(items);
    }

    /** Mutable access for JPA and for the service layer's bulk operations. */
    List<PlanItem> itemsInternal() {
        return items;
    }
}
