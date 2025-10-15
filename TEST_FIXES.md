# Tests Configuration Fixes for IntelliJ

## Problems Fixed

### 1. Parallel Test Execution Timeout

**Problem**: Tests running in parallel caused Docker container conflicts and connection timeouts

**Solution**: Added `maven-surefire-plugin` configuration to disable parallel execution:

```xml
<parallel>none</parallel>
<forkCount>1</forkCount>
<reuseForks>true</reuseForks>
```

### 2. Testcontainers Ryuk Timeout

**Problem**: Ryuk (container cleanup) was causing timeouts

**Solution**: Disabled Ryuk in surefire configuration:

```xml
<systemPropertyVariables>
    <testcontainers.ryuk.disabled>true</testcontainers.ryuk.disabled>
</systemPropertyVariables>
```

### 3. PostgreSQL Container Startup Timeout

**Problem**: Container took too long to start, causing test failures

**Solution**:

- Increased timeout to 120 seconds: `.withStartupTimeoutSeconds(120)`
- Changed to lighter image: `postgres:16-alpine` (faster startup)

### 4. HikariCP Connection Timeout

**Problem**: Database connection pool exhausted during test execution

**Solution**: Added HikariCP configuration with increased timeouts:

```yaml
hikari:
  connection-timeout: 60000
  maximum-pool-size: 5
  minimum-idle: 2
```

## Changes Made

### Files Modified:

1. **`platform-api/pom.xml`**

   - Added `maven-surefire-plugin` with parallel execution disabled
   - Configured Testcontainers Ryuk to be disabled
   - Set forkCount to 1 for sequential execution

2. **`platform-api/src/test/java/BaseIntegrationTest.java`**

   - Changed PostgreSQL image to `postgres:16-alpine`
   - Added `.withStartupTimeoutSeconds(120)`

3. **`platform-api/src/test/resources/application-test.yml`**
   - Added HikariCP connection pool configuration
   - Increased connection timeout to 60000ms
   - Limited pool size to 5 connections

## Test Results

All tests now pass sequentially:

```bash
# Smoke tests
mvn test -Dtest=SpacesServiceIntegrationTest
✅ Tests run: 3, Failures: 0, Errors: 0

# Reservation creation tests
mvn test -Dtest=ReservationCreationTest
✅ Tests run: 6, Failures: 0, Errors: 0
```

## Running Tests in IntelliJ

1. **Right-click** on test class → **Run**
2. **Or** Run all tests: Right-click on `src/test/java` → **Run All Tests**
3. Tests will execute **sequentially** to avoid Docker container conflicts

## Testing Commands

```bash
# Run all tests
mvn clean test

# Run specific test class
mvn test -Dtest=SpacesServiceIntegrationTest

# Run specific test method
mvn test -Dtest=SpacesServiceIntegrationTest#testGetAllActiveSpaces_Success

# Skip tests
mvn clean install -DskipTests
```

## Notes

- Tests now run **sequentially** to prevent Docker container conflicts
- Each test class starts a fresh PostgreSQL container (takes ~10-15 seconds)
- Total test execution time: ~30-60 seconds for all current tests
- Ryuk is disabled for faster test execution









