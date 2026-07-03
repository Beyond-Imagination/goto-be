package kr.bi.go_to.openapi;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import kr.bi.go_to.support.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class OpenApiIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void exposesOpenApiDocsWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.info.title").value("Goto API"))
                .andExpect(jsonPath("$.tags[0].name").value("A. Auth"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/login']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/refresh']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/help-requests']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/help-requests/nearby']").exists())
                .andExpect(
                        jsonPath("$.paths['/api/v1/auth/login'].post.tags[0]").value("A. Auth"))
                .andExpect(jsonPath("$.paths['/api/v1/help-requests'].post.tags[0]")
                        .value("B. Help Request"))
                .andExpect(jsonPath("$.components.schemas.LoginRequest").exists())
                .andExpect(jsonPath("$.components.schemas.LoginResponse").exists())
                .andExpect(jsonPath("$.components.schemas.RefreshRequest").exists())
                .andExpect(jsonPath("$.components.schemas.AccessTokenResponse").exists())
                .andExpect(jsonPath("$.components.schemas.CreateHelpRequestRequest")
                        .exists())
                .andExpect(jsonPath("$.components.schemas.HelpRequestResponse").exists())
                .andExpect(jsonPath("$.components.schemas.NearbyHelpRequestResponse")
                        .exists())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme")
                        .value("bearer"));
    }

    @Test
    void exposesSwaggerUiWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/swagger-ui/index.html"));
    }
}
