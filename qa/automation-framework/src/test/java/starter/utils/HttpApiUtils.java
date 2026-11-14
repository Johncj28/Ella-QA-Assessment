package starter.utils;

import io.restassured.http.ContentType;
import io.restassured.http.Header;
import io.restassured.http.Headers;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static net.serenitybdd.rest.SerenityRest.given;
import static starter.utils.TestGlobalVariables.ContextEnum.HTTP_RESPONSE;
import static starter.utils.TestGlobalVariables.setContext;

@Slf4j
public class HttpApiUtils {

    private static final String BASE_API_URL = EnvConfig.getBaseUrl();
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 5000;
    private static final List<Integer> RETRY_STATUS_CODES = List.of(500, 502, 503);

    private static String cachedDeviceUUID;

    // Console log of every request/response (shortened bodies); see ApiLogFilter for -Dapi.log options
    static {
        net.serenitybdd.rest.SerenityRest.filters(new ApiLogFilter());
    }

    // -------------------- Helper Methods -------------------- //

    private static String resolvePath(String path, Map<String, String> pathParams) {
        if (pathParams == null || pathParams.isEmpty()) {
            return path;
        }
        String resolvedPath = path;
        for (Map.Entry<String, String> entry : pathParams.entrySet()) {
            resolvedPath = resolvedPath.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return resolvedPath;
    }

    private static String resolveDeviceUUID(String deviceUUID) {
        return deviceUUID != null ? deviceUUID : HelperUtils.getDeviceUUID();
    }

    private static String resolveInstallationDate(String installationDate) {
        return installationDate != null ? installationDate : EnvConfig.getInstallationDate();
    }

    // -------------------- Public API -------------------- //

    public static Response request(String method,
                                   String path,
                                   String token,
                                   String generateNewUUID,
                                   Map<String, Object> queryParams,
                                   Object body) {
        String uuidToUse = (generateNewUUID != null) ? generateNewUUID : HelperUtils.getDeviceUUID();
        return sendWithRetry(() -> sendRequest(method, path, buildHeaders(token, uuidToUse), queryParams, body));
    }

    public static Response requestWithHeaders(String method,
                                              String token,
                                              String path,
                                              String deviceUUID,
                                              Map<String, String> pathParams,
                                              Map<String, Object> queryParams,
                                              Object body) {
        String resolvedPath = resolvePath(path, pathParams);
        String uuidToUse = resolveDeviceUUID(deviceUUID);
        return sendWithRetry(() ->
                sendRequest(method, resolvedPath, buildHeaders(token, uuidToUse), queryParams, body)
        );
    }

    public static Response requestWithStandardHeaders(String method,
                                                      String token,
                                                      String path,
                                                      String deviceUUID,
                                                      String installationDate,
                                                      Map<String, String> pathParams,
                                                      Map<String, Object> queryParams,
                                                      Object body) {
        String resolvedPath = resolvePath(path, pathParams);
        String uuidToUse = resolveDeviceUUID(deviceUUID);
        String installationToUse = resolveInstallationDate(installationDate);
        return sendWithRetry(() ->
                sendRequest(method, resolvedPath, buildStandardHeaders(token, uuidToUse, installationToUse), queryParams, body)
        );
    }

    public static Response executeRequest(String method,
                                          String token,
                                          String path,
                                          String deviceUUID,
                                          String installationDate,
                                          String traceId,
                                          Map<String, String> pathParams,
                                          Map<String, Object> queryParams,
                                          Object body) {
        return executeRequest(method, token, path, deviceUUID, installationDate,
                traceId, EnvConfig.getEnableEncryption(),
                pathParams, queryParams, body);
    }

    public static Response executeRequest(String method,
                                          String token,
                                          String path,
                                          String deviceUUID,
                                          String installationDate,
                                          String traceId,
                                          String enableEncryption,
                                          Map<String, String> pathParams,
                                          Map<String, Object> queryParams,
                                          Object body) {
        String resolvedPath = resolvePath(path, pathParams);
        String uuidToUse = resolveDeviceUUID(deviceUUID);
        String installationToUse = resolveInstallationDate(installationDate);
        return sendWithRetry(() ->
                sendRequest(method, resolvedPath, buildRequestHeaders(token, uuidToUse, installationToUse, traceId, enableEncryption), queryParams, body)
        );
    }


    public static Response requestWithUssdHeaders(
            String method,
            String token,
            String path,
            String deviceUUID,
            String installationDate,
            String traceId,
            Map<String, String> pathParams,
            Map<String, Object> queryParams,
            Object body
    ) {
        return requestWithUssdHeaders(
                method,
                token,
                path,
                deviceUUID,
                installationDate,
                traceId,
                EnvConfig.getEnableEncryption(),
                pathParams,
                queryParams,
                body
        );
    }

    public static Response requestWithUssdHeaders(
            String method,
            String token,
            String path,
            String deviceUUID,
            String installationDate,
            String traceId,
            String enableEncryption,
            Map<String, String> pathParams,
            Map<String, Object> queryParams,
            Object body
    ) {
        String resolvedPath = resolvePath(path, pathParams);
        String uuidToUse = resolveDeviceUUID(deviceUUID);
        String installationToUse = resolveInstallationDate(installationDate);
        return sendWithRetry(() ->
                sendRequest(
                        method,
                        resolvedPath,
                        buildUssdHeaders(
                                token,
                                uuidToUse,
                                installationToUse,
                                traceId,
                                enableEncryption
                        ),
                        queryParams,
                        body
                )
        );
    }



    public static Response minimalHeadersRequest(String method,
                                                 String path,
                                                 String token,
                                                 Map<String, Object> queryParams,
                                                 Object body) {
        return sendWithRetry(() -> sendRequest(method, path, buildMinimalHeaders(token), queryParams, body));
    }

    // -------------------- Core Send Logic -------------------- //

    private static Response sendRequest(String method,
                                        String path,
                                        Headers headers,
                                        Map<String, Object> queryParams,
                                        Object body) {

        RequestSpecification req = baseRequest()
                .basePath(path)
                .headers(headers);

        if (queryParams != null && !queryParams.isEmpty()) {
            req.queryParams(queryParams);
        }
        if (body != null) {
            req.body(body);
        }

        return executeMethod(req, method)
                .then()
                .extract()
                .response();
    }

    private static Response executeMethod(RequestSpecification req, String method) {
        return switch (method.toUpperCase()) {
            case "GET" -> req.get();
            case "POST" -> req.post();
            case "PUT" -> req.put();
            case "DELETE" -> req.delete();
            case "PATCH" -> req.patch();
            default -> throw new IllegalArgumentException("Unsupported HTTP method: " + method);
        };
    }

    // -------------------- File Upload -------------------- //
    public static Response uploadFile(String path,
                                      String token,
                                      File file,
                                      String deviceUUID,
                                      String formFieldName,
                                      Map<String, String> pathParams,
                                      Map<String, Object> extraParams) {
        String resolvedPath = resolvePath(path, pathParams);
        String uuidToUse = resolveDeviceUUID(deviceUUID);
        return sendWithRetry(() -> {
            RequestSpecification req = given()
                    .baseUri(BASE_API_URL)
                    .basePath(resolvedPath)
                    .headers(buildHeaders(token, uuidToUse))
                    .relaxedHTTPSValidation()
                    .contentType(ContentType.MULTIPART);

            req.multiPart(formFieldName, file);

            if (extraParams != null && !extraParams.isEmpty()) {
                extraParams.forEach(req::multiPart);
            }

            Response response = req.post();
            return response.then().extract().response();
        });
    }

    public static Response multipartRequest(String method,
                                            String path,
                                            String token,
                                            File file,
                                            String deviceUUID,
                                            String formFieldName,
                                            Map<String, String> pathParams,
                                            Map<String, Object> extraParams) {
        String resolvedPath = resolvePath(path, pathParams);
        String uuidToUse = resolveDeviceUUID(deviceUUID);
        return sendWithRetry(() -> {
            RequestSpecification req = given()
                    .baseUri(BASE_API_URL)
                    .basePath(resolvedPath)
                    .headers(buildHeaders(token, uuidToUse))
                    .relaxedHTTPSValidation()
                    .contentType(ContentType.MULTIPART);

            req.multiPart(formFieldName, file);

            if (extraParams != null && !extraParams.isEmpty()) {
                extraParams.forEach(req::multiPart);
            }

            Response response = switch (method.toUpperCase()) {
                case "POST" -> req.post();
                case "PUT" -> req.put();
                case "PATCH" -> req.patch();
                default -> throw new IllegalArgumentException("Unsupported multipart method: " + method);
            };

            return response.then().extract().response();
        });
    }

    public static Response multipartRequest(String path,
                                            String token,
                                            File file,
                                            String deviceUUID,
                                            String formFieldName,
                                            Map<String, String> pathParams,
                                            Map<String, Object> extraParams) {
        return multipartRequest("POST", path, token, file, deviceUUID, formFieldName, pathParams, extraParams);
    }


    public static Response uploadFile(String path,
                                      String token,
                                      File file,
                                      String deviceUUID,
                                      String formFieldName,
                                      Map<String, Object> extraParams) {
        String uuidToUse = resolveDeviceUUID(deviceUUID);
        return sendWithRetry(() -> {
            RequestSpecification req = given()
                    .baseUri(BASE_API_URL)
                    .basePath(path)
                    .headers(buildHeaders(token, uuidToUse))
                    .relaxedHTTPSValidation()
                    .contentType(ContentType.MULTIPART);

            req.multiPart(formFieldName, file);

            if (extraParams != null && !extraParams.isEmpty()) {
                extraParams.forEach(req::multiPart);
            }

            Response response = req.post();
            return response.then().extract().response();
        });
    }


    // -------------------- Retry Wrapper -------------------- //

    private static Response sendWithRetry(Supplier<Response> supplier) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            Response res = supplier.get();
            if (!RETRY_STATUS_CODES.contains(res.getStatusCode())) {
                setContext(HTTP_RESPONSE.name(), res);
                return res;
            }
            log.warn("Request failed with status {}. Retrying {}/{}...",
                    res.getStatusCode(), attempt, MAX_RETRIES);
            sleep(RETRY_DELAY_MS);
        }
        throw new RuntimeException("Request failed after " + MAX_RETRIES + " attempts.");
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            log.error("Retry delay interrupted", e);
            Thread.currentThread().interrupt();
        }
    }

    // -------------------- Base Config -------------------- //

    private static RequestSpecification baseRequest() {
        return given()
                .contentType(ContentType.JSON)
                .baseUri(BASE_API_URL)
                .relaxedHTTPSValidation();
    }

    // -------------------- Headers -------------------- //

    private static Headers buildHeaders(String token, String deviceUUID) {
        return buildBaseHeaders(token, deviceUUID, EnvConfig.getInstallationDate(), null, null, null);
    }

    private static Headers buildStandardHeaders(String token, String deviceUUID, String installationDate) {
        return buildBaseHeaders(token, deviceUUID, installationDate, EnvConfig.getXSource(), null, null);
    }

    private static Headers buildStandardHeaders(String token, String deviceUUID) {
        return buildStandardHeaders(token, deviceUUID, EnvConfig.getInstallationDate());
    }

    private static Headers buildUssdHeaders(String token, String deviceUUID, String installationDate, String traceId, String enableEncryption) {
        String encryptionValue = enableEncryption != null ? enableEncryption : "disabled";
        return buildBaseHeaders(token, deviceUUID, installationDate, "ussd", traceId, encryptionValue);
    }

    private static Headers buildRequestHeaders(String token, String deviceUUID, String installationDate, String traceId, String enableEncryption) {
        String encryptionValue = enableEncryption != null ? enableEncryption : "disabled";
        return buildBaseHeaders(token, deviceUUID, installationDate, EnvConfig.getXSource(), traceId, encryptionValue);
    }

    private static Headers buildMultipartHeaders(String token, String deviceUUID, String installationDate, String traceId, String enableEncryption) {
        String encryptionValue = enableEncryption != null ? enableEncryption : "disabled";
        return buildBaseHeaders(token, deviceUUID, installationDate, EnvConfig.getXSource(), traceId, encryptionValue);
    }

    private static Headers buildBaseHeaders(String token, String deviceUUID, String installationDate, String xSource, String traceId, String enableEncryption) {
        List<Header> headers = new ArrayList<>();

        if (token != null) {
            headers.add(new Header("Authorization", "Bearer " + resolveToken(token)));
        }

        headers.add(new Header("platform", EnvConfig.getPlatformType()));
        headers.add(new Header("app_version", EnvConfig.getAppVersion()));
        headers.add(new Header("device_uuid", deviceUUID));
        headers.add(new Header("installation_date", installationDate));
        headers.add(new Header("Content-Type", "application/json"));

        if (xSource != null) {
            headers.add(new Header("x-source", xSource));
        }

        if (traceId != null) {
            headers.add(new Header("trace_id", traceId));
        }

        if (enableEncryption != null) {
            headers.add(new Header("enable_encryption", enableEncryption));
        }

        return new Headers(headers);
    }

    private static Headers buildMinimalHeaders(String token) {
        List<Header> headers = new ArrayList<>();
        if (token != null) {
            headers.add(new Header("Authorization", "Bearer " + resolveToken(token)));
        }
        return new Headers(headers);
    }

    private static Headers buildHeaders(String token, boolean generateNewUUID) {
        if (generateNewUUID) {
            cachedDeviceUUID = HelperUtils.generateDeviceUUID();
            log.info("Generated new device_uuid: {}", cachedDeviceUUID);
        } else if (cachedDeviceUUID == null) {
            cachedDeviceUUID = EnvConfig.getDeviceUUID();
            log.info("Using default device_uuid from EnvConfig: {}", cachedDeviceUUID);
        } else {
            log.info("Using cached device_uuid: {}", cachedDeviceUUID);
        }
        return buildHeaders(token, cachedDeviceUUID);
    }

    // -------------------- Token -------------------- //

    private static String resolveToken(String token) {
        return token != null ? token : "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"; // default fallback token
    }


    // -------------------- Path Parameter Requests -------------------- //

    /**
     * Send a request with path parameters.
     * Example:
     * pathTemplate = "fayda/add_account/{id}/{user_code}"
     * pathParams = Map.of("id", "12345", "user_code", "u001")
     * <p>
     * Will resolve into: "fayda/add_account/12345/u001"
     */
    public static Response requestWithPathParams(String method,
                                                 String token,
                                                 String path,
                                                 Map<String, String> pathParams,
                                                 Map<String, Object> queryParams,
                                                 Object body) {
        String resolvedPath = resolvePath(path, pathParams);
        return sendWithRetry(() ->
                sendRequest(method, resolvedPath, buildMinimalHeaders(token), queryParams, body)
        );
    }

    // -------------------- Basic Auth Requests -------------------- //

    public static Response requestWithBasicAuthHeaders(String method,
                                                       String username,
                                                       String password,
                                                       String path,
                                                       String xSource,
                                                       String xTempToken,
                                                       Map<String, String> pathParams,
                                                       Map<String, Object> queryParams,
                                                       Object body) {
        String resolvedPath = resolvePath(path, pathParams);
        return sendWithRetry(() ->
                sendRequestWithBasicAuth(
                        method,
                        resolvedPath,
                        username,
                        password,
                        buildBasicAuthHeaders(xSource, xTempToken),
                        queryParams,
                        body
                )
        );
    }

    private static Response sendRequestWithBasicAuth(String method,
                                                     String path,
                                                     String username,
                                                     String password,
                                                     Headers headers,
                                                     Map<String, Object> queryParams,
                                                     Object body) {

        RequestSpecification req = baseRequest()
                .basePath(path)
                .auth()
                .preemptive()
                .basic(username, password)
                .headers(headers);

        if (queryParams != null && !queryParams.isEmpty()) {
            req.queryParams(queryParams);
        }

        if (body != null) {
            req.body(body);
        }

        return executeMethod(req, method)
                .then()
                .extract()
                .response();
    }

    private static Headers buildBasicAuthHeaders(String xSource,
                                                 String xTempToken) {

        List<Header> headers = new ArrayList<>();

        headers.add(new Header("X-Source",
                xSource != null ? xSource : EnvConfig.getXSource()));

        if (xTempToken != null) {
            headers.add(new Header("X-Temp-Token", xTempToken));
        }

        headers.add(new Header("Content-Type", "application/json"));

        return new Headers(headers);
    }
    
    public static Response sendMultipartRequestWithMetadata(
            String method,
            String path,
            String token,
            File file,
            String deviceUUID,
            String installationDate,
            String traceId,
            String formFieldName,
            Map<String, String> pathParams,
            Map<String, Object> extraParams
    ) {
        return sendMultipartRequestWithMetadata(
                method,
                path,
                token,
                file,
                deviceUUID,
                installationDate,
                traceId,
                EnvConfig.getEnableEncryption(),
                formFieldName,
                pathParams,
                extraParams
        );
    }

    public static Response sendMultipartRequestWithMetadata(
            String method,
            String path,
            String token,
            File file,
            String deviceUUID,
            String installationDate,
            String traceId,
            String enableEncryption,
            String formFieldName,
            Map<String, String> pathParams,
            Map<String, Object> extraParams
    ) {
        String resolvedPath = resolvePath(path, pathParams);
        String uuidToUse = resolveDeviceUUID(deviceUUID);
        String installationToUse = resolveInstallationDate(installationDate);
        return sendWithRetry(() -> {
            Headers headers = buildMultipartHeaders(
                    token,
                    uuidToUse,
                    installationToUse,
                    traceId,
                    enableEncryption
            );

            RequestSpecification req = given()
                    .baseUri(BASE_API_URL)
                    .basePath(resolvedPath)
                    .headers(headers)
                    .relaxedHTTPSValidation()
                    .contentType(ContentType.MULTIPART);

            req.multiPart(formFieldName, file);

            if (extraParams != null && !extraParams.isEmpty()) {
                extraParams.forEach(req::multiPart);
            }

            Response response = switch (method.toUpperCase()) {
                case "POST" -> req.post();
                case "PUT" -> req.put();
                case "PATCH" -> req.patch();
                default -> throw new IllegalArgumentException(
                        "Unsupported multipart method: " + method
                );
            };

            return response.then().extract().response();
        });
    }

}
