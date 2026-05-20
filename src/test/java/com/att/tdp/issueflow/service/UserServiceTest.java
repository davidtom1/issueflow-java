package com.att.tdp.issueflow.service;

import com.att.tdp.issueflow.entity.User;
import com.att.tdp.issueflow.exception.ConflictException;
import com.att.tdp.issueflow.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Test
    void deleteUserThrowsConflictWhenUserIsReferenced() {
        User user = new User();
        user.setId(42L);

        when(userRepository.findById(42L)).thenReturn(Optional.of(user));
        doThrow(new DataIntegrityViolationException("referenced"))
                .when(userRepository).flush();

        assertThatThrownBy(() -> userService.deleteUser(42L))
                .isInstanceOf(ConflictException.class)
                .hasMessage("User is referenced and cannot be deleted");
    }
}
