# Spring Boot 테스트 기초 가이드

## 📋 목차
1. [테스트의 중요성](#1-테스트의-중요성)
2. [JUnit 5 기초](#2-junit-5-기초)
3. [Spring Boot 테스트 어노테이션](#3-spring-boot-테스트-어노테이션)
4. [테스트 피라미드](#4-테스트-피라미드)
5. [테스트 환경 설정](#5-테스트-환경-설정)

---

## 1. 테스트의 중요성

### 🎯 왜 테스트를 작성해야 할까?

#### **품질 보장**
- 버그를 사전에 발견하고 수정
- 리팩토링 시 기존 기능 보호
- 코드 변경에 대한 신뢰성 확보

#### **문서화 효과**
- 코드의 사용법과 동작 방식을 명확히 설명
- 새로운 팀원의 빠른 이해 도움
- API의 예상 동작을 구체적으로 표현

#### **설계 개선**
- 테스트 가능한 코드는 좋은 설계
- 의존성 분리와 단일 책임 원칙 준수 유도
- 인터페이스 설계의 명확성 향상

---

## 2. JUnit 5 기초

### 🏗️ JUnit 5 구조

```
JUnit 5 = JUnit Platform + JUnit Jupiter + JUnit Vintage
```

- **JUnit Platform**: 테스트 엔진 실행을 위한 기반
- **JUnit Jupiter**: JUnit 5 테스트 작성 및 실행을 위한 API
- **JUnit Vintage**: JUnit 3/4 하위 호환성

### 📝 기본 어노테이션

#### **테스트 생명주기**
```java
@TestMethodOrder(OrderAnnotation.class)
class UserServiceTest {
    
    @BeforeAll
    static void setUpAll() {
        // 모든 테스트 실행 전 한 번만 실행
        System.out.println("=== 테스트 클래스 시작 ===");
    }
    
    @BeforeEach
    void setUp() {
        // 각 테스트 메서드 실행 전마다 실행
        System.out.println("테스트 메서드 준비");
    }
    
    @Test
    @Order(1)
    @DisplayName("사용자를 생성할 수 있다")
    void createUser_ShouldReturnUser() {
        // 테스트 로직
        assertTrue(true);
    }
    
    @Test
    @Order(2)
    @Disabled("아직 구현되지 않음")
    void updateUser_ShouldUpdateUserInfo() {
        // 비활성화된 테스트
    }
    
    @AfterEach
    void tearDown() {
        // 각 테스트 메서드 실행 후마다 실행
        System.out.println("테스트 메서드 정리");
    }
    
    @AfterAll
    static void tearDownAll() {
        // 모든 테스트 실행 후 한 번만 실행
        System.out.println("=== 테스트 클래스 종료 ===");
    }
}
```

#### **조건부 테스트**
```java
class ConditionalTests {
    
    @Test
    @EnabledOnOs(OS.LINUX)
    void onlyOnLinux() {
        // Linux에서만 실행
    }
    
    @Test
    @EnabledOnJre(JRE.JAVA_17)
    void onlyOnJava17() {
        // Java 17에서만 실행
    }
    
    @Test
    @EnabledIf("customCondition")
    void enabledOnCustomCondition() {
        // 사용자 정의 조건
    }
    
    boolean customCondition() {
        return "dev".equals(System.getProperty("env"));
    }
}
```

#### **매개변수화 테스트**
```java
class ParameterizedTests {
    
    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   "})
    @DisplayName("빈 문자열은 유효하지 않다")
    void isValidUsername_WithEmptyString_ShouldReturnFalse(String username) {
        assertFalse(UserValidator.isValid(username));
    }
    
    @ParameterizedTest
    @CsvSource({
        "admin, ADMIN",
        "user, USER", 
        "guest, GUEST"
    })
    void parseUserRole_ShouldReturnCorrectRole(String input, UserRole expected) {
        assertEquals(expected, UserRole.fromString(input));
    }
    
    @ParameterizedTest
    @MethodSource("provideUsersForValidation")
    void validateUser_WithVariousUsers_ShouldReturnExpectedResult(User user, boolean expected) {
        assertEquals(expected, UserValidator.validate(user));
    }
    
    static Stream<Arguments> provideUsersForValidation() {
        return Stream.of(
            Arguments.of(new User("john", "john@email.com"), true),
            Arguments.of(new User("", "john@email.com"), false),
            Arguments.of(new User("john", ""), false)
        );
    }
}
```

### 🔍 AssertJ vs JUnit Assertions

#### **JUnit 기본 Assertions**
```java
import static org.junit.jupiter.api.Assertions.*;

@Test
void junitAssertions() {
    User user = new User("john", "john@email.com");
    
    assertEquals("john", user.getUsername());
    assertNotNull(user);
    assertTrue(user.isActive());
    assertAll(
        () -> assertEquals("john", user.getUsername()),
        () -> assertEquals("john@email.com", user.getEmail())
    );
}
```

#### **AssertJ (권장)**
```java
import static org.assertj.core.api.Assertions.*;

@Test
void assertJAssertions() {
    User user = new User("john", "john@email.com");
    
    // 더 읽기 쉬운 문법
    assertThat(user.getUsername()).isEqualTo("john");
    assertThat(user).isNotNull();
    assertThat(user.isActive()).isTrue();
    
    // 체이닝과 복합 검증
    assertThat(user)
        .extracting(User::getUsername, User::getEmail)
        .containsExactly("john", "john@email.com");
    
    // 컬렉션 검증
    List<User> users = Arrays.asList(user);
    assertThat(users)
        .hasSize(1)
        .extracting(User::getUsername)
        .containsExactly("john");
}
```

---

## 3. Spring Boot 테스트 어노테이션

### 🎯 테스트 유형별 어노테이션

#### **@SpringBootTest - 전체 통합 테스트**
```java
// 전체 애플리케이션 컨텍스트 로드
@SpringBootTest
class FullIntegrationTest {
    
    @Autowired
    private UserService userService;
    
    @Test
    void contextLoads() {
        assertThat(userService).isNotNull();
    }
}

// 웹 환경 포함
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class WebIntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Test
    void shouldReturnUsers() throws Exception {
        mockMvc.perform(get("/api/users"))
               .andExpect(status().isOk());
    }
}
```

#### **@WebMvcTest - 웹 레이어 테스트**
```java
// 특정 컨트롤러만 테스트
@WebMvcTest(UserController.class)
class UserControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @MockBean
    private UserService userService;
    
    @Test
    void getUser_ShouldReturnUser() throws Exception {
        // Given
        User user = new User("john", "john@email.com");
        when(userService.findById(1L)).thenReturn(user);
        
        // When & Then
        mockMvc.perform(get("/api/users/1"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.username").value("john"));
    }
}
```

#### **@DataJpaTest - 데이터 레이어 테스트**
```java
@DataJpaTest
class UserRepositoryTest {
    
    @Autowired
    private TestEntityManager entityManager;
    
    @Autowired
    private UserRepository userRepository;
    
    @Test
    void findByUsername_ShouldReturnUser() {
        // Given
        User user = new User("john", "john@email.com");
        entityManager.persistAndFlush(user);
        
        // When
        Optional<User> found = userRepository.findByUsername("john");
        
        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("john@email.com");
    }
}
```

#### **@JsonTest - JSON 직렬화 테스트**
```java
@JsonTest
class UserJsonTest {
    
    @Autowired
    private JacksonTester<User> json;
    
    @Test
    void serialize_ShouldReturnExpectedJson() throws Exception {
        User user = new User("john", "john@email.com");
        
        assertThat(this.json.write(user))
            .extractingJsonPathStringValue("@.username")
            .isEqualTo("john");
    }
    
    @Test
    void deserialize_ShouldReturnExpectedObject() throws Exception {
        String content = "{\"username\":\"john\",\"email\":\"john@email.com\"}";
        
        assertThat(this.json.parse(content))
            .usingRecursiveComparison()
            .isEqualTo(new User("john", "john@email.com"));
    }
}
```

### 🏷️ 테스트 프로파일 활용

#### **application-test.yml**
```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb
    driver-class-name: org.h2.Driver
  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: true
  
logging:
  level:
    org.springframework.web: DEBUG
    org.hibernate.SQL: DEBUG
```

#### **테스트 설정 클래스**
```java
@TestConfiguration
public class TestConfig {
    
    @Bean
    @Primary
    public Clock testClock() {
        return Clock.fixed(
            Instant.parse("2024-01-01T00:00:00Z"), 
            ZoneOffset.UTC
        );
    }
    
    @Bean
    @Primary
    public EmailService mockEmailService() {
        return Mockito.mock(EmailService.class);
    }
}
```

---

## 4. 테스트 피라미드

### 🔺 테스트 피라미드 구조

```
      🔺 E2E Tests (5%)
     🔺🔺 Integration Tests (15%)
    🔺🔺🔺 Unit Tests (80%)
```

#### **Unit Tests (단위 테스트) - 80%**
```java
// 빠르고, 독립적이고, 신뢰할 수 있는 테스트
@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    
    @Mock
    private UserRepository userRepository;
    
    @InjectMocks
    private UserService userService;
    
    @Test
    void createUser_WithValidData_ShouldReturnUser() {
        // Given
        CreateUserRequest request = new CreateUserRequest("john", "john@email.com");
        User savedUser = new User("john", "john@email.com");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        
        // When
        User result = userService.createUser(request);
        
        // Then
        assertThat(result.getUsername()).isEqualTo("john");
        verify(userRepository).save(any(User.class));
    }
}
```

#### **Integration Tests (통합 테스트) - 15%**
```java
// 여러 컴포넌트 간의 상호작용 테스트
@SpringBootTest
@Transactional
class UserIntegrationTest {
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private UserRepository userRepository;
    
    @Test
    void createAndFindUser_ShouldWorkCorrectly() {
        // Given
        CreateUserRequest request = new CreateUserRequest("john", "john@email.com");
        
        // When
        User created = userService.createUser(request);
        User found = userService.findById(created.getId());
        
        // Then
        assertThat(found.getUsername()).isEqualTo("john");
    }
}
```

#### **E2E Tests (종단간 테스트) - 5%**
```java
// 전체 애플리케이션 플로우 테스트
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserE2ETest {
    
    @Autowired
    private TestRestTemplate restTemplate;
    
    @Test
    void fullUserLifecycle_ShouldWorkCorrectly() {
        // Create user
        CreateUserRequest createRequest = new CreateUserRequest("john", "john@email.com");
        ResponseEntity<User> createResponse = 
            restTemplate.postForEntity("/api/users", createRequest, User.class);
        
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        
        // Get user
        Long userId = createResponse.getBody().getId();
        ResponseEntity<User> getResponse = 
            restTemplate.getForEntity("/api/users/" + userId, User.class);
        
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody().getUsername()).isEqualTo("john");
    }
}
```

---

## 5. 테스트 환경 설정

### ⚙️ 테스트 전용 설정

#### **TestContainers 활용**
```java
@SpringBootTest
@Testcontainers
class DatabaseIntegrationTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:13")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
    
    @Test
    void contextLoads() {
        // 실제 PostgreSQL과 연동된 테스트
    }
}
```

#### **테스트 데이터 관리**
```java
@Component
public class TestDataFactory {
    
    public User createTestUser() {
        return User.builder()
            .username("test-" + UUID.randomUUID().toString().substring(0, 8))
            .name("테스트 사용자")
            .email("test@example.com")
            .role(UserRole.USER)
            .build();
    }
    
    public User createTestUser(String username, String email) {
        return User.builder()
            .username(username)
            .name("테스트 사용자")
            .email(email)
            .role(UserRole.USER)
            .build();
    }
}
```

### 🔧 테스트 유틸리티

#### **커스텀 Assertion**
```java
public class UserAssertions {
    
    public static UserAssert assertThat(User actual) {
        return new UserAssert(actual);
    }
    
    public static class UserAssert extends AbstractAssert<UserAssert, User> {
        
        public UserAssert(User user) {
            super(user, UserAssert.class);
        }
        
        public UserAssert hasUsername(String username) {
            isNotNull();
            if (!Objects.equals(actual.getUsername(), username)) {
                failWithMessage("Expected username <%s> but was <%s>", 
                    username, actual.getUsername());
            }
            return this;
        }
        
        public UserAssert isActive() {
            isNotNull();
            if (!actual.isActive()) {
                failWithMessage("Expected user to be active but was inactive");
            }
            return this;
        }
    }
}

// 사용 예시
@Test
void testUser() {
    User user = new User("john", "john@email.com");
    
    UserAssertions.assertThat(user)
        .hasUsername("john")
        .isActive();
}
```

---

## 🎯 테스트 작성 모범 사례

### 1. **테스트 명명 규칙**
```java
// ❌ 나쁜 예
@Test
void test1() { }

@Test
void testUser() { }

// ✅ 좋은 예
@Test
@DisplayName("유효한 사용자 정보로 회원가입하면 사용자가 생성된다")
void createUser_WithValidUserInfo_ShouldCreateUser() { }

@Test
void createUser_WithDuplicateUsername_ShouldThrowException() { }
```

### 2. **Given-When-Then 패턴**
```java
@Test
void transferMoney_WithSufficientBalance_ShouldTransferSuccessfully() {
    // Given (준비)
    Account fromAccount = new Account("123", 1000);
    Account toAccount = new Account("456", 500);
    when(accountRepository.findById("123")).thenReturn(fromAccount);
    when(accountRepository.findById("456")).thenReturn(toAccount);
    
    // When (실행)
    transferService.transfer("123", "456", 200);
    
    // Then (검증)
    assertThat(fromAccount.getBalance()).isEqualTo(800);
    assertThat(toAccount.getBalance()).isEqualTo(700);
    verify(accountRepository, times(2)).save(any(Account.class));
}
```

### 3. **테스트 독립성 보장**
```java
@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    
    @BeforeEach
    void setUp() {
        // 각 테스트마다 깨끗한 상태로 시작
        Mockito.reset(userRepository);
    }
    
    @Test
    void test1() {
        // 이 테스트는 다른 테스트의 영향을 받지 않음
    }
    
    @Test
    void test2() {
        // 이 테스트도 독립적으로 실행됨
    }
}
```

---

## 📚 참고 자료

- [JUnit 5 공식 문서](https://junit.org/junit5/docs/current/user-guide/)
- [Spring Boot 테스트 가이드](https://spring.io/guides/gs/testing-web/)
- [AssertJ 문서](https://assertj.github.io/doc/)
- [TestContainers 문서](https://www.testcontainers.org/)

---

**다음 가이드**: [02-mockito-complete-guide.md](./02-mockito-complete-guide.md) - Mockito 완전 정복 가이드