package com.veryal.bootstrap;

import com.veryal.access.http.dto.AuthResponse;
import com.veryal.access.http.dto.BlockedUserResponse;
import com.veryal.access.http.dto.ContactResponse;
import com.veryal.access.http.dto.ConversationCardResponse;
import com.veryal.access.http.dto.ConversationResponse;
import com.veryal.access.http.dto.FriendRequestResponse;
import com.veryal.access.http.dto.FriendResponse;
import com.veryal.access.http.dto.UserResponse;
import com.veryal.common.core.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 好友申请 → 接受 → 备注 → 黑名单拦截私聊 → 解除。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class FriendIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void friendRequestFriendshipAndBlocking() {
        String suffix = Long.toString(System.nanoTime());
        UserResponse alice = register("fr_alice_" + suffix);
        UserResponse bob = register("fr_bob_" + suffix);
        AuthResponse aliceLogin = login("fr_alice_" + suffix, "fr-a-" + suffix);
        AuthResponse bobLogin = login("fr_bob_" + suffix, "fr-b-" + suffix);

        FriendRequestResponse request = post("/api/v1/friends/requests",
                Map.of("toUserId", bob.userId(), "message", "hi"), aliceLogin.token(),
                new ParameterizedTypeReference<ApiResponse<FriendRequestResponse>>() {
                }).data();
        assertThat(request.status()).isEqualTo("PENDING");

        ResponseEntity<ApiResponse<FriendRequestResponse>> duplicate = rest.exchange(
                "/api/v1/friends/requests", HttpMethod.POST,
                new HttpEntity<>(Map.of("toUserId", bob.userId()), headers(aliceLogin.token())),
                new ParameterizedTypeReference<>() {
                });
        assertThat(duplicate.getStatusCode().value()).isEqualTo(409);

        ApiResponse<List<FriendRequestResponse>> incoming = get("/api/v1/friends/requests",
                bobLogin.token(), new ParameterizedTypeReference<>() {
                });
        assertThat(incoming.data()).extracting(FriendRequestResponse::requestId)
                .contains(request.requestId());

        post("/api/v1/friends/requests/" + request.requestId() + "/accept", Map.of(),
                bobLogin.token(), new ParameterizedTypeReference<ApiResponse<FriendRequestResponse>>() {
                });

        ApiResponse<List<FriendResponse>> aliceFriends = get("/api/v1/friends", aliceLogin.token(),
                new ParameterizedTypeReference<>() {
                });
        assertThat(aliceFriends.data()).extracting(FriendResponse::friendId).contains(bob.userId());
        assertThat(aliceFriends.data().get(0).online()).isFalse();

        HttpHeaders remarkHeaders = headers(bobLogin.token());
        ResponseEntity<ApiResponse<Void>> remark = rest.exchange(
                "/api/v1/friends/" + alice.userId(), HttpMethod.PUT,
                new HttpEntity<>(Map.of("remark", "bestie"), remarkHeaders),
                new ParameterizedTypeReference<>() {
                });
        assertThat(remark.getStatusCode().is2xxSuccessful()).isTrue();

        ContactResponse contacts = get("/api/v1/contacts", bobLogin.token(),
                new ParameterizedTypeReference<ApiResponse<ContactResponse>>() {
                }).data();
        assertThat(contacts.friends()).extracting(FriendResponse::friendId).contains(alice.userId());
        assertThat(contacts.friends().get(0).remark()).isEqualTo("bestie");
        assertThat(contacts.pendingRequests()).isZero();

        ApiResponse<ConversationResponse> conversation = post("/api/v1/conversations",
                Map.of("peerUserId", bob.userId()), aliceLogin.token(),
                new ParameterizedTypeReference<ApiResponse<ConversationResponse>>() {
                });

        // 单聊会话标题优先用好友备注：bob 给 alice 备注了 bestie。
        ApiResponse<List<ConversationCardResponse>> bobCards = get("/api/v1/conversations/cards",
                bobLogin.token(), new ParameterizedTypeReference<>() {
                });
        ConversationCardResponse bobCard = bobCards.data().stream()
                .filter(card -> conversation.data().conversationId().equals(card.conversationId()))
                .findFirst().orElseThrow();
        assertThat(bobCard.title()).isEqualTo("bestie");
        assertThat(bobCard.peerUserId()).isEqualTo(alice.userId());

        // 反向没有备注，仍然显示对方昵称。
        ApiResponse<List<ConversationCardResponse>> aliceCards = get("/api/v1/conversations/cards",
                aliceLogin.token(), new ParameterizedTypeReference<>() {
                });
        ConversationCardResponse aliceCard = aliceCards.data().stream()
                .filter(card -> conversation.data().conversationId().equals(card.conversationId()))
                .findFirst().orElseThrow();
        assertThat(aliceCard.title()).isEqualTo(bob.nickname());

        post("/api/v1/blocks/" + alice.userId(), Map.of(), bobLogin.token(),
                new ParameterizedTypeReference<ApiResponse<Void>>() {
                });
        ApiResponse<List<FriendResponse>> afterBlock = get("/api/v1/friends", bobLogin.token(),
                new ParameterizedTypeReference<>() {
                });
        assertThat(afterBlock.data()).isEmpty();
        ApiResponse<List<BlockedUserResponse>> blocked = get("/api/v1/blocks", bobLogin.token(),
                new ParameterizedTypeReference<>() {
                });
        assertThat(blocked.data()).extracting(BlockedUserResponse::userId).contains(alice.userId());

        ResponseEntity<ApiResponse<Void>> send = rest.exchange(
                "/api/v1/conversations/" + conversation.data().conversationId() + "/messages",
                HttpMethod.POST, new HttpEntity<>(Map.of("conversationId",
                        conversation.data().conversationId(), "clientMessageId", "blk-" + suffix,
                        "type", "TEXT", "content", "hello"), headers(aliceLogin.token())),
                new ParameterizedTypeReference<>() {
                });
        assertThat(send.getStatusCode().value()).isEqualTo(403);

        delete("/api/v1/blocks/" + alice.userId(), bobLogin.token());
        assertThat(get("/api/v1/blocks", bobLogin.token(),
                new ParameterizedTypeReference<ApiResponse<List<BlockedUserResponse>>>() {
                }).data()).isEmpty();
    }

    private UserResponse register(String username) {
        return post("/api/v1/auth/register", Map.of("username", username, "password", "password123",
                        "nickname", username), null,
                new ParameterizedTypeReference<ApiResponse<UserResponse>>() {
                }).data();
    }

    private AuthResponse login(String username, String deviceId) {
        return post("/api/v1/auth/login", Map.of("username", username, "password", "password123",
                        "deviceId", deviceId, "platform", "LINUX", "deviceName", "test"), null,
                new ParameterizedTypeReference<ApiResponse<AuthResponse>>() {
                }).data();
    }

    private <T> ApiResponse<T> post(String path, Object body, String token,
                                    ParameterizedTypeReference<ApiResponse<T>> type) {
        ResponseEntity<ApiResponse<T>> response = rest.exchange(path, HttpMethod.POST,
                new HttpEntity<>(body, headers(token)), type);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    private <T> ApiResponse<T> get(String path, String token,
                                   ParameterizedTypeReference<ApiResponse<T>> type) {
        ResponseEntity<ApiResponse<T>> response = rest.exchange(path, HttpMethod.GET,
                new HttpEntity<>(headers(token)), type);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    private void delete(String path, String token) {
        ResponseEntity<ApiResponse<Void>> response = rest.exchange(path, HttpMethod.DELETE,
                new HttpEntity<>(headers(token)), new ParameterizedTypeReference<>() {
                });
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    }

    private HttpHeaders headers(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return headers;
    }
}
