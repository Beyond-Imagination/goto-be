package kr.bi.go_to.terms;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import kr.bi.go_to.repository.TermRepository;
import kr.bi.go_to.support.OAuthIdentityTestConfiguration;
import kr.bi.go_to.support.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
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
@Import({TestcontainersConfiguration.class, OAuthIdentityTestConfiguration.class})
class TermsControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TermRepository termRepository;

    @Test
    @DisplayName("활성화된 전체 약관 목록을 조회한다")
    void getTermsList() throws Exception {
        mockMvc.perform(get("/api/v1/terms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.terms").isArray())
                .andExpect(jsonPath("$.terms.length()").value(5))
                .andExpect(jsonPath("$.terms[0].id").value("age"))
                .andExpect(jsonPath("$.terms[0].bit").value(1))
                .andExpect(jsonPath("$.terms[0].title").value("만 14세 이상 확인"))
                .andExpect(jsonPath("$.terms[0].required").value(true))
                .andExpect(jsonPath("$.terms[0].version").value("1.0.0"))
                .andExpect(jsonPath("$.terms[0].effectiveDate").value("2026-08-01"))
                .andExpect(jsonPath("$.terms[0].sections").isArray())
                .andExpect(jsonPath("$.terms[0].sections.length()").value(2))
                .andExpect(jsonPath("$.terms[0].createdAt").isNotEmpty())
                .andExpect(jsonPath("$.terms[0].updatedAt").isNotEmpty())
                .andExpect(jsonPath("$.terms[1].id").value("terms"))
                .andExpect(jsonPath("$.terms[1].bit").value(2))
                .andExpect(jsonPath("$.terms[1].title").value("서비스 이용약관"))
                .andExpect(jsonPath("$.terms[1].required").value(true))
                .andExpect(jsonPath("$.terms[2].id").value("privacy"))
                .andExpect(jsonPath("$.terms[2].bit").value(4))
                .andExpect(jsonPath("$.terms[3].id").value("location"))
                .andExpect(jsonPath("$.terms[3].bit").value(8))
                .andExpect(jsonPath("$.terms[4].id").value("marketing"))
                .andExpect(jsonPath("$.terms[4].bit").value(16))
                .andExpect(jsonPath("$.terms[4].required").value(false));
    }

    @Test
    @DisplayName("단일 약관 상세 정보를 조회한다")
    void getSingleTerm() throws Exception {
        mockMvc.perform(get("/api/v1/terms/terms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("terms"))
                .andExpect(jsonPath("$.bit").value(2))
                .andExpect(jsonPath("$.title").value("서비스 이용약관"))
                .andExpect(jsonPath("$.required").value(true))
                .andExpect(jsonPath("$.version").value("1.0.0"))
                .andExpect(jsonPath("$.effectiveDate").value("2026-08-01"))
                .andExpect(jsonPath("$.sections").isArray())
                .andExpect(jsonPath("$.sections[0].title").value("제 1 조 (목적)"))
                .andExpect(jsonPath("$.sections[1].title").value("제 2 조 (용어의 정의)"))
                .andExpect(jsonPath("$.sections[1].items").isArray())
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());
    }

    @Test
    @DisplayName("존재하지 않는 약관 ID 조회 시 404를 반환한다")
    void getNonExistentTermReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/terms/non_existing_term"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("TERM_NOT_FOUND"))
                .andExpect(jsonPath("$.errorMessage").value("약관을 찾을 수 없습니다."));
    }

    @Test
    @DisplayName("특정 약관의 개정 이력 목록을 조회한다")
    void getTermHistories() throws Exception {
        mockMvc.perform(get("/api/v1/terms/terms/histories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].version").value("1.0.0"))
                .andExpect(jsonPath("$[0].effectiveDate").value("2026-08-01"))
                .andExpect(jsonPath("$[0].changeLog").value("최초 제정"))
                .andExpect(jsonPath("$[0].createdBy").value("SYSTEM"))
                .andExpect(jsonPath("$[0].createdAt").isNotEmpty());
    }
}
