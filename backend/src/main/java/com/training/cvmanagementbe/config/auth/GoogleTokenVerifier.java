package com.training.cvmanagementbe.config.auth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.training.cvmanagementbe.enums.ErrorCode;
import com.training.cvmanagementbe.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;

/*
 * Verifies the Google ID token sent by the frontend.
 * The system is STATELESS, so the redirect-based OAuth2 client flow is not used,
 * there is no server session to hold the OAuth state.
 */
@Component
public class GoogleTokenVerifier {

    private final GoogleIdTokenVerifier verifier;

    public GoogleTokenVerifier(@Value("${google.client-id}") String clientId) {
        this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(Collections.singletonList(clientId))
                .build();
    }

    // Returns the verified payload or throws 401 when signature/audient/expiry fails
    public GoogleIdToken.Payload verify(String idToken) {
        try {
            GoogleIdToken token = verifier.verify(idToken);
            if (token == null) {
                throw new ApiException.UnauthorizedException(ErrorCode.GOOGLE_TOKEN_INVALID);
            }
            return token.getPayload();
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException.UnauthorizedException(ErrorCode.GOOGLE_TOKEN_INVALID);
        }
    }
}
