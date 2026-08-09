package com.urbancanopy.plan.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The plan lifecycle state machine (FR-PLAN-06).
 *
 * <p>Worth testing in isolation precisely because it is declared on the enum rather than
 * scattered through service {@code if} statements: the whole machine can be asserted in one
 * place, including the transitions that must <em>not</em> exist.</p>
 */
@DisplayName("PlanStatus")
class PlanStatusTest {

    @Test
    @DisplayName("a draft may be completed or archived, but cannot stay a draft")
    void draftTransitions() {
        assertThat(PlanStatus.DRAFT.allowedTransitions())
                .containsExactlyInAnyOrder(PlanStatus.COMPLETED, PlanStatus.ARCHIVED);
        assertThat(PlanStatus.DRAFT.canTransitionTo(PlanStatus.DRAFT)).isFalse();
    }

    @Test
    @DisplayName("a completed plan may be reopened as a draft or archived")
    void completedTransitions() {
        assertThat(PlanStatus.COMPLETED.allowedTransitions())
                .containsExactlyInAnyOrder(PlanStatus.DRAFT, PlanStatus.ARCHIVED);
    }

    /**
     * The asymmetry is intentional. Archiving is the end of a plan's working life; bringing it
     * straight back to a draft would let an archived plan be edited without the deliberate step
     * of restoring it first.
     */
    @Test
    @DisplayName("an archived plan may only be restored to completed, never straight to draft")
    void archivedTransitions() {
        assertThat(PlanStatus.ARCHIVED.allowedTransitions())
                .containsExactly(PlanStatus.COMPLETED);
        assertThat(PlanStatus.ARCHIVED.canTransitionTo(PlanStatus.DRAFT)).isFalse();
    }

    @Test
    @DisplayName("only an archived plan is closed to editing")
    void editability() {
        assertThat(PlanStatus.DRAFT.isEditable()).isTrue();
        assertThat(PlanStatus.COMPLETED.isEditable()).isTrue();
        assertThat(PlanStatus.ARCHIVED.isEditable()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(PlanStatus.class)
    @DisplayName("no status permits a transition to itself")
    void noSelfTransitions(PlanStatus status) {
        assertThat(status.canTransitionTo(status)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(PlanStatus.class)
    @DisplayName("a null target is never permitted")
    void nullIsNeverPermitted(PlanStatus status) {
        assertThat(status.canTransitionTo(null)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(PlanStatus.class)
    @DisplayName("every status has a human label")
    void everyStatusIsLabelled(PlanStatus status) {
        assertThat(status.getLabel()).isNotBlank();
    }
}
