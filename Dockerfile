# =============================================================================
# Imagem da API, em dois estágios.
# =============================================================================
# O primeiro estágio tem JDK e Maven para compilar; o segundo só o JRE e o jar.
# A imagem final fica menor e não carrega compilador nem código-fonte.
#
#   docker build -t catalogo-produtos-api .
#   docker run -p 8080:8080 -e DB_URL=... -e DB_USER=... -e DB_PASSWORD=... catalogo-produtos-api

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
# O pom.xml entra antes do código: enquanto as dependências não mudam, o Docker
# reaproveita esta camada e o build não baixa tudo de novo.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
# Os testes rodam no 'mvn verify', antes de gerar a imagem; aqui o objetivo é
# só empacotar.
RUN mvn -B -q package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
# Usuário sem privilégios: se a aplicação for comprometida, o invasor não é root
# dentro do contêiner.
RUN useradd --system --uid 10001 catalogo
COPY --from=build /app/target/catalogo-produtos-api-*.jar app.jar
USER catalogo
EXPOSE 8080
# MaxRAMPercentage faz a JVM dimensionar o heap pelo limite de memória do
# contêiner, e não pela memória da máquina inteira.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
