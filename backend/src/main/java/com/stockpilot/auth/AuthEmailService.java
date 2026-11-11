package com.stockpilot.auth;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;
import java.nio.charset.StandardCharsets;

@Service
public class AuthEmailService {

    private final JavaMailSender mailSender;
    private final String publicWebUrl;
    private final String fromEmail;

    public AuthEmailService(JavaMailSender mailSender,
                            @Value("${app.auth.public-web-url}") String publicWebUrl,
                            @Value("${app.auth.from-email}") String fromEmail) {
        this.mailSender = mailSender;
        this.publicWebUrl = publicWebUrl.replaceAll("/+$", "");
        this.fromEmail = fromEmail;
    }

    public void sendVerification(String email, String name, String rawToken) {
        String url = tokenUrl("verify-email", rawToken);
        String html = buildEmail(
                "Potvrdite email adresu",
                "Zdravo, " + name + "!",
                "Kliknite na dugme ispod da biste potvrdili email adresu i aktivirali StockPilot nalog.",
                "Potvrdite email adresu",
                url,
                "Link važi <strong>24 sata</strong>. Ako niste otvorili nalog, zanemarite ovu poruku."
        );
        String text = "Zdravo, " + name + "!\n\n"
                + "Kliknite na link da biste potvrdili email adresu i aktivirali StockPilot nalog:\n"
                + url + "\n\n"
                + "Link važi 24 sata. Ako niste otvorili nalog, zanemarite ovu poruku.\n\n"
                + "StockPilot — Vaše zalihe, pod kontrolom.";
        send(email, "Potvrdite StockPilot nalog", text, html);
    }

    public void sendPasswordReset(String email, String rawToken) {
        String url = tokenUrl("reset-password", rawToken);
        String html = buildEmail(
                "Resetujte lozinku",
                "Zahtev za promenu lozinke",
                "Primili smo zahtev za reset lozinke za vaš StockPilot nalog. Kliknite na dugme ispod da biste postavili novu lozinku.",
                "Postavite novu lozinku",
                url,
                "Link važi <strong>30 minuta</strong>. Ako niste tražili reset, zanemarite ovu poruku."
        );
        String text = "Primili smo zahtev za reset lozinke za vaš StockPilot nalog.\n\n"
                + "Postavite novu lozinku ovde:\n" + url + "\n\n"
                + "Link važi 30 minuta. Ako niste tražili reset, zanemarite ovu poruku.\n\n"
                + "StockPilot — Vaše zalihe, pod kontrolom.";
        send(email, "Resetujte StockPilot lozinku", text, html);
    }

    private String tokenUrl(String path, String token) {
        String page = path.equals("verify-email") ? "verify-email" : "reset-password";
        return publicWebUrl + "/" + page + "?token="
                + UriUtils.encodeQueryParam(token, StandardCharsets.UTF_8);
    }

    private void send(String recipient, String subject, String text, String html) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(fromEmail);
            helper.setTo(recipient);
            helper.setSubject(subject);
            helper.setText(text, html);
            mailSender.send(message);
        } catch (MessagingException e) {
            throw new RuntimeException("Failed to send email to " + recipient, e);
        }
    }

    private String buildEmail(String title, String heading, String body, String buttonLabel, String buttonUrl, String footer) {
        String eyebrow = title.equals("Resetujte lozinku")
                ? "PROMENA LOZINKE" : "POTVRDA EMAIL ADRESE";
        return "<!DOCTYPE html>" +
            "<html lang=\"sr\"><head>" +
            "<meta charset=\"UTF-8\">" +
            "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">" +
            "<meta name=\"color-scheme\" content=\"light\">" +
            "<title>" + escapeHtml(title) + "</title></head>" +
            "<body style=\"margin:0;padding:0;background-color:#f6f3ff;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Arial,sans-serif;\">" +
            "<div style=\"display:none;max-height:0;overflow:hidden;opacity:0;color:transparent;\">"
                + escapeHtml(body) + "</div>" +
            "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" bgcolor=\"#f6f3ff\" style=\"width:100%;background-color:#f6f3ff;\">" +
            "<tr><td align=\"center\" style=\"padding:36px 14px;\">" +
            "<table role=\"presentation\" width=\"560\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"width:100%;max-width:560px;\">" +
            "<tr><td style=\"padding:0 4px 20px;\">" +
            "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"><tr>" +
            "<td valign=\"middle\" style=\"font-size:21px;font-weight:750;letter-spacing:-.7px;color:#211a35;\">" +
            "<span style=\"display:inline-block;vertical-align:middle;width:38px;height:38px;line-height:38px;text-align:center;border-radius:12px;background-color:#6842e8;color:#ffffff;font-size:14px;font-weight:800;letter-spacing:0;\">SP</span>" +
            "<span style=\"vertical-align:middle;padding-left:9px;\">stock<span style=\"color:#6842e8;\">pilot</span></span></td>" +
            "<td align=\"right\" valign=\"middle\" style=\"font-size:10px;font-weight:700;letter-spacing:1.1px;color:#746b87;\">VAŠ BEZBEDAN NALOG</td>" +
            "</tr></table></td></tr>" +
            "<tr><td bgcolor=\"#ffffff\" style=\"background-color:#ffffff;border:1px solid #e9e3f5;border-radius:18px;padding:38px 36px 34px;\">" +
            "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"margin-bottom:23px;\"><tr><td width=\"42\" height=\"42\" align=\"center\" valign=\"middle\" bgcolor=\"#f0eaff\" style=\"width:42px;height:42px;border-radius:14px;background-color:#f0eaff;color:#6842e8;font-size:21px;font-weight:700;\">&#10003;</td></tr></table>" +
            "<p style=\"margin:0 0 10px;font-size:11px;font-weight:750;letter-spacing:1.3px;color:#6842e8;\">" + eyebrow + "</p>" +
            "<h1 style=\"margin:0 0 13px;font-size:27px;font-weight:750;letter-spacing:-.6px;color:#211a35;line-height:1.25;\">" + escapeHtml(heading) + "</h1>" +
            "<p style=\"margin:0 0 25px;font-size:15px;color:#5e586b;line-height:1.7;\">" + escapeHtml(body) + "</p>" +
            "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"margin:0 0 25px;\">" +
            "<tr><td align=\"center\" bgcolor=\"#6842e8\" style=\"border-radius:10px;background-color:#6842e8;\">" +
            "<a href=\"" + escapeHtml(buttonUrl) + "\" style=\"display:inline-block;padding:15px 25px;font-size:14px;font-weight:700;color:#ffffff;text-decoration:none;border-radius:10px;\">" +
            escapeHtml(buttonLabel) + "</a></td></tr></table>" +
            "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" bgcolor=\"#f8f6fc\" style=\"width:100%;background-color:#f8f6fc;border:1px solid #eee9f5;border-radius:11px;margin:0 0 23px;\"><tr>" +
            "<td valign=\"top\" style=\"padding:14px 15px;color:#5e586b;font-size:13px;line-height:1.55;\">" +
            "<strong style=\"color:#302844;\">Važno</strong><br>" + footer + "</td></tr></table>" +
            "<p style=\"margin:0 0 8px;font-size:12px;color:#746b87;line-height:1.5;\">Ako dugme ne radi, otvorite ovaj link u pregledaču:</p>" +
            "<p style=\"margin:0;font-size:12px;line-height:1.6;word-break:break-all;\">" +
            "<a href=\"" + escapeHtml(buttonUrl) + "\" style=\"color:#6842e8;text-decoration:underline;\">" + escapeHtml(buttonUrl) + "</a></p>" +
            "</td></tr>" +
            "<tr><td align=\"center\" style=\"padding:21px 12px 0;font-size:12px;color:#817a91;line-height:1.7;\">" +
            "Ova automatska poruka poslata je u vezi sa vašim StockPilot nalogom.<br>" +
            "<span style=\"color:#a29bad;\">StockPilot &mdash; Va&#353;e zalihe, pod kontrolom.</span>" +
            "</td></tr></table></td></tr></table></body></html>";
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
