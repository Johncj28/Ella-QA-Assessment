package starter.assertions;


import io.cucumber.datatable.DataTable;
import io.restassured.response.Response;
import org.junit.Assert;
import starter.utils.HelperUtils;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static starter.utils.TestGlobalVariables.ContextEnum.HTTP_RESPONSE;
import static starter.utils.TestGlobalVariables.getContext;
import static starter.utils.responseUtils.ResponseUtils.getMessage;

public class AssertionsAPI {

    public static void checkResponseStatusCode(int expected) {
        response().then().statusCode(expected);
    }

    public static void checkResponseStatusCodeEither(int code1, int code2) {
        Response response = getContext(HTTP_RESPONSE.name());
        int actualStatusCode = response.getStatusCode();
        Assert.assertTrue(actualStatusCode == code1 || actualStatusCode == code2);
    }

    public static void checkResponseContains(String jsonFieldName, String expectedValue) {
        String field = HelperUtils.resolvePath(expectedValue);
        Response response = getContext(HTTP_RESPONSE.name());
        String value = response.then().extract().body().jsonPath().getString(jsonFieldName);
        Assert.assertEquals(field, value);
    }

    public static void checkResponseField(String jsonPath, String expectedValue) {
        if ("null".equalsIgnoreCase(expectedValue)) {
            checkResponseContains(jsonPath, null);
            return;
        }

        checkResponseContains(jsonPath, HelperUtils.resolvePath(expectedValue).trim());
    }

    public static void checkResponseSize(String jsonPath, int expectedSize) {
        Response response = getContext(HTTP_RESPONSE.name());
        int result = response.then().extract().body().jsonPath().getList(jsonPath).size();
        Assert.assertEquals(result, expectedSize);
    }

    public static void checkResponseStatusCodes(int... expectedCodes) {
        int actual = response().statusCode();

        boolean matched = Arrays.stream(expectedCodes)
                .anyMatch(code -> code == actual);

        assertTrue(
                matched,
                "Expected status code to be one of " +
                        Arrays.toString(expectedCodes) +
                        " but was " + actual
        );
    }

    public static void validateResponseMessageIsOneOf(DataTable table) {
        List<String> allowedMessages = table.asList();

        String actualMessage = getMessage(response());

        Assert.assertTrue(allowedMessages.contains(actualMessage)
        );
    }

    private static Response response() {
        return getContext(HTTP_RESPONSE.name());
    }
}

