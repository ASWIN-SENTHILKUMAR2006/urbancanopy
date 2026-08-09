package com.urbancanopy.plan.entity;

import com.urbancanopy.common.domain.AuditableEntity;
import com.urbancanopy.species.entity.TreeSpecies;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * One species and its quantity within a plantation plan.
 *
 * <p>Part of the {@link PlantationPlan} composition: it is created and destroyed with its
 * plan and is never addressed independently of it. The unique constraint on
 * {@code (plan_id, species_id)} is the database's enforcement of BR-08 — the service
 * increments an existing item rather than inserting a duplicate, and the constraint
 * guarantees the rule holds even if two requests race.</p>
 *
 * <p>The reference to {@link TreeSpecies} is a live association, not a copy of its values.
 * That is deliberate: correcting a species' sequestration rate should correct every plan
 * that uses it. Reports that must not move are pinned instead by a
 * {@link PlanResultSnapshot} (FR-CALC-14).</p>
 */
@Entity
@Table(
        name = "plan_items",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_plan_item_plan_species", columnNames = {"plan_id", "species_id"}),
        indexes = @Index(name = "idx_plan_items_species", columnList = "species_id"))
public class PlanItem extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_plan_items_plan"))
    private PlantationPlan plan;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "species_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_plan_items_species"))
    private TreeSpecies species;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    protected PlanItem() {
        // Required by JPA.
    }

    PlanItem(PlantationPlan plan, TreeSpecies species, int quantity) {
        this.plan = plan;
        this.species = species;
        this.quantity = quantity;
    }

    /** Increases the quantity — used when a species already in the plan is added again. */
    void increaseQuantityBy(int additional) {
        this.quantity += additional;
    }

    /** Clears the parent reference so orphan removal can delete this row. */
    void detach() {
        this.plan = null;
    }

    public PlantationPlan getPlan() {
        return plan;
    }

    public TreeSpecies getSpecies() {
        return species;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }
}
