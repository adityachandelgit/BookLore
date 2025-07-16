package com.adityachandel.booklore.config.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

public class KoreaderUserDetails implements UserDetails {

    private final String username;
    private final String password;
    @Getter
    private final Long bookLoreUserId; // Added field
    private final Collection<? extends GrantedAuthority> authorities;

    public KoreaderUserDetails(String username, String password, Long bookLoreUserId, Collection<? extends GrantedAuthority> authorities) {
        this.username = username;
        this.password = password;
        this.bookLoreUserId = bookLoreUserId;
        this.authorities = authorities;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
