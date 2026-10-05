package com.malfr3one.mandoub;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private final SupabaseApi api = new SupabaseApi();
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;

    private String accessToken = "";
    private String refreshToken = "";
    private String userId = "";
    private String email = "";
    private boolean isAdmin = false;
    private boolean darkMode = false;
    private JSONObject currentDriver;
    private JSONArray drivers = new JSONArray();
    private JSONArray orders = new JSONArray();
    private String workspaceName = "تاج الملكة";
    private int currentTab = 0; // 0 orders, 1 drivers, 2 settings
    private String orderFilter = "all";

    private static final int PRIMARY = 0xFF155E75;
    private static final int PRIMARY_DARK = 0xFF0F4555;
    private static final int ACCENT = 0xFFF59E0B;
    private static final int SUCCESS = 0xFF16A34A;
    private static final int DANGER = 0xFFDC2626;
    private static final int INFO = 0xFF2563EB;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("mandoub_session", MODE_PRIVATE);
        darkMode = prefs.getBoolean("dark_mode", false);
        applySystemBars();

        String savedRefresh = prefs.getString("refresh_token", "");
        if (!savedRefresh.isEmpty()) {
            showLoading("جاري فتح حسابك...");
            io.execute(() -> {
                try {
                    SupabaseApi.AuthResult result = api.refresh(savedRefresh);
                    saveSession(result);
                    loadIdentityAndDashboard();
                } catch (Exception e) {
                    clearSession();
                    runOnUiThread(this::showLogin);
                }
            });
        } else {
            showLogin();
        }
    }

    private void applySystemBars() {
        getWindow().setStatusBarColor(darkMode ? 0xFF0B1220 : PRIMARY_DARK);
        getWindow().setNavigationBarColor(darkMode ? 0xFF0B1220 : 0xFFFFFFFF);
        getWindow().getDecorView().setSystemUiVisibility(darkMode ? 0 : View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    }

    private void showLogin() {
        applySystemBars();
        LinearLayout root = pageRoot();
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(22), dp(34), dp(22), dp(30));

        TextView mark = new TextView(this);
        mark.setText("م");
        mark.setGravity(Gravity.CENTER);
        mark.setTextSize(28);
        mark.setTypeface(Typeface.DEFAULT_BOLD);
        mark.setTextColor(Color.WHITE);
        mark.setBackground(round(PRIMARY, 28, 0, 0));
        root.addView(mark, new LinearLayout.LayoutParams(dp(68), dp(68)));

        TextView logo = title("مندوب", 32);
        logo.setGravity(Gravity.CENTER);
        logo.setPadding(0, dp(14), 0, 0);
        root.addView(logo, matchWrap());

        TextView sub = text("إدارة التوصيل من قاعدة بياناتك مباشرة", 15);
        sub.setTextColor(mutedColor());
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dp(5), 0, dp(24));
        root.addView(sub, matchWrap());

        LinearLayout card = cardBox();
        card.setPadding(dp(18), dp(20), dp(18), dp(18));

        TextView welcome = title("تسجيل الدخول", 21);
        card.addView(welcome);
        TextView hint = text("أدخل بيانات حساب المدير أو المندوب", 13);
        hint.setTextColor(mutedColor());
        hint.setPadding(0, dp(3), 0, dp(12));
        card.addView(hint);

        EditText emailInput = field("البريد الإلكتروني");
        emailInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        card.addView(emailInput);

        EditText passwordInput = field("كلمة المرور");
        passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        card.addView(passwordInput);

        Button login = primaryButton("دخول");
        login.setOnClickListener(v -> authenticate(emailInput, passwordInput, null, false, false));
        card.addView(login, matchWrapMargins(0, dp(12), 0, 0));
        root.addView(card, matchWrap());

        Button firstAdmin = linkButton("تهيئة أول حساب مدير");
        firstAdmin.setOnClickListener(v -> showFirstAdminDialog());
        root.addView(firstAdmin, matchWrapMargins(0, dp(12), 0, 0));

        TextView note = text("حسابات المناديب يتم إنشاؤها من لوحة المدير بدون رسائل تأكيد بريد، لذلك تقدر تضيف أعداد كبيرة بدون مشكلة حد الإيميلات.", 12);
        note.setGravity(Gravity.CENTER);
        note.setTextColor(mutedColor());
        note.setPadding(dp(10), dp(16), dp(10), 0);
        root.addView(note, matchWrap());

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(bgColor());
        scroll.addView(root);
        setContentView(scroll);
    }

    private void showFirstAdminDialog() {
        LinearLayout form = dialogForm();
        EditText mail = field("بريد المدير");
        mail.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        EditText password = field("كلمة المرور");
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText code = field("كود تفعيل المدير");
        form.addView(mail); form.addView(password); form.addView(code);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("تهيئة أول مدير")
                .setMessage("تستخدم مرة واحدة فقط عند إنشاء أول حساب مدير.")
                .setView(form)
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("إنشاء المدير", null)
                .create();
        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String m = mail.getText().toString().trim();
            String p = password.getText().toString();
            String c = code.getText().toString().trim();
            if (m.isEmpty() || p.length() < 6 || c.isEmpty()) { toast("أكمل البيانات وكلمة المرور 6 أحرف على الأقل"); return; }
            dialog.dismiss();
            authenticate(mail, p, c);
        }));
        dialog.show();
    }

    private void authenticate(String mail, String pass, String setupCode) {
        showLoading("جاري إنشاء حساب المدير...");
        io.execute(() -> {
            try {
                SupabaseApi.AuthResult result = api.signUp(mail, pass);
                if (result.needsConfirmation || result.accessToken == null || result.accessToken.isEmpty()) {
                    runOnUiThread(() -> {
                        showLogin();
                        new AlertDialog.Builder(this)
                                .setTitle("تأكيد البريد")
                                .setMessage("حساب المدير يحتاج تأكيد البريد مرة واحدة. بعد التأكيد ارجع وسجل الدخول ثم فعّل المدير بالكود.")
                                .setPositiveButton("حسناً", null)
                                .show();
                    });
                    return;
                }
                saveSession(result);
                api.claimAdmin(accessToken, setupCode);
                loadIdentityAndDashboard();
            } catch (Exception e) {
                clearSession();
                runOnUiThread(() -> { showLogin(); toast(friendlyError(e)); });
            }
        });
    }

    private void authenticate(EditText emailInput, EditText passwordInput, EditText setupCode, boolean claimAdmin, boolean signup) {
        String mail = emailInput.getText().toString().trim();
        String pass = passwordInput.getText().toString();
        if (mail.isEmpty() || pass.length() < 6) {
            toast("اكتب البريد الإلكتروني وكلمة مرور 6 أحرف على الأقل");
            return;
        }
        showLoading("جاري تسجيل الدخول...");
        io.execute(() -> {
            try {
                SupabaseApi.AuthResult result = api.signIn(mail, pass);
                saveSession(result);
                loadIdentityAndDashboard();
            } catch (Exception e) {
                clearSession();
                runOnUiThread(() -> { showLogin(); toast(friendlyError(e)); });
            }
        });
    }

    private void loadIdentityAndDashboard() throws Exception {
        JSONArray membership = api.getRows(accessToken, "app_members?select=role&user_id=eq." + enc(userId));
        isAdmin = membership.length() > 0 && "admin".equals(membership.getJSONObject(0).optString("role"));
        currentDriver = null;
        if (!isAdmin) {
            JSONArray mine = api.getRows(accessToken, "drivers?select=id,name,email,phone&email=eq." + enc(email));
            if (mine.length() == 0) throw new Exception("هذا الحساب غير مضاف كمندوب");
            currentDriver = mine.getJSONObject(0);
        }
        refreshData();
    }

    private void refreshData() throws Exception {
        try {
            JSONArray ws = api.getRows(accessToken, "workspace?select=name&id=eq.1");
            if (ws.length() > 0) workspaceName = ws.getJSONObject(0).optString("name", workspaceName);
        } catch (Exception ignored) {}
        drivers = api.getRows(accessToken, "drivers?select=id,name,email,phone&order=name.asc");
        orders = api.getRows(accessToken, "orders?select=id,customer,phone,address,district,amount,fee,payment,driver_id,status,notes,created,delivered,settled,maps_url&order=created.desc");
        runOnUiThread(this::renderApp);
    }

    private void renderApp() {
        if (!isAdmin && currentTab == 1) currentTab = 0;
        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackgroundColor(bgColor());

        screen.addView(buildHeader(), matchWrap());

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = pageRoot();
        content.setPadding(dp(14), dp(14), dp(14), dp(18));
        if (currentTab == 0) buildOrdersPage(content);
        else if (currentTab == 1 && isAdmin) buildDriversPage(content);
        else buildSettingsPage(content);
        scroll.addView(content);
        screen.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        screen.addView(buildBottomNav(), matchWrap());
        setContentView(screen);
    }

    private View buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(18), dp(18), dp(18), dp(16));
        header.setBackground(round(darkMode ? 0xFF0B1220 : PRIMARY, 0, 0, 0));

        TextView brand = title(workspaceName, 24);
        brand.setTextColor(Color.WHITE);
        header.addView(brand);

        String who = isAdmin ? "مدير النظام" : (currentDriver == null ? "مندوب" : currentDriver.optString("name", "مندوب"));
        TextView account = text(who + "  •  " + email, 13);
        account.setTextColor(0xFFD7EDF3);
        account.setPadding(0, dp(4), 0, 0);
        header.addView(account);
        return header;
    }

    private void buildOrdersPage(LinearLayout root) {
        if (isAdmin) {
            LinearLayout stats = new LinearLayout(this);
            stats.setOrientation(LinearLayout.HORIZONTAL);
            stats.addView(statCard("الطلبات", String.valueOf(orders.length()), PRIMARY), weighted(1, 0));
            stats.addView(statCard("نشطة", String.valueOf(countActive()), ACCENT), weighted(1, dp(8)));
            stats.addView(statCard("المناديب", String.valueOf(drivers.length()), INFO), weighted(1, dp(8)));
            root.addView(stats, matchWrapMargins(0, 0, 0, dp(14)));

            LinearLayout quick = new LinearLayout(this);
            quick.setOrientation(LinearLayout.HORIZONTAL);
            Button order = primaryButton("+ طلب جديد");
            order.setOnClickListener(v -> showAddOrderDialog());
            quick.addView(order, weightedHeight(1, dp(48), 0));
            Button driver = secondaryButton("+ مندوب");
            driver.setOnClickListener(v -> showAddDriverDialog());
            quick.addView(driver, weightedHeight(1, dp(48), dp(8)));
            root.addView(quick, matchWrapMargins(0, 0, 0, dp(16)));
        }

        root.addView(sectionTitle("الطلبات"));
        root.addView(buildFilters(), matchWrapMargins(0, dp(8), 0, dp(12)));

        int shown = 0;
        for (int i = 0; i < orders.length(); i++) {
            JSONObject order = orders.optJSONObject(i);
            if (order == null || !matchesFilter(order)) continue;
            shown++;
            root.addView(orderCard(order), matchWrapMargins(0, 0, 0, dp(10)));
        }
        if (shown == 0) root.addView(emptyBox("لا توجد طلبات في هذا القسم"), matchWrapMargins(0, dp(12), 0, 0));
    }

    private View buildFilters() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        String[][] options = {{"all","الكل"},{"new","جديد"},{"active","جاري"},{"delivered","تم التوصيل"}};
        for (int i = 0; i < options.length; i++) {
            String key = options[i][0];
            Button b = chipButton(options[i][1], orderFilter.equals(key));
            b.setOnClickListener(v -> { orderFilter = key; renderApp(); });
            row.addView(b, weightedHeight(1, dp(40), i == 0 ? 0 : dp(6)));
        }
        return row;
    }

    private boolean matchesFilter(JSONObject order) {
        String s = order.optString("status");
        if ("all".equals(orderFilter)) return true;
        if ("new".equals(orderFilter)) return "new".equals(s);
        if ("delivered".equals(orderFilter)) return "delivered".equals(s);
        return !"new".equals(s) && !"delivered".equals(s);
    }

    private int countActive() {
        int c = 0;
        for (int i = 0; i < orders.length(); i++) if (!"delivered".equals(orders.optJSONObject(i).optString("status"))) c++;
        return c;
    }

    private View orderCard(JSONObject order) {
        LinearLayout card = cardBox();
        card.setPadding(dp(15), dp(14), dp(15), dp(14));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = title(order.optString("customer", "-"), 18);
        top.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        top.addView(statusPill(order.optString("status")));
        card.addView(top, matchWrap());

        TextView district = text(order.optString("district") + "  •  " + order.optString("address"), 14);
        district.setTextColor(mutedColor());
        district.setPadding(0, dp(9), 0, dp(4));
        card.addView(district);

        LinearLayout money = new LinearLayout(this);
        money.setOrientation(LinearLayout.HORIZONTAL);
        TextView amount = text(order.optInt("amount") + " ر.س", 16);
        amount.setTypeface(Typeface.DEFAULT_BOLD);
        money.addView(amount, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView delivery = text("توصيل " + order.optInt("fee") + " ر.س", 13);
        delivery.setTextColor(mutedColor());
        money.addView(delivery);
        card.addView(money, matchWrapMargins(0, dp(5), 0, 0));

        String payText = "cash".equals(order.optString("payment")) ? "كاش عند الاستلام" : "مدفوع";
        TextView pay = text(payText + (isAdmin ? "  •  " + driverName(order.optString("driver_id")) : ""), 13);
        pay.setTextColor(mutedColor());
        pay.setPadding(0, dp(5), 0, 0);
        card.addView(pay);

        String notes = order.optString("notes", "");
        if (!notes.isEmpty()) {
            TextView n = text("ملاحظة: " + notes, 13);
            n.setPadding(0, dp(7), 0, 0);
            card.addView(n);
        }

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setPadding(0, dp(12), 0, 0);
        Button call = miniButton("اتصال");
        call.setOnClickListener(v -> openDial(order.optString("phone")));
        buttons.addView(call, weightedHeight(1, dp(42), 0));
        Button maps = miniButton("الخريطة");
        maps.setOnClickListener(v -> openMaps(order));
        buttons.addView(maps, weightedHeight(1, dp(42), dp(6)));
        if (isAdmin) {
            Button assign = miniButton(order.optString("driver_id", "").isEmpty() ? "تعيين" : "تغيير");
            assign.setOnClickListener(v -> showAssignDialog(order.optString("id")));
            buttons.addView(assign, weightedHeight(1, dp(42), dp(6)));
        } else if (!"delivered".equals(order.optString("status"))) {
            Button next = actionButton(nextStatusLabel(order.optString("status")));
            next.setOnClickListener(v -> advanceStatus(order));
            buttons.addView(next, weightedHeight(1, dp(42), dp(6)));
        }
        card.addView(buttons, matchWrap());
        return card;
    }

    private TextView statusPill(String status) {
        int bg = 0xFFE0F2FE, fg = 0xFF0369A1;
        if ("received".equals(status)) { bg = 0xFFFEF3C7; fg = 0xFF92400E; }
        if ("onway".equals(status)) { bg = 0xFFDBEAFE; fg = 0xFF1D4ED8; }
        if ("delivered".equals(status)) { bg = 0xFFDCFCE7; fg = 0xFF166534; }
        TextView p = text(statusArabic(status), 12);
        p.setTextColor(fg);
        p.setGravity(Gravity.CENTER);
        p.setPadding(dp(9), dp(5), dp(9), dp(5));
        p.setBackground(round(bg, 20, 0, 0));
        return p;
    }

    private void buildDriversPage(LinearLayout root) {
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = sectionTitle("المناديب");
        head.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button add = primaryButton("+ إضافة");
        add.setOnClickListener(v -> showAddDriverDialog());
        head.addView(add, new LinearLayout.LayoutParams(dp(112), dp(44)));
        root.addView(head, matchWrapMargins(0, 0, 0, dp(12)));

        TextView desc = text("إضافة الحساب تتم مباشرة بدون إرسال رسالة تأكيد بريد.", 13);
        desc.setTextColor(mutedColor());
        desc.setPadding(0, 0, 0, dp(12));
        root.addView(desc);

        if (drivers.length() == 0) {
            root.addView(emptyBox("لم تتم إضافة مناديب بعد"));
            return;
        }
        for (int i = 0; i < drivers.length(); i++) {
            JSONObject d = drivers.optJSONObject(i);
            if (d != null) root.addView(driverCard(d), matchWrapMargins(0, 0, 0, dp(10)));
        }
    }

    private View driverCard(JSONObject d) {
        LinearLayout card = cardBox();
        card.setPadding(dp(15), dp(14), dp(15), dp(14));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView avatar = text(initialOf(d.optString("name")), 18);
        avatar.setTypeface(Typeface.DEFAULT_BOLD);
        avatar.setGravity(Gravity.CENTER);
        avatar.setTextColor(Color.WHITE);
        avatar.setBackground(round(PRIMARY, 24, 0, 0));
        row.addView(avatar, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(12), 0, 0, 0);
        TextView n = title(d.optString("name"), 17);
        info.addView(n);
        TextView em = text(d.optString("email"), 13); em.setTextColor(mutedColor()); info.addView(em);
        TextView ph = text(d.optString("phone"), 13); ph.setTextColor(mutedColor()); info.addView(ph);
        row.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Button call = miniButton("اتصال");
        call.setOnClickListener(v -> openDial(d.optString("phone")));
        row.addView(call, new LinearLayout.LayoutParams(dp(82), dp(42)));
        card.addView(row);
        return card;
    }

    private void buildSettingsPage(LinearLayout root) {
        root.addView(sectionTitle("الإعدادات"), matchWrapMargins(0, 0, 0, dp(12)));

        if (isAdmin) {
            LinearLayout company = cardBox();
            company.setPadding(dp(15), dp(14), dp(15), dp(14));
            company.addView(title("اسم المنشأة", 17));
            EditText name = field("اسم المنشأة");
            name.setText(workspaceName);
            company.addView(name);
            Button save = primaryButton("حفظ الاسم");
            save.setOnClickListener(v -> saveWorkspaceName(name.getText().toString().trim()));
            company.addView(save, matchWrapMargins(0, dp(8), 0, 0));
            root.addView(company, matchWrapMargins(0, 0, 0, dp(10)));
        }

        LinearLayout appearance = cardBox();
        appearance.setPadding(dp(15), dp(14), dp(15), dp(14));
        appearance.addView(title("المظهر", 17));
        CheckBox dark = new CheckBox(this);
        dark.setText("الوضع الداكن");
        dark.setTextColor(textColor());
        dark.setTextSize(15);
        dark.setChecked(darkMode);
        dark.setOnCheckedChangeListener((b, checked) -> {
            if (darkMode == checked) return;
            darkMode = checked;
            prefs.edit().putBoolean("dark_mode", checked).apply();
            applySystemBars();
            renderApp();
        });
        appearance.addView(dark, matchWrapMargins(0, dp(6), 0, 0));
        root.addView(appearance, matchWrapMargins(0, 0, 0, dp(10)));

        LinearLayout account = cardBox();
        account.setPadding(dp(15), dp(14), dp(15), dp(14));
        account.addView(title("الحساب", 17));
        TextView role = text(isAdmin ? "النوع: مدير" : "النوع: مندوب", 14); role.setPadding(0, dp(8), 0, 0); account.addView(role);
        TextView mail = text("البريد: " + email, 14); mail.setTextColor(mutedColor()); mail.setPadding(0, dp(4), 0, 0); account.addView(mail);
        Button refresh = secondaryButton("تحديث البيانات");
        refresh.setOnClickListener(v -> reloadDashboard());
        account.addView(refresh, matchWrapMargins(0, dp(12), 0, 0));
        Button logout = dangerButton("تسجيل الخروج");
        logout.setOnClickListener(v -> { clearSession(); showLogin(); });
        account.addView(logout, matchWrapMargins(0, dp(8), 0, 0));
        root.addView(account, matchWrapMargins(0, 0, 0, dp(10)));

        TextView version = text("Mandoub v3.0.0 • Supabase", 12);
        version.setTextColor(mutedColor());
        version.setGravity(Gravity.CENTER);
        version.setPadding(0, dp(10), 0, dp(12));
        root.addView(version);
    }

    private View buildBottomNav() {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setPadding(dp(8), dp(7), dp(8), dp(9));
        nav.setBackgroundColor(surfaceColor());

        Button ordersBtn = navButton("الطلبات", currentTab == 0);
        ordersBtn.setOnClickListener(v -> { currentTab = 0; renderApp(); });
        nav.addView(ordersBtn, weightedHeight(1, dp(48), 0));
        if (isAdmin) {
            Button driversBtn = navButton("المناديب", currentTab == 1);
            driversBtn.setOnClickListener(v -> { currentTab = 1; renderApp(); });
            nav.addView(driversBtn, weightedHeight(1, dp(48), dp(6)));
        }
        Button settingsBtn = navButton("الإعدادات", currentTab == 2);
        settingsBtn.setOnClickListener(v -> { currentTab = 2; renderApp(); });
        nav.addView(settingsBtn, weightedHeight(1, dp(48), dp(6)));
        return nav;
    }

    private void showAddDriverDialog() {
        LinearLayout form = dialogForm();
        EditText name = field("اسم المندوب");
        EditText mail = field("البريد الإلكتروني");
        mail.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        EditText phone = field("رقم الجوال");
        phone.setInputType(InputType.TYPE_CLASS_PHONE);
        EditText password = field("كلمة مرور مؤقتة");
        password.setText(generatePassword());
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        form.addView(name); form.addView(mail); form.addView(phone); form.addView(password);

        TextView tip = text("لن يتم إرسال أي إيميل تأكيد. أعطِ المندوب الإيميل وكلمة المرور ليستخدمهما مباشرة.", 12);
        tip.setTextColor(mutedColor());
        tip.setPadding(0, dp(6), 0, 0);
        form.addView(tip);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("إضافة مندوب وحساب دخول")
                .setView(form)
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("إنشاء", null)
                .create();
        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String n = name.getText().toString().trim();
            String e = mail.getText().toString().trim().toLowerCase();
            String p = phone.getText().toString().trim();
            String pw = password.getText().toString();
            if (n.isEmpty() || e.isEmpty() || p.isEmpty() || pw.length() < 6) { toast("أكمل البيانات وكلمة المرور 6 أحرف على الأقل"); return; }
            dialog.dismiss();
            runBusy("جاري إنشاء حساب المندوب...", () -> {
                api.createDriverAccount(accessToken, n, e, p, pw);
                refreshData();
                runOnUiThread(() -> showCredentials(e, pw));
            });
        }));
        dialog.show();
    }

    private void showCredentials(String e, String pw) {
        String value = "بيانات دخول تطبيق مندوب\nالبريد: " + e + "\nكلمة المرور: " + pw;
        new AlertDialog.Builder(this)
                .setTitle("تم إنشاء المندوب")
                .setMessage(value)
                .setNegativeButton("إغلاق", null)
                .setPositiveButton("نسخ البيانات", (d, w) -> {
                    ClipboardManager cb = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    cb.setPrimaryClip(ClipData.newPlainText("driver credentials", value));
                    toast("تم نسخ بيانات الدخول");
                }).show();
    }

    private void showAddOrderDialog() {
        LinearLayout form = dialogForm();
        EditText customer = field("اسم العميل");
        EditText phone = field("رقم العميل"); phone.setInputType(InputType.TYPE_CLASS_PHONE);
        EditText address = field("العنوان");
        EditText district = field("الحي");
        EditText maps = field("رابط Google Maps"); maps.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        EditText amount = field("قيمة الطلب"); amount.setInputType(InputType.TYPE_CLASS_NUMBER);
        EditText fee = field("رسوم التوصيل"); fee.setInputType(InputType.TYPE_CLASS_NUMBER);
        EditText notes = field("ملاحظات");
        Spinner payment = new Spinner(this);
        payment.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"كاش عند الاستلام", "مدفوع"}));
        Spinner driver = new Spinner(this);
        List<String> driverLabels = new ArrayList<>();
        driverLabels.add("بدون مندوب");
        for (int i = 0; i < drivers.length(); i++) driverLabels.add(drivers.optJSONObject(i).optString("name"));
        driver.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, driverLabels));

        form.addView(customer); form.addView(phone); form.addView(address); form.addView(district); form.addView(maps);
        form.addView(amount); form.addView(fee);
        form.addView(label("طريقة الدفع")); form.addView(payment, matchWrap());
        form.addView(label("المندوب")); form.addView(driver, matchWrap());
        form.addView(notes);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("إضافة طلب")
                .setView(form)
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("حفظ الطلب", null)
                .create();
        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                String c = customer.getText().toString().trim();
                String ph = phone.getText().toString().trim();
                String ad = address.getText().toString().trim();
                String di = district.getText().toString().trim();
                if (c.isEmpty() || ph.isEmpty() || ad.isEmpty() || di.isEmpty()) { toast("أكمل بيانات الطلب"); return; }
                int a = Integer.parseInt(amount.getText().toString().trim().isEmpty() ? "0" : amount.getText().toString().trim());
                int f = Integer.parseInt(fee.getText().toString().trim().isEmpty() ? "0" : fee.getText().toString().trim());
                JSONObject body = new JSONObject();
                body.put("customer", c); body.put("phone", ph); body.put("address", ad); body.put("district", di);
                body.put("maps_url", maps.getText().toString().trim()); body.put("amount", a); body.put("fee", f);
                body.put("payment", payment.getSelectedItemPosition() == 0 ? "cash" : "paid");
                body.put("notes", notes.getText().toString().trim()); body.put("status", "new");
                if (driver.getSelectedItemPosition() > 0) body.put("driver_id", drivers.getJSONObject(driver.getSelectedItemPosition() - 1).optString("id"));
                else body.put("driver_id", JSONObject.NULL);
                dialog.dismiss();
                runBusy("جاري حفظ الطلب...", () -> { api.insertRow(accessToken, "orders", body); refreshData(); });
            } catch (Exception e) { toast("راجع المبلغ ورسوم التوصيل"); }
        }));
        dialog.show();
    }

    private void showAssignDialog(String orderId) {
        if (drivers.length() == 0) { toast("أضف مندوب أولاً"); return; }
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < drivers.length(); i++) labels.add(drivers.optJSONObject(i).optString("name"));
        Spinner spinner = new Spinner(this);
        spinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
        new AlertDialog.Builder(this)
                .setTitle("اختيار المندوب")
                .setView(spinner)
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("تعيين", (d, which) -> {
                    JSONObject selected = drivers.optJSONObject(spinner.getSelectedItemPosition());
                    runBusy("جاري تعيين المندوب...", () -> {
                        JSONObject body = new JSONObject();
                        body.put("driver_id", selected.optString("id"));
                        api.updateRows(accessToken, "orders?id=eq." + enc(orderId), body);
                        refreshData();
                    });
                }).show();
    }

    private void saveWorkspaceName(String value) {
        if (value.isEmpty()) { toast("اكتب اسم المنشأة"); return; }
        runBusy("جاري حفظ الإعدادات...", () -> {
            JSONObject body = new JSONObject(); body.put("name", value);
            api.updateRows(accessToken, "workspace?id=eq.1", body);
            workspaceName = value;
            refreshData();
        });
    }

    private void advanceStatus(JSONObject order) {
        String current = order.optString("status");
        String next = "new".equals(current) ? "received" : "received".equals(current) ? "onway" : "delivered";
        runBusy("جاري تحديث حالة الطلب...", () -> {
            JSONObject body = new JSONObject();
            body.put("status", next);
            if ("delivered".equals(next)) body.put("delivered", OffsetDateTime.now().toString());
            api.updateRows(accessToken, "orders?id=eq." + enc(order.optString("id")), body);
            refreshData();
        });
    }

    private void reloadDashboard() {
        showLoading("جاري تحديث البيانات...");
        io.execute(() -> {
            try { refreshData(); }
            catch (Exception e) { runOnUiThread(() -> { renderApp(); toast(friendlyError(e)); }); }
        });
    }

    private interface Work { void run() throws Exception; }
    private void runBusy(String message, Work work) {
        showLoading(message);
        io.execute(() -> {
            try { work.run(); }
            catch (Exception e) { runOnUiThread(() -> { renderApp(); toast(friendlyError(e)); }); }
        });
    }

    private void showLoading(String message) {
        LinearLayout root = pageRoot();
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(bgColor());
        ProgressBar bar = new ProgressBar(this);
        root.addView(bar, new LinearLayout.LayoutParams(dp(52), dp(52)));
        TextView label = text(message, 15);
        label.setTextColor(mutedColor());
        label.setGravity(Gravity.CENTER);
        label.setPadding(0, dp(14), 0, 0);
        root.addView(label);
        setContentView(root);
    }

    private void saveSession(SupabaseApi.AuthResult result) {
        accessToken = result.accessToken == null ? "" : result.accessToken;
        refreshToken = result.refreshToken == null ? "" : result.refreshToken;
        userId = result.userId == null ? "" : result.userId;
        email = result.email == null ? "" : result.email.toLowerCase();
        prefs.edit().putString("refresh_token", refreshToken).apply();
    }

    private void clearSession() {
        accessToken = refreshToken = userId = email = "";
        isAdmin = false;
        currentDriver = null;
        prefs.edit().remove("refresh_token").apply();
    }

    private void openDial(String phone) {
        try { startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone))); }
        catch (Exception e) { toast("تعذر فتح الاتصال"); }
    }

    private void openMaps(JSONObject order) {
        String url = order.optString("maps_url", "").trim();
        Uri uri = !url.isEmpty() ? Uri.parse(url) : Uri.parse("geo:0,0?q=" + Uri.encode(order.optString("address") + " " + order.optString("district")));
        try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
        catch (Exception e) { toast("تعذر فتح الخرائط"); }
    }

    private String driverName(String id) {
        if (id == null || id.isEmpty() || "null".equals(id)) return "غير معين";
        for (int i = 0; i < drivers.length(); i++) {
            JSONObject d = drivers.optJSONObject(i);
            if (d != null && id.equals(d.optString("id"))) return d.optString("name", "مندوب");
        }
        return "مندوب";
    }

    private String statusArabic(String value) {
        switch (value) {
            case "received": return "تم الاستلام";
            case "onway": return "في الطريق";
            case "delivered": return "تم التوصيل";
            default: return "جديد";
        }
    }

    private String nextStatusLabel(String value) {
        if ("new".equals(value)) return "استلام";
        if ("received".equals(value)) return "في الطريق";
        return "تم التوصيل";
    }

    private String friendlyError(Exception e) {
        String message = e.getMessage() == null ? "حدث خطأ" : e.getMessage();
        String lower = message.toLowerCase();
        if (lower.contains("invalid login") || lower.contains("invalid credentials")) return "الإيميل أو كلمة المرور غير صحيحة";
        if (lower.contains("email not confirmed")) return "هذا الحساب غير مفعّل";
        if (lower.contains("admin_already_claimed")) return "تم تفعيل حساب مدير سابقاً";
        if (lower.contains("invalid_setup_code")) return "كود المدير غير صحيح";
        if (lower.contains("already registered") || lower.contains("already exists")) return "هذا البريد مسجل بالفعل";
        if (lower.contains("invalid_input")) return "راجع بيانات المندوب وكلمة المرور";
        if (lower.contains("rate limit") || lower.contains("email rate")) return "تم تجاوز حد البريد. أضف المناديب من لوحة المدير الجديدة لتجاوز رسائل التأكيد.";
        if (lower.contains("هذا الحساب غير مضاف")) return message;
        if (lower.contains("network") || lower.contains("unable to resolve host")) return "تحقق من اتصال الإنترنت";
        return message.length() > 150 ? "حدث خطأ أثناء الاتصال بقاعدة البيانات" : message;
    }

    private LinearLayout pageRoot() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.setBackgroundColor(bgColor());
        return root;
    }

    private LinearLayout dialogForm() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(8), dp(20), dp(4));
        return form;
    }

    private LinearLayout cardBox() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(round(surfaceColor(), 18, borderColor(), 1));
        return card;
    }

    private View statCard(String label, String value, int color) {
        LinearLayout card = cardBox();
        card.setPadding(dp(10), dp(12), dp(10), dp(12));
        TextView v = title(value, 22); v.setTextColor(color); v.setGravity(Gravity.CENTER); card.addView(v);
        TextView l = text(label, 12); l.setTextColor(mutedColor()); l.setGravity(Gravity.CENTER); card.addView(l);
        return card;
    }

    private TextView sectionTitle(String value) {
        TextView t = title(value, 20);
        return t;
    }

    private View emptyBox(String value) {
        TextView e = text(value, 15);
        e.setTextColor(mutedColor());
        e.setGravity(Gravity.CENTER);
        e.setPadding(dp(15), dp(34), dp(15), dp(34));
        e.setBackground(round(surfaceColor(), 18, borderColor(), 1));
        return e;
    }

    private TextView title(String value, int size) {
        TextView view = text(value, size);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private TextView text(String value, int size) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(textColor());
        view.setGravity(Gravity.RIGHT);
        return view;
    }

    private TextView label(String value) {
        TextView view = text(value, 13);
        view.setTextColor(mutedColor());
        view.setPadding(0, dp(10), 0, dp(4));
        return view;
    }

    private EditText field(String hint) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setHintTextColor(darkMode ? 0xFF7C899D : 0xFF94A3B8);
        input.setTextColor(textColor());
        input.setTextSize(15);
        input.setSingleLine(true);
        input.setPadding(dp(13), dp(11), dp(13), dp(11));
        input.setBackground(round(darkMode ? 0xFF111827 : 0xFFF8FAFC, 12, borderColor(), 1));
        input.setLayoutParams(matchWrapMargins(0, dp(6), 0, dp(6)));
        return input;
    }

    private Button primaryButton(String text) { return styledButton(text, PRIMARY, Color.WHITE, 13); }
    private Button secondaryButton(String text) { return styledButton(text, darkMode ? 0xFF1F2937 : 0xFFE7EEF2, textColor(), 13); }
    private Button miniButton(String text) { return styledButton(text, darkMode ? 0xFF1F2937 : 0xFFF1F5F9, textColor(), 11); }
    private Button actionButton(String text) { return styledButton(text, PRIMARY, Color.WHITE, 11); }
    private Button dangerButton(String text) { return styledButton(text, DANGER, Color.WHITE, 13); }
    private Button linkButton(String text) { return styledButton(text, Color.TRANSPARENT, PRIMARY, 13); }

    private Button chipButton(String text, boolean selected) {
        return styledButton(text, selected ? PRIMARY : (darkMode ? 0xFF1F2937 : 0xFFEFF4F6), selected ? Color.WHITE : textColor(), 11);
    }

    private Button navButton(String text, boolean selected) {
        return styledButton(text, selected ? (darkMode ? 0xFF164E63 : 0xFFE0F2FE) : Color.TRANSPARENT, selected ? (darkMode ? 0xFF7DD3FC : PRIMARY) : mutedColor(), 12);
    }

    private Button styledButton(String text, int bg, int fg, int size) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(size);
        b.setTextColor(fg);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(8), 0, dp(8), 0);
        b.setBackground(round(bg, 12, 0, 0));
        return b;
    }

    private GradientDrawable round(int color, int radius, int strokeColor, int strokeWidth) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radius));
        if (strokeWidth > 0) g.setStroke(dp(strokeWidth), strokeColor);
        return g;
    }

    private int bgColor() { return darkMode ? 0xFF0B1220 : 0xFFF4F7FA; }
    private int surfaceColor() { return darkMode ? 0xFF111827 : 0xFFFFFFFF; }
    private int textColor() { return darkMode ? 0xFFF3F4F6 : 0xFF172033; }
    private int mutedColor() { return darkMode ? 0xFF94A3B8 : 0xFF64748B; }
    private int borderColor() { return darkMode ? 0xFF273449 : 0xFFE2E8F0; }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams matchWrapMargins(int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = matchWrap();
        p.setMargins(l, t, r, b);
        return p;
    }

    private LinearLayout.LayoutParams weighted(float weight, int leftMargin) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight);
        p.setMargins(leftMargin, 0, 0, 0);
        return p;
    }

    private LinearLayout.LayoutParams weightedHeight(float weight, int height, int leftMargin) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, height, weight);
        p.setMargins(leftMargin, 0, 0, 0);
        return p;
    }

    private String initialOf(String name) {
        if (name == null || name.trim().isEmpty()) return "م";
        return name.trim().substring(0, 1);
    }

    private String generatePassword() {
        return "M" + UUID.randomUUID().toString().replace("-", "").substring(0, 9) + "1";
    }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }
    private String enc(String value) { return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8); }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }
}
