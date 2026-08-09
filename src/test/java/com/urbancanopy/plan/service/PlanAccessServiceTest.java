package com.urbancanopy.plan.service;

import com.urbancanopy.audit.entity.AuditEventType;
import com.urbancanopy.audit.service.AuditService;
import com.urbancanopy.common.exception.ForbiddenOperationException;
import com.urbancanopy.common.exception.ResourceNotFoundException;
import com.urbancanopy.common.security.AuthenticatedUser;
import com.urbancanopy.common.security.CurrentUserProvider;
import com.urbancanopy.common.exception.UnauthenticatedException;
import com.urbancanopy.plan.entity.PlanStatus;
import com.urbancanopy.plan.entity.PlantationPlan;
import com.urbancanopy.plan.repository.PlantationPlanRepository;
import com.urbancanopy.support.EntityFixtures;
import com.urbancanopy.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests the one rule that must never be wrong: who may see and change a plan
 * (FR-PLAN-10, FR-PLAN-11, BR-06).
 *
 * <p>These are unit tests with mocked collaborators, and that is deliberate — the rule is pure
 * decision-making over an owner id and a caller, so exercising it needs no database and no HTTP.
 * Every path into a plan goes through this class, which is why it is worth testing exhaustively:
 * a missing check here is a data leak, not a defect.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PlanAccessService")
class PlanAccessServiceTest {

    private static final Long OWNER_ID = 1L;
    private static final Long STRANGER_ID = 2L;
    private static final Long ADMIN_ID = 3L;
    private static final Long PLAN_ID = 100L;

    @Mock
    private PlantationPlanRepository planRepository;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private AuditService auditService;

    private PlanAccessService accessService;
    private PlantationPlan plan;

    private static final AuthenticatedUser OWNER =
            new AuthenticatedUser(OWNER_ID, "owner@test.local", "Owner", false);
    private static final AuthenticatedUser STRANGER =
            new AuthenticatedUser(STRANGER_ID, "stranger@test.local", "Stranger", false);
    private static final AuthenticatedUser ADMIN =
            new AuthenticatedUser(ADMIN_ID, "admin@test.local", "Admin", true);

    @BeforeEach
    void setUp() {
        accessService = new PlanAccessService(planRepository, currentUserProvider, auditService);

        User owner = EntityFixtures.planner("owner@test.local");
        ReflectionTestUtils.setField(owner, "id", OWNER_ID);

        plan = EntityFixtures.plan(owner, "Avenue Drive");
        ReflectionTestUtils.setField(plan, "id", PLAN_ID);

        when(planRepository.findDetailedById(PLAN_ID)).thenReturn(Optional.of(plan));
        when(planRepository.findByIdAndDeletedFalse(PLAN_ID)).thenReturn(Optional.of(plan));
    }

    private void signedInAs(AuthenticatedUser user) {
        when(currentUserProvider.requireCurrentUser()).thenReturn(user);
        when(currentUserProvider.currentUser()).thenReturn(Optional.ofNullable(user));
    }

    @Nested
    @DisplayName("reading")
    class Reading {

        @Test
        @DisplayName("the owner may read their own plan")
        void ownerMayRead() {
            signedInAs(OWNER);

            assertThat(accessService.requireReadable(PLAN_ID, true)).isSameAs(plan);
            verify(auditService, never()).record(any(), any(), any(), any());
        }

        @Test
        @DisplayName("another planner may not read it — 403, not a pretend 404")
        void strangerMayNotRead() {
            signedInAs(STRANGER);

            assertThatThrownBy(() -> accessService.requireReadable(PLAN_ID, true))
                    .isInstanceOf(ForbiddenOperationException.class)
                    .hasMessageContaining("do not have permission");
        }

        @Test
        @DisplayName("an administrator may read any plan (FR-PLAN-11)")
        void adminMayRead() {
            signedInAs(ADMIN);

            assertThat(accessService.requireReadable(PLAN_ID, true)).isSameAs(plan);
        }

        @Test
        @DisplayName("and that administrator access is recorded, every time")
        void adminReadIsAudited() {
            signedInAs(ADMIN);

            accessService.requireReadable(PLAN_ID, true);

            verify(auditService).record(
                    eq(AuditEventType.ADMIN_VIEWED_PLAN), eq("PlantationPlan"), eq(PLAN_ID), any());
        }

        @Test
        @DisplayName("an unidentified caller is refused before ownership is even considered")
        void anonymousIsRefused() {
            when(currentUserProvider.requireCurrentUser())
                    .thenThrow(new UnauthenticatedException("Authentication is required."));

            assertThatThrownBy(() -> accessService.requireReadable(PLAN_ID, true))
                    .isInstanceOf(UnauthenticatedException.class);
        }

        @Test
        @DisplayName("a missing plan is a 404")
        void missingPlanIsNotFound() {
            signedInAs(OWNER);
            when(planRepository.findDetailedById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> accessService.requireReadable(999L, true))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("writing")
    class Writing {

        @Test
        @DisplayName("the owner may write")
        void ownerMayWrite() {
            signedInAs(OWNER);

            assertThat(accessService.requireOwned(PLAN_ID, false)).isSameAs(plan);
        }

        @Test
        @DisplayName("another planner may not write")
        void strangerMayNotWrite() {
            signedInAs(STRANGER);

            assertThatThrownBy(() -> accessService.requireOwned(PLAN_ID, false))
                    .isInstanceOf(ForbiddenOperationException.class);
        }

        /**
         * The distinction that separates support access from control: an administrator can
         * read any plan, and can change none of them.
         */
        @Test
        @DisplayName("an ADMINISTRATOR may not write to someone else's plan")
        void adminMayNotWrite() {
            signedInAs(ADMIN);

            assertThatThrownBy(() -> accessService.requireOwned(PLAN_ID, false))
                    .isInstanceOf(ForbiddenOperationException.class)
                    .hasMessageContaining("Only the planner who created this plan");
        }
    }

    @Nested
    @DisplayName("editability")
    class Editability {

        @Test
        @DisplayName("a draft is editable")
        void draftIsEditable() {
            signedInAs(OWNER);
            plan.setStatus(PlanStatus.DRAFT);

            assertThat(accessService.requireEditable(PLAN_ID, false)).isSameAs(plan);
        }

        @Test
        @DisplayName("a completed plan is still editable — completing is not freezing the plan")
        void completedIsEditable() {
            signedInAs(OWNER);
            plan.setStatus(PlanStatus.COMPLETED);

            assertThat(accessService.requireEditable(PLAN_ID, false)).isSameAs(plan);
        }

        @Test
        @DisplayName("an archived plan is refused, with the way to reopen it")
        void archivedIsRefused() {
            signedInAs(OWNER);
            plan.setStatus(PlanStatus.ARCHIVED);

            assertThatThrownBy(() -> accessService.requireEditable(PLAN_ID, false))
                    .isInstanceOf(ForbiddenOperationException.class)
                    .hasMessageContaining("Move it back to Completed");
        }

        @Test
        @DisplayName("a soft-deleted plan is not editable either")
        void deletedIsRefused() {
            signedInAs(OWNER);
            plan.markDeleted();

            assertThatThrownBy(() -> accessService.requireEditable(PLAN_ID, false))
                    .isInstanceOf(ForbiddenOperationException.class);
        }
    }
}
