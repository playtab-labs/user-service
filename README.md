# PlayTab User Service
<p align="center">
  <img src="https://github.com/user-attachments/assets/6e1a9189-4433-454a-831e-0f25023012ec" width="150" />
  &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
  <img src="https://github.com/user-attachments/assets/141c91c9-5950-4d0c-aeb2-fc785ca3df69" width="150" />
</p>

PlayTab은 RFID 기반 입장 시스템과 클라우드 플랫폼을 결합해  
대형 축제 현장의 **입장·권한·인원 관리**를 디지털화하는 서비스입니다.

본 레포지토리는 PlayTab 백엔드 중  
**사용자 인증·계정·프로필·약관 동의**를 담당하는 **User Service**입니다.

---

## Service Role

Client (Web / App)  
↓  
GraphQL BFF  
↓  
User Service (gRPC) ← this repo  

- 통신 방식: **gRPC**
- 앞단 구성: **GraphQL BFF**
- 책임: 인증·계정·사용자 상태의 단일 관리

---

## Features

- 자체 로그인 (이메일/비밀번호)
- 소셜 로그인 (OAuth 2.0 / OIDC)
- Access / Refresh Token 발급 및 세션 관리
- Refresh Token Rotation 기반 보안 세션 처리
- 회원가입 및 계정 생성
- 로그아웃 및 세션 무효화
- 사용자 프로필 조회 및 수정
- 약관 동의 관리 (버전 기반)
- 마케팅 수신 동의 상태 관리
- 성인 인증 상태 관리
- 관리자용 사용자 조회 지원

---

## Notes

- User Service는 **인증과 사용자 상태의 단일 책임 서비스**
- 화면 응답 조합은 GraphQL BFF에서 처리
