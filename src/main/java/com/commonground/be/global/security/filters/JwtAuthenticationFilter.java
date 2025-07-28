package com.commonground.be.global.security.filters;


import static com.commonground.be.global.security.JwtProvider.AUTHORIZATION_HEADER;
import static jakarta.servlet.http.HttpServletResponse.SC_OK;

import com.commonground.be.domain.redis.RedisService;
import com.commonground.be.domain.user.dto.LoginRequestDto;
import com.commonground.be.domain.user.repository.UserAdapter;
import com.commonground.be.domain.user.utils.UserRole;
import com.commonground.be.global.response.HttpResponseDto;
import com.commonground.be.global.security.JwtProvider;
import com.commonground.be.global.security.UserDetailsImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Slf4j
public class JwtAuthenticationFilter extends UsernamePasswordAuthenticationFilter {

    private final JwtProvider jwtProvider;
    @Autowired
    private UserAdapter userAdapter;
    @Autowired
    private RedisService redisService;

    public JwtAuthenticationFilter(JwtProvider jwtProvider) {
        this.jwtProvider = jwtProvider;
        setFilterProcessesUrl("/v1/users/login");
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest req, HttpServletResponse res) throws AuthenticationException {
        try {
            return handleStandardLogin(req);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Authentication handleStandardLogin(HttpServletRequest req) throws IOException {
        LoginRequestDto requestDto = new ObjectMapper().readValue(req.getInputStream(), LoginRequestDto.class);
        return getAuthenticationManager().authenticate(
                new UsernamePasswordAuthenticationToken(
                        requestDto.getUsername(),
                        requestDto.getPassword(),
                        null
                )
        );
    }

    @Override
    protected void successfulAuthentication(HttpServletRequest req, HttpServletResponse res,
            FilterChain chain, Authentication authResult) throws IOException {
        log.info("인증 성공 및 JWT 생성");
        String username = ((UserDetailsImpl) authResult.getPrincipal()).getUsername();
        UserRole role = ((UserDetailsImpl) authResult.getPrincipal()).getUser().getUserRole();

        userAdapter.isDeleted(username);

        String accessToken = jwtProvider.createAccessToken(username, role);
        String refreshToken = jwtProvider.createRefreshToken(username, role);

        res.setHeader(AUTHORIZATION_HEADER, accessToken);
        // Redis에 RefreshToken 저장
        redisService.saveRefreshToken(username, refreshToken);

        res.setStatus(SC_OK);
        res.setCharacterEncoding("UTF-8");
        res.setContentType("application/json");

        String jsonResponse = new ObjectMapper().writeValueAsString(new HttpResponseDto(SC_OK, "로그인 성공", accessToken));
        res.getWriter().write(jsonResponse);
    }

    @Override
    protected void unsuccessfulAuthentication(HttpServletRequest req, HttpServletResponse res, AuthenticationException failed) throws IOException {
        res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        res.getWriter().print("{\"error\":\"Unauthorized\", \"message\":\"" + failed.getMessage() + "\"}");
    }
}
