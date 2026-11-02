package uzeduai.be.auth.service;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import uzeduai.be.security.jwt.JwtProperties;

import java.time.Duration;

@Service
public class RefreshCookieService {

    private final JwtProperties jwtProperties;

    public RefreshCookieService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    public void write(HttpServletResponse response, String rawToken) {
        ResponseCookie cookie = baseBuilder(rawToken)
                .maxAge(Duration.ofDays(jwtProperties.getRefreshTokenTtlDays()))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public void clear(HttpServletResponse response) {
        ResponseCookie cookie = baseBuilder("")
                .maxAge(Duration.ZERO)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private ResponseCookie.ResponseCookieBuilder baseBuilder(String value) {
        return ResponseCookie.from(jwtProperties.getRefreshCookieName(), value)
                .httpOnly(true)
                .secure(jwtProperties.isRefreshCookieSecure())
                .path(jwtProperties.getRefreshCookiePath())
                .sameSite(jwtProperties.getRefreshCookieSameSite());
    }
}
