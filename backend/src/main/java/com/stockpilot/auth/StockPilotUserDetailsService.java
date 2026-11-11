package com.stockpilot.auth;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockPilotUserDetailsService implements UserDetailsService {

    private final AccountUserRepository users;
    private final OrganizationMembershipRepository memberships;

    public StockPilotUserDetailsService(AccountUserRepository users,
                                        OrganizationMembershipRepository memberships) {
        this.users = users;
        this.memberships = memberships;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AccountUser user = users.findByEmailIgnoreCase(username)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid email or password"));
        OrganizationMembership membership = memberships.findFirstByIdUserIdOrderByCreatedAtAsc(user.getId())
                .orElseThrow(() -> new UsernameNotFoundException("Account has no organization membership"));
        return new StockPilotPrincipal(user, membership);
    }
}
