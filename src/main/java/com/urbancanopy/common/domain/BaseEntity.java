package com.urbancanopy.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import org.hibernate.proxy.HibernateProxy;

import java.util.Objects;

/**
 * Surrogate identity shared by every entity, together with the one correct
 * {@code equals}/{@code hashCode} implementation for a JPA entity.
 *
 * <p>Two details here are easy to get wrong and expensive to get wrong:</p>
 * <ul>
 *   <li><strong>{@code hashCode} is constant.</strong> An entity's hash must not change
 *       when Hibernate assigns its id on flush. If it did, an entity added to a
 *       {@code HashSet} before flushing could never be found again afterwards.</li>
 *   <li><strong>Proxies are unwrapped before comparison.</strong> A lazily loaded
 *       association is a subclass proxy, so a naive {@code getClass()} comparison reports
 *       that an entity is not equal to itself.</li>
 * </ul>
 *
 * <p>Entities with a {@code null} id are equal only to themselves — two unsaved instances
 * are different objects until the database says otherwise.</p>
 */
@MappedSuperclass
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    public Long getId() {
        return id;
    }

    protected void setId(Long id) {
        this.id = id;
    }

    /** {@code true} before the entity has been persisted. */
    public boolean isNew() {
        return id == null;
    }

    @Override
    public final boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null) {
            return false;
        }
        Class<?> thisType = effectiveClass(this);
        Class<?> otherType = effectiveClass(other);
        if (!thisType.equals(otherType)) {
            return false;
        }
        Long thisId = this.getId();
        return thisId != null && thisId.equals(((BaseEntity) other).getId());
    }

    @Override
    public final int hashCode() {
        return effectiveClass(this).hashCode();
    }

    @Override
    public String toString() {
        return effectiveClass(this).getSimpleName() + "{id=" + id + "}";
    }

    /** Resolves the real entity class, seeing through a Hibernate lazy-loading proxy. */
    private static Class<?> effectiveClass(Object candidate) {
        return candidate instanceof HibernateProxy proxy
                ? proxy.getHibernateLazyInitializer().getPersistentClass()
                : Objects.requireNonNull(candidate).getClass();
    }
}
