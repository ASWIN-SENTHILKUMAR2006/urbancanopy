package com.urbancanopy.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.urbancanopy.common.dto.EnumOption;
import com.urbancanopy.common.dto.PageResponse;
import com.urbancanopy.plan.controller.PlantationPlanController;
import com.urbancanopy.plan.dto.PlanDetailResponse;
import com.urbancanopy.plan.dto.PlantationPlanRequest;
import com.urbancanopy.plan.entity.LandAreaUnit;
import com.urbancanopy.plan.entity.PlantationPurpose;
import com.urbancanopy.plan.service.PlanEstimateService;
import com.urbancanopy.plan.service.PlantationPlanService;
import com.urbancanopy.user.controller.AdminUserController;
import com.urbancanopy.user.service.LoginAttemptHandler;
import com.urbancanopy.user.service.RegistrationService;
import com.urbancanopy.user.service.UrbanCanopyUserDetailsService;
import com.urbancanopy.user.service.UserService;
import com.urbancanopy.config.ApplicationProperties;
import com.urbancanopy.config.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The web-layer security boundary (FR-AUTH-12, FR-ADM-08, FR-SYS-03).
 *
 * <p>A slice test with the real {@link SecurityConfig} and mocked services. The point is not
 * what the services do — that is tested elsewhere — but what the boundary permits: who reaches
 * an endpoint, what happens without a CSRF token, and whether a refusal comes back as the JSON
 * envelope the client can parse rather than a redirect to a login page it cannot.</p>
 */
@WebMvcTest(controllers = {PlantationPlanController.class, AdminUserController.class})
@Import(SecurityConfig.class)
// WebMvcConfig is a WebMvcConfigurer, so the slice picks it up and it needs the app.* binding;
// the test profile supplies the values.
@EnableConfigurationProperties(ApplicationProperties.class)
@ActiveProfiles("test")
@DisplayName("Web security boundary")
class SecurityBoundaryTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PlantationPlanService planService;

    @MockitoBean
    private PlanEstimateService estimateService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private RegistrationService registrationService;

    // SecurityConfig wires these; the slice must supply them.
    @MockitoBean
    private LoginAttemptHandler loginAttemptHandler;

    @MockitoBean
    private UrbanCanopyUserDetailsService userDetailsService;

    private PlantationPlanRequest validRequest() {
        return new PlantationPlanRequest(
                "Avenue Drive", null, null, new BigDecimal("12000"),
                LandAreaUnit.SQUARE_METRE, PlantationPurpose.AIR_QUALITY_IMPROVEMENT, 2026, 10);
    }

    /** What the service would return — the controller reads its title for the confirmation. */
    private PlanDetailResponse createdPlan() {
        return new PlanDetailResponse(
                1L, "Avenue Drive", null, null,
                new BigDecimal("12000"), EnumOption.of("SQUARE_METRE", "Square metres"),
                new BigDecimal("12000.00"),
                EnumOption.of("AIR_QUALITY_IMPROVEMENT", "Air quality improvement"),
                2026, 10, EnumOption.of("DRAFT", "Draft"),
                true, false, List.of(), 0, 0, List.of(),
                "Test Planner", true, Instant.now(), Instant.now());
    }

    @Nested
    @DisplayName("authentication")
    class Authentication {

        @Test
        @WithAnonymousUser
        @DisplayName("refuses an anonymous caller with 401 and a parseable body, not a redirect")
        void anonymousIsRefused() throws Exception {
            mockMvc.perform(get("/api/v1/plantation-plans"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"))
                    .andExpect(jsonPath("$.message").exists());
        }

        @Test
        @WithMockUser(roles = "USER")
        @DisplayName("admits a signed-in planner")
        void plannerIsAdmitted() throws Exception {
            when(planService.searchOwn(any(), any(), any(), any()))
                    .thenReturn(new PageResponse<>(List.of(), 0, 10, 0, 0, true, true));

            mockMvc.perform(get("/api/v1/plantation-plans"))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("authorisation")
    class Authorisation {

        @Test
        @WithMockUser(roles = "USER")
        @DisplayName("refuses a planner reaching an admin endpoint with 403 (FR-ADM-08)")
        void plannerCannotReachAdmin() throws Exception {
            mockMvc.perform(get("/api/v1/admin/users"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("admits an administrator to the admin endpoint")
        void adminMayReachAdmin() throws Exception {
            when(userService.search(any(), any(), any(), any()))
                    .thenReturn(new PageResponse<>(List.of(), 0, 10, 0, 0, true, true));

            mockMvc.perform(get("/api/v1/admin/users"))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("CSRF (FR-AUTH-12)")
    class Csrf {

        @Test
        @WithMockUser(roles = "USER")
        @DisplayName("rejects a state-changing request with no token, and says why")
        void writeWithoutTokenIsRejected() throws Exception {
            mockMvc.perform(post("/api/v1/plantation-plans")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest())))
                    .andExpect(status().isForbidden())
                    // Not "you lack permission": the caller has the rights, their token is stale.
                    .andExpect(jsonPath("$.errorCode").value("CSRF_TOKEN_INVALID"));
        }

        @Test
        @WithMockUser(roles = "USER")
        @DisplayName("accepts the same request once the token is present")
        void writeWithTokenIsAccepted() throws Exception {
            when(planService.create(any())).thenReturn(createdPlan());

            mockMvc.perform(post("/api/v1/plantation-plans")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest())))
                    .andExpect(status().isCreated());
        }

        @Test
        @WithMockUser(roles = "USER")
        @DisplayName("does not demand a token for a read")
        void readsNeedNoToken() throws Exception {
            when(planService.searchOwn(any(), any(), any(), any()))
                    .thenReturn(new PageResponse<>(List.of(), 0, 10, 0, 0, true, true));

            mockMvc.perform(get("/api/v1/plantation-plans"))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("validation")
    class Validation {

        @Test
        @WithMockUser(roles = "USER")
        @DisplayName("returns 400 with per-field messages a form can attach")
        void rejectsInvalidBody() throws Exception {
            String invalid = """
                    {"title":"","landAreaInput":-5,"landAreaUnit":"SQUARE_METRE",
                     "purpose":"CARBON_OFFSET","targetStartYear":2026,"projectionYears":10}
                    """;

            mockMvc.perform(post("/api/v1/plantation-plans")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalid))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.fieldErrors[*].field").value(
                            org.hamcrest.Matchers.hasItems("title", "landAreaInput")));
        }

        @Test
        @WithMockUser(roles = "USER")
        @DisplayName("returns 400 for malformed JSON without leaking the parser's complaint")
        void rejectsMalformedJson() throws Exception {
            mockMvc.perform(post("/api/v1/plantation-plans")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{not json"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("MALFORMED_REQUEST"));
        }
    }
}
