package com.glint.server.web;

import com.glint.server.dal.auth.AnonymousSessionRepository;
import com.glint.server.dal.auth.UserRepository;
import com.glint.server.dal.chat.ChatMessageRepository;
import com.glint.server.dal.chat.ChatSessionRepository;
import com.glint.server.dal.commute.CommuteStationSummaryRepository;
import com.glint.server.dal.matching.MatchingCandidateCache;
import com.glint.server.dal.matching.MatchingLinePresenceCache;
import com.glint.server.dal.profile.ProfileRepository;
import com.glint.server.dal.safety.MessageReportRepository;
import com.glint.server.biz.metro.MetroNetworkService;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 无真实 MySQL/Redis 时，为 web 契约测试提供依赖替身。
 */
abstract class AbstractWebContractTest {

    @MockBean
    protected AnonymousSessionRepository anonymousSessionRepository;

    @MockBean
    protected UserRepository userRepository;

    @MockBean
    protected ProfileRepository profileRepository;

    @MockBean
    protected CommuteStationSummaryRepository commuteStationSummaryRepository;

    @MockBean
    protected ChatSessionRepository chatSessionRepository;

    @MockBean
    protected ChatMessageRepository chatMessageRepository;

    @MockBean
    protected MessageReportRepository messageReportRepository;

    @MockBean
    protected MatchingCandidateCache matchingCandidateCache;

    @MockBean
    protected MatchingLinePresenceCache matchingLinePresenceCache;

    @MockBean
    protected JdbcTemplate jdbcTemplate;

    @MockBean
    protected RedisConnectionFactory redisConnectionFactory;

    @MockBean
    protected RedisConnection redisConnection;

    @MockBean
    protected MetroNetworkService metroNetworkService;

    @MockBean
    protected org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;
}
