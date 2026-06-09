package br.com.virta.backend.service;

import br.com.virta.backend.dto.LoginRequestDTO;
import br.com.virta.backend.dto.RegisterRequestDTO;
import br.com.virta.backend.exception.EmailAlreadyExistsException;
import br.com.virta.backend.exception.InvalidCredentialsException;
import br.com.virta.backend.model.Usuario;
import br.com.virta.backend.repository.UsuarioRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    public AuthService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
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
}
