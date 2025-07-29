# 소셜 로그인 시스템 API 문서

## 🚀 개요

이 시스템은 **카카오와 구글 소셜 로그인 전용**으로 설계된 백엔드 API입니다.  
일반 회원가입이나 패스워드 로그인은 지원하지 않으며, 모든 사용자는 소셜 로그인을 통해서만 가입/로그인할 수 있습니다.

### 기본 정보
- **Base URL**: `http://localhost:8080`
- **API 버전**: v1
- **인증 방식**: JWT (Access Token + Refresh Token)
- **지원 소셜 로그인**: 카카오, 구글

---

## 🔐 인증 시스템

### 토큰 전략
- **Access Token**: Authorization 헤더로 전송, 짧은 수명 (30분~1시간)
- **Refresh Token**: HttpOnly 쿠키로 자동 관리, 긴 수명 (7일)
- **관리자 Token**: 환경변수로 관리되는 마스터 토큰

### 권한 레벨
- **일반 사용자**: 소셜 로그인을 통해 가입한 사용자
- **관리자**: 마스터 토큰을 가진 시스템 관리자

---

## 📡 API 엔드포인트

### 1. 소셜 로그인 (필수)

#### 1.1 소셜 로그인 URL 생성
```http
GET /api/v1/auth/social/{provider}/url?redirect_uri={callback_url}
```

**파라미터:**
- `{provider}`: `kakao` 또는 `google`
- `redirect_uri`: 소셜 로그인 완료 후 리다이렉트할 프론트엔드 URL

**응답:**
```json
{
  "code": "SOCIAL_AUTH_URL_SUCCESS",
  "message": "성공",
  "data": {
    "authUrl": "https://kauth.kakao.com/oauth/authorize?client_id=..."
  }
}
```

**사용법:**
```javascript
// 1. 프론트엔드에서 소셜 로그인 시작
const response = await fetch('/api/v1/auth/social/kakao/url?redirect_uri=http://localhost:3000/callback');
const { data } = await response.json();

// 2. 사용자를 소셜 로그인 페이지로 리다이렉트
window.location.href = data.authUrl;
```

#### 1.2 소셜 로그인 콜백 처리
```http
GET /api/v1/auth/social/{provider}/callback?code={authorization_code}
```

**파라미터:**
- `{provider}`: `kakao` 또는 `google`  
- `code`: 소셜 서비스에서 받은 인가 코드

**응답:**
```json
{
  "code": "SOCIAL_LOGIN_SUCCESS", 
  "message": "성공",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9..."
  }
}
```
- **Refresh Token**은 HttpOnly 쿠키로 자동 설정됨

**사용법:**
```javascript
// 소셜 로그인 콜백 페이지에서
const urlParams = new URLSearchParams(window.location.search);
const code = urlParams.get('code');

const response = await fetch(`/api/v1/auth/social/kakao/callback?code=${code}`);
const { data } = await response.json();

// Access Token을 메모리나 localStorage에 저장
localStorage.setItem('accessToken', data.accessToken);
```

### 2. 토큰 관리

#### 2.1 Access Token 재발급
```http
POST /api/v1/auth/reissue
```

**요청 헤더:** 
- Refresh Token이 쿠키에 자동 포함됨

**응답:**
```json
{
  "code": "SUCCESS",
  "data": {
    "accessToken": "new_jwt_token"
  }
}
```

**사용법:**
```javascript
// Access Token 만료 시 자동 재발급
const refreshToken = async () => {
  const response = await fetch('/api/v1/auth/reissue', {
    method: 'POST',
    credentials: 'include' // 쿠키 포함
  });
  
  if (response.ok) {
    const { data } = await response.json();
    localStorage.setItem('accessToken', data.accessToken);
    return data.accessToken;
  }
  
  // Refresh Token도 만료된 경우 재로그인 필요
  redirectToLogin();
};
```

### 3. 사용자 정보 관리

#### 3.1 프로필 조회
```http
GET /api/v1/users/profile
Authorization: Bearer {access_token}
```

**응답:**
```json
{
  "code": "USER_SUCCESS_GET",
  "data": {
    "userId": 1,
    "username": "user123",
    "name": "홍길동", 
    "nickname": "길동이",
    "email": "user@example.com"
  }
}
```

#### 3.2 프로필 수정
```http
PUT /api/v1/users/update
Authorization: Bearer {access_token}
Content-Type: application/json

{
  "email": "new@example.com",
  "nickname": "새닉네임"
}
```

#### 3.3 로그아웃
```http
POST /api/v1/users/logout  
Authorization: Bearer {access_token}
```

**기능:**
- 현재 세션 무효화
- 모든 토큰 무효화
- 쿠키에서 Refresh Token 제거

#### 3.4 회원 탈퇴
```http
DELETE /api/v1/users/withdraw
Authorization: Bearer {access_token}
```

**주의:** 소프트 삭제 방식으로, 실제 데이터는 삭제되지 않고 `deleted_at` 필드로 관리됩니다.

#### 3.5 사용자 역할 조회
```http
GET /api/v1/users/role
Authorization: Bearer {access_token}
```

**응답:**
```json
{
  "username": "user123",
  "userRole": "USER"
}
```

### 4. 관리자 전용 API

#### 4.1 모든 사용자 조회
```http
GET /api/v1/admin/users
Authorization: Bearer {admin_master_token}
```

#### 4.2 사용자 강제 탈퇴
```http
DELETE /api/v1/admin/users/{userId}
Authorization: Bearer {admin_master_token}
```

#### 4.3 사용자 계정 복원
```http
POST /api/v1/admin/users/{userId}/restore
Authorization: Bearer {admin_master_token}
```

#### 4.4 시스템 상태 확인
```http
GET /api/v1/admin/system/status
Authorization: Bearer {admin_master_token}
```

---

## 🔧 프론트엔드 구현 가이드

### HTTP 클라이언트 설정

```javascript
// axios 기본 설정
import axios from 'axios';

const api = axios.create({
  baseURL: 'http://localhost:8080',
  withCredentials: true // 쿠키 자동 포함
});

// 요청 인터셉터: Access Token 자동 추가
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('accessToken');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// 응답 인터셉터: 토큰 만료 시 자동 재발급
api.interceptors.response.use(
  (response) => response,
  async (error) => {
    if (error.response?.status === 401) {
      try {
        const refreshResponse = await axios.post('/api/v1/auth/reissue', {}, {
          withCredentials: true
        });
        
        const newToken = refreshResponse.data.data.accessToken;
        localStorage.setItem('accessToken', newToken);
        
        // 원래 요청 재시도
        error.config.headers.Authorization = `Bearer ${newToken}`;
        return axios.request(error.config);
      } catch (refreshError) {
        // Refresh Token도 만료된 경우
        localStorage.removeItem('accessToken');
        window.location.href = '/login';
      }
    }
    return Promise.reject(error);
  }
);
```

### 소셜 로그인 완성 예제

```javascript
// 소셜 로그인 컴포넌트
const SocialLogin = () => {
  const handleKakaoLogin = async () => {
    try {
      // 1. 카카오 로그인 URL 요청
      const response = await api.get('/api/v1/auth/social/kakao/url', {
        params: { redirect_uri: window.location.origin + '/callback' }
      });
      
      // 2. 카카오 로그인 페이지로 리다이렉트
      window.location.href = response.data.data.authUrl;
    } catch (error) {
      console.error('카카오 로그인 실패:', error);
    }
  };

  return (
    <div>
      <button onClick={handleKakaoLogin}>카카오로 로그인</button>
      <button onClick={handleGoogleLogin}>구글로 로그인</button>
    </div>
  );
};

// 콜백 처리 컴포넌트
const LoginCallback = () => {
  useEffect(() => {
    const handleCallback = async () => {
      const urlParams = new URLSearchParams(window.location.search);
      const code = urlParams.get('code');
      const provider = window.location.pathname.includes('kakao') ? 'kakao' : 'google';
      
      if (code) {
        try {
          const response = await api.get(`/api/v1/auth/social/${provider}/callback`, {
            params: { code }
          });
          
          // 토큰 저장
          localStorage.setItem('accessToken', response.data.data.accessToken);
          
          // 메인 페이지로 리다이렉트
          window.location.href = '/dashboard';
        } catch (error) {
          console.error('로그인 콜백 처리 실패:', error);
          window.location.href = '/login?error=callback_failed';
        }
      }
    };

    handleCallback();
  }, []);

  return <div>로그인 처리중...</div>;
};
```

### 사용자 정보 관리 예제

```javascript
// 프로필 조회
const fetchProfile = async () => {
  try {
    const response = await api.get('/api/v1/users/profile');
    return response.data.data;
  } catch (error) {
    console.error('프로필 조회 실패:', error);
  }
};

// 프로필 수정
const updateProfile = async (profileData) => {
  try {
    await api.put('/api/v1/users/update', profileData);
    alert('프로필이 수정되었습니다.');
  } catch (error) {
    console.error('프로필 수정 실패:', error);
  }
};

// 로그아웃
const logout = async () => {
  try {
    await api.post('/api/v1/users/logout');
    localStorage.removeItem('accessToken');
    window.location.href = '/login';
  } catch (error) {
    console.error('로그아웃 실패:', error);
  }
};
```

---

## ⚠️ 주의사항

### 보안
1. **Access Token**을 localStorage에 저장하는 것은 XSS 공격에 취약할 수 있습니다. 메모리 저장을 고려하세요.
2. **Refresh Token**은 HttpOnly 쿠키로 자동 관리되므로 프론트엔드에서 직접 다룰 필요가 없습니다.
3. **관리자 토큰**은 절대 클라이언트 코드에 하드코딩하지 마세요.

### 에러 처리
- **401 Unauthorized**: 토큰 만료 또는 유효하지 않은 토큰
- **403 Forbidden**: 권한 부족 (관리자 API 접근 시)
- **404 Not Found**: 존재하지 않는 리소스
- **500 Internal Server Error**: 서버 내부 오류

### 브라우저 호환성
- 이 시스템은 **쿠키와 CORS**를 사용하므로, `withCredentials: true` 설정이 필수입니다.
- **Safari**에서는 SameSite 쿠키 정책에 주의하세요.

---

## 🔍 문제 해결

### Q: 로그인 후 토큰이 저장되지 않습니다.
A: `withCredentials: true` 설정과 CORS 설정을 확인하세요.

### Q: API 호출 시 401 에러가 계속 발생합니다.
A: Access Token 만료일 가능성이 높습니다. 자동 토큰 재발급 로직을 확인하세요.

### Q: 관리자 API에 접근할 수 없습니다.
A: `X-Admin-Token` 헤더 또는 `Authorization` 헤더에 올바른 관리자 토큰이 포함되었는지 확인하세요.

---

## 📞 지원

추가 문의사항이나 기술 지원이 필요한 경우, 백엔드 개발팀에 문의하세요.