package com.veryal.application.ops;

import com.veryal.application.platform.PlatformPrincipal;
import com.veryal.common.exception.BusinessException;
import com.veryal.common.exception.ErrorCode;
import com.veryal.domain.platform.PlatformAdminRole;
import org.springframework.stereotype.Component;

/**
 * 运营后台权限。
 */
@Component
public class OpsPermission {

    public void requireRead(PlatformPrincipal principal) {
        if (principal == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
    }

    public void requireWrite(PlatformPrincipal principal) {
        requireRead(principal);
        if (principal.role() != PlatformAdminRole.PLATFORM_ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "ops admin permission required");
        }
    }
}
