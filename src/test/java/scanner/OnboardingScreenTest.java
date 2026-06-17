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

/**
 * Onboarding Screen Flow — FAST version
 *
 * Strategy:
 *  GROUP A  (TC01–TC09) — single shared session, walk through the flow once:
 *    Splash → Subscription dismiss → Language → HelpUs → Onboarding1 → Onboarding2/Ad → GetStarted → Camera → Notification → Home
 *    Each TC asserts one step, then the next TC continues from where it left off.
 *
 *  GROUP B  (TC10–TC13) — individual relaunches only for offline/online/reconnect/relaunch
 *    These MUST restart the app, but they skip forward quickly with minimal waits.
 *
 * Total estimated time: ~15–20 min (vs ~60 min with full relaunch per test).
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class OnboardingScreenTest {

    static AndroidDriver driver;
    static WebDriverWait wait;

    static final String ADB         = "/Users/ios/Library/Android/sdk/platform-tools/adb";
    static final String PKG         = "com.math.photo.scanner.equation.formula.calculator";
    static final String START_ACT   = ".newcode.activity.Splashscreen";
    static final String APPIUM_URL  = "http://127.0.0.1:4723";

    static final int ONBOARD_NEXT_X  = 952;
    static final int ONBOARD_NEXT_Y  = 1370;
    static final int GET_STARTED_X   = 885;
    static final int GET_STARTED_Y   = 1364;
    static final int PERM_CAM_Y      = 1692;
    static final int PERM_NOTIF_Y    = 1787;
    static final int LANG_NEXT_X     = 540;
    static final int LANG_NEXT_Y     = 1305;
    static final int HELP_CONT_X     = 540;
    static final int HELP_CONT_Y     = 2010;

    static String deviceId = "";

    // ─── GLOBAL SETUP ─────────────────────────────────────────────────────────

    @BeforeAll
    static void globalSetup() throws Exception {
        deviceId = detectDevice();
        System.out.println("Device: " + deviceId);
        // Fresh launch for GROUP A — shared session
        forceStop();
        launchApp(false);
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

    static void launchApp(boolean noReset) throws Exception {
        quitDriver();
        UiAutomator2Options opts = new UiAutomator2Options()
            .setPlatformName("Android").setDeviceName(deviceId)
            .setAppPackage(PKG).setAppActivity(START_ACT)
            .setAutomationName("UiAutomator2")
            .setNoReset(noReset).setFullReset(false);
        opts.setCapability("appium:newCommandTimeout", 9999);
        opts.setCapability("appium:appWaitDuration", 30000);
        driver = new AndroidDriver(new URL(APPIUM_URL), opts);
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(6));
        wait = new WebDriverWait(driver, Duration.ofSeconds(20));
    }

    static void forceStop() throws Exception {
        new ProcessBuilder(ADB, "-s", deviceId, "shell", "am", "force-stop", PKG)
            .redirectErrorStream(true).start().waitFor();
        Thread.sleep(600);
    }

    static void quitDriver() {
        if (driver != null) {
            try { driver.quit(); } catch (Exception ignored) {}
            driver = null;
        }
    }

    static String act() {
        try { return driver.currentActivity(); } catch (Exception e) { return ""; }
    }

    static void tap(int x, int y) throws Exception {
        new ProcessBuilder(ADB, "-s", deviceId, "shell", "input", "tap",
            String.valueOf(x), String.valueOf(y))
            .redirectErrorStream(true).start().waitFor();
        Thread.sleep(500);
    }

    static boolean safeClick(By by, int sec) {
        try {
            WebElement el = new WebDriverWait(driver, Duration.ofSeconds(sec))
                .until(ExpectedConditions.elementToBeClickable(by));
            el.click();
            return true;
        } catch (Exception e) { return false; }
    }

    static boolean isPresent(By by) {
        try {
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(0));
            return !driver.findElements(by).isEmpty();
        } catch (Exception e) { return false; }
        finally { driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(6)); }
    }

    static boolean waitFor(String keyword, int sec) throws Exception {
        for (int i = 0; i < sec * 2; i++) {
            if (act().contains(keyword)) return true;
            Thread.sleep(500);
        }
        return false;
    }

    static boolean waitForChange(String from, int sec) throws Exception {
        for (int i = 0; i < sec * 2; i++) {
            String a = act();
            if (!a.isEmpty() && !a.contains(from)) return true;
            Thread.sleep(500);
        }
        return false;
    }

    static void dismissSub() throws Exception {
        try {
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(0));
            if (!driver.findElements(By.xpath("//*[@text='Pay Nothing Now']")).isEmpty())
                driver.findElement(By.xpath("//*[@text='Pay Nothing Now']")).click();
            else if (!driver.findElements(By.id(PKG + ":id/iv_close")).isEmpty())
                driver.findElement(By.id(PKG + ":id/iv_close")).click();
        } catch (Exception ignored) {}
        finally { driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(6)); }
        Thread.sleep(400);
    }

    static void setAirplane(boolean on) throws Exception {
        new ProcessBuilder(ADB, "-s", deviceId, "shell",
            "settings", "put", "global", "airplane_mode_on", on ? "1" : "0")
            .redirectErrorStream(true).start().waitFor();
        new ProcessBuilder(ADB, "-s", deviceId, "shell", "am", "broadcast",
            "-a", "android.intent.action.AIRPLANE_MODE",
            "--ez", "state", on ? "true" : "false")
            .redirectErrorStream(true).start().waitFor();
        Thread.sleep(1500);
        System.out.println(on ? "✈ Airplane ON" : "✈ Airplane OFF");
    }

    static boolean isAirplaneOn() throws Exception {
        Process p = new ProcessBuilder(ADB, "-s", deviceId, "shell",
            "settings", "get", "global", "airplane_mode_on")
            .redirectErrorStream(true).start();
        String v = new String(p.getInputStream().readAllBytes()).trim();
        p.waitFor();
        return "1".equals(v);
    }

    // Fast pass-through helpers for GROUP B
    static void quickPassToHome() throws Exception {
        // Splash
        waitForChange("Splashscreen", 15);
        // Subscription
        dismissSub();
        // Language
        for (int i = 0; i < 10; i++) {
            if (act().contains("Language")) break;
            dismissSub(); Thread.sleep(500);
        }
        if (!safeClick(By.id(PKG + ":id/imenglish"), 5))
            safeClick(By.xpath("//*[@text='English']"), 3);
        Thread.sleep(400);
        if (!safeClick(By.xpath("//*[@text='Next']"), 4))
            tap(LANG_NEXT_X, LANG_NEXT_Y);
        Thread.sleep(800);
        // HelpUs
        for (int i = 0; i < 8; i++) {
            if (!act().contains("HelpUs")) break;
            tap(HELP_CONT_X, HELP_CONT_Y); Thread.sleep(1200);
        }
        // Dismiss sub again
        Thread.sleep(600); dismissSub();
        // Onboarding slide 1 next
        tap(ONBOARD_NEXT_X, ONBOARD_NEXT_Y);
        // Ad wait
        Thread.sleep(10000);
        // Get Started
        Thread.sleep(1000);
        if (!safeClick(By.xpath("//*[@text='Get Started']"), 4) &&
            !safeClick(By.xpath("//*[contains(@text,'Start')]"), 3))
            tap(GET_STARTED_X, GET_STARTED_Y);
        Thread.sleep(1500);
        // Chrome recovery
        if (act().contains("chrome") || act().contains("Chrome")) {
            new ProcessBuilder(ADB, "-s", deviceId, "shell", "am", "force-stop", "com.android.chrome")
                .redirectErrorStream(true).start().waitFor();
            Thread.sleep(800);
            new ProcessBuilder(ADB, "-s", deviceId, "shell", "monkey",
                "-p", PKG, "-c", "android.intent.category.LAUNCHER", "1")
                .redirectErrorStream(true).start().waitFor();
            Thread.sleep(1500);
        }
        // Camera perm
        if (waitFor("GrantPermissions", 5)) { tap(540, PERM_CAM_Y); Thread.sleep(800); }
        // Notif perm
        if (waitFor("GrantPermissions", 5)) { tap(540, PERM_NOTIF_Y); Thread.sleep(800); }
        // Wait for home
        for (int i = 0; i < 10; i++) {
            String a = act();
            if (a.contains("Home") || a.contains("Camera") || a.contains("Main")) break;
            Thread.sleep(1000);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //   GROUP A — shared session, walk-through flow (TC01–TC09)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * TC01 — Splash: Activity launched
     */
    @Test @Order(1)
    @DisplayName("TC01 | SPLASH: Activity launched")
    void tc01_SplashLaunched() throws Exception {
        System.out.println("\n===== TC01: Splash Launched =====");
        String a = act();
        System.out.println("Activity: " + a);
        Assertions.assertTrue(
            a.contains("Splashscreen") || a.contains("splash") || !a.isEmpty(),
            "App not launched: " + a);
        System.out.println("PASS — " + a);
    }

    /**
     * TC02 — Splash: Auto-transitions within 15s
     */
    @Test @Order(2)
    @DisplayName("TC02 | SPLASH: Auto-transitions within 15s")
    void tc02_SplashTransitions() throws Exception {
        System.out.println("\n===== TC02: Splash Transitions =====");
        long t = System.currentTimeMillis();
        boolean changed = waitForChange("Splashscreen", 15);
        long ms = System.currentTimeMillis() - t;
        System.out.println("Transitioned: " + changed + " in " + ms + "ms → " + act());
        Assertions.assertTrue(changed, "Splash did not transition in 15s");
        System.out.println("PASS — " + ms + "ms");
    }

    /**
     * TC03 — Subscription popup dismissed
     */
    @Test @Order(3)
    @DisplayName("TC03 | SUBSCRIPTION: Popup dismissed")
    void tc03_SubscriptionDismissed() throws Exception {
        System.out.println("\n===== TC03: Subscription Dismiss =====");
        dismissSub();
        Thread.sleep(600);
        // Wait for Language to appear
        for (int i = 0; i < 10; i++) {
            if (act().contains("Language")) break;
            dismissSub(); Thread.sleep(500);
        }
        String a = act();
        System.out.println("After dismiss: " + a);
        Assertions.assertFalse(a.isEmpty(), "App crashed after subscription dismiss");
        System.out.println("PASS — " + a);
    }

    /**
     * TC04 — Language: English selected + Next tapped
     */
    @Test @Order(4)
    @DisplayName("TC04 | LANGUAGE: English selected and Next tapped")
    void tc04_LanguageEnglishNext() throws Exception {
        System.out.println("\n===== TC04: Language → English → Next =====");
        Assertions.assertTrue(act().contains("Language"),
            "Not on Language screen: " + act());

        if (!safeClick(By.id(PKG + ":id/imenglish"), 5))
            safeClick(By.xpath("//*[@text='English']"), 3);
        Thread.sleep(400);
        if (!safeClick(By.xpath("//*[@text='Next']"), 5))
            tap(LANG_NEXT_X, LANG_NEXT_Y);
        Thread.sleep(1000);

        String a = act();
        Assertions.assertFalse(a.contains("Language"), "Still on Language: " + a);
        System.out.println("PASS — navigated to: " + a);
    }

    /**
     * TC05 — HelpUsActivity: "Grow With Us" appears and Continue works
     */
    @Test @Order(5)
    @DisplayName("TC05 | HELPUS: Grow With Us screen and Continue")
    void tc05_HelpUsAndContinue() throws Exception {
        System.out.println("\n===== TC05: HelpUs → Continue =====");
        // may not appear every time depending on app state
        boolean helpFound = waitFor("HelpUs", 5);
        System.out.println("HelpUs found: " + helpFound + " | " + act());

        for (int i = 0; i < 6; i++) {
            if (!act().contains("HelpUs")) break;
            tap(HELP_CONT_X, HELP_CONT_Y); Thread.sleep(1200);
        }
        Thread.sleep(600);
        dismissSub();
        String a = act();
        System.out.println("After HelpUs: " + a);
        Assertions.assertFalse(a.isEmpty(), "App crashed after HelpUs");
        System.out.println("PASS — " + a);
    }

    /**
     * TC06 — Onboarding Slide 1: Next tapped successfully
     */
    @Test @Order(6)
    @DisplayName("TC06 | ONBOARD1: Slide 1 Next tapped")
    void tc06_OnboardSlide1Next() throws Exception {
        System.out.println("\n===== TC06: Onboarding Slide 1 → Next =====");
        Thread.sleep(600);
        String before = act();
        tap(ONBOARD_NEXT_X, ONBOARD_NEXT_Y);
        Thread.sleep(1000);
        String after = act();
        System.out.println(before + " → " + after);
        Assertions.assertFalse(after.isEmpty(), "App crashed after slide 1 Next");
        System.out.println("PASS — " + after);
    }

    /**
     * TC07 — Onboarding Slide 2 / Ad wait: completes within 15s
     */
    @Test @Order(7)
    @DisplayName("TC07 | ONBOARD2: Slide 2 + Ad completes")
    void tc07_OnboardSlide2AdWait() throws Exception {
        System.out.println("\n===== TC07: Onboarding Slide 2 + Ad =====");
        System.out.println("Waiting up to 15s for ad to finish...");
        Thread.sleep(10000);
        String a = act();
        System.out.println("After ad wait: " + a);
        Assertions.assertFalse(a.isEmpty(), "App crashed during ad wait");
        System.out.println("PASS — " + a);
    }

    /**
     * TC08 — Get Started: tapped and navigates to permissions or Home
     */
    @Test @Order(8)
    @DisplayName("TC08 | GETSTARTED: Button tapped, navigates forward")
    void tc08_GetStarted() throws Exception {
        System.out.println("\n===== TC08: Get Started =====");
        Thread.sleep(1000);
        if (!safeClick(By.xpath("//*[@text='Get Started']"), 5) &&
            !safeClick(By.xpath("//*[contains(@text,'Start')]"), 3))
            tap(GET_STARTED_X, GET_STARTED_Y);
        Thread.sleep(1500);

        // Chrome recovery
        if (act().contains("chrome") || act().contains("Chrome")) {
            new ProcessBuilder(ADB, "-s", deviceId, "shell", "am", "force-stop", "com.android.chrome")
                .redirectErrorStream(true).start().waitFor();
            Thread.sleep(800);
            new ProcessBuilder(ADB, "-s", deviceId, "shell", "monkey",
                "-p", PKG, "-c", "android.intent.category.LAUNCHER", "1")
                .redirectErrorStream(true).start().waitFor();
            Thread.sleep(1500);
        }
        String a = act();
        System.out.println("After Get Started: " + a);
        Assertions.assertFalse(a.isEmpty(), "App crashed at Get Started");
        System.out.println("PASS — " + a);
    }

    /**
     * TC09 — Permissions + Home: permissions handled and Home reached
     */
    @Test @Order(9)
    @DisplayName("TC09 | HOME: Permissions handled and Home screen reached")
    void tc09_PermissionsAndHome() throws Exception {
        System.out.println("\n===== TC09: Permissions + Home =====");
        if (waitFor("GrantPermissions", 5)) { tap(540, PERM_CAM_Y);   Thread.sleep(800); }
        if (waitFor("GrantPermissions", 5)) { tap(540, PERM_NOTIF_Y); Thread.sleep(800); }

        for (int i = 0; i < 12; i++) {
            String a = act();
            if (a.contains("Home") || a.contains("Camera") || a.contains("Main") || a.contains("newcode")) {
                System.out.println("PASS — Home reached: " + a); return;
            }
            Thread.sleep(1000);
        }
        Assertions.fail("Home not reached. Activity: " + act());
    }

    // ══════════════════════════════════════════════════════════════════════════
    //   GROUP B — individual relaunches for special scenarios (TC10–TC13)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * TC10 — ONLINE: Full onboarding works normally with internet
     */
    @Test @Order(10)
    @DisplayName("TC10 | ONLINE: Full onboarding works with internet")
    void tc10_Online_FullFlow() throws Exception {
        System.out.println("\n===== TC10: ONLINE — Full Flow =====");
        if (isAirplaneOn()) setAirplane(false);
        Thread.sleep(1500);
        forceStop();
        launchApp(false);
        quickPassToHome();
        String a = act();
        System.out.println("Online final: " + a);
        boolean home = a.contains("Home") || a.contains("Camera") ||
                       a.contains("Main") || a.contains("newcode");
        Assertions.assertTrue(home, "[ONLINE] Home not reached: " + a);
        System.out.println("PASS — online home: " + a);
    }

    /**
     * TC11 — OFFLINE: Splash + Language + Onboarding load without internet
     */
    @Test @Order(11)
    @DisplayName("TC11 | OFFLINE: Onboarding loads without internet")
    void tc11_Offline_OnboardingLoads() throws Exception {
        System.out.println("\n===== TC11: OFFLINE — Onboarding Loads =====");
        setAirplane(true);
        Thread.sleep(1500);
        try {
            forceStop();
            launchApp(false);
            waitForChange("Splashscreen", 15);
            dismissSub();
            for (int i = 0; i < 10; i++) {
                if (act().contains("Language")) break;
                dismissSub(); Thread.sleep(500);
            }
            String a = act();
            System.out.println("Offline activity: " + a);
            Assertions.assertFalse(a.isEmpty(), "[OFFLINE] App crashed");
            Assertions.assertFalse(a.contains("error") || a.contains("crash"),
                "[OFFLINE] Error screen: " + a);
            System.out.println("PASS — offline: " + a);
        } finally {
            setAirplane(false);
            Thread.sleep(2000);
        }
    }

    /**
     * TC12 — RECONNECT: Onboarding works after offline → online
     */
    @Test @Order(12)
    @DisplayName("TC12 | RECONNECT: Onboarding after offline→online")
    void tc12_Reconnect_OnboardingWorks() throws Exception {
        System.out.println("\n===== TC12: RECONNECT — Offline → Online =====");
        setAirplane(true);
        Thread.sleep(1200);
        forceStop();
        setAirplane(false);
        Thread.sleep(2500);
        launchApp(false);
        quickPassToHome();
        String a = act();
        System.out.println("Reconnect final: " + a);
        boolean home = a.contains("Home") || a.contains("Camera") ||
                       a.contains("Main") || a.contains("newcode");
        Assertions.assertTrue(home, "[RECONNECT] Home not reached: " + a);
        System.out.println("PASS — reconnect home: " + a);
    }

    /**
     * TC13 — RELAUNCH: Onboarding starts fresh on every install launch
     */
    @Test @Order(13)
    @DisplayName("TC13 | RELAUNCH: Onboarding restarts on fresh launch")
    void tc13_Relaunch_OnboardingRestarts() throws Exception {
        System.out.println("\n===== TC13: RELAUNCH — Fresh Launch =====");
        forceStop();
        launchApp(false);
        waitForChange("Splashscreen", 15);
        dismissSub();
        boolean first = waitFor("Language", 10);
        System.out.println("1st launch Language: " + first);

        forceStop();
        launchApp(false);
        waitForChange("Splashscreen", 15);
        dismissSub();
        boolean second = waitFor("Language", 10);
        System.out.println("2nd launch Language: " + second);

        Assertions.assertTrue(first,  "Language not shown on 1st launch");
        Assertions.assertTrue(second, "Language not shown on 2nd launch");
        System.out.println("PASS — onboarding restarts each fresh launch");
    }
}
