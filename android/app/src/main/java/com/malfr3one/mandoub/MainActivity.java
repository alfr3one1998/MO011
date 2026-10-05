package com.malfr3one.mandoub;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
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
    private JSONObject currentDriver;
    private JSONArray drivers = new JSONArray();
    private JSONArray orders = new JSONArray();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("mandoub_session", MODE_PRIVATE);
        getWindow().setStatusBarColor(0xFFFFFFFF);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

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

    private void showLogin() {
        LinearLayout root = pageRoot();
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(24), dp(44), dp(24), dp(24));

        TextView logo = title("مندوب", 34);
        root.addView(logo);
        TextView sub = text("إدارة التوصيل بحسابك الخاص", 16);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dp(6), 0, dp(28));
        root.addView(sub);

        EditText emailInput = field("البريد الإلكتروني");
        emailInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        root.addView(emailInput);

        EditText passwordInput = field("كلمة المرور");
        passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        root.addView(passwordInput);

        EditText setupCode = field("كود المدير لأول مرة - اختياري");
        setupCode.setVisibility(View.GONE);
        root.addView(setupCode);

        CheckBox adminSetup = new CheckBox(this);
        adminSetup.setText("تفعيل هذا الحساب كمدير لأول مرة");
        adminSetup.setTextSize(15);
        adminSetup.setPadding(0, dp(8), 0, dp(8));
        adminSetup.setOnCheckedChangeListener((buttonView, checked) -> setupCode.setVisibility(checked ? View.VISIBLE : View.GONE));
        root.addView(adminSetup, matchWrap());

        Button login = primaryButton("تسجيل الدخول");
        login.setOnClickListener(v -> authenticate(emailInput, passwordInput, setupCode, adminSetup.isChecked(), false));
        root.addView(login, matchWrapMargins(0, dp(14), 0, 0));

        Button signup = secondaryButton("إنشاء حساب");
        signup.setOnClickListener(v -> authenticate(emailInput, passwordInput, setupCode, adminSetup.isChecked(), true));
        root.addView(signup, matchWrapMargins(0, dp(10), 0, 0));

        TextView note = text("لا يوجد أي تسجيل دخول تابع لـ ChatGPT. الحسابات والمصادقة من مشروع Supabase الخاص بك فقط.", 13);
        note.setGravity(Gravity.CENTER);
        note.setPadding(dp(8), dp(24), dp(8), 0);
        root.addView(note);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
    }

    private void authenticate(EditText emailInput, EditText passwordInput, EditText setupCode, boolean claimAdmin, boolean signup) {
        String mail = emailInput.getText().toString().trim();
        String pass = passwordInput.getText().toString();
        String code = setupCode.getText().toString().trim();
        if (mail.isEmpty() || pass.length() < 6) {
            toast("اكتب البريد الإلكتروني وكلمة مرور 6 أحرف على الأقل");
            return;
        }
        if (claimAdmin && code.isEmpty()) {
            toast("اكتب كود تفعيل المدير");
            return;
        }

        showLoading(signup ? "جاري إنشاء الحساب..." : "جاري تسجيل الدخول...");
        io.execute(() -> {
            try {
                SupabaseApi.AuthResult result = signup ? api.signUp(mail, pass) : api.signIn(mail, pass);
                if (result.needsConfirmation || result.accessToken == null || result.accessToken.isEmpty()) {
                    runOnUiThread(() -> {
                        showLogin();
                        new AlertDialog.Builder(this)
                                .setTitle("تأكيد البريد")
                                .setMessage("تم إنشاء الحساب. افتح رسالة التأكيد المرسلة من Supabase، ثم ارجع وسجل الدخول.")
                                .setPositiveButton("حسناً", null)
                                .show();
                    });
                    return;
                }
                saveSession(result);
                if (claimAdmin) api.claimAdmin(accessToken, code);
                loadIdentityAndDashboard();
            } catch (Exception e) {
                clearSession();
                runOnUiThread(() -> {
                    showLogin();
                    toast(friendlyError(e));
                });
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
        drivers = api.getRows(accessToken, "drivers?select=id,name,email,phone&order=name.asc");
        orders = api.getRows(accessToken, "orders?select=id,customer,phone,address,district,amount,fee,payment,driver_id,status,notes,created,delivered,settled,maps_url&order=created.desc");
        runOnUiThread(this::showDashboard);
    }

    private void showDashboard() {
        LinearLayout root = pageRoot();
        root.setPadding(dp(16), dp(24), dp(16), dp(32));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView heading = title(isAdmin ? "لوحة المدير" : "طلبات المندوب", 27);
        header.addView(heading, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button logout = smallButton("خروج");
        logout.setOnClickListener(v -> {
            clearSession();
            showLogin();
        });
        header.addView(logout);
        root.addView(header, matchWrap());

        TextView account = text((isAdmin ? "مدير • " : "مندوب • ") + email, 14);
        account.setPadding(0, dp(4), 0, dp(14));
        root.addView(account);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        if (isAdmin) {
            Button addDriver = smallButton("+ مندوب");
            addDriver.setOnClickListener(v -> showAddDriverDialog());
            actions.addView(addDriver, new LinearLayout.LayoutParams(0, dp(48), 1));
            Button addOrder = smallButton("+ طلب");
            addOrder.setOnClickListener(v -> showAddOrderDialog());
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(48), 1);
            p.setMargins(dp(8), 0, 0, 0);
            actions.addView(addOrder, p);
        }
        Button refresh = smallButton("تحديث");
        refresh.setOnClickListener(v -> reloadDashboard());
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(isAdmin ? 0 : ViewGroup.LayoutParams.MATCH_PARENT, dp(48), isAdmin ? 1 : 0);
        if (isAdmin) rp.setMargins(dp(8), 0, 0, 0);
        actions.addView(refresh, rp);
        root.addView(actions, matchWrapMargins(0, 0, 0, dp(18)));

        if (isAdmin) {
            TextView stats = text("المناديب: " + drivers.length() + "    الطلبات: " + orders.length(), 15);
            stats.setTypeface(Typeface.DEFAULT_BOLD);
            stats.setPadding(0, 0, 0, dp(12));
            root.addView(stats);
        }

        if (orders.length() == 0) {
            TextView empty = text("لا توجد طلبات حالياً", 17);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(60), 0, 0);
            root.addView(empty);
        } else {
            for (int i = 0; i < orders.length(); i++) {
                JSONObject order = orders.optJSONObject(i);
                if (order != null) root.addView(orderCard(order), matchWrapMargins(0, 0, 0, dp(12)));
            }
        }

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
    }

    private View orderCard(JSONObject order) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackgroundResource(android.R.drawable.dialog_holo_light_frame);

        String customer = order.optString("customer", "-");
        TextView name = title(customer, 19);
        card.addView(name);
        card.addView(text("الحالة: " + statusArabic(order.optString("status")), 15));
        card.addView(text("العنوان: " + order.optString("address") + " - " + order.optString("district"), 14));
        card.addView(text("المبلغ: " + order.optInt("amount") + " ر.س   التوصيل: " + order.optInt("fee") + " ر.س", 14));
        card.addView(text("الدفع: " + ("cash".equals(order.optString("payment")) ? "كاش" : "مدفوع"), 14));
        if (isAdmin) card.addView(text("المندوب: " + driverName(order.optString("driver_id")), 14));
        String notes = order.optString("notes", "");
        if (!notes.isEmpty()) card.addView(text("ملاحظات: " + notes, 14));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setPadding(0, dp(10), 0, 0);

        Button call = smallButton("اتصال");
        call.setOnClickListener(v -> openDial(order.optString("phone")));
        buttons.addView(call, new LinearLayout.LayoutParams(0, dp(44), 1));

        Button maps = smallButton("الخريطة");
        maps.setOnClickListener(v -> openMaps(order));
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(0, dp(44), 1);
        mp.setMargins(dp(6), 0, 0, 0);
        buttons.addView(maps, mp);

        if (isAdmin) {
            Button assign = smallButton(order.optString("driver_id", "").isEmpty() ? "تعيين" : "تغيير");
            assign.setOnClickListener(v -> showAssignDialog(order.optString("id")));
            LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(0, dp(44), 1);
            ap.setMargins(dp(6), 0, 0, 0);
            buttons.addView(assign, ap);
        } else if (!"delivered".equals(order.optString("status"))) {
            Button next = smallButton(nextStatusLabel(order.optString("status")));
            next.setOnClickListener(v -> advanceStatus(order));
            LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(0, dp(44), 1);
            np.setMargins(dp(6), 0, 0, 0);
            buttons.addView(next, np);
        }

        card.addView(buttons, matchWrap());
        return card;
    }

    private void showAddDriverDialog() {
        LinearLayout form = dialogForm();
        EditText name = field("اسم المندوب");
        EditText mail = field("البريد الإلكتروني");
        mail.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        EditText phone = field("رقم الجوال");
        phone.setInputType(InputType.TYPE_CLASS_PHONE);
        form.addView(name); form.addView(mail); form.addView(phone);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("إضافة مندوب")
                .setView(form)
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("إضافة", null)
                .create();
        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String n = name.getText().toString().trim();
            String e = mail.getText().toString().trim().toLowerCase();
            String p = phone.getText().toString().trim();
            if (n.isEmpty() || e.isEmpty() || p.isEmpty()) { toast("أكمل بيانات المندوب"); return; }
            dialog.dismiss();
            runBusy("جاري إضافة المندوب...", () -> {
                JSONObject body = new JSONObject();
                body.put("name", n); body.put("email", e); body.put("phone", p);
                api.insertRow(accessToken, "drivers", body);
                refreshData();
            });
        }));
        dialog.show();
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
            catch (Exception e) { runOnUiThread(() -> { showDashboard(); toast(friendlyError(e)); }); }
        });
    }

    private interface Work { void run() throws Exception; }
    private void runBusy(String message, Work work) {
        showLoading(message);
        io.execute(() -> {
            try { work.run(); }
            catch (Exception e) { runOnUiThread(() -> { showDashboard(); toast(friendlyError(e)); }); }
        });
    }

    private void showLoading(String message) {
        LinearLayout root = pageRoot();
        root.setGravity(Gravity.CENTER);
        ProgressBar bar = new ProgressBar(this);
        root.addView(bar, new LinearLayout.LayoutParams(dp(54), dp(54)));
        TextView label = text(message, 16);
        label.setPadding(0, dp(16), 0, 0);
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
        prefs.edit().clear().apply();
    }

    private void openDial(String phone) {
        try { startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone))); }
        catch (Exception e) { toast("تعذر فتح الاتصال"); }
    }

    private void openMaps(JSONObject order) {
        String url = order.optString("maps_url", "").trim();
        Uri uri;
        if (!url.isEmpty()) uri = Uri.parse(url);
        else uri = Uri.parse("geo:0,0?q=" + Uri.encode(order.optString("address") + " " + order.optString("district")));
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
        if (lower.contains("email not confirmed")) return "أكد بريدك الإلكتروني أولاً";
        if (lower.contains("admin_already_claimed")) return "تم تفعيل حساب مدير سابقاً";
        if (lower.contains("invalid_setup_code")) return "كود المدير غير صحيح";
        if (lower.contains("هذا الحساب غير مضاف")) return message;
        if (lower.contains("network") || lower.contains("unable to resolve host")) return "تحقق من اتصال الإنترنت";
        return message.length() > 140 ? "حدث خطأ أثناء الاتصال بقاعدة البيانات" : message;
    }

    private LinearLayout pageRoot() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return root;
    }

    private LinearLayout dialogForm() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(8), dp(20), dp(4));
        return form;
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
        view.setTextColor(0xFF151515);
        view.setGravity(Gravity.RIGHT);
        return view;
    }

    private TextView label(String value) {
        TextView view = text(value, 13);
        view.setPadding(0, dp(10), 0, dp(4));
        return view;
    }

    private EditText field(String hint) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setTextSize(16);
        input.setSingleLine(true);
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        input.setLayoutParams(matchWrapMargins(0, dp(6), 0, dp(6)));
        return input;
    }

    private Button primaryButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(16);
        b.setAllCaps(false);
        return b;
    }

    private Button secondaryButton(String text) { return primaryButton(text); }
    private Button smallButton(String text) { Button b = primaryButton(text); b.setTextSize(14); return b; }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams matchWrapMargins(int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = matchWrap();
        p.setMargins(l, t, r, b);
        return p;
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