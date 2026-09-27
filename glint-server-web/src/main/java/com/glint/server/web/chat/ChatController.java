package com.glint.server.web.chat;

import com.glint.server.biz.chat.ChatService;
import com.glint.server.common.chat.ChatContracts.Message;
import com.glint.server.common.chat.ChatContracts.Session;
import com.glint.server.common.dto.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@RestController
@RequestMapping("/api/v1/chat/sessions")
/** 限时聊天接口；日志只记录会话和文本长度，绝不记录消息正文。 */
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Session> create(@RequestAttribute("authenticatedUserId") String userId,
                                       @Valid @RequestBody ChatSessionRequest request) {
        log.info("chat_session_create_requested userId={}, matchId={}", shortId(userId), shortId(request.matchId()));
        Session session = chatService.createSession(request.matchId(), userId);
        log.info("chat_session_created userId={}, sessionId={}", shortId(userId), shortId(session.sessionId()));
        return ApiResponse.ok(session);
    }

    @GetMapping
    public ApiResponse<List<Session>> sessions(@RequestAttribute("authenticatedUserId") String userId) {
        List<Session> sessions = chatService.sessions(userId);
        log.info("chat_sessions_returned userId={}, count={}", shortId(userId), sessions.size());
        return ApiResponse.ok(sessions);
    }

    @GetMapping("/{sessionId}")
    public ApiResponse<Session> session(@RequestAttribute("authenticatedUserId") String userId,
                                        @PathVariable String sessionId) {
        return ApiResponse.ok(chatService.session(sessionId, userId));
    }

    @GetMapping("/{sessionId}/messages")
    public ApiResponse<List<Message>> messages(@RequestAttribute("authenticatedUserId") String userId,
                                                @PathVariable String sessionId) {
        List<Message> messages = chatService.messages(sessionId, userId);
        log.info("chat_messages_returned userId={}, sessionId={}, count={}", shortId(userId), shortId(sessionId), messages.size());
        return ApiResponse.ok(messages);
    }

    @PostMapping("/{sessionId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Message> send(@RequestAttribute("authenticatedUserId") String userId,
                                     @PathVariable String sessionId, @Valid @RequestBody ChatMessageRequest request) {
        log.info("chat_message_send_requested userId={}, sessionId={}, textLength={}", shortId(userId), shortId(sessionId), request.text().length());
        Message message = chatService.send(sessionId, userId, request.text());
        log.info("chat_message_sent userId={}, sessionId={}, messageId={}", shortId(userId), shortId(sessionId), shortId(message.messageId()));
        return ApiResponse.ok(message);
    }

    public record ChatSessionRequest(@NotBlank @Size(max = 64) String matchId) {
    }

    public record ChatMessageRequest(@NotBlank @Size(max = 4_000) String text) {
    }

    private String shortId(String value) { return value.length() <= 8 ? value : value.substring(0, 8); }
}
