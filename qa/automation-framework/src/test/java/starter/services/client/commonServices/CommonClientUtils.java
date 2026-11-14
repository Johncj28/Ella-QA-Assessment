//package starter.services.client.commonServices;
//
//import io.restassured.response.Response;
//import starter.utils.HttpApiUtils;
//import starter.utils.TestDataLoader;
//import starter.utils.model.requestModel.client.CoreUserLogin;
//import starter.utils.model.requestModel.client.Login;
//
//import java.security.SecureRandom;
//import java.util.*;
//
//import static starter.services.apihelper.ApiHelper.sendGet;
//import static starter.utils.HelperUtils.convertObjectToJson;
//import static starter.utils.HelperUtils.extractJsonValue;
//import static starter.utils.HttpApiUtils.executeRequest;
//import static starter.utils.PropertiesReader.getParameterProperties;
//import static starter.utils.TestGlobalVariables.ContextEnum.*;
//import static starter.utils.TestGlobalVariables.getContext;
//import static starter.utils.TestGlobalVariables.setContext;
//
//public class CommonClientUtils {
//    private static final Random random = new Random();
//
//    public static Response login(String user) {
//        CoreUserLogin userLoginData = TestDataLoader.getCoreUser(user);
//        setContext(USER_ID.name(), userLoginData.getUser_id());
//
////
////        String device_uuid = userLoginData.getDevice_uuid();
////        String installation_date = userLoginData.getInstallation_date();
////
////        String loginPin = userLoginData.getPin();
////
////        Login loginPayload = new Login();
////
////        loginPayload.setPin(loginPin);
////
////        Response response = executeRequest(
////                "POST",
////                null,
////                getParameterProperties("AUTH_LOGIN_URL"),
////                device_uuid,
////                installation_date,
////                null,
////                null, null,
////                convertObjectToJson(loginPayload)
////        );
//
////        String accessToken = extractJsonValue(response, "data.access_token");
////        setContext(ACCESS_TOKEN.name(), accessToken);
////        setContext(DEVICE_UUID.name(), device_uuid);
////        setContext(INSTALLATION_DATE.name(), installation_date);
////        setContext(PIN.name(), loginPin);
////        setContext(TRACE_ID.name(), "51c813b22b9c3299e0630b6f030a5ca5");
////
////        setContext(TRACE_ID.name(), "4bf92f3577b34da6a3ce929d0e0e4736");
////        Response syncRes = syncProfile("AUTH_SYNC_PROFILE_URL");
////
////        setContext(USER_ID.name(), extractJsonValue(syncRes, "data.user_id"));
////
////        setContext(PHONE_NUMBER.name(), extractJsonValue(syncRes, "data.phone_number"));
////        setContext(FULL_NAME.name(), extractJsonValue(syncRes, "data.full_name"));
////
//
//        // save default account
//        List<String> linkedAccounts = syncRes.jsonPath().getList("data.linked_accounts");
//
//        String mainAccount = linkedAccounts.stream()
//                .filter(acc -> acc.toLowerCase().contains(":true:"))
//                .map(acc -> acc.split(":")[0])
//                .findFirst()
//                .orElse(null);
//
//        String defaultAccount = linkedAccounts.stream()
//                .filter(acc -> acc.toLowerCase().contains("youth"))
//                .map(acc -> acc.split(":")[0])
//                .findFirst()
//                .orElse(null);
//
//        if (mainAccount != null) {
//            setContext(MAIN_ACCOUNT.name(), mainAccount);
//        }
//        if (defaultAccount != null) {
//            setContext(DEFAULT_ACCOUNT.name(), defaultAccount);
//        }
////        setContext(MAIN_ACCOUNT.name(), mainAccount); // main is the one with True (selected one)
////        setContext(DEFAULT_ACCOUNT.name(), defaultAccount); // default is the one with active status (youth)
//        return response;
//    }
//
//    private static Response syncProfile(String endpoint) {
//        return sendGet(endpoint);
//    }
//
////    public static Response login(String device_uuid, String pin) {
////
////        Login loginPayload = new Login();
////        loginPayload.setPin(pin);
////        Response response = HttpApiUtils.request(
////                "POST",
////                getParameterProperties("LOGIN_URL"),
////                null,
////                device_uuid,
////                null,
////                convertObjectToJson(loginPayload)
////        );
////        return response;
////    }
//
//    public static String getDeviceUUID(String testDataId) {
//        return TestDataLoader.getCoreUser(testDataId).getDevice_uuid();
//    }
//
//    public static String getInstallationDate(String testDataId) {
//        return TestDataLoader.getCoreUser(testDataId).getInstallation_date();
//    }
//
//
//    public static double testAmount() {
//        // 1000 → gives us steps of 0.01
//        int cents = 1000 + random.nextInt(1001);   // 1000 to 2000 cents
//        return cents / 100.0;                      // → 10.00 to 20.00
//    }
//
//    public static int voucherAmount() {
//        // Generates a multiplier between 1 and 5
//        // 100.0 * 1 = 100.0, 100.0 * 5 = 500.0
//        int multiplier = 1 + random.nextInt(5);
//        return multiplier * 100;
//    }
//
//    public static String getWrongDonationID() {
//        SecureRandom random = new SecureRandom();
//        byte[] bytes = new byte[12];
//        random.nextBytes(bytes);
//
//        StringBuilder hexString = new StringBuilder();
//        for (byte b : bytes) {
//            hexString.append(String.format("%02x", b));
//        }
//
//        return hexString.toString();
//    }
//
//    public static Response sendRequest(
//            String method,
//            String token,
//            String endpoint,
//            Map<String, String> pathParams,
//            Map<String, Object> queryParams,
//            Object body
//    ) {
//
//        // ✅ Default token if not provided
//        String finalToken = (token != null)
//                ? token
//                : getContext(ACCESS_TOKEN.name());
//
//        // ✅ Default maps safely
//        Map<String, String> safePathParams =
//                (pathParams == null) ? Collections.emptyMap() : pathParams;
//
//        Map<String, Object> safeQueryParams =
//                (queryParams == null) ? Collections.emptyMap() : queryParams;
//
//        // ✅ Only convert body if not null
//        Object finalBody = (body != null)
//                ? convertObjectToJson(body)
//                : null;
//
//        return executeRequest(
//                method,
//                finalToken,
//                getParameterProperties(endpoint),
//                getContext(DEVICE_UUID.name()),
//                getContext(INSTALLATION_DATE.name()),
//                getContext(TRACE_ID.name()),
//                safePathParams,
//                safeQueryParams,
//                finalBody
//        );
//    }
//
//    public static String removeDashes(String value) {
//        return value.replace("-", "");
//    }
//
//    public static Response getDeviceAccess(String user) {
//        CoreUserLogin userLoginData = TestDataLoader.getCoreUser(user);
//        String device_uuid = userLoginData.getDevice_uuid();
//        String installation_date = userLoginData.getInstallation_date();
//
//        System.out.println("Trace ID" + getContext(TRACE_ID.name()));
//        String pin = userLoginData.getPin();
////
//        Response response = executeRequest(
//                "GET",
//                null,
//                getParameterProperties("DEVICES_LOOKUP_URL"),
//                device_uuid,
//                installation_date,
//                getContext(TRACE_ID.name()),
//                null,
//                null,
//                null
//        );
//        setContext(PIN.name(), pin);
//        String token = extractJsonValue(response, "data.token");
//        setContext(TOKEN.name(), token);
//        return response;
//    }
//}
