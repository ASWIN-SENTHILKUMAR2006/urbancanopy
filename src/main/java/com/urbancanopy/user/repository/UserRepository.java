package com.urbancanopy.user.repository;

import com.urbancanopy.user.entity.Role;
import com.urbancanopy.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/** Persistence access for {@link User}. */
public interface UserRepository extends JpaRepository<User, Long> {

    /** Emails are stored case-folded, so an exact match is a case-insensitive match. */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /** Duplicate check that ignores the record being edited. */
    boolean existsByEmailAndIdNot(String email, Long id);

    long countByEnabledTrue();

    /**
     * Counts enabled administrators. Used to guarantee the system always retains at least
     * one (BR-22) before a role change or account disable is permitted.
     */
    long countByRoleAndEnabledTrue(Role role);

    @Query("""
            SELECT u FROM User u
            WHERE (:search IS NULL
                   OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(u.email)    LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(COALESCE(u.organisation, '')) LIKE LOWER(CONCAT('%', :search, '%')))
              AND (:role    IS NULL OR u.role = :role)
              AND (:enabled IS NULL OR u.enabled = :enabled)
            """)
    Page<User> search(@Param("search") String search,
                      @Param("role") Role role,
                      @Param("enabled") Boolean enabled,
                      Pageable pageable);
}
