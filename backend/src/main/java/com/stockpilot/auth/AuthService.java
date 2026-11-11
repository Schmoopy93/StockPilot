package com.stockpilot.auth;

import com.stockpilot.auth.AuthDtos.RegisterRequest;
import com.stockpilot.organization.Organization;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private final AccountUserRepository users;
    private final OrganizationRepository organizations;
    private final OrganizationMembershipRepository memberships;
    private final AuthTokenRepository tokens;
    private final PasswordEncoder passwordEncoder;
    private final AuthEmailService email;
    private final int verificationTokenHours;
    private final int resetTokenMinutes;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(AccountUserRepository users, OrganizationRepository organizations,
                       OrganizationMembershipRepository memberships, AuthTokenRepository tokens,
                       PasswordEncoder passwordEncoder, AuthEmailService email,
                       @Value("${app.auth.verification-token-hours:24}") int verificationTokenHours,
                       @Value("${app.auth.reset-token-minutes:30}") int resetTokenMinutes) {
        this.users = users;
        this.organizations = organizations;
        this.memberships = memberships;
        this.tokens = tokens;
        this.passwordEncoder = passwordEncoder;
        this.email = email;
        this.verificationTokenHours = verificationTokenHours;
        this.resetTokenMinutes = resetTokenMinutes;
    }

    @Transactional
    public void register(RegisterRequest request) {
        validateBcryptPassword(request.password());
        String normalizedEmail = normalizeEmail(request.email());
        if (users.findByEmailIgnoreCase(normalizedEmail).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered");
        }

        try {
            Organization organization = organizations.saveAndFlush(new Organization(request.organizationName().trim()));
            AccountUser user = users.saveAndFlush(new AccountUser(
                    normalizedEmail, request.name().trim(), passwordEncoder.encode(request.password())));
            memberships.save(new OrganizationMembership(
                    new OrganizationMembershipId(organization.getId(), user.getId()), AuthRole.OWNER));
            issueToken(user, AuthTokenPurpose.EMAIL_VERIFICATION,
                    verificationTokenHours, ChronoUnit.HOURS, email::sendVerification);
        } catch (DataIntegrityViolationException exception) {
            if (users.findByEmailIgnoreCase(normalizedEmail).isPresent()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered", exception);
            }
            throw exception;
        }
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        AuthToken token = consumeToken(rawToken, AuthTokenPurpose.EMAIL_VERIFICATION);
        AccountUser user = users.findById(token.getUserId())
                .orElseThrow(() -> new IllegalStateException("Verification token refers to a missing user"));
        user.verifyEmail(Instant.now());
    }

    @Transactional
    public void resendVerification(String emailAddress) {
        users.findByEmailIgnoreCase(normalizeEmail(emailAddress))
                .filter(user -> user.getEmailVerifiedAt() == null)
                .ifPresent(user -> issueToken(user, AuthTokenPurpose.EMAIL_VERIFICATION,
                        verificationTokenHours, ChronoUnit.HOURS, email::sendVerification));
    }

    @Transactional
    public void requestPasswordReset(String emailAddress) {
        users.findByEmailIgnoreCase(normalizeEmail(emailAddress))
                .filter(user -> user.getEmailVerifiedAt() != null)
                .ifPresent(user -> issueToken(user, AuthTokenPurpose.PASSWORD_RESET,
                        resetTokenMinutes, ChronoUnit.MINUTES,
                        (recipient, ignoredName, rawToken) -> email.sendPasswordReset(recipient, rawToken)));
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        validateBcryptPassword(newPassword);
        AuthToken token = consumeToken(rawToken, AuthTokenPurpose.PASSWORD_RESET);
        AccountUser user = users.findById(token.getUserId())
                .orElseThrow(() -> new IllegalStateException("Password token refers to a missing user"));
        user.changePassword(passwordEncoder.encode(newPassword));
    }

    private AuthToken consumeToken(String rawToken, AuthTokenPurpose purpose) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired token");
        }
        AuthToken token = tokens.findUsableForUpdate(hashToken(rawToken), purpose, Instant.now())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired token"));
        token.consume(Instant.now());
        return token;
    }

    private void issueToken(AccountUser user, AuthTokenPurpose purpose, int lifetime,
                            ChronoUnit unit, VerificationEmailSender sender) {
        tokens.deleteByUserIdAndPurpose(user.getId(), purpose);
        byte[] random = new byte[32];
        secureRandom.nextBytes(random);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        Instant now = Instant.now();
        tokens.save(new AuthToken(hashToken(rawToken), user.getId(), purpose, now, now.plus(lifetime, unit)));
        sender.send(user.getEmail(), user.getDisplayName(), rawToken);
    }

    private String normalizeEmail(String emailAddress) {
        return emailAddress.trim().toLowerCase(Locale.ROOT);
    }

    private void validateBcryptPassword(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Lozinka ne sme biti duža od 72 bajta u UTF-8 zapisu.");
        }
    }

    private String hashToken(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", exception);
        }
    }

    @FunctionalInterface
    private interface VerificationEmailSender {
        void send(String email, String name, String token);
    }
}
