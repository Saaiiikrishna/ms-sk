@echo off
REM Auth Service Build and Test Script for Windows
REM This script helps build and test the auth service

echo === Auth Service Build and Test Script ===

REM Check if Java is installed
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo ERROR: Java is not installed or not in PATH
    echo Please install Java 17 from: https://adoptium.net/
    echo Or use: choco install openjdk17
    pause
    exit /b 1
)

REM Check if Maven is installed
mvn -version >nul 2>&1
if %errorlevel% neq 0 (
    echo ERROR: Maven is not installed or not in PATH
    echo Please install Maven from: https://maven.apache.org/download.cgi
    echo Or use: choco install maven
    pause
    exit /b 1
)

echo Java and Maven are available!

REM Display versions
echo.
echo === Environment Information ===
java -version
echo.
mvn -version
echo.

REM Build the project
echo === Building Auth Service ===
echo Running: mvn clean compile
mvn clean compile
if %errorlevel% neq 0 (
    echo ERROR: Build failed!
    pause
    exit /b 1
)

echo Build successful!
echo.

REM Run tests
echo === Running Tests ===
echo Running: mvn test
mvn test
if %errorlevel% neq 0 (
    echo WARNING: Some tests failed!
) else (
    echo All tests passed!
)

echo.

REM Package the application
echo === Packaging Application ===
echo Running: mvn package -DskipTests
mvn package -DskipTests
if %errorlevel% neq 0 (
    echo ERROR: Packaging failed!
    pause
    exit /b 1
)

echo Packaging successful!
echo.

REM Check if Docker is available
docker --version >nul 2>&1
if %errorlevel% neq 0 (
    echo WARNING: Docker is not available. Skipping Docker build.
    goto :end
)

echo === Building Docker Image ===
echo Running: docker build -t auth-service:latest .
docker build -t auth-service:latest .
if %errorlevel% neq 0 (
    echo ERROR: Docker build failed!
    pause
    exit /b 1
)

echo Docker build successful!

:end
echo.
echo === Build and Test Complete ===
echo.
echo If all steps completed successfully, the auth service is ready!
echo You can now run: docker run -p 8080:8080 auth-service:latest
echo.
pause
