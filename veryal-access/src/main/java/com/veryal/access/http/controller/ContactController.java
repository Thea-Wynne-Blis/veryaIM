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

/**
 * 通讯录聚合。
 */
@RestController
@RequestMapping("/api/v1/contacts")
@RequiredArgsConstructor
public class ContactController {

    private final ContactApplicationService contactApplicationService;

    @GetMapping
    public ApiResponse<ContactResponse> contacts(@CurrentUser AuthenticatedUser user) {
        return ApiResponse.ok(ContactResponse.from(contactApplicationService.contacts(
                user.userId())));
    }
}
