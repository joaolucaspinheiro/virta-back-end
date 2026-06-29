package br.com.virta.backend.service;

import br.com.virta.backend.dto.GoogleUserInfo;
import br.com.virta.backend.dto.LoginRequestDTO;
import br.com.virta.backend.dto.RegisterRequestDTO;
import br.com.virta.backend.exception.EmailAlreadyExistsException;
import br.com.virta.backend.exception.InvalidCredentialsException;
import br.com.virta.backend.model.Usuario;
import br.com.virta.backend.repository.UsuarioRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final BCryptPasswordEncoder passwordEncoder;

    public AuthService(UsuarioRepository usuarioRepository,
                       GoogleTokenVerifier googleTokenVerifier) {
        this.usuarioRepository = usuarioRepository;
        this.googleTokenVerifier = googleTokenVerifier;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    public void register(RegisterRequestDTO dto) {
        if (usuarioRepository.existsByEmail(dto.email())) {
            throw new EmailAlreadyExistsException(dto.email());
        }
        Usuario usuario = new Usuario(dto.nome(), dto.email(), passwordEncoder.encode(dto.senha()));
        usuarioRepository.save(usuario);
    }

    public Usuario login(LoginRequestDTO dto) {
        Usuario usuario = usuarioRepository.findByEmail(dto.email())
                .orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(dto.senha(), usuario.getSenhaCriptografada())) {
            throw new InvalidCredentialsException();
        }
        return usuario;
    }

    /**
     * Login/cadastro via Google. Valida o ID token e, se for o primeiro acesso,
     * cria o usuário com uma senha aleatória (a conta é controlada pelo Google).
     */
    public Usuario loginWithGoogle(String credential) {
        GoogleUserInfo info = googleTokenVerifier.verify(credential);
        return usuarioRepository.findByEmail(info.email())
                .orElseGet(() -> {
                    Usuario novo = new Usuario(
                            info.nome(),
                            info.email(),
                            passwordEncoder.encode(UUID.randomUUID().toString()));
                    novo.setFoto(info.foto());
                    return usuarioRepository.save(novo);
                });
    }
}
