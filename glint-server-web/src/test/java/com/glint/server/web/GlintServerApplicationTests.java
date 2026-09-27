package com.glint.server.web;

import com.glint.server.dal.auth.AnonymousSessionEntity;
import com.glint.server.dal.auth.UserEntity;
import com.glint.server.biz.system.SystemStatusService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class GlintServerApplicationTests extends AbstractWebContractTest {

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void stubPersistence() {
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(anonymousSessionRepository.save(any(AnonymousSessionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(anonymousSessionRepository.findByTokenDigest(anyString())).thenReturn(Optional.empty());
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        when(jdbcTemplate.queryForObject("SELECT MAX(version) FROM t_schema_versions", String.class))
                .thenReturn(SystemStatusService.EXPECTED_SCHEMA_VERSION);
        when(redisConnectionFactory.getConnection()).thenReturn(redisConnection);
        when(redisConnection.ping()).thenReturn("PONG");
    }

    @Test
    void applicationContextLoads() {
    }

    @Test
    void statusEndpointReturnsStandardResponse() throws Exception {
        mockMvc.perform(get("/api/v1/system/status"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.database").value("UP"))
                .andExpect(jsonPath("$.data.redis").value("UP"))
                .andExpect(jsonPath("$.data.schemaVersion").value(SystemStatusService.EXPECTED_SCHEMA_VERSION));
    }

    @Test
    void openApiDocumentIncludesSystemStatusEndpoint() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths./api/v1/system/status").exists());
    }

    @Test
    void unknownApiEndpointReturnsStructuredNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/not-a-real-endpoint"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void sessionInitializationReturnsAnonymousUser() throws Exception {
        mockMvc.perform(post("/api/v1/auth/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.userId").isNotEmpty())
                .andExpect(jsonPath("$.data.sessionToken").isNotEmpty());
    }
}
