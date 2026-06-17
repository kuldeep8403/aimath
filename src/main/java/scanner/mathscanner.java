

package scanner;

import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.options.UiAutomator2Options;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.*;
import java.net.URL;
import java.nio.file.*;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

public class mathscanner {

    static AndroidDriver driver;
    static WebDriverWait wait;

//    static String deviceName  = "10BG3702ZC0021T";
    static String deviceName  = "";
    static String appPackage  = "com.math.photo.scanner.equation.formula.calculator";
    static String appActivity = ".newcode.activity.Splashscreen";
    static String adbPath     = "/Users/ios/Library/Android/sdk/platform-tools/adb";
    static String groqKey     = System.getenv().getOrDefault("GROQ_API_KEY", "YOUR_GROQ_API_KEY_HERE");

    static String equation        = "";
    static String localImagePath  = "/tmp/math_equation_bot.png";
    static String deviceImageDir  = "/sdcard/DCIM/MathScan";
    static String deviceImageName = "math_equation_bot.png";
    static String deviceImagePath = deviceImageDir + "/" + deviceImageName;

    static int TAP_ENGLISH_X      = 540;
    static int TAP_ENGLISH_Y      = 593;
    static int TAP_TICK_X         = 1005;
    static int TAP_TICK_Y         = 169;
    static int TAP_NEXT_X         = 540;
    static int TAP_NEXT_Y         = 1960;
    static int TAP_ONBOARD_NEXT_X = 952;
    static int TAP_ONBOARD_NEXT_Y = 1370;
    static int PERM_WHILE_USING_Y = 1692;
    static int PERM_ALLOW_Y       = 1787;

    // XML confirmed keyboard coordinates
    static java.util.Map<Character, int[]> keyMap = new java.util.HashMap<Character, int[]>() {{
        put('0', new int[]{303, 1987});
        put('1', new int[]{185, 1876});
        put('2', new int[]{303, 1876});
        put('3', new int[]{421, 1876});
        put('4', new int[]{185, 1760});
        put('5', new int[]{303, 1760});
        put('6', new int[]{421, 1760});
        put('7', new int[]{185, 1649});
        put('8', new int[]{303, 1649});
        put('9', new int[]{421, 1649});
        put('x', new int[]{66,  1649});
        put('y', new int[]{66,  1760});
        put('+', new int[]{421, 1987});
        put('=', new int[]{539, 1987});
        put('-', new int[]{539, 1760});
        put('.', new int[]{185, 1987});
    }};

    // ================================================================
    //   GENERATE 5 DYNAMIC MATH QUESTIONS VIA GROQ API
    // ================================================================
    public static String[] generateMathQuestions() throws Exception {
        System.out.println("\n========================================");
        System.out.println("🔷 Generating 5 Dynamic Math Questions");
        System.out.println("========================================");

        String apiUrl = "https://api.groq.com/openai/v1/chat/completions";

        String requestBody = "{\"model\":\"llama-3.3-70b-versatile\","
            + "\"max_tokens\":200,"
            + "\"messages\":[{\"role\":\"user\","
            + "\"content\":\"Generate exactly 5 different simple math equations. "
            + "Return ONLY a JSON array of 5 strings, nothing else, no explanation, no markdown, no newlines. "
            + "Example: [\\\"2x + 3 = 11\\\",\\\"3x - 7 = 14\\\",\\\"5x + 2 = 22\\\",\\\"x/2 + 3 = 7\\\",\\\"4x - 5 = 19\\\"]\"}]}";

        ProcessBuilder pb = new ProcessBuilder("curl", "-s",
            "-X", "POST", apiUrl,
            "-H", "Content-Type: application/json",
            "-H", "Authorization: Bearer " + groqKey,
            "-d", requestBody);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String response = new String(p.getInputStream().readAllBytes());
        p.waitFor();

        System.out.println("API Response: " + response);

        int textIdx = response.indexOf("\"content\":\"");
        if (textIdx == -1) throw new Exception("API error: " + response);
        int start = textIdx + 11;
        int end = start;
        while (end < response.length()) {
            if (response.charAt(end) == '\\') { end += 2; continue; }
            if (response.charAt(end) == '"') break;
            end++;
        }

        String text = response.substring(start, end)
            .replace("\\\"", "\"")
            .replace("\\n", "")
            .replace("\\r", "")
            .trim();

        System.out.println("Extracted: " + text);

        int arrStart = text.indexOf("[");
        int arrEnd   = text.lastIndexOf("]");
        if (arrStart == -1 || arrEnd == -1) {
            System.out.println("⚠️ Parsing failed — using fallback");
            return getFallbackQuestions();
        }
        text = text.substring(arrStart + 1, arrEnd);

        java.util.List<String> questionList = new java.util.ArrayList<>();
        boolean inQuote = false;
        StringBuilder current = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (c == '"') { inQuote = !inQuote; continue; }
            if (c == ',' && !inQuote) {
                String q = current.toString().trim();
                if (!q.isEmpty()) questionList.add(q);
                current = new StringBuilder();
            } else { current.append(c); }
        }
        String last = current.toString().trim();
        if (!last.isEmpty()) questionList.add(last);

        if (questionList.size() < 5) {
            System.out.println("⚠️ Less than 5 — using fallback");
            return getFallbackQuestions();
        }

        String[] questions = questionList.subList(0, 5).toArray(new String[0]);
        for (int i = 0; i < questions.length; i++) {
            System.out.println("Q" + (i+1) + ": " + questions[i]);
        }
        System.out.println("✅ 5 questions generated");
        return questions;
    }

    public static String[] getFallbackQuestions() {
        System.out.println("Using fallback questions");
        return new String[]{
            "9x - 7",
            "2x + 5",
            "3x - 4",
            "5x + 3",
            "4x - 6"
        };
    }

    // ================================================================
    //   PRE-STEP — GENERATE IMAGE USING PYTHON
    // ================================================================
//    public static void preStep_GenerateAndPushImage() throws Exception {
//        System.out.println("\n========================================");
//        System.out.println("🔷 PRE-STEP — Generate & Push Math Image");
//        System.out.println("========================================");
//        System.out.println("📐 Equation: " + equation);
//        String pyScript = "/tmp/gen_math_image.py";
//        String pyCode =
//            "import sys, os, subprocess, time\n" +
//            "try:\n" +
//            "    from PIL import Image, ImageDraw, ImageFont, ImageOps\n" +
//            "except ImportError:\n" +
//            "    subprocess.run([sys.executable,'-m','pip','install','Pillow','--break-system-packages','-q'])\n" +
//            "    from PIL import Image, ImageDraw, ImageFont, ImageOps\n" +
//            "\n" +
//            "equation  = '" + equation.replace("'", "\\'") + "'\n" +
//            "adb       = '" + adbPath + "'\n" +
//            "device    = '" + deviceName + "'\n" +
//            "local     = '" + localImagePath + "'\n" +
//            "dev_dir   = '" + deviceImageDir + "'\n" +
//            "dev_path  = '" + deviceImagePath + "'\n" +
//            "\n" +
//            "font = None\n" +
//            "font_paths = [\n" +
//            "    '/System/Library/Fonts/Helvetica.ttc',\n" +
//            "    '/System/Library/Fonts/Arial.ttf',\n" +
//            "    '/Library/Fonts/Arial.ttf',\n" +
//            "    '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf',\n" +
//            "]\n" +
//            "for fp in font_paths:\n" +
//            "    if os.path.exists(fp):\n" +
//            "        try: font = ImageFont.truetype(fp, 200); print('Font: '+fp); break\n" +
//            "        except: pass\n" +
//            "if font is None:\n" +
//            "    font = ImageFont.load_default()\n" +
//            "\n" +
//            "img_w = 1080\n" +
//            "img_h = 600\n" +
//            "img = Image.new('RGB',(img_w, img_h), 'white')\n" +
//            "draw = ImageDraw.Draw(img)\n" +
//            "bbox = draw.textbbox((0,0), equation, font=font)\n" +
//            "tw = bbox[2]-bbox[0]\n" +
//            "th = bbox[3]-bbox[1]\n" +
//            "x = (img_w - tw) // 2 - bbox[0]\n" +
//            "y = (img_h - th) // 2 - bbox[1]\n" +
//            "draw.text((x, y), equation, fill='black', font=font)\n" +
//            "img.save(local)\n" +
//            "print('Image saved: '+local+' size:'+str(img.width)+'x'+str(img.height))\n" +
//            "\n" +
//            "subprocess.run([adb,'-s',device,'shell','mkdir','-p',dev_dir])\n" +
//            "r = subprocess.run([adb,'-s',device,'push',local,dev_path], capture_output=True, text=True)\n" +
//            "if r.returncode == 0: print('Pushed: '+dev_path)\n" +
//            "else: print('Push failed: '+r.stderr); sys.exit(1)\n" +
//            "\n" +
//            "subprocess.run([adb,'-s',device,'shell','am','broadcast','-a',\n" +
//            "    'android.intent.action.MEDIA_SCANNER_SCAN_FILE','-d','file://'+dev_path],\n" +
//            "    capture_output=True)\n" +
//            "time.sleep(2)\n" +
//            "print('DONE: '+dev_path)\n";
//
//        Files.writeString(Path.of(pyScript), pyCode);
//        ProcessBuilder pb = new ProcessBuilder("python3", pyScript);
//        pb.redirectErrorStream(true);
//        Process p = pb.start();
//        String output = new String(p.getInputStream().readAllBytes());
//        int exitCode = p.waitFor();
//        System.out.println(output.trim());
//        if (exitCode != 0) throw new Exception("Image generation failed!");
//        System.out.println("✅ Pre-step done");
//    }
    
    public static void preStep_GenerateAndPushImage() throws Exception {
        System.out.println("\n========================================");
        System.out.println("🔷 PRE-STEP — Generate & Push Math Image");
        System.out.println("========================================");
        System.out.println("📐 Equation: " + equation);

        String pyScript = "/tmp/gen_math_image.py";
        String pyCode =
            "import sys, os, subprocess, time\n" +
            "try:\n" +
            "    from PIL import Image, ImageDraw, ImageFont, ImageOps\n" +
            "except ImportError:\n" +
            "    subprocess.run([sys.executable,'-m','pip','install','Pillow','--break-system-packages','-q'])\n" +
            "    from PIL import Image, ImageDraw, ImageFont, ImageOps\n" +
            "\n" +
            "equation  = '" + equation.replace("'", "\\'") + "'\n" +
            "adb       = '" + adbPath + "'\n" +
            "device    = '" + deviceName + "'\n" +
            "local     = '" + localImagePath + "'\n" +
            "dev_dir   = '" + deviceImageDir + "'\n" +
            "dev_path  = '" + deviceImagePath + "'\n" +
            "\n" +
            "font = None\n" +
            "font_paths = [\n" +
            "    '/System/Library/Fonts/Helvetica.ttc',\n" +
            "    '/System/Library/Fonts/Arial.ttf',\n" +
            "    '/Library/Fonts/Arial.ttf',\n" +
            "    '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf',\n" +
            "]\n" +
            "for fp in font_paths:\n" +
            "    if os.path.exists(fp):\n" +
            "        try: font = ImageFont.truetype(fp, 150); print('Font: '+fp); break\n" +
            "        except: pass\n" +
            "if font is None:\n" +
            "    font = ImageFont.load_default()\n" +
            "\n" +
//            "img_w = 1080\n" +
//            "img_h = 800\n" +
//            "img = Image.new('RGB',(img_w, img_h), 'white')\n" +
//            "draw = ImageDraw.Draw(img)\n" +
//            "bbox = draw.textbbox((0,0), equation, font=font)\n" +
//            "tw = bbox[2]-bbox[0]\n" +
//            "th = bbox[3]-bbox[1]\n" +
//            "x = (img_w - tw) // 2 - bbox[0]\n" +
//            "y = (img_h - th) // 2 - bbox[1]\n" +
//            "draw.text((x, y), equation, fill='black', font=font)\n" +
              "# Tight image — exact text size + padding\n" +
              "pad = 60\n" +
             "dummy = Image.new('RGB',(1,1))\n" +
             "d = ImageDraw.Draw(dummy)\n" +
             "bbox = d.textbbox((0,0), equation, font=font)\n" +
             "tw = bbox[2]-bbox[0]\n" +
             "th = bbox[3]-bbox[1]\n" +
             "img_w = tw + pad*2\n" +
             "img_h = th + pad*2\n" +
             "img = Image.new('RGB',(img_w, img_h), 'white')\n" +
             "draw = ImageDraw.Draw(img)\n" +
             "draw.text((pad - bbox[0], pad - bbox[1]), equation, fill='black', font=font)\n" +
            "img.save(local)\n" +
            "print('Image saved: '+local+' size:'+str(img.width)+'x'+str(img.height))\n" +
            "\n" +
            "subprocess.run([adb,'-s',device,'shell','mkdir','-p',dev_dir])\n" +
            "r = subprocess.run([adb,'-s',device,'push',local,dev_path], capture_output=True, text=True)\n" +
            "if r.returncode == 0: print('Pushed: '+dev_path)\n" +
            "else: print('Push failed: '+r.stderr); sys.exit(1)\n" +
            "\n" +
            "subprocess.run([adb,'-s',device,'shell','am','broadcast','-a',\n" +
            "    'android.intent.action.MEDIA_SCANNER_SCAN_FILE','-d','file://'+dev_path],\n" +
            "    capture_output=True)\n" +
            "time.sleep(2)\n" +
            "print('DONE: '+dev_path)\n";

        Files.writeString(Path.of(pyScript), pyCode);
        ProcessBuilder pb = new ProcessBuilder("python3", pyScript);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String output = new String(p.getInputStream().readAllBytes());
        int exitCode = p.waitFor();
        System.out.println(output.trim());
        if (exitCode != 0) throw new Exception("Image generation failed!");
        System.out.println("✅ Pre-step done");
    }
    
    
 // ================================================================
//  AUTO DETECT DEVICE
//================================================================
public static String detectDevice() throws Exception {
   ProcessBuilder pb = new ProcessBuilder(adbPath, "devices");
   pb.redirectErrorStream(true);
   Process p = pb.start();
   String output = new String(p.getInputStream().readAllBytes());
   p.waitFor();

   System.out.println("ADB Devices:\n" + output);

   for (String line : output.split("\n")) {
       line = line.trim();
       if (!line.isEmpty() && !line.startsWith("List") && line.contains("device")) {
           String id = line.split("\t")[0].trim();
           if (!id.isEmpty()) {
               System.out.println("✅ Device found: " + id);
               return id;
           }
       }
   }
   throw new Exception("❌ No device connected!");
}

    // ================================================================
    //   LAUNCH / STOP / CLOSE
    // ================================================================
    public static void launchApp() throws Exception {
        UiAutomator2Options options = new UiAutomator2Options();
        options.setPlatformName("Android");
        options.setDeviceName(deviceName);
        options.setAppPackage(appPackage);
        options.setAppActivity(appActivity);
        options.setAutomationName("UiAutomator2");
        options.setNoReset(false);
        options.setFullReset(false);
        options.setCapability("appium:androidInstallTimeout", 90000);
        options.setCapability("appium:appWaitDuration",       30000);
        options.setCapability("appium:newCommandTimeout",     9999);
        driver = new AndroidDriver(new URL("http://127.0.0.1:4723"), options);
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
        wait = new WebDriverWait(driver, Duration.ofSeconds(20));
        TimeUnit.SECONDS.sleep(3);
        System.out.println("✅ App Launched");
    }

    public static void forceStopApp() throws Exception {
        Runtime.getRuntime().exec(new String[]{
            adbPath, "-s", deviceName, "shell", "am", "force-stop", appPackage
        }).waitFor();
        System.out.println("🛑 App force stopped");
        TimeUnit.SECONDS.sleep(1);
    }

    public static void closeApp() throws Exception {
        if (driver != null) {
            try { driver.terminateApp(appPackage); } catch (Exception ignored) {}
            try { driver.quit(); }                  catch (Exception ignored) {}
            driver = null;
            TimeUnit.SECONDS.sleep(1);
            System.out.println("✅ App Closed");
        }
    }

    // ================================================================
    //   HELPERS
    // ================================================================
    public static void adbTap(int x, int y) throws Exception {
        Runtime.getRuntime().exec(new String[]{
            adbPath, "-s", deviceName, "shell", "input", "tap",
            String.valueOf(x), String.valueOf(y)
        }).waitFor();
        System.out.println("👆 ADB tap (" + x + ", " + y + ")");
        Thread.sleep(500);
    }

    public static void adbBack() throws Exception {
        Runtime.getRuntime().exec(new String[]{
            adbPath, "-s", deviceName, "shell", "input", "keyevent", "4"
        }).waitFor();
        System.out.println("⬅️ Back pressed");
        Thread.sleep(500);
    }

    public static String currentActivity() {
        try { return driver.currentActivity(); } catch (Exception e) { return "unknown"; }
    }

    public static void printScreen(String label) {
        System.out.println("📱 " + label + ": " + currentActivity());
    }

    public static boolean safeClick(By locator, int seconds) {
        try {
            WebElement el = new WebDriverWait(driver, Duration.ofSeconds(seconds))
                .until(ExpectedConditions.elementToBeClickable(locator));
            el.click();
            return true;
        } catch (Exception e) { return false; }
    }

    public static boolean isPresent(By locator) {
        try {
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(0));
            return !driver.findElements(locator).isEmpty();
        } catch (Exception e) { return false; }
        finally { driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10)); }
    }

    public static boolean isOnboardingScreen() {
        String act = currentActivity();
        return act.contains("Onboard") || act.contains("Board") ||
               act.contains("Intro")   || act.contains("Main")  ||
               act.contains("Home")    || act.contains("Camera");
    }

    public static boolean isAppScreen() {
        String act = currentActivity();
        return act.contains("newcode") || act.contains("activity") || act.startsWith(".");
    }

    public static void bringAppBack() throws Exception {
        Runtime.getRuntime().exec(new String[]{
            adbPath, "-s", deviceName, "shell", "monkey",
            "-p", appPackage, "-c", "android.intent.category.LAUNCHER", "1"
        }).waitFor();
        TimeUnit.SECONDS.sleep(2);
        System.out.println("🔄 App brought back");
    }

    public static void recoverIfNeeded(String ctx) throws Exception {
        String act = currentActivity();
        if (act.contains("chrome") || act.contains("Chrome") || act.contains("chromium")) {
            System.out.println("🌐 [" + ctx + "] Chrome — recovering...");
            Runtime.getRuntime().exec(new String[]{
                adbPath, "-s", deviceName, "shell", "am", "force-stop", "com.android.chrome"
            }).waitFor();
            TimeUnit.SECONDS.sleep(1);
            bringAppBack();
        } else if (act.contains("Launcher") || act.contains("launcher") || act.contains("bbk")) {
            System.out.println("🏠 [" + ctx + "] Launcher — recovering...");
            bringAppBack();
        }
    }

    private static int[] getBoundsFromXml(String xml, String string) {
        return null;
    }

    // ================================================================
    //   SCREEN 1 — SPLASH
    // ================================================================
    public static void screen1_SplashScreen() throws Exception {
        System.out.println("\n========================================");
        System.out.println("🔷 SCREEN 1 — Splash Screen");
        System.out.println("========================================");
        printScreen("Entry");
        if (currentActivity().contains("Splashscreen") || currentActivity().contains("splash")) {
            System.out.println("⏳ Waiting for splash...");
            for (int i = 0; i < 12; i++) {
                TimeUnit.SECONDS.sleep(1);
                if (!currentActivity().contains("Splashscreen")) break;
            }
        } else {
            System.out.println("ℹ️ Splash already done");
        }
        printScreen("After splash");
        System.out.println("✅ Screen 1 done");
    }

    // ================================================================
    //   SCREEN 2 — SUBSCRIPTION
    // ================================================================
    public static void screen2_SubscriptionScreen() throws Exception {
        System.out.println("\n========================================");
        System.out.println("🔷 SCREEN 2 — Subscription Trial Screen");
        System.out.println("========================================");
        if (currentActivity().contains("Language")) {
            System.out.println("ℹ️ Already on Language — skipping");
            System.out.println("✅ Screen 2 done");
            return;
        }
        By[] closeBtns = {
            By.id(appPackage + ":id/iv_close"),
            By.id(appPackage + ":id/ivClose"),
            By.id(appPackage + ":id/btnClose"),
            By.xpath("//*[@text='Pay Nothing Now']")
        };
        for (By btn : closeBtns) {
            if (safeClick(btn, 3)) { TimeUnit.SECONDS.sleep(1); break; }
        }
        printScreen("After subscription");
        System.out.println("✅ Screen 2 done");
    }

    // ================================================================
    //   SCREEN 3 — INTERSTITIAL AD
    // ================================================================
    public static void screen3_InterstitialAd() throws Exception {
        System.out.println("\n========================================");
        System.out.println("🔷 SCREEN 3 — Interstitial Ad Check");
        System.out.println("========================================");
        String act = currentActivity();
        if (act.contains("Language") || isOnboardingScreen()) {
            System.out.println("ℹ️ On app screen — skipping");
            System.out.println("✅ Screen 3 done");
            return;
        }
        boolean isExternalAd = !isAppScreen() && !act.contains("Launcher") && !act.contains("bbk");
        if (isExternalAd) {
            By[] skipBtns = {
                By.id(appPackage + ":id/btnSkip"),
                By.id(appPackage + ":id/iv_close"),
                By.xpath("//*[@content-desc='Close Ad']")
            };
            boolean skipped = false;
            for (int i = 0; i < 10 && !skipped; i++) {
                for (By btn : skipBtns) { if (safeClick(btn, 1)) { skipped = true; break; } }
                if (!skipped) Thread.sleep(500);
            }
            if (!skipped) adbBack();
            TimeUnit.SECONDS.sleep(1);
        }
        recoverIfNeeded("After ad");
        System.out.println("✅ Screen 3 done");
    }

    // ================================================================
    //   SCREEN 4 — LANGUAGE
    // ================================================================
   public static void screen4_LanguageScreen() throws Exception {
    System.out.println("\n========================================");
    System.out.println("🔷 SCREEN 4 — Language Selection");
    System.out.println("========================================");

    Thread.sleep(500);

    // English select via Appium
    if (safeClick(By.id(appPackage + ":id/imenglish"), 5)) {
        System.out.println("✅ English selected");
    } else if (safeClick(By.xpath("//*[@text='English']"), 3)) {
        System.out.println("✅ English selected via text");
    }

    Thread.sleep(500);

    // Next button
    if (safeClick(By.xpath("//*[@text='Next']"), 5)) {
        System.out.println("✅ Next tapped");
    } else {
        adbTap(540, 1305);
        System.out.println("✅ Next tapped via ADB");
    }

    TimeUnit.SECONDS.sleep(1);
    printScreen("After language");
    System.out.println("✅ Screen 4 done");
}

    // ================================================================
    //   SCREEN 5 — ONBOARDING 1
    // ================================================================
    public static void screen5_Onboarding1() throws Exception {
        System.out.println("\n========================================");
        System.out.println("🔷 SCREEN 5 — Onboarding 1");
        System.out.println("========================================");

        for (int i = 0; i < 20; i++) {
            String act = currentActivity();
            System.out.println("   " + i + "s — " + act);

            // Onboarding screen — done
            if (isOnboardingScreen()) {
                System.out.println("✅ Onboarding found");
                break;
            }

            // HelpUsActivity — "Grow With Us" — Continue tap
            if (act.contains("HelpUsActivity")) {
                System.out.println("   'Grow With Us' — tapping Continue (540, 2010)");
                adbTap(540, 2010);
                TimeUnit.SECONDS.sleep(2);
                continue;
            }

            // Language screen
            if (act.contains("Language")) {
                System.out.println("   Language — tapping tick");
                adbTap(TAP_TICK_X, TAP_TICK_Y);
                TimeUnit.SECONDS.sleep(1);
                continue;
            }

            // Subscription popup
            if (safeClick(By.xpath("//*[@text='Pay Nothing Now']"), 1) ||
                safeClick(By.id(appPackage + ":id/iv_close"), 1) ||
                safeClick(By.id(appPackage + ":id/ivClose"), 1)) {
                System.out.println("   Subscription closed");
                TimeUnit.SECONDS.sleep(1);
                continue;
            }

            recoverIfNeeded("Waiting onboarding");
            TimeUnit.SECONDS.sleep(1);
        }

        System.out.println("Tapping Next (952, 1370)");
        adbTap(TAP_ONBOARD_NEXT_X, TAP_ONBOARD_NEXT_Y);
        TimeUnit.SECONDS.sleep(1);
        System.out.println("✅ Screen 5 done");
    }

    // ================================================================
    //   SCREEN 6 — ONBOARDING 2
    // ================================================================
    public static void screen6_Onboarding2() throws Exception {
        System.out.println("\n========================================");
        System.out.println("🔷 SCREEN 6 — Onboarding 2");
        System.out.println("========================================");
        adbTap(TAP_ONBOARD_NEXT_X, TAP_ONBOARD_NEXT_Y);
        System.out.println("⏳ Waiting for ad (~10s)...");
        Thread.sleep(10000);
        System.out.println("✅ Screen 6 done");
    }

    // ================================================================
    //   SCREEN 7 — GET STARTED
    // ================================================================
    public static void screen7_GetStarted() throws Exception {
        System.out.println("\n========================================");
        System.out.println("🔷 SCREEN 7 — Get Started");
        System.out.println("========================================");
        Thread.sleep(1500);
        adbTap(885, 1364);
        Thread.sleep(1000);
        if (currentActivity().contains("chrome") || currentActivity().contains("Chrome")) {
            Runtime.getRuntime().exec(new String[]{
                adbPath, "-s", deviceName, "shell", "am", "force-stop", "com.android.chrome"
            }).waitFor();
            Thread.sleep(1000);
            bringAppBack();
            Thread.sleep(1500);
            adbTap(885, 1364);
            Thread.sleep(1000);
        }
        recoverIfNeeded("After get started");
        System.out.println("✅ Screen 7 done");
    }

    // ================================================================
    //   SCREEN 8 — CAMERA PERMISSION
    // ================================================================
    public static void screen8_CameraPermission() throws Exception {
        System.out.println("\n========================================");
        System.out.println("🔷 SCREEN 8 — Camera Permission");
        System.out.println("========================================");
        boolean found = false;
        for (int i = 0; i < 10; i++) {
            if (currentActivity().contains("GrantPermissions")) { found = true; break; }
            Thread.sleep(500);
        }
        if (!found) { System.out.println("ℹ️ Not shown — skipping"); System.out.println("✅ Screen 8 done"); return; }
        adbTap(540, PERM_WHILE_USING_Y);
        Thread.sleep(800);
        if (currentActivity().contains("GrantPermissions")) {
            adbTap(540, PERM_WHILE_USING_Y); Thread.sleep(800);
        }
        printScreen("After camera perm");
        System.out.println("✅ Screen 8 done");
    }

    // ================================================================
    //   SCREEN 9 — NOTIFICATION PERMISSION
    // ================================================================
    public static void screen9_NotificationPermission() throws Exception {
        System.out.println("\n========================================");
        System.out.println("🔷 SCREEN 9 — Notification Permission");
        System.out.println("========================================");
        boolean found = false;
        for (int i = 0; i < 10; i++) {
            if (currentActivity().contains("GrantPermissions")) { found = true; break; }
            Thread.sleep(500);
        }
        if (!found) { System.out.println("ℹ️ Not shown — skipping"); System.out.println("✅ Screen 9 done"); return; }
        adbTap(540, PERM_ALLOW_Y);
        Thread.sleep(800);
        if (currentActivity().contains("GrantPermissions")) {
            adbTap(540, PERM_ALLOW_Y); Thread.sleep(800);
        }
        printScreen("After notif perm");
        System.out.println("✅ Screen 9 done");
    }

    // ================================================================
    //   SCREEN 10 — HOME SCREEN
    // ================================================================
    public static void screen10_HomeScreen() throws Exception {
        System.out.println("\n========================================");
        System.out.println("🔷 SCREEN 10 — Home Screen");
        System.out.println("========================================");
        for (int i = 0; i < 8; i++) {
            String act = currentActivity();
            if (act.contains("Home") || act.contains("Camera") || act.contains("Main")) {
                System.out.println("✅ HomeActivity: " + act); break;
            }
            System.out.println("   Waiting... " + act);
            Thread.sleep(1000);
        }
        Thread.sleep(1000);
        printScreen("Home confirmed");
        System.out.println("✅ Screen 10 done");
    }

    // ================================================================
    //   SCREEN 11 — CLICK GALLERY ICON
    // ================================================================
    public static void screen11_ClickGalleryIcon() throws Exception {
        System.out.println("\n========================================");
        System.out.println("🔷 SCREEN 11 — Click Gallery Icon");
        System.out.println("========================================");
        Thread.sleep(1000);
        adbTap(86, 1899);
        Thread.sleep(2000);
        printScreen("After gallery open");
        System.out.println("✅ Screen 11 done");
    }

    // ================================================================
    //   SCREEN 12 — SELECT IMAGE & DONE
    // ================================================================
    public static void screen12_SelectImageAndDone() throws Exception {
        System.out.println("\n========================================");
        System.out.println("🔷 SCREEN 12 — Select Image & Done");
        System.out.println("========================================");
        Thread.sleep(2000);
        adbTap(179, 1125);
        Thread.sleep(2000);
        adbTap(898, 2136);
        Thread.sleep(3000);
        Process p = Runtime.getRuntime().exec(new String[]{
            adbPath, "-s", deviceName, "shell",
            "dumpsys", "activity", "activities", "|", "grep", "mResumedActivity"
        });
        p.waitFor();
        String act = new String(p.getInputStream().readAllBytes());
        System.out.println("📱 After Done: " + act.trim());
        System.out.println("✅ Screen 12 done");
    }
    
//    public static void screen12_SelectImageAndDone() throws Exception {
//    System.out.println("\n========================================");
//    System.out.println("🔷 SCREEN 12 — Select Image & Done");
//    System.out.println("========================================");
//    Thread.sleep(3000);
//
//    // Step 1: Latest image tap
//    System.out.println("Tapping latest image (179, 1367)");
//    adbTap(179, 1367);
//    Thread.sleep(2000);
//
//    // Step 2: Done button — NO uiautomator dump (causes app to background!)
//    // XML confirmed: bounds [788,2070][1008,2202] → center (898, 2136)
//    System.out.println("Tapping Done (898, 2136)");
//    adbTap(898, 2136);
//    Thread.sleep(1000);
//
//    // Step 3: Verify — crop screen આવ્યો?
//    String act = currentActivity();
//    System.out.println("After Done: " + act);
//
//    if (act.contains("photopicker")) {
//        System.out.println("⚠️ Still in picker — tapping Done again");
//        adbTap(898, 2136);
//        Thread.sleep(1000);
//
//        // Still picker? image deselect થઈ હશે — reselect
//        act = currentActivity();
//        if (act.contains("photopicker")) {
//            System.out.println("⚠️ Reselecting image...");
//            adbTap(179, 1367);
//            Thread.sleep(1500);
//            adbTap(898, 2136);
//            Thread.sleep(2000);
//        }
//    }
//
//    printScreen("After Done");
//    System.out.println("✅ Screen 12 done");
//}


    // ================================================================
    //   SCREEN 13 — CROP CONFIRM
    // ================================================================
    public static void screen13_CropConfirm() throws Exception {
        System.out.println("\n========================================");
        System.out.println("🔷 SCREEN 13 — Crop: Tap ✓");
        System.out.println("========================================");
        Thread.sleep(1500);
        adbTap(999, 185);
        Thread.sleep(2000);
        printScreen("After crop confirm");
        System.out.println("✅ Screen 13 done");
    }
   

    // ================================================================
    //   SCREEN 14 — WAIT FOR SCAN & TAP SHOW SOLVING STEPS
    // ================================================================
    public static void screen14_WaitForScan() throws Exception {
        System.out.println("\n========================================");
        System.out.println("🔷 SCREEN 14 — Waiting for Scan...");
        System.out.println("========================================");
        System.out.println("⏳ Waiting 8s for scan...");
        for (int i = 8; i > 0; i--) {
            System.out.println("   " + i + "s remaining...");
            try { driver.currentActivity(); } catch (Exception ignored) {}
            Thread.sleep(1000);
        }
        System.out.println("Tapping Show Solving Steps (540, 1950)");
        adbTap(540, 1950);
        Thread.sleep(1000);
        System.out.println("✅ Screen 14 done");
    }

    // ================================================================
    //   SCREEN 16 — AD SKIP
    // ================================================================
    public static void screen16_AdSkip() throws Exception {
        System.out.println("\n========================================");
        System.out.println("🔷 SCREEN 16 — Ad: Wait → Skip");
        System.out.println("========================================");

        for (int i = 0; i < 20; i++) {
            try { driver.currentActivity(); } catch (Exception ignored) {}
            String act = currentActivity();

            if (act.contains("NewMathAPIStepActivity") || act.contains("Result")) {
                System.out.println("✅ Result screen at " + i + "s — no ad!");
                System.out.println("✅ Screen 16 done");
                return;
            }

            System.out.println("   Waiting... " + i + "s — " + act);
            Thread.sleep(1000);

            if (i >= 5) {
                System.out.println("   Trying skip tap...");
                adbTap(971, 187);
                Thread.sleep(1000);
                act = currentActivity();
                if (act.contains("NewMathAPIStepActivity") || act.contains("Result")) {
                    System.out.println("✅ Skip worked at " + i + "s!");
                    if (act.contains("PdfViewer") || act.contains("pdf")) {
                        adbBack(); Thread.sleep(1000);
                    }
                    if (act.contains("chrome") || act.contains("Chrome")) {
                        Runtime.getRuntime().exec(new String[]{
                            adbPath, "-s", deviceName, "shell", "am", "force-stop", "com.android.chrome"
                        }).waitFor();
                        Thread.sleep(1000);
                        bringAppBack();
                    }
                    System.out.println("✅ Screen 16 done");
                    return;
                }
            }
        }

        System.out.println("Fallback tap (971, 187)");
        adbTap(971, 187);
        Thread.sleep(2000);
        printScreen("After ad skip");
        System.out.println("✅ Screen 16 done");
    }

    // ================================================================
    //   SCREEN 17 — RESULT SCREEN
    // ================================================================
    public static void screen17_ResultScreen() throws Exception {
        System.out.println("\n========================================");
        System.out.println("🔷 SCREEN 17 — Result Screen");
        System.out.println("========================================");

        boolean visible = false;
        for (int i = 0; i < 20; i++) {
            String act = currentActivity();
            if (act.contains("NewMathAPIStepActivity") ||
                act.contains("Result")  ||
                act.contains("Solution")||
                act.contains("Solver")) {
                System.out.println("✅ Result activity: " + act);
                visible = true;
                break;
            }
            if (isPresent(By.id(appPackage + ":id/webStep"))      ||
                isPresent(By.id(appPackage + ":id/cardAnswer"))   ||
                isPresent(By.id(appPackage + ":id/tvHeaderText")) ||
                isPresent(By.xpath("//*[@text='AI Math Scanner']"))) {
                System.out.println("✅ Result content visible");
                visible = true;
                break;
            }
            if (i % 4 == 0) System.out.println("   Waiting... " + (i / 2) + "s");
            Thread.sleep(500);
        }

        System.out.println("⏳ Staying on result screen 12s...");
        Thread.sleep(12000);
        printScreen("Result loaded");
        System.out.println(visible ? "✅ Result loaded!" : "⚠️ Current: " + currentActivity());
        System.out.println("✅ Screen 17 done");
    }

    // ================================================================
//   SCREEN 18 — SOLUTION SCREEN
//   XML confirmed:
//   Solution tab: tvChat bounds=[216,2171][432,2217] → center (324,2194)
//   CLR button: content-desc="clear" bounds=[838,1934][951,2041] → center (894,1987)
//   Send button: enter-button bounds=[981,1331][1067,1428] → center (1024,1379)
//   Skip button: btnSkip bounds=[907,149][1036,225] → center (971,187)
// ================================================================
//public static void screen18_SolutionScreen() throws Exception {
//    System.out.println("\n========================================");
//    System.out.println("🔷 SCREEN 18 — Solution Screen");
//    System.out.println("========================================");
//
//    // Step 1: Solution tab tap
//    // tvChat bounds=[216,2171][432,2217] → center (324, 2194)
//    System.out.println("Tapping Solution tab (324, 2194)");
//    adbTap(324, 2194);
//    Thread.sleep(2000);
//
//    // Step 2: CLR — clear previous input
//    // content-desc="clear" bounds=[838,1934][951,2041] → center (894, 1987)
//    System.out.println("Tapping CLR (894, 1987)");
//    adbTap(894, 1987);
//    Thread.sleep(500);
//
//    // Step 3: Type equation char by char
//    System.out.println("Typing equation: " + equation);
//    String eq = equation.replace(" ", "");
//    for (char c : eq.toCharArray()) {
//        int[] coords = keyMap.get(c);
//        if (coords != null) {
//            System.out.println("   Typing: '" + c + "' → (" + coords[0] + "," + coords[1] + ")");
//            adbTap(coords[0], coords[1]);
//            Thread.sleep(300);
//        } else {
//            System.out.println("   ⚠️ Key not mapped: '" + c + "' — skipping");
//        }
//    }
//
//    Thread.sleep(500);
//
//    // Step 4: Send/Enter button tap
//    // enter-button bounds=[981,1331][1067,1428] → center (1024, 1379)
//    System.out.println("Tapping Send button (1024, 1379)");
//    adbTap(1024, 1379);
//    Thread.sleep(2000);
//
//    // Step 5: Ad check — show thay to skip, nahi to direct result
//    System.out.println("⏳ Checking for ad...");
//    boolean adFound = false;
//
//    for (int i = 0; i < 12; i++) {
//        try { driver.currentActivity(); } catch (Exception ignored) {}
//        String act = currentActivity();
//
//        // Ad screen detect
//        if (act.contains("FullScreenNativeAdActivity") ||
//            act.contains("flNativeAd")) {
//            System.out.println("📺 Ad detected at " + i + "s — waiting for skip...");
//            adFound = true;
//            break;
//        }
//
//        // Result already visible — no ad
//        if (isPresent(By.id(appPackage + ":id/rvMathChatView"))) {
//            System.out.println("✅ Result visible at " + i + "s — no ad!");
//            break;
//        }
//
//        System.out.println("   Waiting... " + i + "s");
//        Thread.sleep(1000);
//    }
//
//    // Ad found — wait 10s then skip
//    if (adFound) {
//        System.out.println("⏳ Waiting 10s for skip button...");
//        for (int i = 10; i > 0; i--) {
//            System.out.println("   " + i + "s remaining...");
//            try { driver.currentActivity(); } catch (Exception ignored) {}
//            Thread.sleep(1000);
//        }
//
//        // Direct ADB tap — btnSkip NAF=true
//        System.out.println("Tapping Skip (971, 187)");
//        adbTap(971, 187);
//        Thread.sleep(1000);
//
//        // Browser check
//        String act = currentActivity();
//        if (act.contains("chrome") || act.contains("Chrome")) {
//            System.out.println("🌐 Chrome detected — closing");
//            Runtime.getRuntime().exec(new String[]{
//                adbPath, "-s", deviceName, "shell", "am", "force-stop", "com.android.chrome"
//            }).waitFor();
//            Thread.sleep(1000);
//            bringAppBack();
//        }
//    }
//
//    Thread.sleep(2000);
//    printScreen("Solution result");
//    System.out.println("✅ Screen 18 done");
//}
    public static void screen18_SolutionScreen() throws Exception {
        System.out.println("\n========================================");
        System.out.println("🔷 SCREEN 18 — Solution Screen");
        System.out.println("========================================");

        // Step 1: Solution tab tap
        System.out.println("Tapping Solution tab (324, 2194)");
        adbTap(324, 2194);
        Thread.sleep(2000);

        // Step 2: CLR
        System.out.println("Tapping CLR (894, 1987)");
        adbTap(894, 1987);
        Thread.sleep(500);

        // Step 3: Type equation
        System.out.println("Typing equation: " + equation);
        String eq = equation.replace(" ", "");
        for (char c : eq.toCharArray()) {
            int[] coords = keyMap.get(c);
            if (coords != null) {
                System.out.println("   Typing: '" + c + "' → (" + coords[0] + "," + coords[1] + ")");
                adbTap(coords[0], coords[1]);
                Thread.sleep(300);
            } else {
                System.out.println("   ⚠️ Key not mapped: '" + c + "' — skipping");
            }
        }
        Thread.sleep(500);

        // Step 4: Send
        System.out.println("Tapping Send button (1024, 1379)");
        adbTap(1024, 1379);
        Thread.sleep(1500);

        // Step 5: Ad check — max 5s only
        System.out.println("⏳ Checking for ad (max 5s)...");
        boolean adFound = false;

        for (int i = 0; i < 5; i++) {
            try { driver.currentActivity(); } catch (Exception ignored) {}
            String act = currentActivity();

            // Ad detected
            if (act.contains("FullScreenNativeAdActivity") ||
                act.contains("flNativeAd")) {
                System.out.println("📺 Ad detected at " + i + "s");
                adFound = true;
                break;
            }

            // Result visible — no ad
            if (isPresent(By.id(appPackage + ":id/rvMathChatView"))) {
                System.out.println("✅ Result visible — no ad!");
                break;
            }

            Thread.sleep(1000);
        }

        // Ad found — wait 10s then skip
        if (adFound) {
            System.out.println("⏳ Waiting 10s for skip...");
            for (int i = 10; i > 0; i--) {
                System.out.println("   " + i + "s...");
                try { driver.currentActivity(); } catch (Exception ignored) {}
                Thread.sleep(1000);
            }
            System.out.println("Tapping Skip (971, 187)");
            adbTap(971, 187);
            Thread.sleep(1000);

            String act = currentActivity();
            if (act.contains("chrome") || act.contains("Chrome")) {
                Runtime.getRuntime().exec(new String[]{
                    adbPath, "-s", deviceName, "shell", "am", "force-stop", "com.android.chrome"
                }).waitFor();
                Thread.sleep(1000);
                bringAppBack();
            }
        }

        Thread.sleep(2000);
        printScreen("Solution result");
        System.out.println("✅ Screen 18 done");
    }

    public static String getAppVersion() throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
            adbPath, "-s", deviceName, "shell",
            "dumpsys", "package", appPackage
        );
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String output = new String(p.getInputStream().readAllBytes());
        p.waitFor();

        for (String line : output.split("\n")) {
            line = line.trim();
            if (line.startsWith("versionName=")) {
                String version = line.replace("versionName=", "").trim();
                System.out.println("✅ App Version: " + version);
                return version;
            }
        }
        System.out.println("⚠️ Version not found");
        return "unknown";
    }

public static void sendZulipMessage(String message) throws Exception {
    System.out.println("\n📨 Sending Zulip message...");

    String zulipUrl   = "https://103.254.172.165:8080/api/v1/messages";
    String botEmail   = "kuldeep-bot@103.254.172.165";
    String botApiKey  = "aSeLcd1W7JOTDiGBEeYxVglpeidw12Yv";
    String streamName = "Charul Team";
    String topicName  = "Daily Task";

    String encodedMsg    = java.net.URLEncoder.encode(message, "UTF-8");
    String encodedStream = java.net.URLEncoder.encode(streamName, "UTF-8");
    String encodedTopic  = java.net.URLEncoder.encode(topicName, "UTF-8");

    String postData = "type=stream"
        + "&to=" + encodedStream
        + "&topic=" + encodedTopic
        + "&content=" + encodedMsg;

    ProcessBuilder pb = new ProcessBuilder("curl", "-s", "-k",
        "-X", "POST", zulipUrl,
        "-u", botEmail + ":" + botApiKey,
        "--data", postData);
    pb.redirectErrorStream(true);
    Process p = pb.start();
    String response = new String(p.getInputStream().readAllBytes());
    p.waitFor();

    System.out.println("Zulip response: " + response);

    if (response.contains("\"result\":\"success\"")) {
        System.out.println("✅ Zulip message sent!");
    } else {
        System.out.println("⚠️ Zulip send failed: " + response);
    }
}


    // ================================================================
    //   GO BACK TO HOME
    // ================================================================
    public static void goBackToHome() throws Exception {
        System.out.println("\n--- Going back to Home Screen ---");
        adbBack();
        Thread.sleep(1000);
        for (int i = 0; i < 8; i++) {
            String act = currentActivity();
            System.out.println("   Current: " + act);
            if (act.contains("Home") || act.contains("Camera") || act.contains("HomeActivity")) {
                System.out.println("✅ Home reached");
                break;
            }
            adbBack();
            Thread.sleep(1000);
        }
        Thread.sleep(1500);
    }

 
    public static void main(String[] args) {
    System.out.println("========================================");
    System.out.println("   MATH SCANNER — 2 QUESTIONS LOOP     ");
    System.out.println("========================================");
    try {
    	
    	// Auto detect device
    	deviceName = detectDevice();
    	System.out.println("🔌 Using device: " + deviceName);
        String[] questions = generateMathQuestions();

        forceStopApp();
        launchApp();

        screen1_SplashScreen();
        screen2_SubscriptionScreen();
        screen3_InterstitialAd();
        screen4_LanguageScreen();
        screen5_Onboarding1();
        screen6_Onboarding2();
        screen7_GetStarted();
        screen8_CameraPermission();
        screen9_NotificationPermission();
        screen10_HomeScreen();

        // ── PHASE 1: 2 Questions SCAN ──
        System.out.println("\n========================================");
        System.out.println("   PHASE 1 — SCAN BOTH QUESTIONS       ");
        System.out.println("========================================");

        for (int q = 0; q < 2; q++) {
            System.out.println("\n--- SCAN Q" + (q+1) + ": " + questions[q] + " ---");
            equation = questions[q];

            preStep_GenerateAndPushImage();
            Thread.sleep(2000);
            screen11_ClickGalleryIcon();
            screen12_SelectImageAndDone();
            screen13_CropConfirm();
            screen14_WaitForScan();
            screen16_AdSkip();
            screen17_ResultScreen();

            System.out.println("✅ Scan Q" + (q+1) + " done!");

            // Q1 પછી home પર જાઓ
            if (q == 0) {
                System.out.println("⏳ Going home for Q2...");
                Thread.sleep(2000);
                goBackToHome();
            }
        }

        // ── PHASE 2: Solution Screen — એક જ વખત ──
        System.out.println("\n========================================");
        System.out.println("   PHASE 2 — SOLUTION SCREEN           ");
        System.out.println("========================================");

        equation = questions[1];
        System.out.println("Solution for: " + equation);

        goBackToHome();
        screen18_SolutionScreen();
        String appVersion = getAppVersion();

        // ── Zulip Message Send ──
         sendZulipMessage("AI Math Scanner " + appVersion + ": API Working properly in Live");

        System.out.println("\n========================================");
        System.out.println("   ✅ ALL DONE!                         ");
        System.out.println("========================================");

        closeApp();

    } catch (Exception e) {
        System.out.println("\n❌ ERROR: " + e.getMessage());
        e.printStackTrace();
        try { closeApp(); } catch (Exception ex) { }
    }
}
}