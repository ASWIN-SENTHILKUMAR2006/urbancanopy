package com.urbancanopy.user.service;

import com.urbancanopy.common.security.AuthenticatedUser;
import com.urbancanopy.user.entity.User;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * The authenticated principal Spring Security holds, carrying just enough of the account to
 * answer every question the application asks about its caller.
 *
 * <p>A snapshot of the four fields the application needs, not the {@link User} entity itself.
 * Putting a JPA entity in the security context attaches it to a session that has long since
 * closed, so the first lazy field a service touches throws — and the alternative, keeping it
 * attached, means the principal drifts from the database silently.</p>
 *
 * <p>Note what is absent: the password hash is passed to the authentication manager and then
 * dropped by {@link #eraseCredentials()}, so a credential never lives in the session for the
 * duration of a login.</p>
 */
public class UrbanCanopyUserDetails implements UserDetails, CredentialsContainer {

    private final Long id;
    private final String email;
    private final String fullName;
    private final boolean admin;
    private final boolean enabled;
    private final boolean accountNonLocked;

    private String passwordHash;

    public UrbanCanopyUserDetails(User user) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.fullName = user.getFullName();
        this.admin = user.isAdmin();
        this.enabled = user.isEnabled();
        this.accountNonLocked = !user.isTemporarilyLocked();
        this.passwordHash = user.getPasswordHash();
    }

    /** The identity the service layer sees, unchanged since Phase 5. */
    public AuthenticatedUser toAuthenticatedUser() {
        return new AuthenticatedUser(id, email, fullName, admin);
    }

    public Long getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // ROLE_ADMIN implies ROLE_USER: an administrator is a planner with more rights, and
        // granting both means an admin can use their own plans without a second account.
        return admin
                ? List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("ROLE_USER"))
                : List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean isAccountNonLocked() {
        return accountNonLocked;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public void eraseCredentials() {
        this.passwordHash = null;
    }
}
