package com.glint.server.web.board;

import com.glint.server.biz.board.BoardService;
import com.glint.server.common.board.BoardContracts.Post;
import com.glint.server.common.board.BoardContracts.Comment;
import com.glint.server.common.board.BoardContracts.PostInteraction;
import com.glint.server.common.dto.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@RestController
@RequestMapping("/api/v1/board/posts")
/** 匿名线路留言板 HTTP 接口；日志不记录留言正文。 */
public class BoardController {

    private static final Logger log = LoggerFactory.getLogger(BoardController.class);

    private final BoardService boardService;

    public BoardController(BoardService boardService) {
        this.boardService = boardService;
    }

    @GetMapping
    public ApiResponse<List<Post>> posts(@RequestAttribute("authenticatedUserId") String userId,
                                         @RequestParam @NotBlank @Size(max = 64) String lineCode) {
        log.info("board_posts_requested userId={}, lineCode={}", shortId(userId), lineCode);
        List<Post> posts = boardService.posts(lineCode, userId);
        log.info("board_posts_returned userId={}, count={}", shortId(userId), posts.size());
        return ApiResponse.ok(posts);
    }

    @GetMapping("/online-count")
    public ApiResponse<Integer> onlineCount(@RequestAttribute("authenticatedUserId") String userId,
                                            @RequestParam @NotBlank @Size(max = 64) String lineCode) {
        int count = boardService.onlineCount(lineCode);
        log.info("board_online_count_returned userId={}, count={}", shortId(userId), count);
        return ApiResponse.ok(count);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Post> create(@RequestAttribute("authenticatedUserId") String userId,
                                    @Valid @RequestBody BoardPostRequest request) {
        log.info("board_post_create_requested userId={}, lineCode={}, contentLength={}", shortId(userId), request.lineCode(), request.content().length());
        Post post = boardService.create(userId, request.lineCode(), request.content());
        log.info("board_post_created userId={}, postId={}", shortId(userId), shortId(post.postId()));
        return ApiResponse.ok(post);
    }

    @PutMapping("/{postId}/like")
    public ApiResponse<PostInteraction> like(@RequestAttribute("authenticatedUserId") String userId,
                                             @PathVariable String postId) {
        return ApiResponse.ok(boardService.setLiked(userId, postId, true));
    }

    @DeleteMapping("/{postId}/like")
    public ApiResponse<PostInteraction> unlike(@RequestAttribute("authenticatedUserId") String userId,
                                               @PathVariable String postId) {
        return ApiResponse.ok(boardService.setLiked(userId, postId, false));
    }

    @GetMapping("/{postId}/comments")
    public ApiResponse<List<Comment>> comments(@RequestAttribute("authenticatedUserId") String userId,
                                               @PathVariable String postId) {
        return ApiResponse.ok(boardService.comments(postId));
    }

    @PostMapping("/{postId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Comment> comment(@RequestAttribute("authenticatedUserId") String userId,
                                        @PathVariable String postId,
                                        @Valid @RequestBody BoardCommentRequest request) {
        return ApiResponse.ok(boardService.comment(userId, postId, request.content()));
    }

    public record BoardPostRequest(
            @NotBlank @Size(max = 64) String lineCode,
            @NotBlank @Size(max = 1_000) String content
    ) {
    }

    public record BoardCommentRequest(@NotBlank @Size(max = 1_000) String content) {
    }

    private String shortId(String value) { return value.length() <= 8 ? value : value.substring(0, 8); }
}
