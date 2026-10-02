package com.evehealthcare.diagnostics;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthApiTest extends BaseApiTest {

    private Map<String, String> signupBody(String email, String password) {
        return Map.of("email", email, "password", password, "fullName", "Jane Doe");
    }

    @Test
    void signupLoginAndMe() throws Exception {
        String email = "Jane-" + UUID.randomUUID() + "@Example.com";
        post("/auth/signup", null, signupBody(email, USER_PASSWORD))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email.toLowerCase()))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());

        // email is case-insensitive at login
        String token = login(email.toUpperCase(), USER_PASSWORD);
        get("/auth/me", token).andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email.toLowerCase()));
    }

    @Test
    void duplicateEmailIsRejected() throws Exception {
        String email = "dup-" + UUID.randomUUID() + "@example.com";
        post("/auth/signup", null, signupBody(email, USER_PASSWORD)).andExpect(status().isCreated());
        post("/auth/signup", null, signupBody(email, USER_PASSWORD)).andExpect(status().isConflict());
    }

    @Test
    void signupValidation() throws Exception {
        post("/auth/signup", null, signupBody("not-an-email", USER_PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("email"));
        post("/auth/signup", null, signupBody("a-" + UUID.randomUUID() + "@example.com", "short1"))
                .andExpect(status().isBadRequest());
        post("/auth/signup", null, signupBody("b-" + UUID.randomUUID() + "@example.com", "onlyletters"))
                .andExpect(status().isBadRequest());
        post("/auth/signup", null, Map.of("email", "c@example.com")).andExpect(status().isBadRequest());
    }

    @Test
    void malformedJsonIsBadRequest() throws Exception {
        post("/auth/login", null, "{not json").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void wrongCredentialsAreUnauthorizedWithSameMessage() throws Exception {
        String email = "login-" + UUID.randomUUID() + "@example.com";
        post("/auth/signup", null, signupBody(email, USER_PASSWORD)).andExpect(status().isCreated());

        post("/auth/login", null, Map.of("email", email, "password", "Wrong-pass1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
        post("/auth/login", null, Map.of("email", "nobody-" + UUID.randomUUID() + "@example.com", "password", "Wrong-pass1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void protectedEndpointsRequireAValidToken() throws Exception {
        get("/auth/me", null).andExpect(status().isUnauthorized());
        get("/bookings", null).andExpect(status().isUnauthorized());
        get("/auth/me", "garbage.token.value").andExpect(status().isUnauthorized());
        post("/bookings", null, Map.of("centreId", 1, "testId", 1, "appointmentTime", futureTime()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tamperedTokenIsRejected() throws Exception {
        String token = newUserToken();
        int i = token.lastIndexOf('.') + 5;                       // a char well inside the signature
        char swapped = token.charAt(i) == 'A' ? 'B' : 'A';
        String tampered = token.substring(0, i) + swapped + token.substring(i + 1);
        get("/auth/me", tampered).andExpect(status().isUnauthorized());
    }
}
