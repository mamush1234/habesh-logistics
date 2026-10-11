package com.habeshlist.security;

import com.habeshlist.entity.User;
import com.habeshlist.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "POST".equals(request.getMethod()) && List.of(
                "/api/auth/login", "/api/auth/register", "/api/auth/invitation")
                .contains(request.getRequestURI().substring(request.getContextPath().length()));
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest req,
                                    @NonNull HttpServletResponse res,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String header = req.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                Claims claims = jwtService.parse(token);
                UUID userId = UUID.fromString(claims.getSubject());
                Optional<User> userOpt = userRepository.findById(userId);
                if (userOpt.isPresent() && userOpt.get().isActive() && SecurityContextHolder.getContext().getAuthentication() == null) {
                    User user = userOpt.get();
                    Integer version = claims.get("credentialVersion", Integer.class);
                    if ((version == null ? 0 : version) != user.getCredentialVersion()) {
                        res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Session revoked. Sign in again.");
                        return;
                    }
                    AuthPrincipal principal = new AuthPrincipal(user.getId(), user.getEmail(), user.getRole());
                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(principal, null,
                                    List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(req));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (JwtException | IllegalArgumentException ex) {
                SecurityContextHolder.clearContext();
                res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired authentication");
                return;
            }
        }
        chain.doFilter(req, res);
    }
}
