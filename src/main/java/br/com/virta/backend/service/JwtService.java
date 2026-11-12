package br.com.virta.backend.service;

import br.com.virta.backend.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;

@Service
public class JwtService {

    private static final long EXPIRATION_MS = 24L * 60 * 60 * 1000; // 24 hours
    private static final long TOTP_CHALLENGE_MS = 5L * 60 * 1000;   // 5 minutes
    private static final String PURPOSE = "purpose";
    private static final String TOTP_CHALLENGE = "totp";

    @Value("${api.security.token.secret}")
    private String secret;


    public String generateToken(User user) {
        Date now = new Date();
        return Jwts.builder()
                .subject(user.getEmail())
                .claim("id", user.getId())
                .claim("name", user.getName())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + EXPIRATION_MS))
                .signWith(getSigningKey())
                .compact();
    }


    public String generateTotpChallenge(User user) {
        Date now = new Date();
        return Jwts.builder()
                .subject(user.getEmail())
                .claim(PURPOSE, TOTP_CHALLENGE)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + TOTP_CHALLENGE_MS))
                .signWith(getSigningKey())
                .compact();
    }


    public String validateToken(String token) {
        Claims claims = parse(token);
        if (TOTP_CHALLENGE.equals(claims.get(PURPOSE, String.class))) {
            throw new JwtException("A TOTP challenge is not an access token.");
        }
        return claims.getSubject();
    }


    public String validateTotpChallenge(String token) {
        Claims claims = parse(token);
        if (!TOTP_CHALLENGE.equals(claims.get(PURPOSE, String.class))) {
            throw new JwtException("Not a TOTP challenge.");
        }
        return claims.getSubject();
    }

    private Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload(); // throws if the signature is wrong or the token expired
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}