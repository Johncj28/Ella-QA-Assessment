package starter.services.apihelper;

import io.restassured.response.Response;
import org.junit.jupiter.api.function.ThrowingSupplier;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static starter.utils.HelperUtils.convertObjectToJson;
import static starter.utils.HelperUtils.extractJsonValue;
import static starter.utils.HttpApiUtils.requestWithPathParams;
import static starter.utils.PropertiesReader.getParameterProperties;
import static starter.utils.TestGlobalVariables.ContextEnum.*;
import static starter.utils.TestGlobalVariables.getContext;
import static starter.utils.TestGlobalVariables.setContext;

public class ApiHelper {
    private static final String VERIFY_TRXN_PIN_URL = "VERIFY_PIN_FOR_TXN_URL";

    public static Response sendGet(String endpoint) {
        return sendRequest("GET", endpoint, null, null, null);
    }


    public static Response sendGet(String endpoint, Map<String, String> pathParams, Map<String, Object> queryParams) {
        return sendRequest("GET", endpoint, pathParams, queryParams, null);
    }

    public static Response sendPost(String endpoint, Object body) {
        return sendRequest("POST", endpoint, null, null, body);
    }

    public static Response sendPut(String endpoint, Object body) {
        return sendRequest("PUT", endpoint, null, null, body);
    }

    public static Response sendPatch(String endpoint, Object body) {
        return sendRequest("PATCH", endpoint, null, null, body);
    }

    public static Response sendPatch(String endpoint, Map<String, String> pathParams, Map<String, Object> queryParams) {
        return sendRequest("PATCH", endpoint, pathParams, queryParams, null);
    }


    public static Response sendPatch(String endpoint, Map<String, String> pathParams, Map<String, Object> queryParams, Object object) {
        return sendRequest("PATCH", endpoint, pathParams, queryParams, object);
    }

    public static Response sendDelete(String endpoint) {
        return sendRequest("DELETE", endpoint, null, null, null);
    }

    public static Response sendDelete(String endpoint, Map<String, String> path, Map<String, Object> query) {
        return sendRequest("DELETE", endpoint, path, query, null);
    }

    public static Response sendPost(String endpoint,
                                    Map<String, String> pathParams,
                                    Map<String, Object> queryParams,
                                    Object body) {

        return sendRequest("POST", endpoint, pathParams, queryParams, body);
    }

    public static Response sendPut(String endpoint, Map<String, String> path, Map<String, Object> query, Object body) {
        return sendRequest("PUT", endpoint, path, query, body);
    }

    private static Response sendRequest(String method,
                                        String endpoint,
                                        Map<String, String> pathParams,
                                        Map<String, Object> queryParams,
                                        Object body) {

        Response res = requestWithPathParams(
                method,
                getContext(ACCESS_TOKEN.name()),
                getParameterProperties(endpoint),
                pathParams,
                queryParams,
                body == null ? null : convertObjectToJson(body)
        );
        return res;
    }

    public static String resolveDataToken() {
        Response sessionResponse = getContext(HTTP_RESPONSE.name());

        String dataToken = extractJsonValue(sessionResponse, "data.data_token");
        String method = extractJsonValue(sessionResponse, "data.method");

        switch (method.toUpperCase()) {
            case "OPEN":
                break;
            case "PIN":
                verifyPIN(dataToken);
                break;
            default:
                throw new IllegalArgumentException("Invalid method: " + method);
        }

        return dataToken;
    }

    public static String extractDataTokenFromContext() {
        Response response = getContext(HTTP_RESPONSE.name());
        return extractJsonValue(response, "data.data_token");
    }

    public static void verifyPIN(String dataToken) {
        Map<String, Object> verifyPINPayload = new HashMap<>();
        verifyPINPayload.put("pin", getContext(PIN.name()));
        verifyPINPayload.put("data_token", dataToken);

        Response verifyPinResponse = sendPost(VERIFY_TRXN_PIN_URL, verifyPINPayload);
        System.out.println("verifyPinResponse: " + verifyPinResponse);
    }

    public static String safeExtractErrorMessage(Response response) {
        try {
            String message = extractJsonValue(response, "error.message");
            return (message == null || message.trim().isEmpty()) ? null : message;
        } catch (Exception e) {
            return null; // field not present or JSON path failed
        }
    }

    public static void waitForRetry(int min) {
        try {
            Thread.sleep(min * 60 * 1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public static String uniqueUUID() {
        return UUID.randomUUID()
                .toString()
                .replace("-", "");
    }

    public static void captureResponse(Response response) {
        setContext(HTTP_RESPONSE.name(), response);
    }

    public static void execute(ThrowingSupplier<Response> action) {
        try {
            captureResponse(action.get());
        } catch (Exception e) {
            throw new RuntimeException(e);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }


    public static String getRandomImagePath(String directoryPath) {
        File directory = new File(directoryPath);
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg"));
        if (files != null && files.length > 0) {
            return files[new Random().nextInt(files.length)].getAbsolutePath();
        }
        return null;
    }
}