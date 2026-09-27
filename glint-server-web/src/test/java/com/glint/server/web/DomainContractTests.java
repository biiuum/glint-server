package com.glint.server.web;

import com.glint.server.biz.board.BoardService;
import com.glint.server.biz.chat.MessageCipher;
import com.glint.server.biz.matching.MatchingCandidateTtlPolicy;
import com.glint.server.biz.matching.MatchingService;
import com.glint.server.biz.presentation.AchievementService;
import com.glint.server.biz.presentation.CommutePreferenceService;
import com.glint.server.biz.presentation.PresentationService;
import com.glint.server.biz.safety.RetentionPolicy;
import com.glint.server.common.board.BoardContracts.Post;
import com.glint.server.common.board.BoardContracts.PostInteraction;
import com.glint.server.common.matching.MatchingContracts.Candidate;
import com.glint.server.common.matching.MatchingContracts.Topic;
import com.glint.server.common.metro.MetroContracts.MetroCity;
import com.glint.server.common.metro.MetroContracts.MetroLine;
import com.glint.server.common.presentation.PresentationContracts.Achievement;
import com.glint.server.common.presentation.PresentationContracts.CommutePreferences;
import com.glint.server.common.presentation.PresentationContracts.HomeSummary;
import com.glint.server.common.presentation.PresentationContracts.ProfileDashboard;
import com.glint.server.dal.auth.AnonymousSessionEntity;
import com.glint.server.dal.auth.UserEntity;
import com.glint.server.dal.profile.ProfileEntity;
import com.glint.server.dal.safety.MessageReportEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DomainContractTests extends AbstractWebContractTest {

    private static final String TEST_KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    private static final String USER_ID = "11111111-1111-1111-1111-111111111111";
    private static final String BEARER = "Bearer test-token";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MatchingService matchingService;

    @MockBean
    private BoardService boardService;

    @MockBean
    private PresentationService presentationService;

    @MockBean
    private AchievementService achievementService;

    @MockBean
    private CommutePreferenceService commutePreferenceService;

    @BeforeEach
    void stubAuthenticatedSession() {
        when(anonymousSessionRepository.findByTokenDigest(anyString())).thenReturn(Optional.of(
                AnonymousSessionEntity.active("session-1", USER_ID, "digest", Instant.now().plusSeconds(3600))
        ));
        UserEntity bound = UserEntity.create(USER_ID);
        bound.bindPhone("13800138000");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(bound));
        when(profileRepository.save(any(ProfileEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(messageReportRepository.save(any(MessageReportEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(commuteStationSummaryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(), any(), any())).thenReturn(1);
        when(metroNetworkService.lineExistsInCity(anyString(), anyString())).thenReturn(true);
        when(metroNetworkService.cityExists(anyString())).thenReturn(true);
    }

    @Test
    void acceptsAnonymousProfileAndNeverExposesContactDetails() throws Exception {
        mockMvc.perform(put("/api/v1/profiles/me")
                        .header("Authorization", BEARER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"avatarKey":"aurora","interestTags":["coffee","books"],"nickname":"测试昵称"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.avatarKey").value("aurora"))
                .andExpect(jsonPath("$.data.contact").doesNotExist());
    }

    @Test
    void rejectsRawLocationFieldsWithStructuredError() throws Exception {
        mockMvc.perform(post("/api/v1/commutes/station-summaries")
                        .header("Authorization", BEARER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"stationCode":"S1","stationName":"人民广场","occurredAt":"2026-08-10T12:00:00Z",
                                "statusSummary":"拥挤","latitude":31.2}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void exposesDeterministicMatchingAndSocialPlaceholders() throws Exception {
        Candidate candidate = new Candidate("match-1", "profile-1", "aurora",
                List.of("coffee", "books"), 1, 2, false, false, false, false, false, false, null, "SAME_STATION");
        Candidate virtual = new Candidate("match-virtual", "profile-virtual", "virtual-sunrise",
                List.of("coffee", "books"), 1, 2, false, false, false, false, false, true, null, null);
        when(matchingService.candidates(USER_ID)).thenReturn(List.of(candidate));
        when(matchingService.virtualPassengers(USER_ID)).thenReturn(List.of(virtual));
        when(matchingService.rescueTopic(eq(USER_ID), eq("人民广场"), eq("列车晚点")))
                .thenReturn(new Topic("人民广场这一段，你一般用来干嘛？"));
        when(boardService.posts("L1", USER_ID)).thenReturn(List.of(
                new Post("post-1", "L1", "hello", Instant.parse("2026-08-10T12:00:00Z"))
        ));

        mockMvc.perform(get("/api/v1/matches/candidates").header("Authorization", BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].unlockLevel").value(1))
                .andExpect(jsonPath("$.data[0].encounterCount").value(2))
                .andExpect(jsonPath("$.data[0].matchReason").value("SAME_STATION"));
        mockMvc.perform(get("/api/v1/matches/virtual-passengers").header("Authorization", BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].isVirtual").value(true));
        mockMvc.perform(post("/api/v1/topics/rescue")
                        .header("Authorization", BEARER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stationName\":\"人民广场\",\"trafficSummary\":\"列车晚点\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.topic").isNotEmpty());
        mockMvc.perform(get("/api/v1/board/posts?lineCode=L1").header("Authorization", BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].lineCode").value("L1"));
    }

    @Test
    void validatesTextOnlyMessageAndCreatesIndependentReport() throws Exception {
        mockMvc.perform(post("/api/v1/chat/sessions/session-demo/messages")
                        .header("Authorization", BEARER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senderId\":\"user-1\",\"text\":\"你好\",\"imageUrl\":\"https://example.test/a.png\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(post("/api/v1/safety/message-reports")
                        .header("Authorization", BEARER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messageId\":\"message-1\",\"reason\":\"abuse\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void returnsServerDrivenPresentationResourcesAndBoardInteractions() throws Exception {
        when(presentationService.homeSummary(USER_ID)).thenReturn(new HomeSummary(12, 3, 2, 1, 0));
        when(presentationService.profileDashboard(USER_ID)).thenReturn(
                new ProfileDashboard("小橙子", "aurora", "通勤达人", 128, 5, 23, 3, 6));
        when(achievementService.achievements(USER_ID)).thenReturn(List.of(
                new Achievement("FIRST_MEET", "初次相遇", "achievement-first-meet", "解锁 1 位同路人", 1, 1, true, Instant.now())
        ));
        when(commutePreferenceService.current(USER_ID)).thenReturn(
                new CommutePreferences("07:30", "09:30", List.of("line-2"), true, true, false, "st-1", "人民广场"));
        when(boardService.setLiked(USER_ID, "post-1", true)).thenReturn(new PostInteraction("post-1", 9, 2, true));

        mockMvc.perform(get("/api/v1/home/summary").header("Authorization", BEARER))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.encounterCount").value(12));
        mockMvc.perform(get("/api/v1/profiles/me/dashboard").header("Authorization", BEARER))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.title").value("通勤达人"));
        mockMvc.perform(get("/api/v1/achievements").header("Authorization", BEARER))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].unlocked").value(true));
        mockMvc.perform(get("/api/v1/commute-preferences/me").header("Authorization", BEARER))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.preferredLineIds[0]").value("line-2"));
        mockMvc.perform(put("/api/v1/board/posts/post-1/like").header("Authorization", BEARER))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.likeCount").value(9));
    }

    @Test
    void encryptsMessagesWithAesGcmAndPersistsNonceMetadata() {
        MessageCipher cipher = new MessageCipher(TEST_KEY);

        var encrypted = cipher.encrypt("只保存密文");

        assertThat(encrypted.ciphertext()).isNotEqualTo("只保存密文");
        assertThat(encrypted.nonce()).isNotBlank();
        assertThat(encrypted.keyVersion()).isEqualTo("v1");
        assertThat(cipher.decrypt(encrypted)).isEqualTo("只保存密文");
    }

    @Test
    void capsMatchingCandidateTtlAtTwentyFourHours() {
        MatchingCandidateTtlPolicy policy = new MatchingCandidateTtlPolicy();

        assertThat(policy.cacheTtl(Duration.ofHours(48))).isEqualTo(Duration.ofHours(24));
        assertThatThrownBy(() -> policy.cacheTtl(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void expiresStationAndMatchDataAtTwentyFourHoursAndMessagesAtSevenDays() {
        RetentionPolicy policy = new RetentionPolicy();
        Instant createdAt = Instant.parse("2026-08-10T12:00:00Z");

        assertThat(policy.stationSummaryExpiresAt(createdAt)).isEqualTo(Instant.parse("2026-08-11T12:00:00Z"));
        assertThat(policy.messageExpiresAt(createdAt)).isEqualTo(Instant.parse("2026-08-17T12:00:00Z"));
        assertThat(policy.isExpired(createdAt, Duration.ofHours(24), Instant.parse("2026-08-11T12:00:00Z"))).isTrue();
    }

    @Test
    void listsMetroCitiesAndLinesFromNetworkService() throws Exception {
        when(metroNetworkService.listCities()).thenReturn(List.of(
                new MetroCity("3100", "上海市", "shanghai"),
                new MetroCity("1100", "北京市", "beijing")
        ));
        when(metroNetworkService.listLines("3100")).thenReturn(List.of(
                new MetroLine("line-2", "3100", "line-2", "2号线", 0)
        ));

        mockMvc.perform(get("/api/v1/metro/cities").header("Authorization", BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].cityCode").value("3100"))
                .andExpect(jsonPath("$.data[1].cityName").value("北京市"));
        mockMvc.perform(get("/api/v1/metro/cities/3100/lines").header("Authorization", BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].lineName").value("2号线"))
                .andExpect(jsonPath("$.data[0].colorHex").value("#93C83E"));
    }

    @Test
    void acceptsLineBasedCommuteSummaryWithoutRawCoordinates() throws Exception {
        mockMvc.perform(post("/api/v1/commutes/station-summaries")
                        .header("Authorization", BEARER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cityCode":"3100","lineId":"line-2","stationCode":"st-1","stationName":"人民广场",
                                "occurredAt":"2026-08-10T12:00:00Z","statusSummary":"searching","directionConfidence":0.5}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.cityCode").value("3100"))
                .andExpect(jsonPath("$.data.lineId").value("line-2"));
    }

    @Test
    void rejectsUnknownMetroLineOnCommuteSummary() throws Exception {
        when(metroNetworkService.lineExistsInCity("3100", "no-such-line")).thenReturn(false);
        mockMvc.perform(post("/api/v1/commutes/station-summaries")
                        .header("Authorization", BEARER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cityCode":"3100","lineId":"no-such-line","stationCode":"st-1","stationName":"人民广场",
                                "occurredAt":"2026-08-10T12:00:00Z","statusSummary":"searching","directionConfidence":0.5}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("城市或线路不存在"));
    }

    @Test
    void rejectsMetroSyncWithoutAdminKey() throws Exception {
        mockMvc.perform(post("/api/v1/metro/sync"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void rejectsGuestCommuteWriteWithAccountRequired() throws Exception {
        UserEntity guest = UserEntity.create(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(guest));
        mockMvc.perform(post("/api/v1/commutes/station-summaries")
                        .header("Authorization", BEARER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cityCode":"3100","lineId":"line-2","stationCode":"st-1","stationName":"人民广场",
                                "occurredAt":"2026-08-10T12:00:00Z","statusSummary":"searching","directionConfidence":0.5}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_REQUIRED"));
    }

    @Test
    void allowsGuestToReadHomeSummary() throws Exception {
        UserEntity guest = UserEntity.create(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(guest));
        when(presentationService.homeSummary(USER_ID)).thenReturn(new HomeSummary(0, 0, 0, 0, 0));
        mockMvc.perform(get("/api/v1/home/summary").header("Authorization", BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.encounterCount").value(0));
    }
}
