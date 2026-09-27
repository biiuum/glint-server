package com.glint.server.web.matching;

import com.glint.server.biz.matching.MatchingService;
import com.glint.server.common.dto.ApiResponse;
import com.glint.server.common.matching.MatchingContracts.Candidate;
import com.glint.server.common.matching.MatchingContracts.Topic;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
/** 匹配候选、解锁动作和话题救场接口。 */
public class MatchingController {

    private static final Logger log = LoggerFactory.getLogger(MatchingController.class);

    private final MatchingService matchingService;

    public MatchingController(MatchingService matchingService) {
        this.matchingService = matchingService;
    }

    @GetMapping("/matches/candidates")
    public ApiResponse<List<Candidate>> candidates(@RequestAttribute("authenticatedUserId") String userId) {
        List<Candidate> candidates = matchingService.candidates(userId);
        log.info("match_candidates_returned userId={}, count={}", shortId(userId), candidates.size());
        return ApiResponse.ok(candidates);
    }

    @PostMapping("/matches/{matchId}/actions")
    public ApiResponse<Candidate> action(@RequestAttribute("authenticatedUserId") String userId,
                                         @PathVariable String matchId, @Valid @RequestBody MatchActionRequest request) {
        log.info("match_action_requested userId={}, matchId={}, action={}", shortId(userId), shortId(matchId), request.action());
        Candidate candidate = matchingService.action(userId, matchId, request.action().name(), 0, false, false);
        log.info("match_action_completed userId={}, matchId={}, unlockLevel={}", shortId(userId), shortId(matchId), candidate.unlockLevel());
        return ApiResponse.ok(candidate);
    }

    @PostMapping("/matches/{matchId}/unlock")
    public ApiResponse<Candidate> unlock(@RequestAttribute("authenticatedUserId") String userId,
                                         @PathVariable String matchId, @Valid @RequestBody UnlockRequest request) {
        log.info("match_unlock_requested userId={}, matchId={}, level={}, consent={}", shortId(userId), shortId(matchId), request.requestedLevel(), request.consent());
        Candidate candidate = matchingService.action(userId, matchId, "REQUEST_UNLOCK", request.requestedLevel(),
                request.consent(), request.realInfoExchangeConfirmed());
        return ApiResponse.ok(candidate);
    }

    @GetMapping("/matches/virtual-passengers")
    public ApiResponse<List<Candidate>> virtualPassengers(@RequestAttribute("authenticatedUserId") String userId) {
        List<Candidate> passengers = matchingService.virtualPassengers(userId);
        log.info("virtual_passengers_returned userId={}, count={}", shortId(userId), passengers.size());
        return ApiResponse.ok(passengers);
    }

    @PostMapping("/topics/rescue")
    public ApiResponse<Topic> rescueTopic(@RequestAttribute("authenticatedUserId") String userId,
                                          @Valid @RequestBody TopicRescueRequest request) {
        log.info("topic_rescue_requested userId={}, stationNameLength={}, trafficSummaryLength={}", shortId(userId), request.stationName().length(), request.trafficSummary().length());
        return ApiResponse.ok(matchingService.rescueTopic(userId, request.stationName(), request.trafficSummary()));
    }

    public record MatchActionRequest(
            @NotNull MatchAction action
    ) {
    }

    public record UnlockRequest(
            @NotNull Integer requestedLevel,
            boolean consent,
            boolean realInfoExchangeConfirmed
    ) {
    }

    public record TopicRescueRequest(
            @NotBlank @Size(max = 128) String stationName,
            @NotBlank @Size(max = 256) String trafficSummary
    ) {
    }

    public enum MatchAction {
        LIKE, COMMENT, MARK_INTERESTED, REQUEST_UNLOCK, CONFIRM_REAL_INFO_EXCHANGE
    }

    private String shortId(String value) { return value.length() <= 8 ? value : value.substring(0, 8); }
}
