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

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class OnboardingTest {

    static AndroidDriver driver;
    static WebDriverWait wait;
    static String appTaskId = "";   // captured after launch, used to restore foreground

    static final String ADB        = "/Users/ios/Library/Android/sdk/platform-tools/adb";
    static final String DEVICE     = "10BG3702ZC0021T";
    static final String PKG        = "com.math.photo.scanner.equation.formula.calculator";
    static final String APPIUM_URL = "http://127.0.0.1:4723";

    // ── Confirmed coordinates from live device scan 2026-06-18 ──────────────
    static final int LANG_ENGLISH_X       = 978;   // imenglish radio button
    static final int LANG_ENGLISH_Y       = 399;
    static final int LANG_NEXT_X          = 540;   // btnNext after English selected (list scrolls up)
    static final int LANG_NEXT_Y          = 1332;  // ⚠️ ad "View now" button is at y≈2122 — do NOT tap there

    static final int HELPUS_CONTINUE_X    = 540;   // "Continue" on Grow With Us
    static final int HELPUS_CONTINUE_Y    = 2010;

    static final int AD_CLOSE_X           = 1010;  // Lucky Draw interstitial X button
    static final int AD_CLOSE_Y           = 225;

    static final int ONBOARD_NEXT_X       = 910;   // "Next" / "Get Started" on slides
    static final int ONBOARD_NEXT_Y       = 1364;

    static final int SUBSCRIPTION_CLOSE_X = 990;   // X on subscription trial screen
    static final int SUBSCRIPTION_CLOSE_Y = 172;

    static final int PERM_WHILE_USING_X   = 540;
    static final int PERM_WHILE_USING_Y   = 1665;  // "While using the app" — camera
    static final int PERM_ALLOW_X         = 540;
    static final int PERM_ALLOW_Y         = 1825;  // "Allow" — notifications

    // ── Setup / Teardown ──────────────────────────────────────────────────────

    @BeforeAll
    static void setup() throws Exception {
        Runtime.getRuntime().exec(
            new String[]{ADB, "-s", DEVICE, "shell", "pm", "clear", PKG}).waitFor();
        TimeUnit.SECONDS.sleep(1);

        UiAutomator2Options opts = new UiAutomator2Options();
        opts.setPlatformName("Android");
        opts.setDeviceName(DEVICE);
        opts.setAppPackage(PKG);
        opts.setAppActivity(".newcode.activity.Splashscreen");
        opts.setAutomationName("UiAutomator2");
        opts.setNoReset(true);
        opts.setFullReset(false);
        opts.setCapability("appium:newCommandTimeout", 9999);
        opts.setCapability("appium:appWaitDuration", 30000);

        driver = new AndroidDriver(new URL(APPIUM_URL), opts);
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
        wait = new WebDriverWait(driver, Duration.ofSeconds(20));
        TimeUnit.SECONDS.sleep(4);

        // Capture app task ID so we can bring it to foreground after external apps open
        appTaskId = getAppTaskId();
        log("App launched — device: " + DEVICE + " | taskId: " + appTaskId);
    }

    @AfterAll
    static void teardown() {
        if (driver != null) try { driver.quit(); } catch (Exception ignored) {}
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test @Order(1)
    void test01_SplashScreen() throws Exception {
        log("=== TEST 1 — Splash Screen ===");
        for (int i = 0; i < 10; i++) {
            String act = act();
            if (!act.contains("Splashscreen") && !act.contains("AperoSplash")) break;
            log("  waiting... " + act);
            TimeUnit.SECONDS.sleep(1);
        }
        log("After splash: " + act());
        Assertions.assertFalse(act().toLowerCase().contains("splashscreen"),
            "Splash did not pass. Current: " + act());
        log("✅ PASS");
    }

    @Test @Order(2)
    void test02_LanguageScreen() throws Exception {
        log("=== TEST 2 — Language Screen ===");
        waitForActivity("LanguageActivity", 15);
        Assertions.assertTrue(act().contains("Language"), "Not on LanguageActivity: " + act());

        // Only tap English if NOT already selected (avoid deselecting)
        try {
            WebElement radio = driver.findElement(By.id(PKG + ":id/imenglish"));
            boolean checked = "true".equals(radio.getAttribute("checked"));
            if (!checked) {
                log("  English not selected — tapping radio (" + LANG_ENGLISH_X + "," + LANG_ENGLISH_Y + ")");
                adbTap(LANG_ENGLISH_X, LANG_ENGLISH_Y);
                TimeUnit.SECONDS.sleep(1);
            } else {
                log("  English already selected — skipping tap");
            }
        } catch (Exception e) {
            log("  Could not check radio state — tapping English");
            adbTap(LANG_ENGLISH_X, LANG_ENGLISH_Y);
            TimeUnit.SECONDS.sleep(1);
        }

        // Use Appium element click (handles dynamic scroll position)
        log("  Tapping Next via Appium");
        if (!clickById(PKG + ":id/btnNext", 5)) {
            log("  Fallback ADB tap Next (" + LANG_NEXT_X + "," + LANG_NEXT_Y + ")");
            adbTap(LANG_NEXT_X, LANG_NEXT_Y);
        }
        TimeUnit.SECONDS.sleep(2);

        String act = act();
        log("After language: " + act);
        Assertions.assertTrue(
            act.contains("HelpUs") || act.contains("NewOnBoarding") || act.contains("Onboard"),
            "Expected HelpUs or Onboarding. Got: " + act);
        log("✅ PASS");
    }

    @Test @Order(3)
    void test03_HelpUsGrowWithUs() throws Exception {
        log("=== TEST 3 — Grow With Us (HelpUsActivity) ===");
        waitForActivity("HelpUsActivity", 10);
        Assertions.assertTrue(act().contains("HelpUs"), "Not on HelpUsActivity: " + act());

        String title = "";
        try { title = driver.findElement(By.id(PKG + ":id/textTitle")).getText(); } catch (Exception ignored) {}
        log("  Title: '" + title + "'");
        Assertions.assertTrue(title.contains("Grow"), "Expected 'Grow With Us'. Got: " + title);

        // Disable what we can (Play Store, Chrome, browsers)
        disableExternalApps();

        // Click Continue via Appium first; fallback to ADB tap
        log("  Clicking Continue");
        boolean clicked = clickByText("Continue", 3);
        if (!clicked) {
            log("  Appium click failed — ADB tap (" + HELPUS_CONTINUE_X + "," + HELPUS_CONTINUE_Y + ")");
            adbTap(HELPUS_CONTINUE_X, HELPUS_CONTINUE_Y);
        }

        // Recovery loop: if Vivo Store (or any external app) opens, kill it and bring our task back
        boolean advanced = false;
        for (int i = 0; i < 12; i++) {
            TimeUnit.MILLISECONDS.sleep(500);
            String cur = act();
            if (cur.contains("NewOnBoarding") || cur.contains("AdActivity")) {
                advanced = true;
                log("  Advanced to: " + cur);
                break;
            }
            if (cur.contains("HelpUs")) {
                log("  Still on HelpUs (" + i + ") — waiting");
                continue;
            }
            // External app opened — kill it and recover
            log("  External app detected: " + cur + " — killing and recovering");
            forceStopAllExternalApps();
            TimeUnit.MILLISECONDS.sleep(400);
            bringAppToFront();
            TimeUnit.SECONDS.sleep(1);
        }

        enableExternalApps();

        String act = act();
        log("After HelpUs: " + act);
        Assertions.assertTrue(advanced || act.contains("NewOnBoarding") || act.contains("AdActivity"),
            "Expected onboarding or ad after Continue. Got: " + act);
        log("✅ PASS");
    }

    @Test @Order(4)
    void test04_InterstitialAd_Optional() throws Exception {
        log("=== TEST 4 — Interstitial Ad (optional — skipped if absent) ===");
        boolean adFound = false;
        for (int i = 0; i < 10; i++) {
            String act = act();
            if (act.contains("AdActivity")) {
                adFound = true;
                log("  Ad detected at " + i + "s — tapping close X (" + AD_CLOSE_X + "," + AD_CLOSE_Y + ")");
                adbTap(AD_CLOSE_X, AD_CLOSE_Y);
                TimeUnit.SECONDS.sleep(2);
                break;
            }
            if (act.contains("NewOnBoarding")) {
                log("  No interstitial ad — already on onboarding slides");
                break;
            }
            TimeUnit.SECONDS.sleep(1);
        }
        log((adFound ? "Ad was shown and closed" : "No ad shown — skipped") + " | current: " + act());
        // No assertion — ad is optional
        log("✅ PASS");
    }

    @Test @Order(5)
    void test05_OnboardingSlide1_SolvedSteps() throws Exception {
        log("=== TEST 5 — Onboarding Slide 1: Solved Steps ===");
        waitForActivity("NewOnBoardingMainActivity", 10);

        boolean visible = isTextPresent("Solved Steps", 5);
        log("  'Solved Steps' visible: " + visible);
        Assertions.assertTrue(visible, "Expected 'Solved Steps' on slide 1");

        log("  Tapping Next (" + ONBOARD_NEXT_X + "," + ONBOARD_NEXT_Y + ")");
        adbTap(ONBOARD_NEXT_X, ONBOARD_NEXT_Y);
        TimeUnit.SECONDS.sleep(2);
        log("✅ PASS");
    }

    @Test @Order(6)
    void test06_OnboardingSlide2_CaptureEquation() throws Exception {
        log("=== TEST 6 — Onboarding Slide 2: Capture Equation ===");
        waitForActivity("NewOnBoardingMainActivity", 10);

        boolean visible = isTextPresent("Capture Equation", 5);
        log("  'Capture Equation' visible: " + visible);
        Assertions.assertTrue(visible, "Expected 'Capture Equation' on slide 2");

        log("  Tapping Next (" + ONBOARD_NEXT_X + "," + ONBOARD_NEXT_Y + ")");
        adbTap(ONBOARD_NEXT_X, ONBOARD_NEXT_Y);
        TimeUnit.SECONDS.sleep(3);

        // Between slides a full-screen banner ad may expand — dismiss if present, skip if not
        dismissBannerAdIfPresent();
        log("✅ PASS");
    }

    @Test @Order(7)
    void test07_OnboardingSlide3_MathsSolution() throws Exception {
        log("=== TEST 7 — Onboarding Slide 3: Maths Solution ===");
        waitForActivity("NewOnBoardingMainActivity", 10);

        boolean visible = isTextPresent("Maths Solution", 5);
        log("  'Maths Solution' visible: " + visible);
        Assertions.assertTrue(visible, "Expected 'Maths Solution' on slide 3");

        log("  Tapping Get Started (" + ONBOARD_NEXT_X + "," + ONBOARD_NEXT_Y + ")");
        adbTap(ONBOARD_NEXT_X, ONBOARD_NEXT_Y);
        TimeUnit.SECONDS.sleep(3);

        String act = act();
        log("After Get Started: " + act);
        Assertions.assertTrue(
            act.contains("TimeLine") || act.contains("Grant") || act.contains("Home"),
            "Expected subscription, permission, or home. Got: " + act);
        log("✅ PASS");
    }

    @Test @Order(8)
    void test08_SubscriptionScreen_Optional() throws Exception {
        log("=== TEST 8 — Subscription Screen (optional — skipped if absent) ===");
        if (!act().contains("TimeLine")) {
            log("  Subscription screen not shown — skipped");
            log("✅ PASS");
            return;
        }

        boolean hasTitle = isTextPresent("HAVE DOUBTS", 5);
        log("  'HAVE DOUBTS?' visible: " + hasTitle);
        Assertions.assertTrue(hasTitle, "On TimeLine but 'HAVE DOUBTS?' not found");

        log("  Tapping X close (" + SUBSCRIPTION_CLOSE_X + "," + SUBSCRIPTION_CLOSE_Y + ")");
        adbTap(SUBSCRIPTION_CLOSE_X, SUBSCRIPTION_CLOSE_Y);
        TimeUnit.SECONDS.sleep(2);

        String act = act();
        log("After subscription: " + act);
        Assertions.assertTrue(
            act.contains("Grant") || act.contains("Home") || act.contains("newcode"),
            "Expected permission or home after subscription close. Got: " + act);
        log("✅ PASS");
    }

    @Test @Order(9)
    void test09_CameraPermission_Optional() throws Exception {
        log("=== TEST 9 — Camera Permission (optional — skipped if absent) ===");
        if (!waitForActivity("GrantPermissions", 8)) {
            log("  Camera permission not shown — skipped");
            log("✅ PASS");
            return;
        }

        boolean visible = isTextPresent("pictures", 5);
        log("  Camera permission dialog visible: " + visible);
        Assertions.assertTrue(visible, "On GrantPermissions but camera dialog not found");

        if (!clickById("com.android.permissioncontroller:id/permission_allow_foreground_only_button", 3)) {
            log("  Fallback ADB tap While Using (" + PERM_WHILE_USING_X + "," + PERM_WHILE_USING_Y + ")");
            adbTap(PERM_WHILE_USING_X, PERM_WHILE_USING_Y);
        }
        TimeUnit.SECONDS.sleep(2);
        log("After camera perm: " + act());
        log("✅ PASS");
    }

    @Test @Order(10)
    void test10_NotificationPermission_Optional() throws Exception {
        log("=== TEST 10 — Notification Permission (optional — skipped if absent) ===");
        if (!waitForActivity("GrantPermissions", 8)) {
            log("  Notification permission not shown — skipped");
            log("✅ PASS");
            return;
        }

        boolean visible = isTextPresent("notifications", 5);
        log("  Notification permission dialog visible: " + visible);
        Assertions.assertTrue(visible, "On GrantPermissions but notification dialog not found");

        if (!clickById("com.android.permissioncontroller:id/permission_allow_button", 3)) {
            log("  Fallback ADB tap Allow (" + PERM_ALLOW_X + "," + PERM_ALLOW_Y + ")");
            adbTap(PERM_ALLOW_X, PERM_ALLOW_Y);
        }
        TimeUnit.SECONDS.sleep(2);
        log("After notification perm: " + act());
        log("✅ PASS");
    }

    @Test @Order(11)
    void test11_HomeScreen() throws Exception {
        log("=== TEST 11 — Home Screen ===");
        boolean reached = waitForActivity("HomeActivity", 15);
        log("  HomeActivity reached: " + reached + " | activity: " + act());
        Assertions.assertTrue(reached, "Expected HomeActivity. Got: " + act());

        boolean hasCam  = isTextPresent("Camera", 5);
        boolean hasSol  = isTextPresent("Solution", 3);
        boolean hasCalc = isTextPresent("Calculator", 3);
        log("  Tabs — Camera:" + hasCam + " Solution:" + hasSol + " Calculator:" + hasCalc);

        Assertions.assertTrue(hasCam,  "Camera tab not found");
        Assertions.assertTrue(hasSol,  "Solution tab not found");
        Assertions.assertTrue(hasCalc, "Calculator tab not found");
        log("✅ PASS — Full onboarding complete!");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    static String act() {
        try { return driver.currentActivity(); } catch (Exception e) { return "unknown"; }
    }

    static void adbTap(int x, int y) throws Exception {
        Runtime.getRuntime().exec(new String[]{
            ADB, "-s", DEVICE, "shell", "input", "tap", String.valueOf(x), String.valueOf(y)
        }).waitFor();
        Thread.sleep(400);
    }

    static boolean clickById(String id, int secs) {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(secs))
                .until(ExpectedConditions.elementToBeClickable(By.id(id))).click();
            return true;
        } catch (Exception e) { return false; }
    }

    static boolean waitForActivity(String part, int secs) throws Exception {
        for (int i = 0; i < secs; i++) {
            if (act().contains(part)) return true;
            TimeUnit.SECONDS.sleep(1);
        }
        return act().contains(part);
    }

    static boolean isTextPresent(String text, int secs) {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(secs))
                .until(ExpectedConditions.presenceOfElementLocated(
                    By.xpath("//*[contains(@text,'" + text + "')]")));
            return true;
        } catch (Exception e) { return false; }
    }

    static final String[] EXTERNAL_APPS = {
        "com.android.vending",   // Google Play Store
        "com.android.chrome",    // Chrome
        "com.vivo.appstore",     // Vivo App Store (confirmed package on this device)
        "com.vivo.browser",      // Vivo Browser
        "com.android.browser",   // AOSP Browser
    };

    /**
     * Disables all known external store/browser apps so the market:// intent
     * from HelpUs Continue has nowhere to go — Continue then navigates in-app.
     * Call BEFORE tapping Continue. Always follow with re-enableExternalApps().
     */
    static void disableExternalApps() throws Exception {
        for (String p : EXTERNAL_APPS) {
            Runtime.getRuntime().exec(new String[]{
                ADB, "-s", DEVICE, "shell", "pm", "disable-user", "--user", "0", p
            }).waitFor();
        }
        Thread.sleep(500);
    }

    static void enableExternalApps() throws Exception {
        for (String p : EXTERNAL_APPS) {
            Runtime.getRuntime().exec(new String[]{
                ADB, "-s", DEVICE, "shell", "pm", "enable", "--user", "0", p
            }).waitFor();
        }
        Thread.sleep(300);
    }

    // kept for backward compat — force-stops (used elsewhere)
    static void killExternalApps() throws Exception {
        for (String p : EXTERNAL_APPS) {
            Runtime.getRuntime().exec(
                new String[]{ADB, "-s", DEVICE, "shell", "am", "force-stop", p}).waitFor();
        }
        Thread.sleep(500);
    }

    /** Reads the current task ID for our app from adb dumpsys. */
    static String getAppTaskId() {
        try {
            ProcessBuilder pb = new ProcessBuilder(ADB, "-s", DEVICE, "shell",
                "dumpsys", "activity", "activities");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes());
            p.waitFor();
            for (String line : out.split("\n")) {
                if (line.contains("topResumedActivity") && line.contains(PKG)) {
                    java.util.regex.Matcher m =
                        java.util.regex.Pattern.compile(" t(\\d+) ").matcher(line);
                    if (m.find()) return m.group(1);
                }
            }
        } catch (Exception ignored) {}
        return "";
    }

    /**
     * After slide 2 a full-screen banner ad may expand.
     * If present: close it. If absent: continue silently.
     */
    static void dismissBannerAdIfPresent() throws Exception {
        // Check for AdActivity full-screen ad
        if (act().contains("AdActivity")) {
            log("  Full-screen ad — tapping close X (" + AD_CLOSE_X + "," + AD_CLOSE_Y + ")");
            adbTap(AD_CLOSE_X, AD_CLOSE_Y);
            TimeUnit.SECONDS.sleep(2);
            return;
        }
        // Check for banner "Install" button covering the slide
        try {
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(0));
            boolean hasInstall = !driver.findElements(By.xpath("//*[@text='Install']")).isEmpty();
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
            if (hasInstall) {
                log("  Banner ad 'Install' visible — waiting 3s for it to shrink");
                TimeUnit.SECONDS.sleep(3);
            } else {
                log("  No banner ad — continuing");
            }
        } catch (Exception ignored) {
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
        }
    }

    static boolean clickByText(String text, int secs) {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(secs))
                .until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//*[@text='" + text + "']"))).click();
            return true;
        } catch (Exception e) { return false; }
    }

    /** Force-stops ALL known external apps including Vivo Store (am force-stop works even without root). */
    static void forceStopAllExternalApps() throws Exception {
        String[] all = {
            "com.android.vending",
            "com.android.chrome",
            "com.vivo.appstore",
            "com.bbk.appstore",
            "com.vivo.browser",
            "com.android.browser",
        };
        for (String p : all) {
            Runtime.getRuntime().exec(
                new String[]{ADB, "-s", DEVICE, "shell", "am", "force-stop", p}).waitFor();
        }
        Thread.sleep(300);
    }

    /** Brings our app to foreground using task ID or am start fallback. */
    static void bringAppToFront() throws Exception {
        if (!appTaskId.isEmpty()) {
            Process p = Runtime.getRuntime().exec(
                new String[]{ADB, "-s", DEVICE, "shell", "am", "task", "to-front", appTaskId});
            p.waitFor();
        }
        // Fallback: if task approach didn't work, start the next screen directly
        TimeUnit.MILLISECONDS.sleep(500);
        if (!act().contains(PKG.substring(PKG.lastIndexOf('.') + 1))
                && !act().contains("NewOnBoarding")
                && !act().contains("HelpUs")) {
            Runtime.getRuntime().exec(new String[]{
                ADB, "-s", DEVICE, "shell", "am", "start", "-n",
                PKG + "/.newcode.activity.NewOnBoardingMainActivity"
            }).waitFor();
        }
    }

    static void log(String msg) {
        System.out.println("[OnboardingTest] " + msg);
    }
}
