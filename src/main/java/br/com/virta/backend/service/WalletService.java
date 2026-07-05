package br.com.virta.backend.service;

import br.com.virta.backend.dto.AddMemberRequestDTO;
import br.com.virta.backend.dto.UpdateMemberRoleRequestDTO;
import br.com.virta.backend.dto.WalletMemberResponseDTO;
import br.com.virta.backend.dto.WalletRequestDTO;
import br.com.virta.backend.dto.WalletResponseDTO;
import br.com.virta.backend.exception.BusinessException;
import br.com.virta.backend.exception.ConflictException;
import br.com.virta.backend.exception.ResourceNotFoundException;
import br.com.virta.backend.model.User;
import br.com.virta.backend.model.Wallet;
import br.com.virta.backend.model.WalletMember;
import br.com.virta.backend.model.WalletRole;
import br.com.virta.backend.repository.UserRepository;
import br.com.virta.backend.repository.WalletMemberRepository;
import br.com.virta.backend.repository.WalletRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class WalletService {

    private final WalletRepository walletRepository;
    private final WalletMemberRepository memberRepository;
    private final UserRepository userRepository;

    public WalletService(WalletRepository walletRepository,
                         WalletMemberRepository memberRepository,
                         UserRepository userRepository) {
        this.walletRepository = walletRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<WalletResponseDTO> list(String email) {
        User user = currentUser(email);
        return memberRepository.findByUser(user).stream()
                .map(m -> toDto(m.getWallet(), m.getRole()))
                .toList();
    }

    @Transactional
    public WalletResponseDTO create(String email, WalletRequestDTO dto) {
        User user = currentUser(email);
        Wallet wallet = walletRepository.save(new Wallet(user, dto.name(), dto.description()));
        memberRepository.save(new WalletMember(wallet, user, WalletRole.OWNER));
        return toDto(wallet, WalletRole.OWNER);
    }

    @Transactional(readOnly = true)
    public WalletResponseDTO get(String email, Long walletId) {
        User user = currentUser(email);
        Wallet wallet = walletOrThrow(walletId);
        WalletMember membership = membershipOrThrow(wallet, user);
        return toDto(wallet, membership.getRole());
    }

    @Transactional
    public WalletResponseDTO update(String email, Long walletId, WalletRequestDTO dto) {
        User user = currentUser(email);
        Wallet wallet = walletOrThrow(walletId);
        WalletMember membership = membershipOrThrow(wallet, user);
        requireOwner(membership);
        wallet.setName(dto.name());
        wallet.setDescription(dto.description());
        walletRepository.save(wallet);
        return toDto(wallet, membership.getRole());
    }

    @Transactional
    public void delete(String email, Long walletId) {
        User user = currentUser(email);
        Wallet wallet = walletOrThrow(walletId);
        requireOwner(membershipOrThrow(wallet, user));
        memberRepository.deleteAll(memberRepository.findByWallet(wallet));
        walletRepository.delete(wallet);
    }

    @Transactional(readOnly = true)
    public List<WalletMemberResponseDTO> listMembers(String email, Long walletId) {
        User user = currentUser(email);
        Wallet wallet = walletOrThrow(walletId);
        membershipOrThrow(wallet, user); // any member can list
        return memberRepository.findByWallet(wallet).stream()
                .map(this::toMemberDto)
                .toList();
    }

    @Transactional
    public WalletMemberResponseDTO addMember(String email, Long walletId, AddMemberRequestDTO dto) {
        User user = currentUser(email);
        Wallet wallet = walletOrThrow(walletId);
        requireOwner(membershipOrThrow(wallet, user));
        if (dto.role() == WalletRole.OWNER) {
            throw new BusinessException("A wallet can only have one owner.");
        }
        User target = userRepository.findByEmail(dto.email())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + dto.email()));
        if (memberRepository.existsByWalletAndUser(wallet, target)) {
            throw new ConflictException("User is already a member of this wallet.");
        }
        WalletMember member = memberRepository.save(new WalletMember(wallet, target, dto.role()));
        return toMemberDto(member);
    }

    @Transactional
    public WalletMemberResponseDTO updateMemberRole(String email, Long walletId, Long userId,
                                                    UpdateMemberRoleRequestDTO dto) {
        User user = currentUser(email);
        Wallet wallet = walletOrThrow(walletId);
        requireOwner(membershipOrThrow(wallet, user));
        if (dto.role() == WalletRole.OWNER) {
            throw new BusinessException("A wallet can only have one owner.");
        }
        WalletMember member = memberOrThrow(wallet, userId);
        if (member.getRole() == WalletRole.OWNER) {
            throw new BusinessException("The owner's role cannot be changed.");
        }
        member.setRole(dto.role());
        memberRepository.save(member);
        return toMemberDto(member);
    }

    @Transactional
    public void removeMember(String email, Long walletId, Long userId) {
        User user = currentUser(email);
        Wallet wallet = walletOrThrow(walletId);
        requireOwner(membershipOrThrow(wallet, user));
        WalletMember member = memberOrThrow(wallet, userId);
        if (member.getRole() == WalletRole.OWNER) {
            throw new BusinessException("The owner cannot be removed.");
        }
        memberRepository.delete(member);
    }

    // ----- helpers -----

    private User currentUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));
    }

    private Wallet walletOrThrow(Long walletId) {
        return walletRepository.findById(walletId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found."));
    }

    private WalletMember membershipOrThrow(Wallet wallet, User user) {
        return memberRepository.findByWalletAndUser(wallet, user)
                .orElseThrow(() -> new AccessDeniedException("You are not a member of this wallet."));
    }

    private WalletMember memberOrThrow(Wallet wallet, Long userId) {
        return memberRepository.findByWalletAndUserId(wallet, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found."));
    }

    private void requireOwner(WalletMember membership) {
        if (membership.getRole() != WalletRole.OWNER) {
            throw new AccessDeniedException("Only the wallet owner can perform this action.");
        }
    }

    private WalletResponseDTO toDto(Wallet wallet, WalletRole role) {
        return new WalletResponseDTO(
                wallet.getId(),
                wallet.getName(),
                wallet.getDescription(),
                wallet.getCreatedAt(),
                role,
                wallet.getOwner().getName());
    }

    private WalletMemberResponseDTO toMemberDto(WalletMember member) {
        User u = member.getUser();
        return new WalletMemberResponseDTO(
                u.getId(), u.getName(), u.getEmail(), member.getRole(), member.getJoinedAt());
    }
}
