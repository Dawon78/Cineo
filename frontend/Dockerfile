# 1단계: 빌드 - Node로 React를 dist 폴더(HTML, JS, CSS)로 만든다
FROM node:22-alpine AS build
WORKDIR /app

# 라이브러리 목록만 먼저 복사해서 설치해 둔다 (코드만 바뀌면 이 단계는 캐시를 재사용)
COPY package.json package-lock.json ./
RUN npm ci

# 빌드할 때 JS에 박힐 값. API 주소는 비워서 화면과 같은 주소(/api)로 요청하게 한다
ARG VITE_API_BASE_URL=
ARG VITE_PORTONE_IMP_CODE
ARG VITE_PORTONE_CHANNEL_KEY

COPY . .
RUN npm run build

# 2단계: 실행 - nginx와 빌드 결과만 담는다
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html