package com.stockpilot.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stockpilot.auth.AuthDtos.RegisterRequest;
import com.stockpilot.organization.Organization;
import java.lang.reflect.Field;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AccountUserRepository users;
    @Mock
    private OrganizationRepository organizations;
    @Mock
    private OrganizationMembershipRepository memberships;
    @Mock
    private AuthTokenRepository tokens;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthEmailService email;

    private AuthService auth;

    @BeforeEach
    void setUp() {
        auth = new AuthService(users, organizations, memberships, tokens, passwordEncoder, email, 24, 30);
    }

    @Test
    void registrationNormalizesEmailHashesPasswordAndPersistsOnlyHashedVerificationToken() throws Exception {
        UUID organizationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Organization organization = new Organization("Bean & Bloom");
        AccountUser user = new AccountUser("marko@example.test", "Marko", "encoded-password");
        ReflectionTestUtils.setField(organization, "id", organizationId);
        ReflectionTestUtils.setField(user, "id", userId);

        when(users.findByEmailIgnoreCase("marko@example.test")).thenReturn(java.util.Optional.empty());
        when(organizations.saveAndFlush(any(Organization.class))).thenReturn(organization);
        when(passwordEncoder.encode("VerySecurePassword123!")).thenReturn("encoded-password");
        when(users.saveAndFlush(any(AccountUser.class))).thenReturn(user);

        auth.register(new RegisterRequest(
                "  Marko@Example.Test ", " Marko ", " Bean & Bloom ", "VerySecurePassword123!"));

        ArgumentCaptor<OrganizationMembership> membershipCaptor =
                ArgumentCaptor.forClass(OrganizationMembership.class);
        verify(memberships).save(membershipCaptor.capture());
        assertThat(membershipCaptor.getValue().getId().getOrganizationId()).isEqualTo(organizationId);
        assertThat(membershipCaptor.getValue().getId().getUserId()).isEqualTo(userId);
        assertThat(membershipCaptor.getValue().getRole()).isEqualTo(AuthRole.OWNER);

        ArgumentCaptor<AuthToken> tokenCaptor = ArgumentCaptor.forClass(AuthToken.class);
        verify(tokens).save(tokenCaptor.capture());
        Field hashField = AuthToken.class.getDeclaredField("tokenHash");
        hashField.setAccessible(true);
        String persistedHash = (String) hashField.get(tokenCaptor.getValue());
        ArgumentCaptor<String> rawTokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(email).sendVerification(
                org.mockito.ArgumentMatchers.eq("marko@example.test"),
                org.mockito.ArgumentMatchers.eq("Marko"),
                rawTokenCaptor.capture());

        assertThat(rawTokenCaptor.getValue()).hasSize(43);
        assertThat(persistedHash).hasSize(64).isNotEqualTo(rawTokenCaptor.getValue());
        assertThat(user.getPasswordHash()).isEqualTo("encoded-password");
        verify(passwordEncoder).encode("VerySecurePassword123!");
        verify(tokens).deleteByUserIdAndPurpose(userId, AuthTokenPurpose.EMAIL_VERIFICATION);
    }

    @Test
    void rejectsBcryptPasswordsExceedingItsByteLimit() {
        String multibytePassword = "ž".repeat(37);

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        auth.register(new RegisterRequest(
                                "marko@example.test", "Marko", "Bean & Bloom", multibytePassword)))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    @Test
    void resendsVerificationForUnverifiedAccountWithoutChangingEmailForVerifiedOrUnknownAccounts() {
        AccountUser user = new AccountUser("marko@example.test", "Marko", "encoded-password");
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        when(users.findByEmailIgnoreCase("marko@example.test"))
                .thenReturn(java.util.Optional.of(user));

        auth.resendVerification("  Marko@Example.Test ");

        verify(tokens).deleteByUserIdAndPurpose(user.getId(), AuthTokenPurpose.EMAIL_VERIFICATION);
        verify(email).sendVerification(eq("marko@example.test"), eq("Marko"), any());

        org.mockito.Mockito.clearInvocations(tokens, email);
        when(users.findByEmailIgnoreCase("missing@example.test"))
                .thenReturn(java.util.Optional.empty());

        auth.resendVerification("missing@example.test");

        org.mockito.Mockito.verifyNoInteractions(tokens, email);
    }

    @Test
    void doesNotResendVerificationForAlreadyVerifiedAccount() {
        AccountUser user = new AccountUser("marko@example.test", "Marko", "encoded-password");
        user.verifyEmail(java.time.Instant.now());
        when(users.findByEmailIgnoreCase("marko@example.test"))
                .thenReturn(java.util.Optional.of(user));

        auth.resendVerification("marko@example.test");

        org.mockito.Mockito.verifyNoInteractions(tokens, email);
    }

    @Test
    void changingPasswordIncrementsSessionVersionToRevokeExistingSessions() {
        AccountUser user = new AccountUser("marko@example.test", "Marko", "old-hash");

        user.changePassword("new-hash");

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        assertThat(user.getSessionVersion()).isEqualTo(1);
    }
}
