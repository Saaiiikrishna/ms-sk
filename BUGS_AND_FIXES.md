# Potential Bugs and Suggested Fixes

## 1. Trailing Markdown Fragments in Java Files
Several source and test files contained leftover markdown sections beginning with ``` which truncated compilation. Examples include:
- `auth-service/src/main/java/com/mysillydreams/auth/controller/AuthController.java`
- `user-service/src/main/java/com/mysillydreams/userservice/web/admin/AdminController.java`
- multiple test classes in `user-service`
- `auth-service/src/test/java/com/mysillydreams/auth/repository/AdminMfaConfigRepositoryIntegrationTest.java`

**Fix**: remove the markdown fragments from the files so that only valid Java code remains. This has been applied in this PR.

## 2. Missing Dependency Injection in `DeliveryController`
`DeliveryController.verifyOtp()` references `orderAssignmentRepository` but no field or constructor parameter provides this repository. The code will not compile.

**Suggested Solution**: Inject `OrderAssignmentRepository` into the controller similar to other services:
```java
private final OrderAssignmentRepository orderAssignmentRepository;

@Autowired
public DeliveryController(..., OrderAssignmentRepository orderAssignmentRepository) {
    this.orderAssignmentRepository = orderAssignmentRepository;
    ...
}
```

## 3. Use of `TestRestTemplate.getFreePort()` in `FullSagaSmokeTest`
The integration test attempts to obtain a free port using `TestRestTemplate.getFreePort()` which does not exist.

**Suggested Solution**: Replace with a utility such as `SocketUtils.findAvailableTcpPort()` from Spring or use `ServerSocket` to allocate a free port.

## 4. Duplicate `<dependencies>` Block in `user-service/pom.xml`
An extra `<dependencies>` section was accidentally committed around line 160 of the POM, causing Maven to fail parsing the file.

**Fix**: Remove the duplicated block so that only one `<dependencies>` section remains.

## 5. Incomplete TODO placeholders
Several services contain TODO markers for security, OTP validation, or event verification logic. These do not break compilation but represent unfinished behaviour. Implement the missing logic as required by business rules.

