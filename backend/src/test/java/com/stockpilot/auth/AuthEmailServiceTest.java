package com.stockpilot.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(MockitoExtension.class)
class AuthEmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    private AuthEmailService emailService;

    @BeforeEach
    void setUp() {
        emailService = new AuthEmailService(mailSender, "https://stockpilot.example/", "hello@stockpilot.example");
    }

    @Test
    void verificationEmailHasBrandedHtmlAndAccessiblePlainTextAlternative() throws Exception {
        when(mailSender.createMimeMessage())
                .thenReturn(new MimeMessage(Session.getInstance(new Properties())));

        emailService.sendVerification("marko@example.test", "A&B <Team>", "safe-token");

        ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(messageCaptor.capture());
        MimeMessage message = messageCaptor.getValue();
        List<String> textParts = new ArrayList<>();
        collectTextParts(message, textParts);
        String plainText = textParts.stream().filter(part -> part.startsWith("Zdravo,")).findFirst().orElse("");
        String html = textParts.stream().filter(part -> part.contains("<!DOCTYPE html>")).findFirst().orElse("");

        assertThat(message.getSubject()).isEqualTo("Potvrdite StockPilot nalog");
        assertThat(message.getRecipients(MimeMessage.RecipientType.TO)[0].toString())
                .isEqualTo("marko@example.test");
        assertThat(plainText)
                .contains("https://stockpilot.example/verify-email?token=safe-token")
                .contains("Link važi 24 sata");
        assertThat(html)
                .contains("POTVRDA EMAIL ADRESE")
                .contains("background-color:#6842e8")
                .contains("Zdravo, A&amp;B &lt;Team&gt;!")
                .contains("https://stockpilot.example/verify-email?token=safe-token")
                .doesNotContain("Zdravo, A&B <Team>!");
    }

    private static void collectTextParts(Part part, List<String> textParts)
            throws MessagingException, IOException {
        Object content = part.getContent();
        if (content instanceof String text) {
            textParts.add(text);
        } else if (content instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                collectTextParts(multipart.getBodyPart(i), textParts);
            }
        }
    }
}
