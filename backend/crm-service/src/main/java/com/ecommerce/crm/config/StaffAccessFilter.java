package com.ecommerce.crm.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;

public class StaffAccessFilter extends OncePerRequestFilter {
    private static final Set<String> STAFF = Set.of("ADMIN", "ROLE_ADMIN", "MANAGER", "ROLE_MANAGER", "SUPER_ADMIN", "ROLE_SUPER_ADMIN");
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) { return !request.getRequestURI().startsWith("/api/crm/"); }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String role = request.getHeader("X-User-Role");
        if (role == null || !STAFF.contains(role.toUpperCase(Locale.ROOT))) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Manager or administrator access required.");
            return;
        }
        chain.doFilter(request, response);
    }
}
