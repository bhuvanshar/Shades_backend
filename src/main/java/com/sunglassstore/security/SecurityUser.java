package com.sunglassstore.security;

import com.sunglassstore.entity.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Adapts our User entity to Spring Security's UserDetails interface.
 * Flattens roles into ROLE_ prefixed authorities and adds raw permission names.
 */
@Getter
public class SecurityUser implements UserDetails {

    private final Long userId;
    private final String email;
    private final String passwordHash;
    private final String name;
    private final boolean active;
    private final boolean accountLocked;
    private final Set<GrantedAuthority> authorities;

    public SecurityUser(User user) {
        this.userId = user.getUserId();
        this.email = user.getEmail();
        this.passwordHash = user.getPasswordHash();
        this.name = user.getName();
        this.active = Boolean.TRUE.equals(user.getIsActive());
        this.accountLocked = Boolean.TRUE.equals(user.getAccountLocked());
        this.authorities = buildAuthorities(user);
    }

    private Set<GrantedAuthority> buildAuthorities(User user) {
        Set<GrantedAuthority> auths = new HashSet<>();
        user.getRoles().forEach(role -> {
            auths.add(new SimpleGrantedAuthority("ROLE_" + role.getRoleName()));
            role.getPermissions().forEach(permission ->
                    auths.add(new SimpleGrantedAuthority(permission.getPermissionName()))
            );
        });
        return auths;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
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
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !accountLocked;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
