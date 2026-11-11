package com.stockpilot.auth;

import com.stockpilot.auth.AuthDtos.EmailRequest;
import com.stockpilot.auth.AuthDtos.LoginRequest;
import com.stockpilot.auth.AuthDtos.MessageResponse;
import com.stockpilot.auth.AuthDtos.RegisterRequest;
import com.stockpilot.auth.AuthDtos.ResetPasswordRequest;
import com.stockpilot.auth.AuthDtos.TokenRequest;
import com.stockpilot.auth.AuthDtos.UserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.HtmlUtils;

@RestController
@RequestMapping("/api/auth")
@Validated
@Profile("!demo")
public class AuthController {

    private final AuthService auth;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final OrganizationRepository organizations;
    private final String publicApiUrl;

    public AuthController(AuthService auth, AuthenticationManager authenticationManager,
                          SecurityContextRepository securityContextRepository,
                          OrganizationRepository organizations,
                          @Value("${app.auth.public-api-url}") String publicApiUrl) {
        this.auth = auth;
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.organizations = organizations;
        this.publicApiUrl = publicApiUrl.replaceAll("/+$", "");
    }

    @PostMapping("/register")
    public MessageResponse register(@Valid @RequestBody RegisterRequest request) {
        auth.register(request);
        return new MessageResponse("Proverite email i potvrdite nalog pre prijave.");
    }

    @PostMapping("/login")
    public UserResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest,
                              HttpServletResponse servletResponse) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(
                            request.email().trim(), request.password()));
        } catch (DisabledException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Potvrdite email pre prijave.");
        } catch (BadCredentialsException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email ili lozinka nisu ispravni.");
        }

        servletRequest.getSession(true);
        servletRequest.changeSessionId();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, servletRequest, servletResponse);
        return toUserResponse((StockPilotPrincipal) authentication.getPrincipal());
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> currentUser(Authentication authentication) {
        if (authentication == null
                || !(authentication.getPrincipal() instanceof StockPilotPrincipal principal)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(toUserResponse(principal));
    }

    @PostMapping("/verify-email")
    public MessageResponse verifyEmail(@Valid @RequestBody TokenRequest request) {
        auth.verifyEmail(request.token());
        return new MessageResponse("Email je potvrđen. Sada možete da se prijavite.");
    }

    @PostMapping("/resend-verification")
    public MessageResponse resendVerification(@Valid @RequestBody EmailRequest request) {
        auth.resendVerification(request.email());
        return new MessageResponse(
                "Ako nalog postoji i email nije potvrđen, poslaćemo novo uputstvo za potvrdu.");
    }

    @PostMapping(value = "/verify-email/form", consumes = "application/x-www-form-urlencoded")
    public String verifyEmailForm(@RequestParam String token) {
        auth.verifyEmail(token);
        return "Email je potvrđen. Sada možete da se prijavite.";
    }

    @GetMapping(value = "/verify-email", produces = "text/html")
    public String verificationPage(@RequestParam String token, CsrfToken csrfToken) {
        String escapedToken = HtmlUtils.htmlEscape(token);
        return formPage("Potvrdite email", "Potvrda email adrese",
                publicApiUrl + "/auth/verify-email/form", csrfToken, """
                <input type="hidden" name="token" value="%s">
                <button type="submit">Potvrdi email adresu</button>
                """.formatted(escapedToken));
    }

    @PostMapping("/forgot-password")
    public MessageResponse forgotPassword(@Valid @RequestBody EmailRequest request) {
        auth.requestPasswordReset(request.email());
        return new MessageResponse("Ako nalog postoji, poslaćeno je uputstvo za reset lozinke.");
    }

    @PostMapping("/reset-password")
    public MessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        auth.resetPassword(request.token(), request.newPassword());
        return new MessageResponse("Lozinka je promenjena. Sada možete da se prijavite.");
    }

    @PostMapping(value = "/reset-password/form", consumes = "application/x-www-form-urlencoded")
    public String resetPasswordForm(@RequestParam String token, @RequestParam String newPassword) {
        if (newPassword.length() < 12 || newPassword.length() > 128) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Lozinka mora imati između 12 i 128 karaktera.");
        }
        auth.resetPassword(token, newPassword);
        return "Lozinka je promenjena. Sada možete da se prijavite.";
    }

    @GetMapping(value = "/reset-password", produces = "text/html")
    public String passwordResetPage(@RequestParam String token, CsrfToken csrfToken) {
        String escapedToken = HtmlUtils.htmlEscape(token);
        return formPage("Reset lozinke", "Postavite novu lozinku",
                publicApiUrl + "/auth/reset-password/form", csrfToken, """
                <input type="hidden" name="token" value="%s">
                <label>Nova lozinka<input name="newPassword" type="password" minlength="12" maxlength="128" required autocomplete="new-password"></label>
                <button type="submit">Sačuvaj lozinku</button>
                """.formatted(escapedToken));
    }

    private UserResponse toUserResponse(StockPilotPrincipal principal) {
        var organization = organizations.findById(principal.organizationId())
                .orElseThrow(() -> new IllegalStateException("Membership refers to a missing organization"));
        return new UserResponse(principal.userId(), principal.getUsername(), principal.displayName(),
                principal.organizationId(), organization.getName(), principal.role(), principal.emailVerifiedAt());
    }

    private String formPage(String title, String heading, String action, CsrfToken csrfToken, String fields) {
        return """
                <!doctype html><html lang="sr"><meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>%s</title>
                <body><main><h1>%s</h1><form method="post" action="%s">
                <input type="hidden" name="%s" value="%s">%s
                </form></main></body></html>
                """.formatted(HtmlUtils.htmlEscape(title), HtmlUtils.htmlEscape(heading),
                HtmlUtils.htmlEscape(action), csrfToken.getParameterName(),
                HtmlUtils.htmlEscape(csrfToken.getToken()), fields);
    }

}
