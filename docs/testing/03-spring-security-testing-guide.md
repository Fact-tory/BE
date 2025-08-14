# Spring Security 테스트 완전 가이드

## 📋 목차
1. [Spring Security 테스트 개요](#1-spring-security-테스트-개요)
2. [인증 테스트](#2-인증-테스트)
3. [인가(권한) 테스트](#3-인가권한-테스트)
4. [JWT 토큰 테스트](#4-jwt-토큰-테스트)
5. [MockMvc와 Security 통합](#5-mockmvc와-security-통합)
6. [실제 프로젝트 테스트 예제](#6-실제-프로젝트-테스트-예제)
7. [문제 해결 가이드](#7-문제-해결-가이드)

---

## 1. Spring Security 테스트 개요

### 🛡️ Spring Security 테스트의 중요성

Spring Security는 애플리케이션의 핵심 보안 기능을 담당하므로, 철저한 테스트가 필수입니다.

#### **테스트해야 할 보안 요소들**
- ✅ **인증(Authentication)**: 사용자가 누구인지 확인
- ✅ **인가(Authorization)**: 사용자가 무엇을 할 수 있는지 확인
- ✅ **세션 관리**: 세션 생성, 만료, 무효화
- ✅ **CSRF 보호**: Cross-Site Request Forgery 방어
- ✅ **보안 헤더**: XSS, Clickjacking 등 방어
- ✅ **로그인/로그아웃**: 인증 플로우
- ✅ **접근 제어**: URL 기반 권한 검사

### 🏗️ Spring Security 테스트 계층

```
🔺 E2E Security Tests
  - 전체 보안 플로우 테스트
  - 실제 인증/권한 확인

🔺🔺 Integration Security Tests  
  - 컨트롤러 + Security 통합
  - MockMvc + @WithMockUser

🔺🔺🔺 Unit Security Tests
  - Security 구성 요소 단위 테스트
  - Filter, Provider, Service 등
```

### 📦 필요한 의존성

```gradle
dependencies {
    // Spring Security 테스트
    testImplementation 'org.springframework.security:spring-security-test'
    
    // Spring Boot 테스트
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    
    // MockMvc
    testImplementation 'org.springframework.boot:spring-boot-starter-web'
    
    // JWT 테스트 (필요시)
    testImplementation 'io.jsonwebtoken:jjwt-api:0.11.5'
    testRuntimeOnly 'io.jsonwebtoken:jjwt-impl:0.11.5'
    testRuntimeOnly 'io.jsonwebtoken:jjwt-jackson:0.11.5'
}
```

---

## 2. 인증 테스트

### 🔐 기본 인증 테스트

#### **@WithMockUser - 가짜 사용자로 테스트**
```java
@WebMvcTest(UserController.class)
@Import(TestSecurityConfig.class)
class UserControllerAuthTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @MockitoBean
    private UserService userService;
    
    @Test
    @WithMockUser(username = "john", roles = {"USER"})
    @DisplayName("인증된 사용자는 프로필을 조회할 수 있다")
    void getProfile_WithAuthenticatedUser_ShouldReturnProfile() throws Exception {
        // Given
        User user = new User("john", "john@email.com");
        when(userService.getCurrentUser()).thenReturn(user);
        
        // When & Then
        mockMvc.perform(get("/api/users/profile"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.username").value("john"));
    }
    
    @Test
    @DisplayName("인증되지 않은 사용자는 프로필 조회가 거부된다")
    void getProfile_WithoutAuthentication_ShouldReturn401() throws Exception {
        mockMvc.perform(get("/api/users/profile"))
               .andExpected(status().isUnauthorized());
    }
    
    @Test
    @WithAnonymousUser
    @DisplayName("익명 사용자는 공개 페이지에만 접근할 수 있다")
    void getPublicPage_WithAnonymousUser_ShouldAllowAccess() throws Exception {
        mockMvc.perform(get("/api/public/info"))
               .andExpect(status().isOk());
    }
}
```

#### **커스텀 인증 어노테이션**
```java
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@WithSecurityContext(factory = WithMockCustomUserSecurityContextFactory.class)
public @interface WithMockCustomUser {
    String username() default "testuser";
    String email() default "test@email.com";
    String[] roles() default {"USER"};
    long userId() default 1L;
}

public class WithMockCustomUserSecurityContextFactory 
    implements WithSecurityContextFactory<WithMockCustomUser> {
    
    @Override
    public SecurityContext createSecurityContext(WithMockCustomUser annotation) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        
        // 커스텀 UserDetails 생성
        CustomUserDetails userDetails = new CustomUserDetails(
            annotation.userId(),
            annotation.username(),
            annotation.email(),
            Arrays.stream(annotation.roles())
                  .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                  .collect(Collectors.toList())
        );
        
        Authentication auth = new UsernamePasswordAuthenticationToken(
            userDetails, null, userDetails.getAuthorities());
        
        context.setAuthentication(auth);
        return context;
    }
}

// 사용 예시
@Test
@WithMockCustomUser(username = "admin", email = "admin@company.com", 
                   roles = {"ADMIN", "USER"}, userId = 100L)
void adminFunction_WithAdminUser_ShouldWork() throws Exception {
    mockMvc.perform(get("/api/admin/users"))
           .andExpect(status().isOk());
}
```

### 🎯 실제 인증 프로세스 테스트

#### **로그인 테스트**
```java
@SpringBootTest
@AutoConfigureMockMvc
class LoginIntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @MockitoBean
    private UserDetailsService userDetailsService;
    
    @MockitoBean
    private PasswordEncoder passwordEncoder;
    
    @Test
    @DisplayName("올바른 사용자명과 비밀번호로 로그인 성공")
    void login_WithValidCredentials_ShouldAuthenticateUser() throws Exception {
        // Given
        String username = "john";
        String password = "password123";
        String encodedPassword = "$2a$10$encoded.password.hash";
        
        UserDetails userDetails = User.builder()
            .username(username)
            .password(encodedPassword)
            .authorities("ROLE_USER")
            .build();
        
        when(userDetailsService.loadUserByUsername(username)).thenReturn(userDetails);
        when(passwordEncoder.matches(password, encodedPassword)).thenReturn(true);
        
        // When & Then
        mockMvc.perform(formLogin("/login")
                       .user(username)
                       .password(password))
               .andExpect(authenticated().withUsername(username))
               .andExpect(authenticated().withRoles("USER"));
    }
    
    @Test
    @DisplayName("잘못된 비밀번호로 로그인 실패")
    void login_WithInvalidPassword_ShouldFail() throws Exception {
        // Given
        String username = "john";
        String wrongPassword = "wrongpassword";
        String encodedPassword = "$2a$10$encoded.password.hash";
        
        UserDetails userDetails = User.builder()
            .username(username)
            .password(encodedPassword)
            .authorities("ROLE_USER")
            .build();
        
        when(userDetailsService.loadUserByUsername(username)).thenReturn(userDetails);
        when(passwordEncoder.matches(wrongPassword, encodedPassword)).thenReturn(false);
        
        // When & Then
        mockMvc.perform(formLogin("/login")
                       .user(username)
                       .password(wrongPassword))
               .andExpect(unauthenticated());
    }
}
```

#### **로그아웃 테스트**
```java
@Test
@WithMockUser
@DisplayName("로그아웃 시 세션이 무효화된다")
void logout_ShouldInvalidateSession() throws Exception {
    // When & Then
    mockMvc.perform(logout("/logout"))
           .andExpect(unauthenticated())
           .andExpect(redirectedUrl("/login?logout"));
    
    // 로그아웃 후 보호된 리소스 접근 시도
    mockMvc.perform(get("/api/users/profile"))
           .andExpect(status().isUnauthorized());
}
```

---

## 3. 인가(권한) 테스트

### 🏛️ 역할 기반 접근 제어 테스트

#### **다중 역할 테스트**
```java
@WebMvcTest(AdminController.class)
@Import(TestSecurityConfig.class)
class AdminControllerAuthorizationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @MockitoBean
    private AdminService adminService;
    
    @Test
    @WithMockUser(roles = {"ADMIN"})
    @DisplayName("ADMIN 역할은 관리자 페이지에 접근할 수 있다")
    void adminPage_WithAdminRole_ShouldAllowAccess() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard"))
               .andExpect(status().isOk());
    }
    
    @Test
    @WithMockUser(roles = {"USER"})
    @DisplayName("USER 역할은 관리자 페이지에 접근할 수 없다")
    void adminPage_WithUserRole_ShouldDenyAccess() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard"))
               .andExpect(status().isForbidden());
    }
    
    @Test
    @WithMockUser(roles = {"ADMIN", "SUPER_ADMIN"})
    @DisplayName("SUPER_ADMIN 역할은 모든 관리 기능에 접근할 수 있다")
    void superAdminFunction_WithSuperAdminRole_ShouldAllowAccess() throws Exception {
        mockMvc.perform(delete("/api/admin/users/999"))
               .andExpect(status().isOk());
    }
    
    @Test
    @WithMockUser(roles = {"ADMIN"})  // SUPER_ADMIN 아님
    @DisplayName("일반 ADMIN은 사용자 삭제 권한이 없다")
    void deleteUser_WithAdminRole_ShouldDenyAccess() throws Exception {
        mockMvc.perform(delete("/api/admin/users/999"))
               .andExpect(status().isForbidden());
    }
}
```

#### **메서드 레벨 보안 테스트**
```java
@TestMethodSecurity  // Method Security 활성화
@ExtendWith(MockitoExtension.class)
class UserServiceSecurityTest {
    
    @MockitoBean
    private UserRepository userRepository;
    
    @Autowired
    private UserService userService;
    
    @Test
    @WithMockUser(username = "john", roles = {"USER"})
    @DisplayName("사용자는 자신의 정보만 수정할 수 있다")
    void updateUser_OwnerAccess_ShouldAllowUpdate() {
        // Given
        Long userId = 1L;
        UpdateUserRequest request = new UpdateUserRequest("새로운 이름");
        
        // When & Then - 예외가 발생하지 않아야 함
        assertThatCode(() -> userService.updateUser(userId, request))
            .doesNotThrowAnyException();
    }
    
    @Test
    @WithMockUser(username = "jane", roles = {"USER"})
    @DisplayName("사용자는 다른 사용자의 정보를 수정할 수 없다")
    void updateUser_NonOwnerAccess_ShouldDenyAccess() {
        // Given
        Long otherUserId = 999L;  // jane이 아닌 다른 사용자
        UpdateUserRequest request = new UpdateUserRequest("새로운 이름");
        
        // When & Then
        assertThatThrownBy(() -> userService.updateUser(otherUserId, request))
            .isInstanceOf(AccessDeniedException.class);
    }
    
    @Test
    @WithMockUser(roles = {"ADMIN"})
    @DisplayName("관리자는 모든 사용자 정보를 수정할 수 있다")
    void updateUser_AdminAccess_ShouldAllowAnyUpdate() {
        // Given
        Long anyUserId = 999L;
        UpdateUserRequest request = new UpdateUserRequest("새로운 이름");
        
        // When & Then
        assertThatCode(() -> userService.updateUser(anyUserId, request))
            .doesNotThrowAnyException();
    }
}

// UserService에서 보안 어노테이션 사용
@Service
public class UserService {
    
    @PreAuthorize("hasRole('ADMIN') or authentication.name == @userRepository.findById(#userId).username")
    public void updateUser(Long userId, UpdateUserRequest request) {
        // 구현
    }
    
    @PreAuthorize("hasRole('ADMIN')")
    public List<User> getAllUsers() {
        // 관리자만 접근 가능
    }
    
    @PostAuthorize("hasRole('ADMIN') or returnObject.username == authentication.name")
    public User getUserById(Long userId) {
        // 결과를 확인한 후 권한 검사
    }
}
```

### 🔒 리소스 기반 권한 테스트

#### **소유자 확인 테스트**
```java
@ExtendWith(MockitoExtension.class)
class PostServiceSecurityTest {
    
    @Mock
    private PostRepository postRepository;
    
    @InjectMocks
    private PostService postService;
    
    @Test
    @WithMockCustomUser(userId = 1L, username = "john")
    @DisplayName("포스트 작성자는 자신의 포스트를 수정할 수 있다")
    void updatePost_ByOwner_ShouldAllowUpdate() {
        // Given
        Long postId = 100L;
        Post post = new Post();
        post.setId(postId);
        post.setAuthorId(1L);  // 현재 사용자와 동일
        
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        
        UpdatePostRequest request = new UpdatePostRequest("새로운 제목", "새로운 내용");
        
        // When & Then
        assertThatCode(() -> postService.updatePost(postId, request))
            .doesNotThrowAnyException();
    }
    
    @Test
    @WithMockCustomUser(userId = 2L, username = "jane")
    @DisplayName("다른 사용자는 포스트를 수정할 수 없다")
    void updatePost_ByNonOwner_ShouldDenyAccess() {
        // Given
        Long postId = 100L;
        Post post = new Post();
        post.setId(postId);
        post.setAuthorId(1L);  // 다른 사용자 소유
        
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        
        UpdatePostRequest request = new UpdatePostRequest("새로운 제목", "새로운 내용");
        
        // When & Then
        assertThatThrownBy(() -> postService.updatePost(postId, request))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("포스트를 수정할 권한이 없습니다");
    }
}
```

---

## 4. JWT 토큰 테스트

### 🎫 JWT 토큰 생성 및 검증 테스트

#### **JwtProvider 단위 테스트**
```java
@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class JwtProviderTest {
    
    private JwtProvider jwtProvider;
    
    @BeforeEach
    void setUp() {
        jwtProvider = new JwtProvider();
        // 테스트용 설정값 주입
        ReflectionTestUtils.setField(jwtProvider, "secretKey", "test-secret-key-for-jwt-token-generation-at-least-32-chars");
        ReflectionTestUtils.setField(jwtProvider, "accessTokenExpiration", 3600000L);
        ReflectionTestUtils.setField(jwtProvider, "refreshTokenExpiration", 86400000L);
        
        jwtProvider.init();  // 키 초기화
    }
    
    @Test
    @DisplayName("유효한 사용자 정보로 Access Token을 생성할 수 있다")
    void createAccessToken_WithValidUserInfo_ShouldGenerateToken() {
        // Given
        String username = "john";
        UserRole userRole = UserRole.USER;
        
        // When
        String token = jwtProvider.createAccessToken(username, userRole);
        
        // Then
        assertThat(token).isNotNull();
        assertThat(token).startsWith("Bearer ");
        
        // JWT 구조 검증
        String actualToken = token.substring("Bearer ".length());
        String[] parts = actualToken.split("\\.");
        assertThat(parts).hasSize(3);  // Header.Payload.Signature
    }
    
    @Test
    @DisplayName("생성된 토큰에서 사용자 정보를 추출할 수 있다")
    void extractUserInfo_FromGeneratedToken_ShouldReturnCorrectInfo() {
        // Given
        String username = "john";
        UserRole userRole = UserRole.ADMIN;
        String token = jwtProvider.createAccessToken(username, userRole);
        
        // When
        String extractedUsername = jwtProvider.getUsernameFromToken(token);
        String extractedRole = jwtProvider.getClaimFromToken(token, "role");
        
        // Then
        assertThat(extractedUsername).isEqualTo(username);
        assertThat(extractedRole).isEqualTo(userRole.name());
    }
    
    @Test
    @DisplayName("유효한 토큰은 검증을 통과한다")
    void validateToken_WithValidToken_ShouldReturnTrue() {
        // Given
        String token = jwtProvider.createAccessToken("john", UserRole.USER);
        
        // When
        boolean isValid = jwtProvider.validateAccessToken(token);
        
        // Then
        assertThat(isValid).isTrue();
    }
    
    @Test
    @DisplayName("만료된 토큰은 검증에 실패한다")
    void validateToken_WithExpiredToken_ShouldReturnFalse() {
        // Given - 과거 시간으로 만료된 토큰 생성
        ReflectionTestUtils.setField(jwtProvider, "accessTokenExpiration", -1L);
        jwtProvider.init();
        
        String expiredToken = jwtProvider.createAccessToken("john", UserRole.USER);
        
        // 짧은 대기 후 검증
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // When
        boolean isValid = jwtProvider.validateAccessToken(expiredToken);
        
        // Then
        assertThat(isValid).isFalse();
    }
    
    @Test
    @DisplayName("잘못된 서명의 토큰은 검증에 실패한다")
    void validateToken_WithInvalidSignature_ShouldReturnFalse() {
        // Given
        String validToken = jwtProvider.createAccessToken("john", UserRole.USER);
        String tokenWithInvalidSignature = validToken.substring(0, validToken.length() - 10) + "invalidsig";
        
        // When
        boolean isValid = jwtProvider.validateAccessToken(tokenWithInvalidSignature);
        
        // Then
        assertThat(isValid).isFalse();
    }
    
    @Test
    @DisplayName("세션 ID가 포함된 토큰을 생성할 수 있다")
    void createAccessTokenWithSession_ShouldIncludeSessionId() {
        // Given
        String username = "john";
        UserRole userRole = UserRole.USER;
        String sessionId = "session-123";
        
        // When
        String token = jwtProvider.createAccessTokenWithSession(username, userRole, sessionId);
        
        // Then
        assertThat(token).isNotNull();
        
        String extractedSessionId = jwtProvider.getClaimFromToken(token, "sessionId");
        assertThat(extractedSessionId).isEqualTo(sessionId);
    }
}
```

#### **JWT 필터 테스트**
```java
@ExtendWith(MockitoExtension.class)
class JwtAuthorizationFilterTest {
    
    @InjectMocks
    private JwtAuthorizationFilter jwtAuthorizationFilter;
    
    @Mock
    private JwtProvider jwtProvider;
    
    @Mock
    private UserDetailsService userDetailsService;
    
    @Mock
    private FilterChain filterChain;
    
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    
    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        SecurityContextHolder.clearContext();
    }
    
    @Test
    @DisplayName("유효한 JWT 토큰으로 인증 성공")
    void doFilterInternal_WithValidJWT_ShouldSetAuthentication() throws Exception {
        // Given
        String token = "Bearer valid.jwt.token";
        String username = "john";
        
        request.addHeader("Authorization", token);
        request.setRequestURI("/api/users/profile");
        
        UserDetails userDetails = User.builder()
            .username(username)
            .password("password")
            .authorities("ROLE_USER")
            .build();
        
        when(jwtProvider.getAccessTokenFromHeader(request)).thenReturn("valid.jwt.token");
        when(jwtProvider.validateAccessToken("valid.jwt.token")).thenReturn(true);
        when(jwtProvider.getUsernameFromToken("valid.jwt.token")).thenReturn(username);
        when(userDetailsService.loadUserByUsername(username)).thenReturn(userDetails);
        
        // When
        jwtAuthorizationFilter.doFilterInternal(request, response, filterChain);
        
        // Then
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getName()).isEqualTo(username);
        assertThat(auth.getAuthorities()).hasSize(1);
        
        verify(filterChain).doFilter(request, response);
    }
    
    @Test
    @DisplayName("JWT 토큰 없이 공개 경로 접근 시 통과")
    void doFilterInternal_PublicPath_ShouldPassWithoutAuthentication() throws Exception {
        // Given
        request.setRequestURI("/api/v1/auth/social/google/url");
        
        // When
        jwtAuthorizationFilter.doFilterInternal(request, response, filterChain);
        
        // Then
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNull();
        
        verify(filterChain).doFilter(request, response);
        verify(jwtProvider, never()).getAccessTokenFromHeader(any());
    }
    
    @Test
    @DisplayName("유효하지 않은 JWT 토큰으로 인증 실패")
    void doFilterInternal_WithInvalidJWT_ShouldReturn401() throws Exception {
        // Given
        String invalidToken = "Bearer invalid.jwt.token";
        
        request.addHeader("Authorization", invalidToken);
        request.setRequestURI("/api/users/profile");
        
        when(jwtProvider.getAccessTokenFromHeader(request)).thenReturn("invalid.jwt.token");
        when(jwtProvider.validateAccessToken("invalid.jwt.token")).thenReturn(false);
        
        // When
        jwtAuthorizationFilter.doFilterInternal(request, response, filterChain);
        
        // Then
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).isEqualTo("application/json");
        
        verify(filterChain, never()).doFilter(request, response);
        
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNull();
    }
}
```

### 🔄 토큰 재발급 테스트

#### **토큰 재발급 API 테스트**
```java
@WebMvcTest(AuthController.class)
@Import(TestSecurityConfig.class)
class TokenReissueTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @MockitoBean
    private JwtProvider jwtProvider;
    
    @MockitoBean
    private UserService userService;
    
    @MockitoBean
    private SessionService sessionService;
    
    @Test
    @DisplayName("유효한 Refresh Token으로 토큰 재발급 성공")
    void reissueToken_WithValidRefreshToken_ShouldReturnNewTokens() throws Exception {
        // Given
        String refreshToken = "valid.refresh.token";
        String newAccessToken = "Bearer new.access.token";
        String newRefreshToken = "Bearer new.refresh.token";
        String username = "john";
        
        User user = new User("john", "john@email.com");
        Cookie refreshCookie = new Cookie("refreshToken", refreshToken);
        
        when(jwtProvider.getRefreshTokenFromCookie(any())).thenReturn(refreshToken);
        when(jwtProvider.validateAccessToken(refreshToken)).thenReturn(true);
        when(jwtProvider.getUsernameFromToken(refreshToken)).thenReturn(username);
        when(userService.findByUsername(username)).thenReturn(user);
        when(jwtProvider.createAccessToken(username, UserRole.USER)).thenReturn(newAccessToken);
        when(jwtProvider.createRefreshToken(username, UserRole.USER)).thenReturn(newRefreshToken);
        
        // When & Then
        MvcResult result = mockMvc.perform(post("/api/v1/auth/reissue")
                                 .cookie(refreshCookie)
                                 .contentType(MediaType.APPLICATION_JSON))
                                 .andExpect(status().isOk())
                                 .andExpect(header().string("Authorization", newAccessToken))
                                 .andExpect(cookie().exists("refreshToken"))
                                 .andReturn();
        
        // 새로운 Refresh Token 쿠키 검증
        Cookie newRefreshCookie = result.getResponse().getCookie("refreshToken");
        assertThat(newRefreshCookie).isNotNull();
        assertThat(newRefreshCookie.isHttpOnly()).isTrue();
        assertThat(newRefreshCookie.getSecure()).isTrue();
    }
    
    @Test
    @DisplayName("Refresh Token이 없으면 재발급 실패")
    void reissueToken_WithoutRefreshToken_ShouldReturnBadRequest() throws Exception {
        // Given
        when(jwtProvider.getRefreshTokenFromCookie(any())).thenReturn(null);
        
        // When & Then
        mockMvc.perform(post("/api/v1/auth/reissue")
                       .contentType(MediaType.APPLICATION_JSON))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.message").value("Refresh Token 쿠키가 없습니다."));
    }
}
```

---

## 5. MockMvc와 Security 통합

### 🎭 MockMvc Security 설정

#### **TestSecurityConfig 구성**
```java
@TestConfiguration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class TestSecurityConfig {
    
    @Bean
    @Primary
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
    
    @Bean
    @Primary
    public UserDetailsService userDetailsService() {
        // 테스트용 사용자 생성
        UserDetails user = User.builder()
            .username("testuser")
            .password(passwordEncoder().encode("password"))
            .roles("USER")
            .build();
            
        UserDetails admin = User.builder()
            .username("admin")
            .password(passwordEncoder().encode("admin"))
            .roles("USER", "ADMIN")
            .build();
            
        return new InMemoryUserDetailsManager(user, admin);
    }
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/public/**").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/users/**").hasAnyRole("USER", "ADMIN")
                .anyRequest().authenticated()
            )
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            );
            
        return http.build();
    }
}
```

#### **MockMvc 보안 테스트 유틸리티**
```java
@TestComponent
public class SecurityTestUtils {
    
    public static RequestPostProcessor jwtToken(String username, String... roles) {
        return request -> {
            // JWT 토큰 생성 로직
            String token = createTestJwtToken(username, roles);
            request.addHeader("Authorization", "Bearer " + token);
            return request;
        };
    }
    
    public static RequestPostProcessor basicAuth(String username, String password) {
        return httpBasic(username, password);
    }
    
    public static RequestPostProcessor csrf() {
        return SecurityMockMvcRequestPostProcessors.csrf();
    }
    
    private static String createTestJwtToken(String username, String... roles) {
        // 테스트용 JWT 토큰 생성 로직
        return "test.jwt.token.for." + username;
    }
}

// 사용 예시
@Test
void secureEndpoint_WithJwtToken_ShouldAllowAccess() throws Exception {
    mockMvc.perform(get("/api/users/profile")
                   .with(SecurityTestUtils.jwtToken("john", "USER")))
           .andExpect(status().isOk());
}
```

### 🔒 CSRF 테스트

#### **CSRF 보호 테스트**
```java
@WebMvcTest(UserController.class)
@Import(TestSecurityConfig.class)
class CsrfProtectionTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @MockitoBean
    private UserService userService;
    
    @Test
    @WithMockUser
    @DisplayName("CSRF 토큰 없이 POST 요청 시 403 Forbidden")
    void postRequest_WithoutCsrfToken_ShouldReturn403() throws Exception {
        CreateUserRequest request = new CreateUserRequest("john", "john@email.com");
        
        mockMvc.perform(post("/api/users")
                       .contentType(MediaType.APPLICATION_JSON)
                       .content(objectMapper.writeValueAsString(request)))
               .andExpect(status().isForbidden());
    }
    
    @Test
    @WithMockUser
    @DisplayName("CSRF 토큰과 함께 POST 요청 시 성공")
    void postRequest_WithCsrfToken_ShouldSucceed() throws Exception {
        CreateUserRequest request = new CreateUserRequest("john", "john@email.com");
        User savedUser = new User("john", "john@email.com");
        
        when(userService.createUser(any(CreateUserRequest.class))).thenReturn(savedUser);
        
        mockMvc.perform(post("/api/users")
                       .with(csrf())  // CSRF 토큰 추가
                       .contentType(MediaType.APPLICATION_JSON)
                       .content(objectMapper.writeValueAsString(request)))
               .andExpect(status().isCreated())
               .andExpect(jsonPath("$.username").value("john"));
    }
}
```

---

## 6. 실제 프로젝트 테스트 예제

### 🏢 종합적인 보안 시나리오 테스트

#### **소셜 로그인 보안 테스트**
```java
@SpringBootTest
@AutoConfigureMockMvc
class SocialLoginSecurityTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @MockitoBean
    private GoogleService googleService;
    
    @MockitoBean
    private UserService userService;
    
    @MockitoBean
    private JwtProvider jwtProvider;
    
    @Test
    @DisplayName("Google 소셜 로그인 전체 플로우 테스트")
    void googleSocialLogin_FullFlow_ShouldAuthenticateUser() throws Exception {
        // 1. 소셜 로그인 URL 요청 (인증 불필요)
        mockMvc.perform(get("/api/v1/auth/social/google/url")
                       .param("redirectUri", "http://localhost:3000/callback"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.authUrl").exists());
        
        // 2. 소셜 로그인 콜백 처리
        String authCode = "google_auth_code_123";
        SocialUserInfo socialUserInfo = new SocialUserInfo();
        socialUserInfo.setSocialId("google_123");
        socialUserInfo.setName("John Doe");
        socialUserInfo.setEmail("john@gmail.com");
        socialUserInfo.setProvider("GOOGLE");
        
        User savedUser = new User("john_google_123", "John Doe", "john@gmail.com");
        String accessToken = "Bearer new.access.token";
        String refreshToken = "Bearer new.refresh.token";
        
        when(googleService.getUserInfo(authCode)).thenReturn(socialUserInfo);
        when(userService.registerSocialUserIfNeeded(socialUserInfo)).thenReturn(savedUser);
        when(jwtProvider.createAccessToken(savedUser.getUsername(), savedUser.getUserRole()))
            .thenReturn(accessToken);
        when(jwtProvider.createRefreshToken(savedUser.getUsername(), savedUser.getUserRole()))
            .thenReturn(refreshToken);
        
        MvcResult loginResult = mockMvc.perform(get("/api/v1/auth/social/google/callback")
                                      .param("code", authCode))
                                      .andExpect(status().isOk())
                                      .andExpect(header().string("Authorization", accessToken))
                                      .andExpect(cookie().exists("refreshToken"))
                                      .andReturn();
        
        // 3. 인증된 토큰으로 보호된 리소스 접근
        String returnedToken = loginResult.getResponse().getHeader("Authorization");
        
        // JWT 토큰 검증을 위한 Mock 설정
        when(jwtProvider.getAccessTokenFromHeader(any())).thenReturn(accessToken.substring("Bearer ".length()));
        when(jwtProvider.validateAccessToken(any())).thenReturn(true);
        when(jwtProvider.getUsernameFromToken(any())).thenReturn(savedUser.getUsername());
        when(userService.findByUsername(savedUser.getUsername())).thenReturn(savedUser);
        
        mockMvc.perform(get("/api/v1/users/profile")
                       .header("Authorization", returnedToken))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.username").value(savedUser.getUsername()));
    }
}
```

#### **멀티 레이어 보안 테스트**
```java
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MultiLayerSecurityTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @MockitoBean
    private JwtProvider jwtProvider;
    
    @MockitoBean
    private TokenManager tokenManager;
    
    @MockitoBean
    private SessionService sessionService;
    
    @Test
    @DisplayName("JWT + 세션 + 토큰 버전 검증이 모두 통과해야 접근 가능")
    void multiLayerSecurity_AllValidationsPass_ShouldAllowAccess() throws Exception {
        // Given
        String username = "john";
        String sessionId = "session_123";
        String token = "valid.jwt.token";
        Long tokenVersion = 5L;
        
        // JWT 검증 Mock
        when(jwtProvider.getAccessTokenFromHeader(any())).thenReturn(token);
        when(jwtProvider.validateAccessToken(token)).thenReturn(true);
        when(jwtProvider.getUsernameFromToken(token)).thenReturn(username);
        when(jwtProvider.getClaimFromToken(token, "sessionId")).thenReturn(sessionId);
        when(jwtProvider.getClaimFromToken(token, "tokenVersion")).thenReturn(tokenVersion.toString());
        
        // 세션 검증 Mock
        when(sessionService.validateSession(sessionId)).thenReturn(true);
        
        // 토큰 버전 검증 Mock
        when(tokenManager.isTokenVersionValid(username, tokenVersion)).thenReturn(true);
        
        // When & Then
        mockMvc.perform(get("/api/v1/users/profile")
                       .header("Authorization", "Bearer " + token))
               .andExpect(status().isOk());
        
        // 모든 검증이 호출되었는지 확인
        verify(jwtProvider).validateAccessToken(token);
        verify(sessionService).validateSession(sessionId);
        verify(tokenManager).isTokenVersionValid(username, tokenVersion);
        verify(sessionService).updateSessionAccess(sessionId);
    }
    
    @Test
    @DisplayName("세션이 만료된 경우 접근 거부")
    void multiLayerSecurity_ExpiredSession_ShouldDenyAccess() throws Exception {
        // Given
        String username = "john";
        String sessionId = "expired_session";
        String token = "valid.jwt.token";
        
        when(jwtProvider.getAccessTokenFromHeader(any())).thenReturn(token);
        when(jwtProvider.validateAccessToken(token)).thenReturn(true);
        when(jwtProvider.getUsernameFromToken(token)).thenReturn(username);
        when(jwtProvider.getClaimFromToken(token, "sessionId")).thenReturn(sessionId);
        
        // 세션 만료
        when(sessionService.validateSession(sessionId)).thenReturn(false);
        
        // When & Then
        mockMvc.perform(get("/api/v1/users/profile")
                       .header("Authorization", "Bearer " + token))
               .andExpect(status().isUnauthorized())
               .andExpect(jsonPath("$.message").value("세션이 만료되었습니다."));
        
        // 세션이 만료되면 토큰 버전 검증은 하지 않음
        verify(sessionService).validateSession(sessionId);
        verify(tokenManager, never()).isTokenVersionValid(anyString(), anyLong());
        verify(sessionService, never()).updateSessionAccess(anyString());
    }
}
```

---

## 7. 문제 해결 가이드

### 🚨 자주 발생하는 Security 테스트 오류

#### **문제 1: @WithMockUser가 작동하지 않음**
```java
// ❌ 문제 상황
@WebMvcTest(UserController.class)  // TestSecurityConfig 누락
class UserControllerTest {
    
    @Test
    @WithMockUser
    void test() throws Exception {
        // Security 설정이 없어서 인증이 제대로 작동하지 않음
    }
}

// ✅ 해결책
@WebMvcTest(UserController.class)
@Import(TestSecurityConfig.class)  // Security 설정 추가
class UserControllerTest {
    
    @Test
    @WithMockUser
    void test() throws Exception {
        // 정상 작동
    }
}
```

#### **문제 2: Security 컨텍스트가 초기화되지 않음**
```java
// ❌ 문제 상황
@ExtendWith(MockitoExtension.class)  // Spring Security Test 지원 없음
class SecurityTest {
    
    @Test
    @WithMockUser  // 작동하지 않음
    void test() {
        // SecurityContextHolder.getContext()가 비어있음
    }
}

// ✅ 해결책 1: Spring 테스트 컨텍스트 사용
@SpringBootTest
class SecurityTest {
    
    @Test
    @WithMockUser
    void test() {
        // 정상 작동
    }
}

// ✅ 해결책 2: 수동으로 Security 컨텍스트 설정
@ExtendWith(MockitoExtension.class)
class SecurityTest {
    
    @Test
    void test() {
        // 수동으로 인증 설정
        UserDetails user = User.builder()
            .username("john")
            .password("password")
            .authorities("ROLE_USER")
            .build();
            
        Authentication auth = new UsernamePasswordAuthenticationToken(
            user, null, user.getAuthorities());
            
        SecurityContextHolder.getContext().setAuthentication(auth);
        
        // 테스트 실행
    }
}
```

#### **문제 3: CSRF 테스트 실패**
```java
// ❌ 문제: CSRF가 활성화된 상태에서 POST 요청 실패
@Test
@WithMockUser
void postTest() throws Exception {
    mockMvc.perform(post("/api/users")
                   .contentType(MediaType.APPLICATION_JSON)
                   .content("{}"))
           .andExpect(status().isOk());  // 403 Forbidden 발생
}

// ✅ 해결책 1: CSRF 토큰 추가
@Test
@WithMockUser
void postTest() throws Exception {
    mockMvc.perform(post("/api/users")
                   .with(csrf())  // CSRF 토큰 추가
                   .contentType(MediaType.APPLICATION_JSON)
                   .content("{}"))
           .andExpected(status().isOk());
}

// ✅ 해결책 2: 테스트 설정에서 CSRF 비활성화
@TestConfiguration
public class TestSecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable());  // 테스트에서 CSRF 비활성화
        return http.build();
    }
}
```

#### **문제 4: JWT 토큰 테스트에서 Mock 설정 누락**
```java
// ❌ 문제: JWT 필터에서 NullPointerException 발생
@Test
void jwtTest() throws Exception {
    mockMvc.perform(get("/api/users/profile")
                   .header("Authorization", "Bearer token"))
           .andExpect(status().isOk());  // NPE 발생
}

// ✅ 해결책: 필요한 모든 JWT 관련 Mock 설정
@Test
void jwtTest() throws Exception {
    // JWT Provider Mock 설정
    when(jwtProvider.getAccessTokenFromHeader(any())).thenReturn("token");
    when(jwtProvider.validateAccessToken("token")).thenReturn(true);
    when(jwtProvider.getUsernameFromToken("token")).thenReturn("john");
    
    // UserDetailsService Mock 설정
    UserDetails userDetails = User.builder()
        .username("john")
        .password("password")
        .authorities("ROLE_USER")
        .build();
    when(userDetailsService.loadUserByUsername("john")).thenReturn(userDetails);
    
    mockMvc.perform(get("/api/users/profile")
                   .header("Authorization", "Bearer token"))
           .andExpect(status().isOk());
}
```

### 🔧 Security 테스트 최적화 팁

#### **테스트 성능 향상**
```java
// ✅ @WebMvcTest 사용으로 컨텍스트 로딩 최소화
@WebMvcTest(UserController.class)
@Import(TestSecurityConfig.class)
class FastSecurityTest {
    // 필요한 컴포넌트만 로드하여 빠른 테스트
}

// ✅ 공통 Security Mock 설정 분리
@TestConfiguration
public class CommonSecurityMocks {
    
    @Bean
    @Primary
    public JwtProvider mockJwtProvider() {
        JwtProvider mock = Mockito.mock(JwtProvider.class);
        // 공통 설정
        when(mock.validateAccessToken(anyString())).thenReturn(true);
        return mock;
    }
}
```

#### **테스트 데이터 관리**
```java
@TestComponent
public class SecurityTestDataFactory {
    
    public static final String VALID_TOKEN = "Bearer valid.test.token";
    public static final String INVALID_TOKEN = "Bearer invalid.test.token";
    public static final String EXPIRED_TOKEN = "Bearer expired.test.token";
    
    public User createTestUser(UserRole role) {
        return User.builder()
            .username("test-user-" + UUID.randomUUID().toString().substring(0, 8))
            .name("테스트 사용자")
            .email("test@example.com")
            .role(role)
            .build();
    }
    
    public void setupValidJwtMocks(JwtProvider jwtProvider, String username, UserRole role) {
        when(jwtProvider.getAccessTokenFromHeader(any())).thenReturn(VALID_TOKEN.substring(7));
        when(jwtProvider.validateAccessToken(any())).thenReturn(true);
        when(jwtProvider.getUsernameFromToken(any())).thenReturn(username);
        when(jwtProvider.getClaimFromToken(any(), eq("role"))).thenReturn(role.name());
    }
}
```

---

## 📚 참고 자료 및 학습 자료

### 🔗 공식 문서
- [Spring Security Testing](https://docs.spring.io/spring-security/reference/servlet/test/index.html)
- [Spring Security Test](https://docs.spring.io/spring-security/site/docs/current/reference/html5/#test)
- [MockMvc Security](https://docs.spring.io/spring-security/site/docs/current/reference/html5/#test-mockmvc)

### 📖 추천 학습 자료
- Spring Security in Action - Laurențiu Spilcă
- Spring Boot 2 Recipes - Marten Deinum

### 🎯 실습 체크리스트
- [ ] @WithMockUser를 활용한 기본 인증 테스트
- [ ] 역할 기반 접근 제어 테스트
- [ ] JWT 토큰 생성/검증 테스트
- [ ] 토큰 재발급 플로우 테스트
- [ ] 소셜 로그인 보안 테스트
- [ ] 멀티 레이어 보안 검증 테스트

---

**다음 가이드**: [04-common-testing-issues-solutions.md](./04-common-testing-issues-solutions.md) - 테스트 문제 해결 완전 가이드