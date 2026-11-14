package starter.utils;

import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import static starter.utils.HelperUtils.extractJsonValue;
import static starter.utils.TestGlobalVariables.ContextEnum.HTTP_RESPONSE;

@Slf4j
public class TestGlobalVariables {

    private static final Map<String, Object> context = new ConcurrentHashMap<>();

    private TestGlobalVariables() {
        // Utility class: prevent instantiation
    }


    public static void addUserCode(String userCode) {
        addToListContext(ContextEnum.LIST_USER_CODE, userCode);
    }

    public static List<String> getUserCodes() {
        return getListFromContext(ContextEnum.LIST_USER_CODE);
    }


//    public static void setContext(String key, Object value) {
//        context.put(key, value);

    /// /        log.info("Set context: {} = {}", key, value);
//    }
    public static void setContext(String key, Object value) {

        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("Context key cannot be null or empty");
        }

        if (value == null) {
            log.warn("Skipping context update: {} is null", key);
            return; // 🚨 PREVENT CRASH
        }

        context.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public static <T> T getContext(String key) {
//        log.info("Get context: {} = {}", key, context.get(key));
        Object value = context.get(key);
        return value != null ? (T) value : null;
    }


    public static List<Object> getContextWithSubKey(String subKey) {
        return context.entrySet().stream()
                .filter(entry -> entry.getKey().contains(subKey))
                .map(Map.Entry::getValue)
                .collect(Collectors.toList());
    }

    public static void clearContextWithSubKey(String subKey) {
        context.keySet().removeIf(key -> key.contains(subKey));
    }

    public static void storeContextFromResponse(Response res, String jsonPath, Enum<?> key) {
        String value = extractJsonValue(res, jsonPath);
        if (value != null && !value.isEmpty()) {
            setContext(key.name(), value);
        } else {
            log.warn("Key '{}' not found in response at path '{}'", key.name(), jsonPath);
        }
    }

    public static void addToListContext(Enum<?> key, String value) {
        List<String> list = (List<String>) context.get(key.name());
        if (list == null) {
            list = new CopyOnWriteArrayList<>();
            context.put(key.name(), list);
        }
        list.add(value);
    }

    // Retrieve the list from context
    @SuppressWarnings("unchecked")
    public static List<String> getListFromContext(Enum<?> key) {
        return (List<String>) context.getOrDefault(key.name(), new CopyOnWriteArrayList<String>());
    }

    public enum ContextEnum {
        ACCESS_TOKEN,
        HTTP_RESPONSE,
        USER_ID,
        USER_CODE,
        OTP,
        SESSION_ID,
        TEMP_TOKEN,
        PHONE_NUMBER,
        DEVICE_UUID,
        INSTALLATION_DATE,
        BRANCH_TOKEN,
        ACTION_CODE,
        DEVICE_UUI,
        PIN,
        NEW_PIN,
        MAKER_ACCESS_TOKEN,
        CHECKER_ACCESS_TOKEN,
        VOUCHER_CODE,
        FAYDA_CODE,
        FAYDA_STATE,
        REQUEST_ID,
        TOKEN_ID,
        ID,
        USERS, // users for equb membership acceptance
        PAYER_TOKEN,
        REQUESTED_DATE,
        CPS_MAKER_ACCESS_TOKEN,
        CPS_CHECKER_ACCESS_TOKEN,
        REQUEST_ACTION,
        FAYDA_RESPONSE_DATA,
        FAYDA_REDIRECTED_URL,
        FAYDA_USER_INFO_RESPONSE,
        FAYDA_SUB_ID,
        PIN_RESPONSE_FULL_NAME,
        PIN_RESPONSE_PHONE_NUMBER,
        LIST_USER_CODE,
        GENERATED_FULL_NAME,
        GENERATED_PHONE_NUMBER,
        SINGLE_MEMBER_ID,
        ACCOUNT_NUMBER,
        GROUP_ID,
        BENEFICIARY_IDS,
        MEMBER_IDS,
        DATA_TOKEN,
        PARTICIPANT_ID,
        CARD_ID,
        BRANCH_NAME,
        ARTICLE_ID,
        CATEGORY_ID,
        TAGS_ID,
        NOTIFICATION_ID,
        FT_REFERENCE,
        TOKEN,
        VARIANT_ID,
        CONTRACT_ID,
        LOAN_ID,
        MERCHANT_ID,
        PRODUCT_ID,
        PRODUCT_NAME,
        MERCHANT_NAME,
        CATEGORY_NAME,
        BUSINESS_ID,
        EVENT_ID,
        TICKET_ID,
        ATTRIBUTE_NAME,
        VALUE,
        BOOKMARK_ID,
        //        TOKEN,
//        NOTIFICATION_ID,
        PRICE,
        FULL_NAME,
        ORDER_ID,
        DEFAULT_ACCOUNT,
        MAIN_ACCOUNT,
        CURRENT_OUTSTANDING,
        PAYMENT_AMOUNT,
        PHYSICAL_CARD_FEE,
        VIRTUAL_CARD_FEE,
        TRACE_ID,
        CREDIT_BANK,
        CREDIT_ACCOUNT,
        UU_ID,
        ACTION_TYPE,
        ADMIN_CHECKER_ACCESS_TOKEN,
        MAKER_ROLE,
        CHECKER_ROLE,
        AUDITOR_ROLE,
        VIRTUAL_CARD_ID
    }

    public static void setResponse(Response response) {
        setContext(HTTP_RESPONSE.name(), response);
    }

}
