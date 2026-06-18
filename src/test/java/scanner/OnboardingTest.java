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

    static final String ADB         = "/Users/ios/Library/Android/sdk/platform-tools/adb";
    static final String DEVICE      = "10BG3702ZC0021T";
    static final String PKG         = "com.math.photo.scanner.equation.formula.calculator";
    static final String APPIUM_URL  = "http://127.0.0.1:4723";

    // ── Confirmed coordinates from live device scan (2026-06-18) ──────────────
    // Language screen
    static final int LANG_ENGLISH_X        = 978;   // imenglish radio button center
    static final int LANG_ENGLISH_Y        = 399;
    static final int LANG_NEXT_X           = 540;   // btnNext AFTER English selected (scrolls up)
    static final int LANG_NEXT_Y           = 1332;  // ⚠️ NOT 2119 — ad "View now" is at y≈2122

    // HelpUs "Grow With Us" screen
    static final int HELPUS_CONTINUE_X     = 540;   // btnNext text="Continue"
    static final int HELPUS_CONTINUE_Y     = 2010;

    // Interstitial ad (AdActivity — "Lucky Draw")
    static final int AD_CLOSE_X            = 1010;  // X close button
    static final int AD_CLOSE_Y            = 225;

    // Onboarding slides (NewOnBoardingMainActivity) — all 3 slides use same coords
    static final int ONBOARD_NEXT_X        = 910;   // "Next" / "Get Started" button
    static final int ONBOARD_NEXT_Y        = 1364;

    // Subscription / Trial screen (TimeLineActivity)
    static final int SUBSCRIPTION_CLOSE_X  = 990;  // X close button
    static final int SUBSCRIPTION_CLOSE_Y  = 172;

    // Camera permission
    static final int PERM_CAM_WHILE_USING_X = 540;
    static final int PERM_CAM_WHILE_USING_Y = 1665; // "While using the app"

    // Notification permission
    static final int PERM_NOTIF_ALLOW_X    = 540;
    static final int PERM_NOTIF_ALLOW_Y    = 1825;  // "Allow"

    // ── Setup ─────────────────────────────────────────────────────────────────

    @BeforeAll
    static void setup() throws Exception {
        // Clear app data for fresh onboarding
        Runtime.getRuntime().exec(new String[]{ADB, "-s", DEVICE, "shell", "pm", "clear", PKG}).waitFor();
        TimeUnit.SECONDS.sleep(1);

        UiAutomator2Options options = new UiAutomator2Options();
        options.setPlatformName("Android");
        options.setDeviceName(DEVICE);
        options.setAppPackage(PKG);
        options.setAppActivity(".newcode.activity.Splashscreen");
        options.setAutomationName("UiAutomator2");
        options.setNoReset(true);
        options.setFullReset(false);
        options.setCapability("appium:newCommandTimeout", 9999);
        options.setCapability("appium:appWaitDuration", 30000);

        driver = new AndroidDriver(new URL(APPIUM_URL), options);
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
        wait = new WebDriverWait(driver, Duration.ofSeconds(20));
        TimeUnit.SECONDS.sleep(4);
        log("App launched — device: " + DEVICE);
    }

    @AfterAll
    static void teardown() {
        if (driver != null) {
            try { driver.quit(); } catch (Exception ignored) {}
        }
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test @Order(1)
    void test01_SplashScreen() throws Exception {
        log("=== TEST 1 — Splash Screen ===");
        // Splash auto-navigates; wait for it to pass
        for (int i = 0; i < 10; i++) {
            String act = act();
            if (!act.contains("Splashscreen") && !act.contains("splash")) break;
            log("  waiting splash... " + act);
            TimeUnit.SECONDS.sleep(1);
        }
        log("After splash: " + act());
        Assertions.assertFalse(act().contains("Splashscreen"), "Splash should have passed");
        log("PASS");
    }

    @Test @Order(2)
    void test02_LanguageScreen() throws Exception {
        log("=== TEST 2 — Language Screen ===");
        waitForActivity("LanguageActivity", 15);

        // Check if English already selected — only tap if NOT checked to avoid deselecting
        boolean alreadySelected = false;
        try {
            WebElement radio = driver.findElement(By.id(PKG + ":id/imenglish"));
            alreadySelected = "true".equals(radio.getAttribute("checked"));
        } catch (Exception ignored) {}

        if (!alreadySelected) {
            log("  English not selected — tapping radio");
            adbTap(LANG_ENGLISH_X, LANG_ENGLISH_Y);
            TimeUnit.SECONDS.sleep(1);
        } else {
            log("  English already selected — skipping tap");
        }

        // After selecting English the list scrolls up, btnNext moves to y≈1332
        // Use Appium element click (handles any scroll position correctly)
        log("  Tapping Next (Appium by id)");
        if (!clickById(PKG + ":id/btnNext", 5)) {
            // Fallback: ADB tap at confirmed coordinate
            log("  Fallback ADB tap Next at (" + LANG_NEXT_X + "," + LANG_NEXT_Y + ")");
            adbTap(LANG_NEXT_X, LANG_NEXT_Y);
        }

        TimeUnit.SECONDS.sleep(2);
        String act = act();
        log("After language: " + act);
        Assertions.assertTrue(
            act.contains("HelpUs") || act.contains("Onboard") || act.contains("NewOnBoarding"),
            "Expected HelpUs or Onboarding, got: " + act
        );
        log("PASS");
    }

    @Test @Order(3)
    void test03_HelpUsGrowWithUs() throws Exception {
        log("=== TEST 3 — Grow With Us (HelpUsActivity) ===");
        waitForActivity("HelpUsActivity", 10);

        String titleText = "";
        try {
            titleText = driver.findElement(By.id(PKG + ":id/textTitle")).getText();
        } catch (Exception ignored) {}
        log("  Screen title: " + titleText);
        Assertions.assertTrue(titleText.contains("Grow"), "Expected 'Grow With Us', got: " + titleText);

        // Tap Continue — may open external app; handle recovery
        log("  Tapping Continue (" + HELPUS_CONTINUE_X + "," + HELPUS_CONTINUE_Y + ")");
        adbTap(HELPUS_CONTINUE_X, HELPUS_CONTINUE_Y);
        TimeUnit.SECONDS.sleep(3);

        recoverFromExternalApp();

        String act = act();
        log("After HelpUs: " + act);
        // Should be onboarding slides or ad
        Assertions.assertTrue(
            act.contains("NewOnBoarding") || act.contains("AdActivity") || act.contains("Onboard"),
            "Expected onboarding slides or ad, got: " + act
        );
        log("PASS");
    }

    @Test @Order(4)
    void test04_InterstitialAdIfPresent() throws Exception {
        log("=== TEST 4 — Interstitial Ad (if present) ===");
        for (int i = 0; i < 15; i++) {
            String act = act();
            if (act.contains("AdActivity")) {
                log("  Ad detected at " + i + "s — tapping close X (" + AD_CLOSE_X + "," + AD_CLOSE_Y + ")");
                adbTap(AD_CLOSE_X, AD_CLOSE_Y);
                TimeUnit.SECONDS.sleep(2);
                break;
            }
            if (act.contains("NewOnBoarding")) {
                log("  No ad — already on onboarding slides");
                break;
            }
            Thread.sleep(1000);
        }
        log("After ad check: " + act());
        log("PASS");
    }

    @Test @Order(5)
    void test05_OnboardingSlide1_SolvedSteps() throws Exception {
        log("=== TEST 5 — Onboarding Slide 1: Solved Steps ===");
        waitForActivity("NewOnBoardingMainActivity", 10);

        // Verify slide 1 content
        boolean hasSolvedSteps = isTextPresent("Solved Steps", 5);
        log("  Slide 1 'Solved Steps' visible: " + hasSolvedSteps);
        Assertions.assertTrue(hasSolvedSteps, "Expected 'Solved Steps' text on slide 1");

        log("  Tapping Next at (" + ONBOARD_NEXT_X + "," + ONBOARD_NEXT_Y + ")");
        adbTap(ONBOARD_NEXT_X, ONBOARD_NEXT_Y);
        TimeUnit.SECONDS.sleep(2);
        log("PASS");
    }

    @Test @Order(6)
    void test06_OnboardingSlide2_CaptureEquation() throws Exception {
        log("=== TEST 6 — Onboarding Slide 2: Capture Equation ===");
        waitForActivity("NewOnBoardingMainActivity", 10);

        boolean hasCaptureEq = isTextPresent("Capture Equation", 5);
        log("  Slide 2 'Capture Equation' visible: " + hasCaptureEq);
        Assertions.assertTrue(hasCaptureEq, "Expected 'Capture Equation' text on slide 2");

        log("  Tapping Next at (" + ONBOARD_NEXT_X + "," + ONBOARD_NEXT_Y + ")");
        adbTap(ONBOARD_NEXT_X, ONBOARD_NEXT_Y);
        TimeUnit.SECONDS.sleep(3);

        // After slide 2, a full-screen banner ad may expand and cover the screen
        // Wait for it to settle, then handle it
        handleExpandedBannerAd();

        log("PASS");
    }

    @Test @Order(7)
    void test07_OnboardingSlide3_MathsSolution() throws Exception {
        log("=== TEST 7 — Onboarding Slide 3: Maths Solution ===");
        waitForActivity("NewOnBoardingMainActivity", 10);

        boolean hasMathsSolution = isTextPresent("Maths Solution", 5);
        log("  Slide 3 'Maths Solution' visible: " + hasMathsSolution);
        Assertions.assertTrue(hasMathsSolution, "Expected 'Maths Solution' text on slide 3");

        log("  Tapping Get Started at (" + ONBOARD_NEXT_X + "," + ONBOARD_NEXT_Y + ")");
        adbTap(ONBOARD_NEXT_X, ONBOARD_NEXT_Y);
        TimeUnit.SECONDS.sleep(3);

        String act = act();
        log("After Get Started: " + act);
        Assertions.assertTrue(
            act.contains("TimeLine") || act.contains("Grant") || act.contains("Home"),
            "Expected subscription, permission, or home. Got: " + act
        );
        log("PASS");
    }

    @Test @Order(8)
    void test08_SubscriptionScreen() throws Exception {
        log("=== TEST 8 — Subscription / Trial Screen ===");

        String act = act();
        if (!act.contains("TimeLine") && !act.contains("subscription")) {
            log("  Subscription screen not shown — skipping");
            log("PASS");
            return;
        }

        // Verify screen title
        boolean hasTitle = isTextPresent("HAVE DOUBTS", 5);
        log("  'HAVE DOUBTS?' visible: " + hasTitle);

        // Close via X button at top-right (confirmed at 990, 172)
        log("  Tapping X close at (" + SUBSCRIPTION_CLOSE_X + "," + SUBSCRIPTION_CLOSE_Y + ")");
        adbTap(SUBSCRIPTION_CLOSE_X, SUBSCRIPTION_CLOSE_Y);
        TimeUnit.SECONDS.sleep(2);

        act = act();
        log("After subscription close: " + act);
        Assertions.assertTrue(
            act.contains("Grant") || act.contains("Home") || act.contains("newcode"),
            "Expected permission or home after closing subscription. Got: " + act
        );
        log("PASS");
    }

    @Test @Order(9)
    void test09_CameraPermission() throws Exception {
        log("=== TEST 9 — Camera Permission ===");

        // May or may not appear
        boolean shown = waitForActivity("GrantPermissions", 8);
        if (!shown) {
            log("  Camera permission not shown — skipping");
            log("PASS");
            return;
        }

        boolean hasCameraMsg = isTextPresent("pictures", 5);
        log("  Camera permission dialog visible: " + hasCameraMsg);
        Assertions.assertTrue(hasCameraMsg, "Expected camera permission dialog");

        // Tap "While using the app" — confirmed at (540, 1665)
        // Try Appium first (by resource-id)
        if (!clickById("com.android.permissioncontroller:id/permission_allow_foreground_only_button", 3)) {
            log("  Fallback ADB tap While Using at (" + PERM_CAM_WHILE_USING_X + "," + PERM_CAM_WHILE_USING_Y + ")");
            adbTap(PERM_CAM_WHILE_USING_X, PERM_CAM_WHILE_USING_Y);
        }
        TimeUnit.SECONDS.sleep(2);
        log("After camera perm: " + act());
        log("PASS");
    }

    @Test @Order(10)
    void test10_NotificationPermission() throws Exception {
        log("=== TEST 10 — Notification Permission ===");

        boolean shown = waitForActivity("GrantPermissions", 8);
        if (!shown) {
            log("  Notification permission not shown — skipping");
            log("PASS");
            return;
        }

        boolean hasNotifMsg = isTextPresent("notifications", 5);
        log("  Notification permission dialog visible: " + hasNotifMsg);
        Assertions.assertTrue(hasNotifMsg, "Expected notification permission dialog");

        // Tap "Allow" — confirmed at (540, 1825)
        if (!clickById("com.android.permissioncontroller:id/permission_allow_button", 3)) {
            log("  Fallback ADB tap Allow at (" + PERM_NOTIF_ALLOW_X + "," + PERM_NOTIF_ALLOW_Y + ")");
            adbTap(PERM_NOTIF_ALLOW_X, PERM_NOTIF_ALLOW_Y);
        }
        TimeUnit.SECONDS.sleep(2);
        log("After notification perm: " + act());
        log("PASS");
    }

    @Test @Order(11)
    void test11_HomeScreen() throws Exception {
        log("=== TEST 11 — Home Screen (Camera View) ===");

        boolean reached = waitForActivity("HomeActivity", 15);
        log("  HomeActivity reached: " + reached + " — activity: " + act());
        Assertions.assertTrue(reached, "Expected HomeActivity after completing onboarding. Got: " + act());

        // Verify bottom navigation tabs are visible
        boolean hasCameraTab     = isTextPresent("Camera", 5);
        boolean hasSolutionTab   = isTextPresent("Solution", 3);
        boolean hasCalculatorTab = isTextPresent("Calculator", 3);
        log("  Camera tab: " + hasCameraTab);
        log("  Solution tab: " + hasSolutionTab);
        log("  Calculator tab: " + hasCalculatorTab);

        Assertions.assertTrue(hasCameraTab,     "Camera tab not found on Home screen");
        Assertions.assertTrue(hasSolutionTab,   "Solution tab not found on Home screen");
        Assertions.assertTrue(hasCalculatorTab, "Calculator tab not found on Home screen");
        log("PASS — Onboarding complete!");
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

    static void adbBack() throws Exception {
        Runtime.getRuntime().exec(new String[]{
            ADB, "-s", DEVICE, "shell", "input", "keyevent", "4"
        }).waitFor();
        Thread.sleep(500);
    }

    static boolean clickById(String resourceId, int timeoutSecs) {
        try {
            WebElement el = new WebDriverWait(driver, Duration.ofSeconds(timeoutSecs))
                .until(ExpectedConditions.elementToBeClickable(By.id(resourceId)));
            el.click();
            return true;
        } catch (Exception e) { return false; }
    }

    static boolean waitForActivity(String activityPart, int timeoutSecs) throws Exception {
        for (int i = 0; i < timeoutSecs; i++) {
            if (act().contains(activityPart)) return true;
            TimeUnit.SECONDS.sleep(1);
        }
        return act().contains(activityPart);
    }

    static boolean isTextPresent(String text, int timeoutSecs) {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(timeoutSecs))
                .until(ExpectedConditions.presenceOfElementLocated(
                    By.xpath("//*[contains(@text,'" + text + "')]")));
            return true;
        } catch (Exception e) { return false; }
    }

    /**
     * After tapping Continue on HelpUs or Get Started, an external app
     * (Chrome, Play Store, Vivo Store) may open. Kill it and return to app.
     */
    static void recoverFromExternalApp() throws Exception {
        String act = act();
        if (act.contains("chrome") || act.contains("Chrome")) {
            log("  Chrome opened — killing it");
            Runtime.getRuntime().exec(new String[]{
                ADB, "-s", DEVICE, "shell", "am", "force-stop", "com.android.chrome"
            }).waitFor();
            TimeUnit.SECONDS.sleep(1);
        } else if (act.contains("vending") || act.contains("android.vending")) {
            log("  Play Store opened — killing it");
            Runtime.getRuntime().exec(new String[]{
                ADB, "-s", DEVICE, "shell", "am", "force-stop", "com.android.vending"
            }).waitFor();
            TimeUnit.SECONDS.sleep(1);
        } else if (act.contains("appstore") || act.contains("vivo")) {
            log("  Vivo App Store opened — killing it");
            Runtime.getRuntime().exec(new String[]{
                ADB, "-s", DEVICE, "shell", "am", "force-stop", "com.bbk.appstore"
            }).waitFor();
            TimeUnit.SECONDS.sleep(1);
        } else if (act.contains("Launcher") || act.contains("launcher")) {
            log("  Launcher detected — bringing app back");
            Runtime.getRuntime().exec(new String[]{
                ADB, "-s", DEVICE, "shell", "monkey", "-p", PKG,
                "-c", "android.intent.category.LAUNCHER", "1"
            }).waitFor();
            TimeUnit.SECONDS.sleep(2);
        }
    }

    /**
     * Between onboarding slides, a full-screen banner ad may expand.
     * Wait for it to settle, then close if still covering content.
     */
    static void handleExpandedBannerAd() throws Exception {
        TimeUnit.SECONDS.sleep(2);
        String act = act();
        if (act.contains("AdActivity")) {
            log("  Full-screen ad detected — tapping close X (" + AD_CLOSE_X + "," + AD_CLOSE_Y + ")");
            adbTap(AD_CLOSE_X, AD_CLOSE_Y);
            TimeUnit.SECONDS.sleep(2);
        }
        // Check if banner Install button is at bottom (covering content area)
        // If "Install" is tappable, skip it by waiting for ad to minimise
        try {
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(0));
            boolean hasInstall = !driver.findElements(
                By.xpath("//*[@text='Install']")).isEmpty();
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
            if (hasInstall) {
                log("  Banner ad 'Install' visible — waiting for content...");
                TimeUnit.SECONDS.sleep(3);
            }
        } catch (Exception ignored) {
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
        }
    }

    static void log(String msg) {
        System.out.println("[OnboardingTest] " + msg);
    }
}
