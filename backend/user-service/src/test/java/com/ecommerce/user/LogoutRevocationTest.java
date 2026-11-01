package com.ecommerce.user;

import com.ecommerce.user.model.User;
import com.ecommerce.user.repository.UserRepository;
import com.ecommerce.user.service.TokenRevocationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class LogoutRevocationTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired TokenRevocationService tokenRevocationService;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        jdbcTemplate.execute("DELETE FROM revoked_tokens WHERE 1=1");
    }

    private User createUser(String email, String password) {
        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setName("Test User");
        user.setRole("USER");
        user.setActive(true);
        return userRepository.save(user);
    }

    private String loginAndGetToken(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    @Test
    @DisplayName("POST /api/users/logout returns 204 with and without a token")
    void testLogoutAlwaysReturns204() throws Exception {
        // No auth header
        mockMvc.perform(post("/api/users/logout"))
                .andExpect(status().isNoContent());

        // Invalid token
        mockMvc.perform(post("/api/users/logout")
                        .header("Authorization", "Bearer not.a.real.token"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Platform tokens now include a jti claim")
    void testPlatformTokenContainsJti() throws Exception {
        createUser("jti.test@ecommerce.com", "StrongPassword123");
        String token = loginAndGetToken("jti.test@ecommerce.com", "StrongPassword123");

        Claims claims = Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(jwtSecret.getBytes()))
                .build()
                .parseClaimsJws(token)
                .getBody();

        assertThat(claims.getId()).isNotNull().isNotBlank();
    }

    @Test
    @DisplayName("Logout revokes the token's JTI; subsequent is-revoked checks return true")
    void testLogoutRevokesToken() throws Exception {
        createUser("logout.revoke@ecommerce.com", "StrongPassword123");
        String token = loginAndGetToken("logout.revoke@ecommerce.com", "StrongPassword123");

        Claims claims = Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(jwtSecret.getBytes()))
                .build()
                .parseClaimsJws(token)
                .getBody();
        String jti = claims.getId();
        assertThat(jti).isNotNull();
        assertThat(tokenRevocationService.isRevoked(jti)).isFalse();

        mockMvc.perform(post("/api/users/logout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        assertThat(tokenRevocationService.isRevoked(jti)).isTrue();
    }

    @Test
    @DisplayName("GET /api/users/internal/is-revoked returns false before logout and true after")
    void testInternalIsRevokedEndpoint() throws Exception {
        createUser("is.revoked@ecommerce.com", "StrongPassword123");
        String token = loginAndGetToken("is.revoked@ecommerce.com", "StrongPassword123");

        Claims claims = Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(jwtSecret.getBytes()))
                .build()
                .parseClaimsJws(token)
                .getBody();
        String jti = claims.getId();

        mockMvc.perform(get("/api/users/internal/is-revoked").param("jti", jti))
                .andExpect(status().isOk())
                .andExpect(content().string("false"));

        mockMvc.perform(post("/api/users/logout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/users/internal/is-revoked").param("jti", jti))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));
    }
}
