package com.stockpilot.auth;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountUserRepository extends JpaRepository<AccountUser, UUID> {

    @Query("select u from AccountUser u where lower(u.email) = lower(:email)")
    Optional<AccountUser> findByEmailIgnoreCase(@Param("email") String email);

    @Query("select u.sessionVersion from AccountUser u where u.id = :id")
    Optional<Integer> findSessionVersionById(@Param("id") UUID id);
}
