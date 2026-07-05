package br.com.virta.backend.service;

import br.com.virta.backend.dto.UserResponseDTO;
import br.com.virta.backend.exception.BusinessException;
import br.com.virta.backend.exception.InvalidCredentialsException;
import br.com.virta.backend.model.User;
import br.com.virta.backend.repository.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public UserResponseDTO getCurrentUser(String email) {
        return toDto(findByEmail(email));
    }

    @Transactional
    public UserResponseDTO updateProfile(String email, String name) {
        User user = findByEmail(email);
        user.setName(name);
        userRepository.save(user);
        return toDto(user);
    }

    /** Changes the authenticated user's password after verifying the current one. */
    @Transactional
    public void changePassword(String email, String currentPassword, String newPassword) {
        User user = findByEmail(email);
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BusinessException("Current password is incorrect.");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    private User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);
    }

    private UserResponseDTO toDto(User user) {
        return new UserResponseDTO(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getPhoto(),
                user.getCreatedAt());
    }
}
