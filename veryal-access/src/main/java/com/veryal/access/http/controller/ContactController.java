package com.veryal.access.http.controller;

import com.veryal.access.http.dto.ContactResponse;
import com.veryal.access.http.security.CurrentUser;
import com.veryal.application.contact.ContactApplicationService;
import com.veryal.application.identity.AuthenticatedUser;
import com.veryal.common.core.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 通讯录聚合。
 */
@Tag(name = "通讯录")
@RestController
@RequestMapping("/api/v1/contacts")
@RequiredArgsConstructor
public class ContactController {

    private final ContactApplicationService contactApplicationService;

    @Operation(summary = "通讯录聚合", description = "一次返回好友、群聊与待处理的好友申请，通讯录页面只需调用这一个接口。")
    @GetMapping
    public ApiResponse<ContactResponse> contacts(@CurrentUser AuthenticatedUser user) {
        return ApiResponse.ok(ContactResponse.from(contactApplicationService.contacts(
                user.userId())));
    }
}
