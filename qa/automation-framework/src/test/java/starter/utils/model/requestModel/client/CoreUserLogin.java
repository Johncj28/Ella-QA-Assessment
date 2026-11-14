package starter.utils.model.requestModel.client;

import lombok.Data;

@Data
public class CoreUserLogin {
    private String id;
    private String type;
    private String phone;
    private String pin;
    private int amount;
    private String account_number;
    private String customer_number;
    private String customer_name;
    private String name;
    private String source;
    private String transactionRef;
    private String device_uuid;
    private String installation_date;
    private String user_id;
    private String user_code;
    private String token;
    private String credit_bank;
}
