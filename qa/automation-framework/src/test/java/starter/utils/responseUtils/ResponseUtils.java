package starter.utils.responseUtils;


import io.restassured.response.Response;

public class ResponseUtils {
    public static String getMessage(Response response) {
        String message = response.jsonPath().getString("message");
        return message != null ? message : response.jsonPath().getString("error.message");
    }

}
