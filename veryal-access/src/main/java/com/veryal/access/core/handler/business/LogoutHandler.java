package com.veryal.access.core.handler.business;

import com.veryal.access.core.Connection;
import com.veryal.access.core.protocol.CommandContext;
import com.veryal.access.core.protocol.CommandHandler;
import com.veryal.application.identity.AuthenticatedUser;
import com.veryal.application.identity.SessionApplicationService;
import com.veryal.protocol.api.Command;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 主动登出：清理在线状态后关闭连接。
 */
@Component
@RequiredArgsConstructor
public class LogoutHandler implements CommandHandler {

    private final SessionApplicationService sessionApplicationService;

    @Override
    public Command command() {
        return Command.LOGOUT;
    }

    @Override
    public void handle(CommandContext context) {
        Connection connection = context.connection();
        sessionApplicationService.offline(new AuthenticatedUser(connection.userId(), connection.tenantId(),
                connection.deviceId(), connection.tokenId()), connection.connectionId());
        connection.close();
    }
}
