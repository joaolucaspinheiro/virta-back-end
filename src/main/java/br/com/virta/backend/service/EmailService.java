package br.com.virta.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
@Service
public class EmailService {
    private final JavaMailSender mailSender;
    private final String from;
    private final String frontendUrl;


    public EmailService(JavaMailSender mailSender, @Value("${spring.mail.username}") String from, @Value("${app.frontend-url}") String frontendUrl){
        this.mailSender = mailSender;
        this.from = from;
        this.frontendUrl = frontendUrl;
}

public void sendPasswordReset(String to, String token){
        String link = frontendUrl + "/redefinir-senha/" + token;
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
    message.setSubject("Virta - Redefinição de senha");
    message.setText("Para definir uma nova senha, acesse o link abaixo (válido por 1 hora):\n\n"
            + link
            + "\n\nSe você não pediu isso, ignore este e-mail.");

    mailSender.send(message);
}
}
