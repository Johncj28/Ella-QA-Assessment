package starter.utils.requestUtils;

import java.util.HashMap;
import java.util.Map;

import static starter.utils.HelperUtils.convertObjectToJson;

public class RequestUtils {
    private static Map<String, Object> buildMap(boolean stringifyValues, Object... keyValues) {

        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("Key-value pairs must be even");
        }

        Map<String, Object> map = new HashMap<>();

        for (int i = 0; i < keyValues.length; i += 2) {
            Object value = keyValues[i + 1];

            map.put(
                    String.valueOf(keyValues[i]),
                    stringifyValues ? String.valueOf(value) : value
            );
        }

        return map;
    }

    public static Map<String, Object> body(Object... keyValues) {
        return buildMap(false, keyValues);
    }

    public static String bodyJson(Object... keyValues) {
        Map<String, Object> map = buildMap(false, keyValues);
        return convertObjectToJson(map);
    }

    public static Map<String, String> pathParams(Object... keyValues) {

        Map<String, Object> raw = buildMap(true, keyValues);

        Map<String, String> map = new HashMap<>();

        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            map.put(entry.getKey(), String.valueOf(entry.getValue()));
        }

        return map;
    }

    public static Map<String, Object> queryParams(Object... keyValues) {
        return buildMap(true, keyValues);
    }
}
