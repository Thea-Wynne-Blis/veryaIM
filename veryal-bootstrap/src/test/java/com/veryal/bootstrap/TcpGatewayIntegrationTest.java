package com.veryal.bootstrap;

import com.google.protobuf.ByteString;
import com.veryal.access.tcp.TcpGatewayServer;
import com.veryal.application.conversation.ConversationApplicationService;
import com.veryal.application.group.GroupApplicationService;
import com.veryal.application.group.GroupView;
import com.veryal.application.identity.AuthApplicationService;
import com.veryal.application.identity.AuthResult;
import com.veryal.application.identity.LoginCommand;
import com.veryal.application.identity.RegisterCommand;
import com.veryal.application.identity.UserProfile;
import com.veryal.application.message.MessageApplicationService;
import com.veryal.bootstrap.support.TcpTestClient;
import com.veryal.common.core.CursorPage;
import com.veryal.domain.delivery.DeliveryRepository;
import com.veryal.domain.message.Message;
import com.veryal.domain.message.MessageType;
import com.veryal.protocol.api.Command;
import com.veryal.protocol.protobuf.DeliveryAck;
import com.veryal.protocol.protobuf.HeartbeatRequest;
import com.veryal.protocol.protobuf.LoginRequest;
import com.veryal.protocol.protobuf.LoginResponse;
import com.veryal.protocol.protobuf.MessageNotify;
import com.veryal.protocol.protobuf.ReadAck;
import com.veryal.protocol.protobuf.SendMessageRequest;
import com.veryal.protocol.protobuf.ServerAck;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TCP Gateway 端到端测试：真实 Socket、认证、心跳、发送、实时推送、投递回执。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class TcpGatewayIntegrationTest {

    @Autowired
    private TcpGatewayServer tcpGatewayServer;

    @Autowired
    private AuthApplicationService authApplicationService;

    @Autowired
    private ConversationApplicationService conversationApplicationService;

    @Autowired
    private GroupApplicationService groupApplicationService;

    @Autowired
    private MessageApplicationService messageApplicationService;

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Test
    void clientCanLoginSendAndPeerReceivesNotification() throws Exception {
        String suffix = Long.toString(System.nanoTime());
        String aliceName = "tcp_alice_" + suffix;
        String bobName = "tcp_bob_" + suffix;
        UserProfile alice = authApplicationService.register(new RegisterCommand(aliceName, "password123", "Alice"));
        UserProfile bob = authApplicationService.register(new RegisterCommand(bobName, "password123", "Bob"));
        AuthResult aliceLogin = authApplicationService.login(new LoginCommand(aliceName, "password123", "tcp-device-a-" + suffix, "LINUX", "alice"));
        AuthResult bobLogin = authApplicationService.login(new LoginCommand(bobName, "password123", "tcp-device-b-" + suffix, "LINUX", "bob"));

        String conversationId = conversationApplicationService.createPrivate(alice.userId(), bob.userId()).conversationId();

        int port = tcpGatewayServer.boundPort();
        assertThat(port).isPositive();

        try (TcpTestClient aliceClient = TcpTestClient.connect(port);
             TcpTestClient bobClient = TcpTestClient.connect(port)) {

            aliceClient.send(Command.LOGIN, LoginRequest.newBuilder()
                    .setToken(aliceLogin.token())
                    .setDeviceId("tcp-device-a-" + suffix)
                    .setPlatform("LINUX")
                    .build());
            LoginResponse aliceLoginResponse = aliceClient.awaitPayload(Command.LOGIN_RESPONSE,
                    LoginResponse.parser());
            assertThat(aliceLoginResponse.getCode()).isZero();
            assertThat(aliceLoginResponse.getUserId()).isEqualTo(alice.userId());

            bobClient.send(Command.LOGIN, LoginRequest.newBuilder()
                    .setToken(bobLogin.token())
                    .setDeviceId("tcp-device-b-" + suffix)
                    .setPlatform("LINUX")
                    .build());
            assertThat(bobClient.awaitPayload(Command.LOGIN_RESPONSE, LoginResponse.parser()).getCode()).isZero();

            aliceClient.send(Command.HEARTBEAT, HeartbeatRequest.newBuilder()
                    .setClientTime(System.currentTimeMillis())
                    .build());
            aliceClient.await(Command.HEARTBEAT_ACK);

            aliceClient.send(Command.SEND_MESSAGE, SendMessageRequest.newBuilder()
                    .setClientMessageId("tcp-msg-" + suffix)
                    .setConversationId(conversationId)
                    .setMessageType(MessageType.TEXT.code())
                    .setContent(ByteString.copyFromUtf8("hello over tcp"))
                    .setClientSequence(1L)
                    .build());
            ServerAck ack = aliceClient.awaitPayload(Command.SERVER_ACK, ServerAck.parser());
            assertThat(ack.getConversationSequence()).isEqualTo(1L);
            assertThat(ack.getClientMessageId()).isEqualTo("tcp-msg-" + suffix);

            MessageNotify notify = bobClient.awaitPayload(Command.MESSAGE_NOTIFY, MessageNotify.parser());
            assertThat(notify.getConversationId()).isEqualTo(conversationId);
            assertThat(notify.getSenderId()).isEqualTo(alice.userId());
            assertThat(notify.getContent().toStringUtf8()).isEqualTo("hello over tcp");
            assertThat(notify.getSequence()).isEqualTo(1L);

            bobClient.send(Command.DELIVERY_ACK, DeliveryAck.newBuilder()
                    .setMessageId(ack.getServerMessageId())
                    .setDeviceId("tcp-device-b-" + suffix)
                    .setDeliveredSequence(1L)
                    .build());
            awaitDeliveryRecord(Long.parseLong(ack.getServerMessageId()), "tcp-device-b-" + suffix);
            var deliveries = messageApplicationService.listDeliveries(bob.userId(), conversationId, Long.parseLong(ack.getServerMessageId()));
            assertThat(deliveries).hasSize(1);
            assertThat(deliveries.get(0).deviceId()).isEqualTo("tcp-device-b-" + suffix);
            bobClient.send(Command.READ_ACK, ReadAck.newBuilder()
                    .setConversationId(conversationId)
                    .setReadSequence(1L)
                    .build());

            // 给业务线程池一点时间处理 ACK，再断言历史消息已持久化
            CursorPage<Message> history = awaitHistory(conversationId, alice);
            assertThat(history.items()).hasSize(1);
            assertThat(history.items().get(0).textContent()).isEqualTo("hello over tcp");
        }
    }

    @Test
    void loginWithInvalidTokenIsRejected() throws Exception {
        try (TcpTestClient client = TcpTestClient.connect(tcpGatewayServer.boundPort())) {
            client.send(Command.LOGIN, LoginRequest.newBuilder()
                    .setToken("invalid-token")
                    .setDeviceId("device-x")
                    .build());
            assertThat(client.await(Command.ERROR)).isNotNull();
        }
    }

    @Test
    void groupMessageReachesOnlineMember() throws Exception {
        String suffix = Long.toString(System.nanoTime());
        String ownerName = "tcp_group_owner_" + suffix;
        String memberName = "tcp_group_member_" + suffix;
        UserProfile owner = authApplicationService.register(new RegisterCommand(ownerName, "password123", "Owner"));
        UserProfile member = authApplicationService.register(new RegisterCommand(memberName, "password123", "Member"));
        String ownerDevice = "tcp-group-owner-" + suffix;
        String memberDevice = "tcp-group-member-" + suffix;
        AuthResult ownerLogin = authApplicationService.login(new LoginCommand(ownerName, "password123", ownerDevice, "LINUX", "owner"));
        AuthResult memberLogin = authApplicationService.login(new LoginCommand(memberName, "password123", memberDevice, "LINUX", "member"));
        GroupView group = groupApplicationService.create(owner.userId(),
                "tcp-group-" + suffix, List.of(member.userId()));

        try (TcpTestClient ownerClient = TcpTestClient.connect(tcpGatewayServer.boundPort());
             TcpTestClient memberClient = TcpTestClient.connect(tcpGatewayServer.boundPort())) {
            ownerClient.send(Command.LOGIN, LoginRequest.newBuilder()
                    .setToken(ownerLogin.token()).setDeviceId(ownerDevice).setPlatform("LINUX").build());
            assertThat(ownerClient.awaitPayload(Command.LOGIN_RESPONSE, LoginResponse.parser()).getCode())
                    .isZero();
            memberClient.send(Command.LOGIN, LoginRequest.newBuilder()
                    .setToken(memberLogin.token()).setDeviceId(memberDevice).setPlatform("LINUX").build());
            assertThat(memberClient.awaitPayload(Command.LOGIN_RESPONSE, LoginResponse.parser()).getCode())
                    .isZero();

            ownerClient.send(Command.SEND_MESSAGE, SendMessageRequest.newBuilder()
                    .setClientMessageId("tcp-group-msg-" + suffix)
                    .setConversationId(group.groupId())
                    .setMessageType(MessageType.TEXT.code())
                    .setContent(ByteString.copyFromUtf8("hello group tcp"))
                    .build());
            ServerAck ack = ownerClient.awaitPayload(Command.SERVER_ACK, ServerAck.parser());
            assertThat(ack.getConversationSequence()).isEqualTo(1L);

            MessageNotify notify = memberClient.awaitPayload(Command.MESSAGE_NOTIFY, MessageNotify.parser());
            assertThat(notify.getConversationId()).isEqualTo(group.groupId());
            assertThat(notify.getContent().toStringUtf8()).isEqualTo("hello group tcp");
        }
    }

    private CursorPage<Message> awaitHistory(String conversationId, UserProfile sender) throws InterruptedException {
        CursorPage<Message> history = messageApplicationService.history(sender.userId(), conversationId, 0, 10);
        long deadline = System.currentTimeMillis() + 3000;
        while (history.items().isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
            history = messageApplicationService.history(sender.userId(),
                    conversationId, 0, 10);
        }
        return history;
    }

    private void awaitDeliveryRecord(long messageId, String deviceId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 3000;
        while (deliveryRepository.find(messageId, deviceId).isEmpty()
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertThat(deliveryRepository.find(messageId, deviceId)).isPresent();
    }
}
