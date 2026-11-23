package com.veryal.access.http.controller;

import com.veryal.access.http.dto.CreateNoteRequest;
import com.veryal.access.http.dto.NoteResponse;
import com.veryal.access.http.dto.NoteSummaryResponse;
import com.veryal.access.http.dto.UpdateNoteRequest;
import com.veryal.access.http.security.CurrentUser;
import com.veryal.application.identity.AuthenticatedUser;
import com.veryal.application.note.NoteApplicationService;
import com.veryal.common.core.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 个人笔记 API：Markdown 原文保存，渲染在客户端完成。
 */
@Tag(name = "笔记")
@RestController
@RequestMapping("/api/v1/notes")
@RequiredArgsConstructor
public class NoteController {

    private final NoteApplicationService noteApplicationService;

    @Operation(summary = "笔记列表", description = "按最近更新时间倒序；keyword 可选，过滤标题与正文。")
    @GetMapping
    public ApiResponse<List<NoteSummaryResponse>> list(@CurrentUser AuthenticatedUser user,
                                                       @RequestParam(required = false) String keyword,
                                                       @RequestParam(defaultValue = "200") int limit) {
        return ApiResponse.ok(noteApplicationService.list(user.userId(), keyword, limit).stream()
                .map(NoteSummaryResponse::from)
                .toList());
    }

    @Operation(summary = "笔记详情")
    @GetMapping("/{noteId}")
    public ApiResponse<NoteResponse> get(@CurrentUser AuthenticatedUser user,
                                         @PathVariable long noteId) {
        return ApiResponse.ok(NoteResponse.from(noteApplicationService.get(user.userId(), noteId)));
    }

    @Operation(summary = "新建笔记", description = "标题留空时由服务端从正文首行提取。")
    @PostMapping
    public ApiResponse<NoteResponse> create(@CurrentUser AuthenticatedUser user,
                                            @Valid @RequestBody CreateNoteRequest request) {
        return ApiResponse.ok(NoteResponse.from(noteApplicationService.create(
                user.userId(), request.title(), request.content())));
    }

    @Operation(summary = "更新笔记", description = "未传的字段保留原值。")
    @PutMapping("/{noteId}")
    public ApiResponse<NoteResponse> update(@CurrentUser AuthenticatedUser user,
                                            @PathVariable long noteId,
                                            @Valid @RequestBody UpdateNoteRequest request) {
        return ApiResponse.ok(NoteResponse.from(noteApplicationService.update(
                user.userId(), noteId, request.title(), request.content())));
    }

    @Operation(summary = "删除笔记")
    @DeleteMapping("/{noteId}")
    public ApiResponse<Void> delete(@CurrentUser AuthenticatedUser user,
                                    @PathVariable long noteId) {
        noteApplicationService.delete(user.userId(), noteId);
        return ApiResponse.ok();
    }
}
