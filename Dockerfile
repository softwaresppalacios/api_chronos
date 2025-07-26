# Etapa 1: Construcción del proyecto con Gradle
FROM gradle:8.5-jdk21 AS builder

# Establecer el directorio de trabajo
WORKDIR /app

# Copiar los archivos del proyecto al contenedor
COPY . .

# Compilar el proyecto y generar el archivo .jar
RUN gradle build -x test

# Etapa 2: Imagen final ligera con el .jar generado
FROM openjdk:21-jdk-slim

# Establecer el directorio de trabajo
WORKDIR /app

# Copiar el archivo .jar desde la etapa de construcción
COPY --from=builder /app/build/libs/*.jar api-chronos.jar

# Exponer el puerto del microservicio (DEV = 50025)
EXPOSE 50025

# Ejecutar la aplicación
CMD ["java", "-jar", "api-chronos.jar"]