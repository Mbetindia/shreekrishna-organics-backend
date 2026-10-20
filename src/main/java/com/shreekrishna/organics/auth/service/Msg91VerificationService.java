
package com.shreekrishna.organics.auth.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

@Service
public class Msg91VerificationService {

    private final String authKey;
    private final String verifyUrl;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public Msg91VerificationService(
            @Value("${msg91.auth-key}") String authKey,
            @Value("${msg91.verify-url}") String verifyUrl,
            ObjectMapper objectMapper
    ) {
        this.authKey = authKey;
        this.verifyUrl = verifyUrl;
        this.objectMapper = objectMapper;

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public String verifyAndGetMobile(String accessToken) {

        // Step 1: Validate the received access token.
        if (accessToken == null
                || accessToken.isBlank()
                || accessToken.length() > 8192) {

            throw new IllegalArgumentException(
                    "Invalid MSG91 access token"
            );
        }

        try {

            // Step 2: Create request body as documented by MSG91.
            // The authkey must remain on the backend only.
            String requestBody = objectMapper.writeValueAsString(
                    Map.of(
                            "authkey", authKey,
                            "access-token", accessToken
                    )
            );

            // Step 3: Prepare MSG91 verification request.
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(verifyUrl))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .POST(
                            HttpRequest.BodyPublishers.ofString(
                                    requestBody
                            )
                    )
                    .build();

            // Step 4: Call MSG91 verification API.
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            // Step 5: Check HTTP status.
            if (response.statusCode() != 200) {
                throw new IllegalArgumentException(
                        "MSG91 OTP verification failed"
                );
            }

            // Step 6: Read MSG91 response.
            JsonNode json = objectMapper.readTree(
                    response.body()
            );

            String type = json.path("type").asText("");
            String verifiedMobile =
                    json.path("message").asText("");

            // Step 7: Accept only successful verification.
            if (!"success".equalsIgnoreCase(type)) {
                throw new IllegalArgumentException(
                        "Invalid or expired OTP verification"
                );
            }

            // Step 8: Validate verified Indian mobile number.
            // Expected format: 91 followed by 10 digits.
            if (!verifiedMobile.matches("91[6-9][0-9]{9}")) {
                throw new IllegalArgumentException(
                        "MSG91 did not return a valid Indian mobile number"
                );
            }

            // Step 9: Return canonical 10-digit mobile number.
            return verifiedMobile.substring(2);

        } catch (InterruptedException ex) {

            Thread.currentThread().interrupt();

            throw new IllegalStateException(
                    "OTP verification was interrupted",
                    ex
            );

        } catch (IllegalArgumentException ex) {

            throw ex;

        } catch (Exception ex) {

            throw new IllegalStateException(
                    "Unable to verify OTP with MSG91",
                    ex
            );
        }
    }
}
