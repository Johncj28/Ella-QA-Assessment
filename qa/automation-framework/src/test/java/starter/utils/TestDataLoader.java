package starter.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import starter.utils.model.requestModel.client.*;

import java.io.File;
import java.util.Arrays;


public class TestDataLoader {

    private static final String BASE_PATH = "src/test/resources/test_data/client/";

    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * Generic method to load test data from JSON.
     */
    private static <T> T loadTestData(String fileName, String id, Class<T[]> clazz) {
        try {
            //#src/test/resources/test_data/actions_data.json
            String path = BASE_PATH + fileName;
            T[] allData = mapper.readValue(new File(path), clazz);
//            System.out.println("🔎 Looking for id: [" + id + "]");
            return Arrays.stream(allData)
                    .filter(d -> {
                        try {
                            return d.getClass().getMethod("getId")
                                    .invoke(d).toString().equalsIgnoreCase(id);
                        } catch (Exception e) {
                            throw new RuntimeException("Missing getProfileName() method in class: " + d.getClass(), e);
                        }
                    })
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Test data not found for profile: " + id));
        } catch (Exception e) {
            throw new RuntimeException("Failed to load test data: " + id, e);
        }
    }

    public static UserCreate getUserCreateData(String id) {
        return loadTestData("user_create.json", id, UserCreate[].class);
}
    public static ProductCreate getProductCreateData(String id) {
        return loadTestData("product_create.json", id, ProductCreate[].class);
    }
    public static TransactionCreate getTransactionCreateData(String id) {
        return loadTestData("transaction_create.json", id, TransactionCreate[].class);
    }
    public static CoreUserLogin getCoreUser(String id) {
        return loadTestData("core_login.json", id, CoreUserLogin[].class);
    }
}