package scanner;

import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.options.UiAutomator2Options;
import org.junit.jupiter.api.*;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.net.URL;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Language Screen Test Suite
 * Tests: screen loads, English selection, Next button, offline/online behavior, relaunch
 *
 * Prerequisites:
 *   - Appium server running on http://127.0.0.1:4723
 *   - Android device connected via ADB
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class LanguageScreenTest {

    static AndroidDriver driver;
    static WebDriverWait wait;

    static final String ADB          = "/Users/ios/Library/Android/sdk/platform-tools/adb";
    static final String APP_PACKAGE  = "com.math.photo.scanner.equation.formula.calculator";
    static final String APP_ACTIVITY = ".newcode.activity.Splashscreen";
    static final String APPIUM_URL   = "http://127.0.0.1:4723";

    static String deviceId = "";

    // ─── SETUP ────────────────────────────────────────────────────────────────

    @BeforeAll
    static void globalSetup() throws Exception {
        deviceId = detectDevice();
        System.out.println("Device: " + deviceId);
    }

    @AfterAll
    static void globalTeardown() {
        quitDriver();
    }

    // ─── HELPERS ──────────────────────────────────────────────────────────────

    static String detectDevice() throws Exception {
        Process p = new ProcessBuilder(ADB, "devices").redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        p.waitFor();
        for (String line : out.split("\n")) {
            line = line.trim();
            if (!line.isEmpty() && !line.startsWith("List") && line.contains("device")) {
                String id = line.split("\t")[0].trim();
                if (!id.isEmpty()) return id;
            }
        }
        throw new Exception("No device found.");
    }

    static void launchApp() throws Exception {
        quitDriver();
        UiAutomator2Options opts = new UiAutomator2Options()
            .setPlatformName("Android")
            .setDeviceName(deviceId)
            .setAppPackage(APP_PACKAGE)
            .setAppActivity(APP_ACTIVITY)
            .setAutomationName("UiAutomator2")
            .setNoReset(false)
            .setFullReset(false);
        opts.setCapability("appium:newCommandTimeout", 9999);
        opts.setCapability("appium:appWaitDuration", 30000);
        driver = new AndroidDriver(new URL(APPIUM_URL), opts);
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(8));
        wait = new WebDriverWait(driver, Duration.ofSeconds(20));
    }

    static void forceStop() throws Exception {
        new ProcessBuilder(ADB, "-s", deviceId, "shell", "am", "force-stop", APP_PACKAGE)
            .redirectErrorStream(true).start().waitFor();
        Thread.sleep(500);
    }

    static void quitDriver() {
        if (driver != null) {
            try { driver.quit(); } catch (Exception ignored) {}
            driver = null;
        }
    }

    static String currentActivity() {
        try { return driver.currentActivity(); } catch (Exception e) { return ""; }
    }

    // Wait until splash is gone and Language screen appears (max 15s)
    static boolean waitForLanguageScreen() throws Exception {
        for (int i = 0; i < 30; i++) {
            String act = currentActivity();
            if (act.contains("Language")) return true;
            // dismiss subscription popup if it appears
            try {
                driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(0));
                if (!driver.findElements(By.xpath("//*[@text='Pay Nothing Now']")).isEmpty()) {
                    driver.findElement(By.xpath("//*[@text='Pay Nothing Now']")).click();
                    Thread.sleep(500);
                }
            } catch (Exception ignored) {}
            finally { driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(8)); }
            Thread.sleep(500);
        }
        return currentActivity().contains("Language");
    }

    static boolean isElementPresent(By by) {
        try {
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(0));
            return !driver.findElements(by).isEmpty();
        } catch (Exception e) { return false; }
        finally { driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(8)); }
    }

    static boolean safeClick(By by, int seconds) {
        try {
            WebElement el = new WebDriverWait(driver, Duration.ofSeconds(seconds))
                .until(ExpectedConditions.elementToBeClickable(by));
            el.click();
            return true;
        } catch (Exception e) { return false; }
    }

    static void adbTap(int x, int y) throws Exception {
        new ProcessBuilder(ADB, "-s", deviceId, "shell", "input", "tap",
            String.valueOf(x), String.valueOf(y))
            .redirectErrorStream(true).start().waitFor();
        Thread.sleep(500);
    }

    static void setAirplaneMode(boolean on) throws Exception {
        String val = on ? "1" : "0";
        new ProcessBuilder(ADB, "-s", deviceId, "shell",
            "settings", "put", "global", "airplane_mode_on", val)
            .redirectErrorStream(true).start().waitFor();
        new ProcessBuilder(ADB, "-s", deviceId, "shell", "am", "broadcast",
            "-a", "android.intent.action.AIRPLANE_MODE", "--ez", "state", on ? "true" : "false")
            .redirectErrorStream(true).start().waitFor();
        Thread.sleep(1500);
        System.out.println(on ? "✈ Airplane mode ON" : "✈ Airplane mode OFF");
    }

    static boolean isAirplaneModeOn() throws Exception {
        Process p = new ProcessBuilder(ADB, "-s", deviceId, "shell",
            "settings", "get", "global", "airplane_mode_on")
            .redirectErrorStream(true).start();
        String val = new String(p.getInputStream().readAllBytes()).trim();
        p.waitFor();
        return "1".equals(val);
    }

    // ─── TEST CASES ───────────────────────────────────────────────────────────

    /**
     * TC01 — Language screen loads after splash
     */
    @Test
    @Order(1)
    @DisplayName("TC01 | Language: Screen loads after splash")
    void tc01_LanguageScreenLoads() throws Exception {
        System.out.println("\n===== TC01: Language Screen Loads =====");
        forceStop();
        launchApp();

        boolean found = waitForLanguageScreen();
        String activity = currentActivity();
        System.out.println("Activity: " + activity);

        Assertions.assertTrue(found,
            "Language screen did not appear. Current: " + activity);
        System.out.println("PASS — Language screen loaded: " + activity);
    }

    /**
     * TC02 — English language option is visible
     */
    @Test
    @Order(2)
    @DisplayName("TC02 | Language: English option visible")
    void tc02_EnglishOptionVisible() throws Exception {
        System.out.println("\n===== TC02: English Option Visible =====");
        forceStop();
        launchApp();
        waitForLanguageScreen();

        boolean byId   = isElementPresent(By.id(APP_PACKAGE + ":id/imenglish"));
        boolean byText = isElementPresent(By.xpath("//*[@text='English']"));

        System.out.println("English by id: " + byId + " | by text: " + byText);
        Assertions.assertTrue(byId || byText,
            "English option not found on Language screen");
        System.out.println("PASS — English option visible");
    }

    /**
     * TC03 — English can be selected
     */
    @Test
    @Order(3)
    @DisplayName("TC03 | Language: English can be selected")
    void tc03_EnglishSelectable() throws Exception {
        System.out.println("\n===== TC03: English Selectable =====");
        forceStop();
        launchApp();
        waitForLanguageScreen();

        boolean clicked = safeClick(By.id(APP_PACKAGE + ":id/imenglish"), 5);
        if (!clicked) clicked = safeClick(By.xpath("//*[@text='English']"), 3);

        System.out.println("English selected: " + clicked);
        Assertions.assertTrue(clicked, "Could not click English option");
        System.out.println("PASS — English selected");
    }

    /**
     * TC04 — Next button is present on Language screen
     */
    @Test
    @Order(4)
    @DisplayName("TC04 | Language: Next button present")
    void tc04_NextButtonPresent() throws Exception {
        System.out.println("\n===== TC04: Next Button Present =====");
        forceStop();
        launchApp();
        waitForLanguageScreen();

        // Select English first
        safeClick(By.id(APP_PACKAGE + ":id/imenglish"), 5);
        Thread.sleep(500);

        boolean nextById   = isElementPresent(By.id(APP_PACKAGE + ":id/btn_next"));
        boolean nextByText = isElementPresent(By.xpath("//*[@text='Next']"));

        System.out.println("Next by id: " + nextById + " | by text: " + nextByText);
        Assertions.assertTrue(nextById || nextByText,
            "Next button not found on Language screen");
        System.out.println("PASS — Next button present");
    }

    /**
     * TC05 — Tapping Next navigates away from Language screen
     */
    @Test
    @Order(5)
    @DisplayName("TC05 | Language: Next navigates to next screen")
    void tc05_NextNavigatesAway() throws Exception {
        System.out.println("\n===== TC05: Next Navigates Away =====");
        forceStop();
        launchApp();
        waitForLanguageScreen();

        // Select English
        if (!safeClick(By.id(APP_PACKAGE + ":id/imenglish"), 5))
            safeClick(By.xpath("//*[@text='English']"), 3);
        Thread.sleep(500);

        // Tap Next
        if (!safeClick(By.xpath("//*[@text='Next']"), 5))
            adbTap(540, 1305);
        Thread.sleep(2000);

        String after = currentActivity();
        System.out.println("After Next: " + after);

        Assertions.assertFalse(after.contains("Language"),
            "Still on Language screen after tapping Next. Activity: " + after);
        System.out.println("PASS — navigated to: " + after);
    }

    /**
     * TC06 — ONLINE: Language screen loads and Next works normally
     */
    @Test
    @Order(6)
    @DisplayName("TC06 | ONLINE: Language screen works normally")
    void tc06_Online_LanguageWorks() throws Exception {
        System.out.println("\n===== TC06: ONLINE — Language Normal Flow =====");
        if (isAirplaneModeOn()) setAirplaneMode(false);
        Thread.sleep(2000);

        forceStop();
        launchApp();
        boolean found = waitForLanguageScreen();
        System.out.println("Language screen found: " + found + " | Activity: " + currentActivity());

        Assertions.assertTrue(found, "[ONLINE] Language screen did not appear");

        if (!safeClick(By.id(APP_PACKAGE + ":id/imenglish"), 5))
            safeClick(By.xpath("//*[@text='English']"), 3);
        Thread.sleep(500);

        if (!safeClick(By.xpath("//*[@text='Next']"), 5)) adbTap(540, 1305);
        Thread.sleep(2000);

        String after = currentActivity();
        Assertions.assertFalse(after.contains("Language"),
            "[ONLINE] Stuck on Language after Next. Activity: " + after);
        System.out.println("PASS — online: navigated to: " + after);
    }

    /**
     * TC07 — OFFLINE: Language screen still loads (no network needed)
     */
    @Test
    @Order(7)
    @DisplayName("TC07 | OFFLINE: Language screen loads without internet")
    void tc07_Offline_LanguageLoads() throws Exception {
        System.out.println("\n===== TC07: OFFLINE — Language Loads =====");
        setAirplaneMode(true);
        Thread.sleep(2000);

        try {
            forceStop();
            launchApp();
            boolean found = waitForLanguageScreen();
            String activity = currentActivity();
            System.out.println("Language screen found offline: " + found + " | " + activity);

            Assertions.assertTrue(found,
                "[OFFLINE] Language screen did not appear. Current: " + activity);
            System.out.println("PASS — language screen loaded offline");
        } finally {
            setAirplaneMode(false);
            Thread.sleep(2000);
        }
    }

    /**
     * TC08 — OFFLINE: English selection and Next still work without internet
     */
    @Test
    @Order(8)
    @DisplayName("TC08 | OFFLINE: English + Next work without internet")
    void tc08_Offline_SelectAndNext() throws Exception {
        System.out.println("\n===== TC08: OFFLINE — Select English & Next =====");
        setAirplaneMode(true);
        Thread.sleep(2000);

        try {
            forceStop();
            launchApp();
            waitForLanguageScreen();

            boolean clicked = safeClick(By.id(APP_PACKAGE + ":id/imenglish"), 5);
            if (!clicked) clicked = safeClick(By.xpath("//*[@text='English']"), 3);
            Thread.sleep(500);

            boolean next = safeClick(By.xpath("//*[@text='Next']"), 5);
            if (!next) { adbTap(540, 1305); next = true; }
            Thread.sleep(2000);

            String after = currentActivity();
            System.out.println("[OFFLINE] After Next: " + after);

            Assertions.assertFalse(after.contains("Language"),
                "[OFFLINE] Stuck on Language screen without internet");
            System.out.println("PASS — offline: navigated to: " + after);
        } finally {
            setAirplaneMode(false);
            Thread.sleep(2000);
        }
    }

    /**
     * TC09 — RECONNECT: Language works after going offline then back online
     */
    @Test
    @Order(9)
    @DisplayName("TC09 | RECONNECT: Language works after offline→online")
    void tc09_Reconnect_LanguageWorks() throws Exception {
        System.out.println("\n===== TC09: RECONNECT — Offline → Online =====");
        setAirplaneMode(true);
        Thread.sleep(1500);
        forceStop();
        setAirplaneMode(false);
        Thread.sleep(2500);

        launchApp();
        boolean found = waitForLanguageScreen();
        System.out.println("Language after reconnect: " + found + " | " + currentActivity());

        Assertions.assertTrue(found,
            "[RECONNECT] Language screen not found after going back online");

        if (!safeClick(By.id(APP_PACKAGE + ":id/imenglish"), 5))
            safeClick(By.xpath("//*[@text='English']"), 3);
        Thread.sleep(500);
        if (!safeClick(By.xpath("//*[@text='Next']"), 5)) adbTap(540, 1305);
        Thread.sleep(2000);

        String after = currentActivity();
        Assertions.assertFalse(after.contains("Language"),
            "[RECONNECT] Stuck on Language. Activity: " + after);
        System.out.println("PASS — reconnect: navigated to: " + after);
    }

    /**
     * TC10 — Relaunch: Language screen appears again on fresh install launch
     */
    @Test
    @Order(10)
    @DisplayName("TC10 | RELAUNCH: Language screen on every fresh launch")
    void tc10_Relaunch_LanguageEveryTime() throws Exception {
        System.out.println("\n===== TC10: RELAUNCH — Language on Every Fresh Launch =====");
        forceStop();
        launchApp();
        boolean first = waitForLanguageScreen();
        System.out.println("First launch language: " + first);

        forceStop();
        launchApp();
        boolean second = waitForLanguageScreen();
        System.out.println("Second launch language: " + second);

        Assertions.assertTrue(first,  "Language not shown on 1st launch");
        Assertions.assertTrue(second, "Language not shown on 2nd launch");
        System.out.println("PASS — language screen appears on every fresh launch");
    }
}
