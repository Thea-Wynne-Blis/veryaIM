package com.veryal.access.core.push.offline;

import com.veryal.common.json.JsonUtils;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

/**
 * FCM HTTP v1 OAuth2 访问令牌（Service Account JWT Bearer）。
 */
@Slf4j
final class FcmAccessTokenProvider {

    private static final String SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final String clientEmail;
    private final String tokenUri;
    private final PrivateKey privateKey;

    private volatile String accessToken;
    private volatile Instant expiresAt = Instant.EPOCH;

    FcmAccessTokenProvider(String serviceAccountJsonPath) {
        try {
            String json = Files.readString(Path.of(stripFilePrefix(serviceAccountJsonPath)));
            @SuppressWarnings("unchecked")
            Map<String, Object> account = JsonUtils.mapper().readValue(json, Map.class);
            this.clientEmail = String.valueOf(account.get("client_email"));
            this.tokenUri = String.valueOf(account.getOrDefault("token_uri",
                    "https://oauth2.googleapis.com/token"));
            this.privateKey = loadPrivateKey(String.valueOf(account.get("private_key")));
        } catch (Exception e) {
            throw new IllegalStateException("failed to load FCM service account", e);
        }
    }

    synchronized String token() {
        if (accessToken != null && Instant.now().isBefore(expiresAt.minusSeconds(60))) {
            return accessToken;
        }
        try {
            long now = Instant.now().getEpochSecond();
            String header = encode("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
            String claims = encode(JsonUtils.toJson(Map.of(
                    "iss", clientEmail,
                    "scope", SCOPE,
                    "aud", tokenUri,
                    "iat", now,
                    "exp", now + 3600)).getBytes(StandardCharsets.UTF_8));
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update((header + "." + claims).getBytes(StandardCharsets.UTF_8));
            String assertion = header + "." + claims + "." + encode(signature.sign());

            String form = "grant_type=" + URLEncoder.encode(
                    "urn:ietf:params:oauth:grant-type:jwt-bearer", StandardCharsets.UTF_8)
                    + "&assertion=" + URLEncoder.encode(assertion, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder(URI.create(tokenUri))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form))
                    .build();
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new IllegalStateException("FCM token endpoint returned " + response.statusCode());
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> body = JsonUtils.mapper().readValue(response.body(), Map.class);
            accessToken = String.valueOf(body.get("access_token"));
            long expiresIn = ((Number) body.getOrDefault("expires_in", 3600)).longValue();
            expiresAt = Instant.now().plusSeconds(expiresIn);
            return accessToken;
        } catch (Exception e) {
            throw new IllegalStateException("failed to obtain FCM access token", e);
        }
    }

    private static PrivateKey loadPrivateKey(String pem) throws Exception {
        String normalized = pem.replace("\\n", "\n")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(normalized);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    private static String stripFilePrefix(String path) {
        return path.startsWith("file:") ? path.substring("file:".length()) : path;
    }

    private static String encode(byte[] bytes) {
        return ENCODER.encodeToString(bytes);
    }
}
