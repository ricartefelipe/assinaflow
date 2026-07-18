package br.com.ricarte.assinaflow.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UserOwnershipFilter extends OncePerRequestFilter {

    private static final Pattern USER_PATH = Pattern.compile("^/api/v1/users/([^/]+)(/.*)?$");

    private final ObjectMapper objectMapper;

    public UserOwnershipFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String path = request.getRequestURI();
        Matcher matcher = USER_PATH.matcher(path);
        if (matcher.matches()) {
            String pathUserId = matcher.group(1);
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && auth.getPrincipal() != null) {
                String subject = auth.getName();
                if (!subject.equals(pathUserId)) {
                    ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                            HttpStatus.FORBIDDEN,
                            "Acesso negado a recurso de outro usuario."
                    );
                    pd.setTitle("Forbidden");
                    pd.setType(URI.create("about:blank"));
                    pd.setInstance(URI.create(path));
                    pd.setProperty("code", "FORBIDDEN");
                    pd.setProperty("timestamp", Instant.now().toString());
                    response.setStatus(HttpStatus.FORBIDDEN.value());
                    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                    objectMapper.writeValue(response.getOutputStream(), pd);
                    return;
                }
            }
        }
        filterChain.doFilter(request, response);
    }
}
