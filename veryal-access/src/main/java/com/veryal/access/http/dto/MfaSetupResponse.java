package com.veryal.access.http.dto;

import com.veryal.application.platform.PlatformAdminService;

/**
 * MFA 初始化结果。
 */
public record MfaSetupResponse(String secret, String otpauthUrl) {

    public static MfaSetupResponse from(PlatformAdminService.MfaSetup setup) {
        return new MfaSetupResponse(setup.secret(), setup.otpauthUrl());
    }
}
