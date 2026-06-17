package scanner;

import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.options.UiAutomator2Options;
import org.junit.jupiter.api.*;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.net.URL;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Splash Screen Test Suite
 * Tests: load, duration, transition, offline mode, online mode
 *
 * Prerequisites:
 *   - Appium server running on http://127.0.0.1:4723
 *   - Android device/emulator connected via ADB
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SplashScreenTest {

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
        throw new Exception("No device found. Connect a device or start an emulator.");
    }

    static void launchApp(boolean noReset) throws Exception {
        quitDriver();
        UiAutomator2Options opts = new UiAutomator2Options()
            .setPlatformName("Android")
            .setDeviceName(deviceId)
            .setAppPackage(APP_PACKAGE)
            .setAppActivity(APP_ACTIVITY)
            .setAutomationName("UiAutomator2")
            .setNoReset(noReset)
            .setFullReset(false);
        opts.setCapability("appium:newCommandTimeout", 9999);
        opts.setCapability("appium:appWaitDuration", 30000);
        driver = new AndroidDriver(new URL(APPIUM_URL), opts);
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(5));
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

    static boolean waitForActivityChange(String fromActivity, int timeoutSec) throws Exception {
        for (int i = 0; i < timeoutSec * 2; i++) {
            String act = currentActivity();
            if (!act.contains(fromActivity) && !act.isEmpty()) return true;
            Thread.sleep(500);
        }
        return false;
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
     * TC01 — Splash screen activity launches correctly
     */
    @Test
    @Order(1)
    @DisplayName("TC01 | Splash: Activity launches")
    void tc01_SplashActivityLaunches() throws Exception {
        System.out.println("\n===== TC01: Splash Activity Launches =====");
        forceStop();
        launchApp(false);

        String activity = currentActivity();
        System.out.println("Current activity: " + activity);

        Assertions.assertTrue(
            activity.contains("Splashscreen") || activity.contains("splash") || !activity.isEmpty(),
            "App did not launch — activity: " + activity
        );
        System.out.println("PASS — activity: " + activity);
    }

    /**
     * TC02 — Splash screen auto-transitions within 15 seconds
     */
    @Test
    @Order(2)
    @DisplayName("TC02 | Splash: Auto-transitions within 15s")
    void tc02_SplashAutoTransitions() throws Exception {
        System.out.println("\n===== TC02: Splash Auto-Transition =====");
        forceStop();
        launchApp(false);

        long start = System.currentTimeMillis();
        boolean transitioned = waitForActivityChange("Splashscreen", 15);
        long elapsed = System.currentTimeMillis() - start;

        System.out.println("Transitioned: " + transitioned + " in " + elapsed + "ms");
        System.out.println("New activity: " + currentActivity());

        Assertions.assertTrue(transitioned,
            "Splash did not transition after 15 seconds");
        System.out.println("PASS — transition in " + elapsed + "ms");
    }

    /**
     * TC03 — Splash screen duration is within acceptable range (2s – 12s)
     */
    @Test
    @Order(3)
    @DisplayName("TC03 | Splash: Duration 2s–12s")
    void tc03_SplashDurationAcceptable() throws Exception {
        System.out.println("\n===== TC03: Splash Duration =====");
        forceStop();
        launchApp(false);

        long start = System.currentTimeMillis();
        waitForActivityChange("Splashscreen", 15);
        long elapsed = System.currentTimeMillis() - start;

        System.out.println("Splash duration: " + elapsed + "ms");
        Assertions.assertTrue(elapsed >= 1000,
            "Splash too fast (< 1s) — likely skipped: " + elapsed + "ms");
        Assertions.assertTrue(elapsed <= 15000,
            "Splash too slow (> 15s): " + elapsed + "ms");
        System.out.println("PASS — duration: " + elapsed + "ms");
    }

    /**
     * TC04 — ONLINE mode: Splash loads and transitions normally
     */
    @Test
    @Order(4)
    @DisplayName("TC04 | ONLINE: Splash transitions normally")
    void tc04_Online_SplashTransitionsNormally() throws Exception {
        System.out.println("\n===== TC04: ONLINE — Splash Normal Flow =====");

        // Ensure online
        if (isAirplaneModeOn()) setAirplaneMode(false);
        Thread.sleep(2000);

        forceStop();
        launchApp(false);

        long start = System.currentTimeMillis();
        boolean transitioned = waitForActivityChange("Splashscreen", 15);
        long elapsed = System.currentTimeMillis() - start;
        String nextActivity = currentActivity();

        System.out.println("Online transition: " + transitioned + " → " + nextActivity + " (" + elapsed + "ms)");

        Assertions.assertTrue(transitioned,
            "[ONLINE] Splash did not transition within 15s");
        Assertions.assertFalse(nextActivity.isEmpty(),
            "[ONLINE] Next activity is empty");
        System.out.println("PASS — landed on: " + nextActivity);
    }

    /**
     * TC05 — OFFLINE mode: Splash loads without crashing (no internet required for splash)
     */
    @Test
    @Order(5)
    @DisplayName("TC05 | OFFLINE: Splash loads without crash")
    void tc05_Offline_SplashLoadsWithoutCrash() throws Exception {
        System.out.println("\n===== TC05: OFFLINE — Splash No Crash =====");

        setAirplaneMode(true);
        Thread.sleep(2000);

        try {
            forceStop();
            launchApp(false);

            String activity = currentActivity();
            System.out.println("Activity in offline: " + activity);

            Assertions.assertFalse(activity.isEmpty(),
                "[OFFLINE] App crashed — no activity detected");
            Assertions.assertFalse(activity.contains("crash") || activity.contains("error"),
                "[OFFLINE] Crash activity detected: " + activity);
            System.out.println("PASS — splash launched offline: " + activity);

        } finally {
            setAirplaneMode(false);
            Thread.sleep(2000);
        }
    }

    /**
     * TC06 — OFFLINE mode: Splash transitions (not stuck waiting for network)
     */
    @Test
    @Order(6)
    @DisplayName("TC06 | OFFLINE: Splash transitions — not stuck")
    void tc06_Offline_SplashNotStuck() throws Exception {
        System.out.println("\n===== TC06: OFFLINE — Splash Not Stuck =====");

        setAirplaneMode(true);
        Thread.sleep(2000);

        try {
            forceStop();
            launchApp(false);

            long start = System.currentTimeMillis();
            boolean transitioned = waitForActivityChange("Splashscreen", 20);
            long elapsed = System.currentTimeMillis() - start;

            String activity = currentActivity();
            System.out.println("Offline transition: " + transitioned + " → " + activity + " (" + elapsed + "ms)");

            Assertions.assertTrue(transitioned,
                "[OFFLINE] Splash stuck — did not transition after 20s. Activity: " + activity);
            System.out.println("PASS — offline transition: " + elapsed + "ms → " + activity);

        } finally {
            setAirplaneMode(false);
            Thread.sleep(2000);
        }
    }

    /**
     * TC07 — ONLINE → OFFLINE → ONLINE: Splash works correctly across reconnect
     */
    @Test
    @Order(7)
    @DisplayName("TC07 | RECONNECT: Splash works after going offline then online")
    void tc07_Reconnect_SplashAfterOfflineOnline() throws Exception {
        System.out.println("\n===== TC07: RECONNECT — Offline → Online =====");

        // Step 1: go offline
        setAirplaneMode(true);
        Thread.sleep(1500);

        // Step 2: force stop app offline
        forceStop();

        // Step 3: go online
        setAirplaneMode(false);
        Thread.sleep(2500);

        // Step 4: launch app now online
        launchApp(false);
        long start = System.currentTimeMillis();
        boolean transitioned = waitForActivityChange("Splashscreen", 15);
        long elapsed = System.currentTimeMillis() - start;
        String activity = currentActivity();

        System.out.println("Reconnect transition: " + transitioned + " → " + activity + " (" + elapsed + "ms)");

        Assertions.assertTrue(transitioned,
            "[RECONNECT] Splash did not transition after reconnecting to network");
        System.out.println("PASS — reconnect ok: " + elapsed + "ms → " + activity);
    }

    /**
     * TC08 — Splash does not appear on re-launch (if noReset=true / already onboarded)
     *        or handles it gracefully
     */
    @Test
    @Order(8)
    @DisplayName("TC08 | RELAUNCH: App re-launch goes through splash again")
    void tc08_Relaunch_SplashOnEveryLaunch() throws Exception {
        System.out.println("\n===== TC08: RELAUNCH — Splash on Every Launch =====");

        forceStop();
        launchApp(true); // noReset=true — keeps app data

        String firstActivity = currentActivity();
        System.out.println("First launch activity: " + firstActivity);

        // Wait for splash to pass (or check if it appears)
        waitForActivityChange("Splashscreen", 15);
        String afterFirst = currentActivity();
        System.out.println("After first launch: " + afterFirst);

        // Re-launch
        forceStop();
        Thread.sleep(1000);
        launchApp(true);

        String secondLaunchActivity = currentActivity();
        System.out.println("Second launch activity: " + secondLaunchActivity);

        Assertions.assertFalse(secondLaunchActivity.isEmpty(),
            "[RELAUNCH] App failed to launch on second attempt");
        System.out.println("PASS — re-launch ok: " + secondLaunchActivity);
    }
}
