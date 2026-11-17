package com.veryal.domain.delivery;

import java.util.List;

/**
 * 实时会话控制端口：主动断开已建立的实时连接。
 *
 * <p>登出、下线设备、封禁等操作需要「立即生效」，不能等客户端自己断开。
 * 应用层通过本端口发起，具体实现由接入层（本地网关）提供。</p>
 *
 * <p><b>单节点语义</b>：连接只存在于当前实例，因此这里只会关闭本节点持有的连接。
 * 多实例部署时需要把指令转发到连接所在节点（网关队列）才能全集群生效。</p>
 */
public interface RealtimeSessionPort {

    /**
     * 关闭用指定 Token 建立的全部连接（登出时使用，不影响同用户的其他设备）。
     *
     * @param tokenId Token 的 jti
     * @param reason  关闭原因，会随关闭帧下发给客户端
     * @return 实际关闭的连接数
     */
    int closeByTokenId(String tokenId, String reason);

    /**
     * 关闭指定设备的全部连接（下线设备时使用）。
     *
     * @return 实际关闭的连接数
     */
    int closeByDevice(long userId, String deviceId, String reason);

    /**
     * 关闭指定用户的全部连接（封禁等强制下线场景）。
     *
     * @return 实际关闭的连接数
     */
    int closeByUserId(long userId, String reason);

    default int closeByDevices(long userId, List<String> deviceIds, String reason) {
        if (deviceIds == null || deviceIds.isEmpty()) {
            return 0;
        }
        int closed = 0;
        for (String deviceId : deviceIds) {
            closed += closeByDevice(userId, deviceId, reason);
        }
        return closed;
    }
}
