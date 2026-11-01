package com.ecommerce.user;

import com.ecommerce.user.model.User;
import com.ecommerce.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
public class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Should save user and retrieve by email")
    void testSaveAndFindByEmail() {
        User user = new User();
        user.setEmail("integration@ecommerce.com");
        user.setPassword("secret123");
        user.setName("Test User");
        user.setRole("USER");
        user.setActive(true);

        User saved = userRepository.save(user);
        assertThat(saved.getId()).isNotNull();

        Optional<User> found = userRepository.findByEmail("integration@ecommerce.com");
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Test User");
        assertThat(found.get().getRole()).isEqualTo("USER");
        assertThat(found.get().isActive()).isTrue();
    }

    @Test
    @DisplayName("Should return empty optional when user does not exist")
    void testFindByEmailNotFound() {
        Optional<User> found = userRepository.findByEmail("nonexistent@ecommerce.com");
        assertThat(found).isEmpty();
    }
}
