# Mockito 완전 정복 가이드

## 📋 목차
1. [Mockito란 무엇인가?](#1-mockito란-무엇인가)
2. [Mock, Stub, Spy의 차이점](#2-mock-stub-spy의-차이점)
3. [Mockito 기본 사용법](#3-mockito-기본-사용법)
4. [고급 Mockito 기능](#4-고급-mockito-기능)
5. [Spring Boot와 Mockito 통합](#5-spring-boot와-mockito-통합)
6. [실전 예제와 패턴](#6-실전-예제와-패턴)
7. [문제 해결 가이드](#7-문제-해결-가이드)

---

## 1. Mockito란 무엇인가?

### 🎯 Mockito의 목적

**Mockito**는 Java에서 가장 널리 사용되는 모킹 프레임워크로, 단위 테스트에서 의존성을 격리하여 테스트 대상만을 순수하게 테스트할 수 있게 해줍니다.

#### **왜 Mock이 필요한가?**
```java
// ❌ Mock 없이 테스트하면...
class UserService {
    private UserRepository userRepository;  // 데이터베이스 의존
    private EmailService emailService;      // 외부 이메일 서비스 의존
    private PaymentService paymentService;  // 외부 결제 서비스 의존
    
    public User createUser(CreateUserRequest request) {
        // 실제 데이터베이스, 이메일, 결제 서비스가 모두 필요
        // 테스트가 느리고, 불안정하고, 외부 의존성에 영향받음
    }
}

// ✅ Mock을 사용하면...
@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    @Mock private UserRepository userRepository;
    @Mock private EmailService emailService;
    @Mock private PaymentService paymentService;
    
    @InjectMocks private UserService userService;
    
    @Test
    void createUser_ShouldWork() {
        // 외부 의존성 없이 순수하게 UserService 로직만 테스트
        // 빠르고, 안정적이고, 예측 가능한 테스트
    }
}
```

### 🏗️ 테스트 더블(Test Double) 개념

```
Test Double (테스트 대역)
├─ Dummy Object (더미)
├─ Fake Object (페이크)
├─ Stub (스텁)
├─ Mock (목)
└─ Spy (스파이)
```

---

## 2. Mock, Stub, Spy의 차이점

### 🎭 각 개념의 정확한 이해

#### **Dummy Object (더미 객체)**
```java
// 단순히 매개변수를 채우기 위한 객체
public class DummyUser extends User {
    public DummyUser() {
        super("dummy", "dummy@email.com");
    }
}

@Test
void processUsers_ShouldHandleNullUsers() {
    User dummy = new DummyUser();  // 실제로는 사용되지 않음
    
    List<User> users = Arrays.asList(null, dummy, null);
    processor.processUsers(users);  // dummy는 그냥 자리만 채움
}
```

#### **Fake Object (페이크 객체)**
```java
// 실제 동작하지만 간소화된 구현
public class FakeUserRepository implements UserRepository {
    private Map<Long, User> users = new HashMap<>();
    private AtomicLong idGenerator = new AtomicLong();
    
    @Override
    public User save(User user) {
        Long id = idGenerator.incrementAndGet();
        user.setId(id);
        users.put(id, user);
        return user;
    }
    
    @Override
    public Optional<User> findById(Long id) {
        return Optional.ofNullable(users.get(id));
    }
}

@Test
void userServiceTest_WithFakeRepository() {
    UserRepository fakeRepo = new FakeUserRepository();  // 실제 동작하는 가짜 구현
    UserService service = new UserService(fakeRepo);
    
    User saved = service.createUser(new CreateUserRequest("john", "john@email.com"));
    User found = service.findById(saved.getId());
    
    assertThat(found).isEqualTo(saved);
}
```

#### **Stub (스텁)**
```java
// 미리 정의된 답변을 반환하는 객체
@Test
void calculateTotalPrice_WithStub() {
    // Stub: 항상 정해진 값을 반환
    PriceService priceStub = Mockito.mock(PriceService.class);
    when(priceStub.getPrice("PRODUCT_A")).thenReturn(100.0);
    when(priceStub.getPrice("PRODUCT_B")).thenReturn(200.0);
    
    OrderService orderService = new OrderService(priceStub);
    
    // When
    double total = orderService.calculateTotal(Arrays.asList("PRODUCT_A", "PRODUCT_B"));
    
    // Then
    assertThat(total).isEqualTo(300.0);
    // 스텁은 호출 여부는 검증하지 않음 (상태 기반 테스트)
}
```

#### **Mock (목)**
```java
// 호출과 상호작용을 검증하는 객체
@Test
void sendWelcomeEmail_WithMock() {
    // Mock: 호출 여부와 방식을 검증
    EmailService emailMock = Mockito.mock(EmailService.class);
    UserService userService = new UserService(emailMock);
    
    // When
    userService.createUser(new CreateUserRequest("john", "john@email.com"));
    
    // Then: 상호작용 검증 (행위 기반 테스트)
    verify(emailMock).sendWelcomeEmail("john@email.com");
    verify(emailMock, times(1)).sendWelcomeEmail(anyString());
    verify(emailMock, never()).sendSpamEmail(anyString());
}
```

#### **Spy (스파이)**
```java
// 실제 객체를 감시하면서 일부만 가짜로 만드는 객체
@Test
void userService_WithSpy() {
    // Spy: 실제 객체를 래핑하여 일부 메서드만 모킹
    UserService realService = new UserService();
    UserService spyService = Mockito.spy(realService);
    
    // 일부 메서드만 스텁 처리
    doReturn("mocked-id").when(spyService).generateUserId();
    
    // When: 실제 createUser 메서드는 호출되지만, generateUserId는 모킹됨
    User user = spyService.createUser(new CreateUserRequest("john", "john@email.com"));
    
    // Then
    assertThat(user.getId()).isEqualTo("mocked-id");
    
    // 실제 호출도 검증 가능
    verify(spyService).createUser(any(CreateUserRequest.class));
    verify(spyService).generateUserId();
}
```

### 📊 각 유형별 비교표

| 유형 | 목적 | 구현 방식 | 검증 방식 | 사용 시기 |
|------|------|-----------|-----------|-----------|
| **Dummy** | 매개변수 채우기 | 단순 객체 | 검증 안함 | 매개변수가 필요하지만 사용되지 않을 때 |
| **Fake** | 간소화된 실제 구현 | 직접 구현 | 상태 검증 | 실제 구현이 복잡하거나 느릴 때 |
| **Stub** | 정해진 응답 반환 | Mock + when() | 상태 검증 | 특정 입력에 대한 출력만 필요할 때 |
| **Mock** | 상호작용 검증 | Mock + verify() | 행위 검증 | 메서드 호출 여부가 중요할 때 |
| **Spy** | 부분적 모킹 | 실제 객체 래핑 | 혼합 검증 | 기존 객체의 일부만 변경하고 싶을 때 |

---

## 3. Mockito 기본 사용법

### 🚀 Mock 객체 생성 방법

#### **1. @Mock 어노테이션 (권장)**
```java
@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    
    @Mock
    private UserRepository userRepository;
    
    @Mock
    private EmailService emailService;
    
    @InjectMocks
    private UserService userService;  // Mock들이 자동 주입됨
    
    @Test
    void testWithMockAnnotation() {
        // Mock 객체들이 자동으로 생성되고 주입됨
    }
}
```

#### **2. Mockito.mock() 메서드**
```java
class UserServiceTest {
    
    private UserRepository userRepository;
    private EmailService emailService;
    private UserService userService;
    
    @BeforeEach
    void setUp() {
        userRepository = Mockito.mock(UserRepository.class);
        emailService = Mockito.mock(EmailService.class);
        userService = new UserService(userRepository, emailService);
    }
    
    @Test
    void testWithMockMethod() {
        // Mock 객체들을 수동으로 생성하고 주입
    }
}
```

#### **3. MockitoAnnotations.openMocks()**
```java
class LegacyUserServiceTest {
    
    @Mock
    private UserRepository userRepository;
    
    private AutoCloseable closeable;
    
    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
    }
    
    @AfterEach
    void tearDown() throws Exception {
        closeable.close();
    }
}
```

### 🎯 기본 Stubbing (스터빙)

#### **when().thenReturn() - 반환값 설정**
```java
@Test
void basicStubbing() {
    // 단일 반환값
    when(userRepository.findById(1L)).thenReturn(Optional.of(new User("john")));
    
    // 여러 호출에 대한 다른 반환값
    when(emailService.sendEmail(anyString()))
        .thenReturn(true)      // 첫 번째 호출
        .thenReturn(false)     // 두 번째 호출
        .thenReturn(true);     // 세 번째 호출 이후
    
    // 메서드 체이닝
    when(userRepository.findById(anyLong()))
        .thenReturn(Optional.of(new User("john")))
        .thenReturn(Optional.empty());
}
```

#### **when().thenThrow() - 예외 발생**
```java
@Test
void exceptionStubbing() {
    // 특정 예외 던지기
    when(userRepository.findById(999L))
        .thenThrow(new UserNotFoundException("User not found"));
    
    // 런타임 예외
    when(emailService.sendEmail(null))
        .thenThrow(IllegalArgumentException.class);
    
    // 여러 예외
    when(paymentService.processPayment(any()))
        .thenThrow(NetworkException.class)
        .thenThrow(TimeoutException.class);
}
```

#### **Answer 인터페이스 활용**
```java
@Test
void advancedStubbing() {
    // 복잡한 로직이 필요한 경우
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
        User user = invocation.getArgument(0);
        user.setId(System.currentTimeMillis());  // ID 자동 생성
        user.setCreatedAt(LocalDateTime.now());  // 생성 시간 설정
        return user;
    });
    
    // 인덱스별 인수 접근
    when(calculatorService.add(anyInt(), anyInt())).thenAnswer(invocation -> {
        Integer arg1 = invocation.getArgument(0);
        Integer arg2 = invocation.getArgument(1);
        return arg1 + arg2;
    });
}
```

### 🔍 검증 (Verification)

#### **기본 호출 검증**
```java
@Test
void basicVerification() {
    // Given
    User user = new User("john", "john@email.com");
    when(userRepository.save(any(User.class))).thenReturn(user);
    
    // When
    userService.createUser(new CreateUserRequest("john", "john@email.com"));
    
    // Then: 메서드 호출 검증
    verify(userRepository).save(any(User.class));
    verify(emailService).sendWelcomeEmail("john@email.com");
    
    // 호출 횟수 검증
    verify(userRepository, times(1)).save(any(User.class));
    verify(emailService, never()).sendSpamEmail(anyString());
    verify(auditService, atLeast(1)).logUserCreation(anyString());
    verify(cacheService, atMost(2)).invalidateUserCache();
}
```

#### **인수 검증 (ArgumentCaptor)**
```java
@Test
void argumentCapture() {
    // ArgumentCaptor로 전달된 인수 검증
    ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
    ArgumentCaptor<String> emailCaptor = ArgumentCaptor.forClass(String.class);
    
    // When
    userService.createUser(new CreateUserRequest("john", "john@email.com"));
    
    // Then: 전달된 인수 캡처
    verify(userRepository).save(userCaptor.capture());
    verify(emailService).sendWelcomeEmail(emailCaptor.capture());
    
    // 캡처된 인수 검증
    User capturedUser = userCaptor.getValue();
    assertThat(capturedUser.getUsername()).isEqualTo("john");
    assertThat(capturedUser.getEmail()).isEqualTo("john@email.com");
    
    String capturedEmail = emailCaptor.getValue();
    assertThat(capturedEmail).isEqualTo("john@email.com");
}
```

#### **호출 순서 검증**
```java
@Test
void verificationOrder() {
    // When
    userService.createUserWithCompleteProcess(request);
    
    // Then: 호출 순서 검증
    InOrder inOrder = inOrder(userRepository, emailService, auditService);
    
    inOrder.verify(userRepository).save(any(User.class));
    inOrder.verify(emailService).sendWelcomeEmail(anyString());
    inOrder.verify(auditService).logUserCreation(anyString());
}
```

---

## 4. 고급 Mockito 기능

### 🎪 Spy 객체 활용

#### **@Spy 어노테이션**
```java
@ExtendWith(MockitoExtension.class)
class UserServiceSpyTest {
    
    @Spy
    private UserValidator userValidator = new UserValidator();  // 실제 객체
    
    @Mock
    private UserRepository userRepository;
    
    @InjectMocks
    private UserService userService;
    
    @Test
    void validateUser_WithSpy() {
        // 실제 UserValidator의 대부분 메서드는 그대로 사용
        // 일부만 스터빙
        doReturn(false).when(userValidator).isEmailUnique("duplicate@email.com");
        
        // When
        CreateUserRequest request = new CreateUserRequest("john", "duplicate@email.com");
        
        // Then
        assertThatThrownBy(() -> userService.createUser(request))
            .isInstanceOf(ValidationException.class);
        
        // 실제 메서드 호출도 검증 가능
        verify(userValidator).validateUsername("john");  // 실제 호출됨
        verify(userValidator).isEmailUnique("duplicate@email.com");  // 스터빙됨
    }
}
```

#### **부분 모킹 (Partial Mocking)**
```java
@Test
void partialMocking() {
    UserService realService = new UserService(userRepository, emailService);
    UserService spyService = spy(realService);
    
    // 특정 메서드만 모킹
    doReturn("GENERATED_ID").when(spyService).generateUserId();
    doNothing().when(spyService).logUserCreation(anyString());
    
    // When: 실제 createUser 로직은 실행되지만, 일부 메서드는 모킹됨
    User user = spyService.createUser(request);
    
    // Then
    assertThat(user.getId()).isEqualTo("GENERATED_ID");
    verify(spyService).generateUserId();
    verify(spyService).logUserCreation(anyString());
}
```

### 🔄 doReturn() vs when().thenReturn()

#### **차이점과 사용 시기**
```java
@Test
void doReturnVsWhenThenReturn() {
    List<String> spyList = spy(new ArrayList<>());
    
    // ❌ when().thenReturn() - 실제 메서드가 호출됨 (Spy에서 문제 발생 가능)
    when(spyList.get(0)).thenReturn("mocked");  // IndexOutOfBoundsException 가능!
    
    // ✅ doReturn() - 실제 메서드 호출 없이 스터빙
    doReturn("mocked").when(spyList).get(0);  // 안전함
    
    // void 메서드 스터빙
    doNothing().when(spyList).clear();
    doThrow(new RuntimeException()).when(spyList).add("error");
}
```

### 🎭 Mock 생성 시 설정

#### **Answer와 MockSettings**
```java
@Test
void mockWithSettings() {
    // 기본 Answer 설정
    UserRepository mockRepo = mock(UserRepository.class, RETURNS_DEEP_STUBS);
    
    // 복잡한 설정
    UserService mockService = mock(UserService.class, withSettings()
        .name("MockUserService")
        .defaultAnswer(CALLS_REAL_METHODS)
        .serializable()
        .verboseLogging());
    
    // Custom Answer
    EmailService mockEmail = mock(EmailService.class, invocation -> {
        String email = invocation.getArgument(0);
        return email.contains("@gmail.com");  // Gmail만 성공
    });
}
```

#### **Final Class와 Static Method 모킹**
```java
// mockito-inline 의존성 필요
@Test
void mockFinalClassAndStaticMethod() {
    // Final 클래스 모킹
    FinalUserService finalService = mock(FinalUserService.class);
    when(finalService.processUser(any())).thenReturn(true);
    
    // Static 메서드 모킹
    try (MockedStatic<LocalDateTime> mockedStatic = mockStatic(LocalDateTime.class)) {
        LocalDateTime fixedTime = LocalDateTime.of(2024, 1, 1, 0, 0);
        mockedStatic.when(LocalDateTime::now).thenReturn(fixedTime);
        
        // 테스트 실행
        User user = userService.createUser(request);
        assertThat(user.getCreatedAt()).isEqualTo(fixedTime);
    }
}
```

### 🔧 ArgumentMatcher 커스터마이징

#### **커스텀 Matcher 작성**
```java
public class UserMatchers {
    
    public static User userWithEmail(String email) {
        return argThat(user -> user != null && Objects.equals(user.getEmail(), email));
    }
    
    public static CreateUserRequest requestWithUsername(String username) {
        return argThat(request -> 
            request != null && Objects.equals(request.getUsername(), username));
    }
}

@Test
void customArgumentMatcher() {
    // When
    userService.createUser(new CreateUserRequest("john", "john@email.com"));
    
    // Then: 커스텀 매처 사용
    verify(userRepository).save(userWithEmail("john@email.com"));
    verify(auditService).logUserCreation(requestWithUsername("john"));
}
```

---

## 5. Spring Boot와 Mockito 통합

### 🌱 Spring Boot 테스트에서 Mock 사용

#### **@MockBean과 @SpyBean**
```java
@SpringBootTest
class UserServiceIntegrationTest {
    
    @MockBean
    private UserRepository userRepository;  // Spring 컨텍스트의 Bean을 Mock으로 교체
    
    @SpyBean
    private EmailService emailService;      // Spring 컨텍스트의 Bean을 Spy로 교체
    
    @Autowired
    private UserService userService;        // Mock이 주입된 실제 서비스
    
    @Test
    void createUser_WithMockBean() {
        // Given
        User savedUser = new User("john", "john@email.com");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        
        // When
        User result = userService.createUser(new CreateUserRequest("john", "john@email.com"));
        
        // Then
        assertThat(result.getUsername()).isEqualTo("john");
        verify(userRepository).save(any(User.class));
        verify(emailService).sendWelcomeEmail("john@email.com");  // SpyBean의 실제 메서드 호출
    }
}
```

#### **@WebMvcTest에서 Mock 활용**
```java
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
               .andExpect(jsonPath("$.username").value("john"))
               .andExpect(jsonPath("$.email").value("john@email.com"));
        
        verify(userService).findById(1L);
    }
}
```

### 🔧 Spring Boot 3.4.x 변경사항

#### **@MockitoBean 사용**
```java
// Spring Boot 3.4.x 이후
@WebMvcTest(UserController.class)
class UserControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @MockitoBean  // @MockBean 대신 사용
    private UserService userService;
    
    @MockitoBean
    private EmailService emailService;
    
    @Test
    void testWithNewAnnotation() {
        // 기존과 동일한 방식으로 사용
        when(userService.findById(1L)).thenReturn(new User("john"));
    }
}
```

### 🎯 테스트 슬라이스별 Mock 전략

#### **@DataJpaTest - Repository 레이어**
```java
@DataJpaTest
class UserRepositoryTest {
    
    @Autowired
    private TestEntityManager entityManager;
    
    @Autowired
    private UserRepository userRepository;
    
    // Mock 없이 실제 JPA 동작 테스트
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

#### **@JsonTest - JSON 직렬화**
```java
@JsonTest
class UserJsonTest {
    
    @Autowired
    private JacksonTester<User> json;
    
    @MockBean  // 필요한 경우에만 Mock 사용
    private UserValidator userValidator;
    
    @Test
    void serialize_ShouldCreateExpectedJson() throws Exception {
        User user = new User("john", "john@email.com");
        
        assertThat(this.json.write(user))
            .extractingJsonPathStringValue("@.username")
            .isEqualTo("john");
    }
}
```

---

## 6. 실전 예제와 패턴

### 🎮 실제 프로젝트 시나리오

#### **복잡한 비즈니스 로직 테스트**
```java
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {
    
    @Mock private ProductRepository productRepository;
    @Mock private UserRepository userRepository;
    @Mock private PaymentService paymentService;
    @Mock private InventoryService inventoryService;
    @Mock private EmailService emailService;
    @Mock private AuditService auditService;
    
    @InjectMocks private OrderService orderService;
    
    @Test
    @DisplayName("재고가 충분한 상품 주문 시 성공적으로 처리된다")
    void createOrder_WithSufficientInventory_ShouldProcessSuccessfully() {
        // Given
        User user = new User("john", "john@email.com");
        Product product = new Product("LAPTOP", 999.99, 10);
        OrderRequest request = new OrderRequest("john", "LAPTOP", 2);
        
        when(userRepository.findByUsername("john")).thenReturn(Optional.of(user));
        when(productRepository.findByCode("LAPTOP")).thenReturn(Optional.of(product));
        when(inventoryService.checkAvailability("LAPTOP", 2)).thenReturn(true);
        when(inventoryService.reserve("LAPTOP", 2)).thenReturn(true);
        when(paymentService.processPayment(user, 1999.98)).thenReturn(
            new PaymentResult("SUCCESS", "TXN123"));
        
        // When
        OrderResult result = orderService.createOrder(request);
        
        // Then
        assertThat(result.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(result.getTotalAmount()).isEqualTo(1999.98);
        
        // 호출 순서 검증
        InOrder inOrder = inOrder(inventoryService, paymentService, emailService, auditService);
        inOrder.verify(inventoryService).reserve("LAPTOP", 2);
        inOrder.verify(paymentService).processPayment(user, 1999.98);
        inOrder.verify(emailService).sendOrderConfirmation(eq(user.getEmail()), any(Order.class));
        inOrder.verify(auditService).logOrderCreation(any(Order.class));
    }
    
    @Test
    @DisplayName("재고 부족 시 주문이 실패하고 적절한 예외가 발생한다")
    void createOrder_WithInsufficientInventory_ShouldThrowException() {
        // Given
        User user = new User("john", "john@email.com");
        Product product = new Product("LAPTOP", 999.99, 1);  // 재고 1개
        OrderRequest request = new OrderRequest("john", "LAPTOP", 2);  // 주문 2개
        
        when(userRepository.findByUsername("john")).thenReturn(Optional.of(user));
        when(productRepository.findByCode("LAPTOP")).thenReturn(Optional.of(product));
        when(inventoryService.checkAvailability("LAPTOP", 2)).thenReturn(false);
        
        // When & Then
        assertThatThrownBy(() -> orderService.createOrder(request))
            .isInstanceOf(InsufficientInventoryException.class)
            .hasMessageContaining("재고가 부족합니다");
        
        // 결제나 이메일 발송은 호출되지 않아야 함
        verify(paymentService, never()).processPayment(any(), anyDouble());
        verify(emailService, never()).sendOrderConfirmation(anyString(), any());
    }
}
```

#### **외부 API 호출 모킹**
```java
@ExtendWith(MockitoExtension.class)
class WeatherServiceTest {
    
    @Mock private RestTemplate restTemplate;
    @Mock private WeatherApiConfig config;
    
    @InjectMocks private WeatherService weatherService;
    
    @Test
    void getCurrentWeather_ShouldReturnWeatherInfo() throws Exception {
        // Given
        String apiUrl = "https://api.weather.com/current";
        String apiKey = "test-api-key";
        String expectedUrl = apiUrl + "?key=" + apiKey + "&location=Seoul";
        
        when(config.getApiUrl()).thenReturn(apiUrl);
        when(config.getApiKey()).thenReturn(apiKey);
        
        WeatherApiResponse apiResponse = new WeatherApiResponse();
        apiResponse.setTemperature(25.5);
        apiResponse.setHumidity(60);
        apiResponse.setCondition("Sunny");
        
        when(restTemplate.getForObject(expectedUrl, WeatherApiResponse.class))
            .thenReturn(apiResponse);
        
        // When
        WeatherInfo result = weatherService.getCurrentWeather("Seoul");
        
        // Then
        assertThat(result.getTemperature()).isEqualTo(25.5);
        assertThat(result.getCondition()).isEqualTo("Sunny");
        
        verify(restTemplate).getForObject(expectedUrl, WeatherApiResponse.class);
    }
    
    @Test
    void getCurrentWeather_WhenApiCallFails_ShouldReturnDefaultWeather() {
        // Given
        when(config.getApiUrl()).thenReturn("https://api.weather.com/current");
        when(config.getApiKey()).thenReturn("test-api-key");
        when(restTemplate.getForObject(anyString(), eq(WeatherApiResponse.class)))
            .thenThrow(new RestClientException("API call failed"));
        
        // When
        WeatherInfo result = weatherService.getCurrentWeather("Seoul");
        
        // Then
        assertThat(result.getTemperature()).isEqualTo(0.0);  // 기본값
        assertThat(result.getCondition()).isEqualTo("Unknown");
    }
}
```

### 🔄 비동기 처리 테스트

#### **@Async 메서드 테스트**
```java
@ExtendWith(MockitoExtension.class)
class AsyncUserServiceTest {
    
    @Mock private UserRepository userRepository;
    @Mock private EmailService emailService;
    @Mock private AuditService auditService;
    
    @InjectMocks private AsyncUserService userService;
    
    @Test
    void processUserAsync_ShouldCompleteSuccessfully() throws Exception {
        // Given
        User user = new User("john", "john@email.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        
        // When
        CompletableFuture<String> future = userService.processUserAsync(1L);
        String result = future.get(5, TimeUnit.SECONDS);  // 타임아웃 설정
        
        // Then
        assertThat(result).isEqualTo("User processed successfully");
        
        // 비동기 메서드 호출 검증
        verify(userRepository).findById(1L);
        verify(emailService).sendEmail(user.getEmail());
        verify(auditService).logUserProcessing(user.getId());
    }
    
    @Test
    void processUserAsync_WhenUserNotFound_ShouldCompleteExceptionally() {
        // Given
        when(userRepository.findById(999L)).thenReturn(Optional.empty());
        
        // When
        CompletableFuture<String> future = userService.processUserAsync(999L);
        
        // Then
        assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS))
            .hasCauseInstanceOf(UserNotFoundException.class);
    }
}
```

### 🎭 고급 모킹 패턴

#### **Builder 패턴과 함께 사용**
```java
@Test
void createUserWithBuilder_ShouldWork() {
    // Given
    UserBuilder builderMock = mock(UserBuilder.class);
    User expectedUser = new User("john", "john@email.com");
    
    // Builder 체이닝 모킹
    when(builderMock.username("john")).thenReturn(builderMock);
    when(builderMock.email("john@email.com")).thenReturn(builderMock);
    when(builderMock.role(UserRole.USER)).thenReturn(builderMock);
    when(builderMock.build()).thenReturn(expectedUser);
    
    // When
    User result = builderMock
        .username("john")
        .email("john@email.com")
        .role(UserRole.USER)
        .build();
    
    // Then
    assertThat(result).isEqualTo(expectedUser);
}
```

#### **콜백 함수 모킹**
```java
@Test
void processWithCallback_ShouldInvokeCallback() {
    // Given
    UserProcessor processor = mock(UserProcessor.class);
    Consumer<User> callback = mock(Consumer.class);
    User user = new User("john", "john@email.com");
    
    // doAnswer로 콜백 호출 시뮬레이션
    doAnswer(invocation -> {
        Consumer<User> cb = invocation.getArgument(1);
        cb.accept(user);  // 콜백 호출
        return null;
    }).when(processor).processUser(eq(1L), any(Consumer.class));
    
    // When
    processor.processUser(1L, callback);
    
    // Then
    verify(callback).accept(user);
}
```

---

## 7. 문제 해결 가이드

### 🚨 자주 발생하는 오류와 해결책

#### **문제 1: void 메서드에 when().thenReturn() 사용**
```java
// ❌ 오류 발생
when(userRepository.save(any(User.class))).thenReturn(user);
//   ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
//   'void' type not allowed here

// ✅ 해결책
doNothing().when(userRepository).save(any(User.class));

// 또는 실제 동작 시뮬레이션
doAnswer(invocation -> {
    User user = invocation.getArgument(0);
    user.setId(1L);  // ID 설정 등의 사이드 이펙트
    return null;
}).when(userRepository).save(any(User.class));
```

#### **문제 2: UnnecessaryStubbingException**
```java
// ❌ 문제: 스터빙은 했지만 실제로 호출되지 않음
@Test
void testMethod() {
    when(userRepository.findById(1L)).thenReturn(Optional.of(user));  // 사용되지 않음
    when(userRepository.findById(2L)).thenReturn(Optional.of(user2)); // 이것만 사용됨
    
    userService.processUser(2L);  // 1L은 호출되지 않음
}

// ✅ 해결책 1: 불필요한 스터빙 제거
@Test
void testMethod() {
    when(userRepository.findById(2L)).thenReturn(Optional.of(user2));
    
    userService.processUser(2L);
}

// ✅ 해결책 2: lenient() 사용
@Test
void testMethod() {
    lenient().when(userRepository.findById(1L)).thenReturn(Optional.of(user));
    when(userRepository.findById(2L)).thenReturn(Optional.of(user2));
    
    userService.processUser(2L);
}
```

#### **문제 3: ArgumentMatcher 혼용**
```java
// ❌ 오류: ArgumentMatcher와 실제 값 혼용
when(userService.createUser("john", any(String.class))).thenReturn(user);

// ✅ 해결책: 모두 ArgumentMatcher 또는 모두 실제 값
when(userService.createUser(eq("john"), any(String.class))).thenReturn(user);
when(userService.createUser("john", "john@email.com")).thenReturn(user);
```

#### **문제 4: Spy 객체에서 when() 사용**
```java
List<String> spyList = spy(new ArrayList<>());

// ❌ 문제: 실제 메서드가 호출되어 예외 발생 가능
when(spyList.get(0)).thenReturn("mocked");  // IndexOutOfBoundsException!

// ✅ 해결책: doReturn() 사용
doReturn("mocked").when(spyList).get(0);
```

#### **문제 5: final 클래스/메서드 모킹**
```java
// ❌ 기본 Mockito로는 final 클래스 모킹 불가
final class FinalUserService {
    public void processUser() { }
}

// ✅ 해결책: mockito-inline 의존성 추가
// build.gradle
testImplementation 'org.mockito:mockito-inline:4.11.0'

// 이제 final 클래스도 모킹 가능
FinalUserService mockService = mock(FinalUserService.class);
```

### 🔧 디버깅 팁

#### **Mock 상태 확인**
```java
@Test
void debugMockState() {
    // Mock 객체의 상호작용 확인
    System.out.println(mockingDetails(userRepository).printInvocations());
    
    // Stubbing 정보 확인
    System.out.println(mockingDetails(userRepository).getStubbings());
    
    // Mock 여부 확인
    assertThat(mockingDetails(userRepository).isMock()).isTrue();
}
```

#### **Verification 실패 시 디버깅**
```java
@Test
void debugVerification() {
    // When
    userService.createUser(request);
    
    // 모든 호출 확인
    verify(userRepository, atLeastOnce()).save(any(User.class));
    
    // 정확한 인수로 호출되었는지 확인
    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(captor.capture());
    
    User capturedUser = captor.getValue();
    System.out.println("Captured user: " + capturedUser);
    
    // 호출되지 않은 경우 확인
    verifyNoInteractions(emailService);  // 전혀 호출되지 않음
    verifyNoMoreInteractions(userRepository);  // 추가 호출 없음
}
```

### ⚡ 성능 최적화

#### **Mock 재사용**
```java
class UserServiceTest {
    
    private static UserRepository userRepository;
    private static EmailService emailService;
    
    @BeforeAll
    static void setUpMocks() {
        // 테스트 클래스 레벨에서 Mock 생성 (한 번만)
        userRepository = mock(UserRepository.class);
        emailService = mock(EmailService.class);
    }
    
    @BeforeEach
    void resetMocks() {
        // 각 테스트 전에 Mock 상태 리셋
        reset(userRepository, emailService);
    }
}
```

#### **불필요한 Mock 생성 피하기**
```java
// ❌ 과도한 Mock 생성
@Mock private Service1 service1;
@Mock private Service2 service2;
@Mock private Service3 service3;  // 실제로는 사용하지 않음

// ✅ 필요한 Mock만 생성
@Mock private Service1 service1;
@Mock private Service2 service2;
// service3는 필요할 때만 생성
```

---

## 📚 참고 자료 및 추가 학습

### 🔗 공식 문서
- [Mockito 공식 문서](https://javadoc.io/doc/org.mockito/mockito-core/latest/org/mockito/Mockito.html)
- [Spring Boot 테스트 가이드](https://spring.io/guides/gs/testing-web/)

### 📖 추천 도서
- "Growing Object-Oriented Software, Guided by Tests" - Steve Freeman
- "Test Driven Development: By Example" - Kent Beck

### 🎯 실습 과제
1. 복잡한 비즈니스 로직을 가진 서비스 클래스의 단위 테스트 작성
2. 외부 API 호출이 포함된 서비스의 테스트 작성 (RestTemplate Mock 활용)
3. 비동기 처리 로직의 테스트 작성
4. Spy를 활용한 부분 모킹 테스트 작성

---

**다음 가이드**: [03-spring-security-testing-guide.md](./03-spring-security-testing-guide.md) - Spring Security 테스트 완전 가이드