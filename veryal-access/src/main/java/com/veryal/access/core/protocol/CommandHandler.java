package com.veryal.access.core.protocol;

import com.veryal.protocol.api.Command;

/**
 * 消息命令处理器（策略模式）。新增指令时只增加实现类，不修改分发逻辑。
 */
public interface CommandHandler {

    Command command();

    void handle(CommandContext context);
}
