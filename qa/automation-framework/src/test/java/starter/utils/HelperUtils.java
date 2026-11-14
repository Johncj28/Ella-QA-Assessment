package starter.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.javafaker.Faker;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Assert;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static starter.utils.Constants.PATTERN_NAMED_PARAM_TO_UPPER_CASE;
import static starter.utils.TestGlobalVariables.ContextEnum.PIN;
import static starter.utils.TestGlobalVariables.getContext;

@Slf4j
public class HelperUtils {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final Faker faker = new Faker(new Locale("en"));

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final SecureRandom secureRandom = new SecureRandom();

    // =====================================================================================
    // Path Resolver Utilities
    // =====================================================================================

    public static String resolvePath(final String apiPath) {
        if (apiPath == null) return null;

        StringBuilder resolvedPath = new StringBuilder();
        Matcher matcher = Constants.PATTERN_NAMED_PARAM_HINT_PROPERTY.matcher(apiPath);

        while (matcher.find()) {
            String propertyName = matcher.group(1);

            if (propertyName.startsWith(Constants.NAMED_PARAM_HINT_NUMBER_OF_LENGTH)) {
                int length = extractLength(propertyName);
                matcher.appendReplacement(resolvedPath, RandomStringUtils.randomNumeric(length));

            } else if (propertyName.startsWith(Constants.NAMED_PARAM_HINT_STRING_OF_LENGTH)) {
                int length = extractLength(propertyName);
                matcher.appendReplacement(resolvedPath, RandomStringUtils.randomAlphanumeric(length));

            } else if (propertyName.startsWith(Constants.NAMED_PARAM_HINT_GENERATED_UUID)) {
                matcher.appendReplacement(resolvedPath, UUID.randomUUID().toString());

            } else if (propertyName.startsWith(Constants.TO_UPPER_CASE)) {
                matcher.appendReplacement(resolvedPath, resolvePathToUpperCase(propertyName));

            } else {
                Object value = getContext(propertyName);
                Assert.assertNotNull(propertyName + " not persisted.", value);
                matcher.appendReplacement(resolvedPath, value.toString());
            }
        }

        matcher.appendTail(resolvedPath);
        return resolvedPath.toString();
    }

    public static String resolvePathToUpperCase(final String apiPath) {
        Matcher matcher = PATTERN_NAMED_PARAM_TO_UPPER_CASE.matcher(apiPath);
        return matcher.find() ? matcher.group(1).toUpperCase() : apiPath;
    }

    public static int extractLength(String propertyName) {
        return Integer.parseInt(propertyName.substring(propertyName.lastIndexOf(":") + 1));
    }

    public static String generateDeviceUUID() {
        return UUID.randomUUID().toString();
    }

    public static String generateRandomPhoneNumber() {
        Random random = new Random();
        StringBuilder sb = new StringBuilder("+2519");
        for (int i = 0; i < 8; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }


    public static String generatePinByType(String type) {
        SecureRandom random = new SecureRandom();
        int length = 6 + random.nextInt(5); // 6 to 10 digits

        return switch (type.toLowerCase()) {
            case "valid" -> generateStrongPin(length, random);
            case "weak" -> generateWeakPin(length, random);
            case "used" -> getContext(PIN.name());
            case "short_pin" -> generateShortPin(random);
            case "non_numeric_pin" -> generateAlphanumericPin(length, random);
            default -> throw new IllegalArgumentException("Invalid PIN type: " + type);
        };
    }

    private static String generateStrongPin(int length, SecureRandom random) {
        String pin;
        do {
            pin = random.ints(length, 0, 10)
                    .mapToObj(String::valueOf)
                    .collect(Collectors.joining());
        } while (isWeak(pin)); // Keep generating until it's not weak
        return pin;
    }

    private static String generateWeakPin(int length, SecureRandom random) {
        int strategy = random.nextInt(3); // 0: Repetitive, 1: Sequential Asc, 2: Sequential Desc
        int startDigit = random.nextInt(10);

        return switch (strategy) {
            case 0 -> String.valueOf(startDigit).repeat(length);
            case 1 -> IntStream.range(0, length)
                    .map(i -> (startDigit + i) % 10)
                    .mapToObj(String::valueOf)
                    .collect(Collectors.joining());
            default -> IntStream.range(0, length)
                    .map(i -> (startDigit - i + 10) % 10)
                    .mapToObj(String::valueOf)
                    .collect(Collectors.joining());
        };
    }

    private static boolean isWeak(String pin) {
        if (pin.length() < 2) return true;

        boolean isRepetitive = true;
        boolean isAscending = true;
        boolean isDescending = true;

        for (int i = 1; i < pin.length(); i++) {
            int curr = Character.getNumericValue(pin.charAt(i));
            int prev = Character.getNumericValue(pin.charAt(i - 1));

            if (curr != prev) isRepetitive = false;
            if (curr != (prev + 1) % 10) isAscending = false;
            if (curr != (prev - 1 + 10) % 10) isDescending = false;
        }

        return isRepetitive || isAscending || isDescending;
    }

    private static String generateShortPin(SecureRandom random) {
        // Generates a numeric PIN between 1 and 5 digits
        int shortLength = 1 + random.nextInt(5);
        return random.ints(shortLength, 0, 10)
                .mapToObj(String::valueOf)
                .collect(Collectors.joining());
    }

    private static String generateAlphanumericPin(int length, SecureRandom random) {
        // Character pool including uppercase, lowercase, and special characters
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz!@#$%^&*";
        return IntStream.range(0, length)
                .map(i -> chars.charAt(random.nextInt(chars.length())))
                .mapToObj(c -> String.valueOf((char) c))
                .collect(Collectors.joining());
    }

    public static String generateRandomNickName() {

        String firstName = faker.name().firstName()
                .replaceAll("[^a-zA-Z]", ""); // safety: remove special chars

        int suffix = ThreadLocalRandom.current().nextInt(10, 100); // 10–99

        String nickName = firstName + suffix;

        // Ensure max length < 10
        if (nickName.length() > 9) {
            nickName = nickName.substring(0, 9);
        }

        return nickName;
    }

    public static <T> String convertObjectToJson(T object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (Exception e) {
            //log.error("Failed to convert object to JSON: {}", object, e);
            return null;
        }
    }


    public static String extractJsonValue(Response response, String key) {
        return response.then().extract().jsonPath().getString(key);
    }

    private static String cachedDeviceUUID;

    public static void setDeviceUUID(String deviceUUID) {
        cachedDeviceUUID = deviceUUID;
    }

    public static String getDeviceUUID() {
        if (cachedDeviceUUID == null) {
            throw new IllegalStateException("Device UUID has not been initialized. Call performDeviceLookup first.");
        }
        return cachedDeviceUUID;
    }

    public static String generateRandomEqubName() {
        String[] prefixes = {
                "EAGLELION", "UNITY", "FAMILY", "SUNRISE", "PROGRESS",
                "VICTORY", "HARMONY", "FREEDOM", "HOPE", "DREAM"
        };

        String[] suffixes = {
                "Fund", "Group", "Savings", "Circle", "Club",
                "Union", "Plan", "Alliance", "Network", "Society"
        };

        java.util.Random random = new java.util.Random();
        String prefix = prefixes[random.nextInt(prefixes.length)];
        String suffix = suffixes[random.nextInt(suffixes.length)];
        int number = 100 + random.nextInt(900); // adds a 3-digit number

        return prefix + " " + suffix + " " + number;
    }


    public static String getRandomAccount() {
        List<String> accounts = Arrays.asList(
                "1000184556344",
                "1000446116658",
                "1000446116669",
                "1000446116677",
                "1000446116685",
                "1000446116693"
        );

        return accounts.get(ThreadLocalRandom.current().nextInt(accounts.size()));
    }

    public static String generateRandomFullName() {
        String[] firstNames = {"John", "Mary", "Michael", "Sara", "David", "Linda", "James", "Emma", "Henok", "Tom", "Abebe"};
        String[] lastNames = {"Smith", "Johnson", "Brown", "Williams", "Jones", "Garcia", "Miller", "Davis"};

        Random random = new Random();
        String firstName = firstNames[random.nextInt(firstNames.length)];
        String lastName = lastNames[random.nextInt(lastNames.length)];

        return firstName + " " + lastName;
    }


    public static int getRandStarRating() {
        return ThreadLocalRandom.current().nextInt(1, 6); // 1–5 inclusive
    }

    public static String getRandomText() {
        return faker.lorem().sentence();
    }

    public static Map<String, Object> getMiniStatementDateRange() {
        return createRange(LocalDate.now().minusDays(29), LocalDate.now());
    }

    public static Map<String, Object> getInvalidMiniStatementDateRange() {
        return createRange(LocalDate.now().minusMonths(2), LocalDate.now());
    }

    public static Map<String, Object> getFromGreaterThanToDateRange() {
        return createRange(LocalDate.now(), LocalDate.now().minusDays(1));
    }

    private static Map<String, Object> createRange(LocalDate from, LocalDate to) {
        Map<String, Object> dates = new HashMap<>();
        dates.put("from", from.format(FORMATTER));
        dates.put("to", to.format(FORMATTER));
        return dates;
    }

    public static Map<String, Object> getFiveMonthRangeYearMonthFormat() {
        return createDateRange(LocalDate.now().minusMonths(5), "yyyy-MM");
    }

    public static Map<String, Object> getInvalidDateFormatRange() {
        return createDateRange(LocalDate.now().minusMonths(1), "dd-MM-yyyy");
    }

    public static Map<String, Object> getMoreThanFiveYearRangeYearFormat() {
        return createDateRange(LocalDate.now().minusYears(7), "yyyy-MM");
    }

    private static Map<String, Object> createDateRange(LocalDate fromDate, String pattern) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
        Map<String, Object> dates = new HashMap<>();
        dates.put("from", fromDate.format(formatter));
        dates.put("to", LocalDate.now().format(formatter));
        return dates;
    }

    public static String getRandomReason() {
        return faker.lorem().sentence(5);
    }



}
