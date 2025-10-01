# Auction

## 프로젝트 정보
- **경매 서비스**
- **목적** : 실시간 경매 서비스를 구현
- **기간** : 2025.08.05 ~ 진행중

## 관련 저장소
**프론트엔드**: [링크](https://github.com/ForgeDouble/AUCTION_FRONT)

## Stacks

**Language & Framework**

![Java](https://img.shields.io/badge/Java-ED8B00?style=flat-square&logo=java&logoColor=white)
![SpringBoot](https://img.shields.io/badge/SpringBoot-6DB33F?style=flat-square&logo=spring&logoColor=white)

**Database**

![MariaDB](https://img.shields.io/badge/MariaDB-003545?style=flat&logo=mariadb&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-DC382D?style=flat-square&logo=redis&logoColor=white)

**Technology**

![RabbitMQ](https://img.shields.io/badge/RabbitMQ-FF6600?style=flat&logo=rabbitmq&logoColor=white)
![FCM](https://img.shields.io/badge/FCM-FFCA28?style=flat&logo=firebase&logoColor=black)
![S3](https://img.shields.io/badge/S3-569A31?style=flat&logo=amazons3&logoColor=white)



# 시작가이드


## 환경변수

| 변수명 | 설명 | 예시 | 필수 여부 |
|--------|------|------|-----------|
| `AES_GCM_KEY_B64` | fcm | `` | ✅ |
| `FIREBASE_SERVICE_ACCOUNT_JSON` | fcm| `` | ✅ |
| `AWS_BUCKET_NAME` | S3 관련 AWS 버킷 이름 | `your-bucket-name` | ✅ |
| `AWS_SECRET_ACCESS_KEY` | S3 관련 AWS 엑세스 키 | `ABsdsd2DAs...` | ✅ |
| `AWS_ACCESS_KEY_ID` | S3 관련 AWS 키 ID | `ADS5SDF3...` | ✅ |

## API 문서

1. (대기중) 다운로드
2. Postman에서 Import
3. Environment 설정:
   - `user_token`: 로그인 후 받은 JWT 토큰
4. 테스트 시작!

## 시스템 아키텍처

