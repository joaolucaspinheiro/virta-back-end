package br.com.virta.backend.service;

import br.com.virta.backend.dto.GoogleUserInfo;
import br.com.virta.backend.dto.LoginRequestDTO;
import br.com.virta.backend.dto.RegisterRequestDTO;
import br.com.virta.backend.exception.EmailAlreadyExistsException;
import br.com.virta.backend.exception.InvalidCredentialsException;
import br.com.virta.backend.model.User;
import br.com.virta.backend.repository.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(UserRepository userRepository,
                       GoogleTokenVerifier googleTokenVerifier) {
        this.userRepository = userRepository;
        this.googleTokenVerifier = googleTokenVerifier;
    }

    public void register(RegisterRequestDTO dto) {
        if (userRepository.existsByEmail(dto.email())) {
            throw new EmailAlreadyExistsException(dto.email());
        }
        User user = new User(dto.name(), dto.email(), passwordEncoder.encode(dto.password()));
        userRepository.save(user);
    }

    public User login(LoginRequestDTO dto) {
        User user = userRepository.findByEmail(dto.email())
                .orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(dto.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        return user;
    }

    /**
     * Login/sign-up via Google. Validates the ID token and, on first access,
     * creates the user with a random password (the account is owned by Google).
     */
    public User loginWithGoogle(String credential) {
        GoogleUserInfo info = googleTokenVerifier.verify(credential);
        return userRepository.findByEmail(info.email())
                .orElseGet(() -> {
                    User created = new User(
                            info.name(),
                            info.email(),
                            passwordEncoder.encode(UUID.randomUUID().toString()));
                    created.setPhoto(info.photo());
                    return userRepository.save(created);
                });
    }
}
