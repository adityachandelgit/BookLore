# Stage 1: Build the Angular app
FROM node:22-alpine AS angular-build

WORKDIR /angular-app

COPY ./booklore-ui/package.json ./booklore-ui/package-lock.json ./
RUN npm install --force
COPY ./booklore-ui /angular-app/

RUN npm run build --configuration=production

# Stage 2: Build the Spring Boot app with Gradle
FROM gradle:8-jdk21-alpine AS springboot-build

WORKDIR /springboot-app

COPY ./booklore-api/build.gradle ./booklore-api/settings.gradle /springboot-app/
COPY ./booklore-api/src /springboot-app/src

# Inject version into application.yaml using yq
ARG APP_VERSION
RUN apk add --no-cache yq && \
    yq eval '.app.version = strenv(APP_VERSION)' -i /springboot-app/src/main/resources/application.yaml

RUN gradle clean build

# Stage 3: Final image
FROM eclipse-temurin:21-jre-alpine

RUN apk update && apk add nginx

COPY ./nginx.conf /etc/nginx/nginx.conf
COPY --from=angular-build /angular-app/dist/booklore/browser /usr/share/nginx/html
COPY --from=springboot-build /springboot-app/build/libs/booklore-api-0.0.1-SNAPSHOT.jar /app/app.jar

EXPOSE 8080 80

CMD /usr/sbin/nginx -g "daemon off;" & \
    java -jar /app/app.jar