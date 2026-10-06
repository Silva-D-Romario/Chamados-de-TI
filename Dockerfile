FROM node:22-alpine AS frontend
WORKDIR /workspace/Front
COPY Front/package.json Front/package-lock.json ./
RUN npm ci
COPY Front/ ./
RUN npm run build

FROM maven:3.9.9-eclipse-temurin-17 AS backend
WORKDIR /workspace/Back
COPY Back/pom.xml ./
COPY Back/src ./src
COPY --from=frontend /workspace/Front/dist ./src/main/resources/static
RUN mvn --batch-mode -Dmaven.test.skip=true package

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S chamados && adduser -S chamados -G chamados \
    && mkdir -p /data/attachments && chown -R chamados:chamados /app /data
COPY --from=backend --chown=chamados:chamados /workspace/Back/target/chamados-api-0.0.1-SNAPSHOT.jar /app/app.jar
USER chamados
ENV PORT=8080
ENV ATTACHMENTS_DIR=/data/attachments
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD wget -qO- "http://localhost:${PORT}/actuator/health" >/dev/null || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
