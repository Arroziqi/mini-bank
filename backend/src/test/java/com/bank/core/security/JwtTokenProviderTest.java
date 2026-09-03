package com.bank.core.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;
    private static final String SECRET = "testSecretKey123456789012345678901234567890";
    private static final long EXPIRATION_MS = 86400000;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider();
        setField("jwtSecret", SECRET);
        setField("jwtExpirationInMs", EXPIRATION_MS);
        jwtTokenProvider.init();
    }

    private void setField(String fieldName, Object value) {
        try {
            var field = JwtTokenProvider.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(jwtTokenProvider, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void generateToken_shouldReturnNonEmptyToken() {
        var authentication = createMockAuthentication("testuser");
        String token = jwtTokenProvider.generateToken(authentication);
        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    void getUsernameFromJWT_shouldExtractCorrectUsername() {
        var authentication = createMockAuthentication("testuser");
        String token = jwtTokenProvider.generateToken(authentication);
        String username = jwtTokenProvider.getUsernameFromJWT(token);
        assertEquals("testuser", username);
    }

    @Test
    void validateToken_shouldReturnTrueForValidToken() {
        var authentication = createMockAuthentication("testuser");
        String token = jwtTokenProvider.generateToken(authentication);
        assertTrue(jwtTokenProvider.validateToken(token));
    }

    @Test
    void validateToken_shouldReturnFalseForExpiredToken() {
        jwtTokenProvider = new JwtTokenProvider();
        setField("jwtSecret", SECRET);
        setField("jwtExpirationInMs", -1);
        jwtTokenProvider.init();

        var authentication = createMockAuthentication("testuser");
        String token = jwtTokenProvider.generateToken(authentication);
        assertFalse(jwtTokenProvider.validateToken(token));
    }

    @Test
    void validateToken_shouldReturnFalseForTamperedToken() {
        var authentication = createMockAuthentication("testuser");
        String token = jwtTokenProvider.generateToken(authentication);
        String tamperedToken = token.substring(0, token.length() - 5) + "XXXXX";
        assertFalse(jwtTokenProvider.validateToken(tamperedToken));
    }

    private org.springframework.security.authentication.UsernamePasswordAuthenticationToken createMockAuthentication(String username) {
        var userDetails = org.springframework.security.core.userdetails.User.builder()
                .username(username)
                .password("password")
                .roles("USER")
                .build();
        return new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities()
        );
    }
}
