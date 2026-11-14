package starter.utils;

import net.serenitybdd.core.di.SerenityInfrastructure;
import net.thucydides.model.util.EnvironmentVariables;

public class EnvConfig {

    private static final EnvironmentVariables variables = SerenityInfrastructure.getEnvironmentVariables();

    private static final String ENV =
            variables.getProperty("serenity.environment", "qa");

    // Read from system properties or serenity.conf default
    private static final String environment = variables.getProperty("serenity.environment", "dev");
    private static final String platform = System.getProperty("platform", "android").toLowerCase();
    private static final String branch = System.getProperty("branch", "").toLowerCase();

    // Helper to read nested config values
    private static String get(String key) {
        return variables.getProperty("environments." + environment + "." + key);
    }

    private static String getPlatformProperty(String key) {
        return variables.getProperty("environments." + environment + ".platforms." + platform + "." + key);
    }

    private static String getBranchProperty(String role, String key) {
        return variables.getProperty("environments." + environment + ".branch." + role + "." + key);
    }

    private static String getCpsProperty(String role, String key) {
        return variables.getProperty("environments." + environment + ".cps." + role + "." + key);
    }

    private static String getDbProperty(String key) {
        return variables.getProperty("environments." + environment + ".mongo_db." + key);
    }

    // Public getters
    public static String getEnvironmentName() {
        return environment;
    }

    public static String getPlatformName() {
        return platform;
    }

    public static String getBaseUrl() {
        return get("base.url");
    }

    // for vault management only
    public static String getBaseUrl_() {
        return get("base_.url");
    }

    public static String getBaseDbUrl() {
        return get("base.url_db");
    }

    public static String getPhoneNumber() {
        return get("phone_number");
    }

    public static String getPin() {
        return get("pin");
    }

    public static String getAccountNumber() {
        return get("accountNumber");
    }

    public static String getAppVersion() {
        return getPlatformProperty("app_version");
    }

    public static String getDeviceUUID() {
        return getPlatformProperty("device_uuid");
    }

    public static String getInstallationDate() {
        return getPlatformProperty("installation_date");
    }

    public static String getPlatformType() {
        return getPlatformProperty("platform");
    }

    public static String getXSource() {
        return getPlatformProperty("x_source");
    }

    public static String getEnableEncryption() {
        return getPlatformProperty("enable_encryption");
    }


    public static String getMakerUsername() {
        return getBranchProperty("Maker", "username");
    }

    public static String getMakerPassword() {
        return getBranchProperty("Maker", "password");
    }

    public static String getCheckerUsername() {
        return getBranchProperty("Checker", "username");
    }

    public static String getCheckerPassword() {
        return getBranchProperty("Checker", "password");
    }


    public static String getCpsMakerUsername() {
        return getCpsProperty("Maker", "username");
    }

    public static String getCpsMakerPassword() {
        return getCpsProperty("Maker", "password");
    }

    public static String getCpsCheckerUsername() {
        return getCpsProperty("Checker", "username");
    }

    public static String getCpsCheckerPassword() {
        return getCpsProperty("Checker", "password");
    }

    public static String getDbUrl() {
        return getDbProperty("db_url");
    }

    public static String getDbName() {
        return getDbProperty("db_name");
    }

    public static String getMongoDbUrl() {
        return getDbProperty("db_url");
    }

    public static String getMongoDbName() {
        return getDbProperty("db_name");
    }



    /* =========================
       Core resolvers
       ========================= */

    private static String envKey(String key) {
        return variables.getProperty("environments." + ENV + "." + key);
    }

    private static String branchKey(String role, String key) {
        return variables.getProperty(
                "environments." + ENV + ".branch." + role + "." + key
        );
    }

    private static String branchNestedKey(String role, String user, String key) {
        return variables.getProperty(
                "environments." + ENV + ".branch." + role + "." + user + "." + key
        );
    }

    private static String cpsKey(String role, String key) {
        return variables.getProperty(
                "environments." + ENV + ".cps." + role + "." + key
        );
    }

    private static String cpsNestedKey(String role, String user, String key) {
        return variables.getProperty(
                "environments." + ENV + ".cps." + role + "." + user + "." + key
        );
    }






    /* =========================
       Base URLs
       ========================= */

    public static String baseUrl() {
        return envKey("base.url");
    }

    public static String baseDbUrl() {
        return envKey("base.url_db");
    }

    /* =========================
       Branch users
       ========================= */

    public static String makerUsername() {
        return branchKey("Maker", "username");
    }

    public static String makerPassword() {
        return branchKey("Maker", "password");
    }

    public static String checkerUsername(String checker) {
        return branchNestedKey("Checker", checker, "username");
    }

    public static String checkerPassword(String checker) {
        return branchNestedKey("Checker", checker, "password");
    }

    /* =========================
   Auditor users
   ========================= */

    public static String auditorUsername() {
        return branchKey("Auditor", "username");
    }

    public static String auditorPassword() {
        return branchKey("Auditor", "password");
    }
    /* =========================
       CPS users
       ========================= */

    public static String makerCpsUsername() {
        return cpsKey("Maker", "username");
    }

    public static String makerCpsPassword() {
        return cpsKey("Maker", "password");
    }

    public static String checkerCpsUsername(String checker) {
        return cpsNestedKey("Checker", checker, "username");
    }

    public static String checkerCpsPassword(String checker) {
        return cpsNestedKey("Checker", checker, "password");
    }

    public static String auditorCpsUsername() {
        return cpsKey("Auditor", "username");
    }

    public static String auditorCpsPassword() {
        return cpsKey("Auditor", "password");
    }

    public static String adminUsername() {
        return cpsKey("Admin", "username");
    }

    public static String adminPassword() {
        return cpsKey("Admin", "password");
    }


    private EnvConfig() {
        // utility class
    }

}


