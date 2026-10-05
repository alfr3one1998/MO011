package com.malfr3one.mandoub;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class V4Activity extends Activity {
    private final SupabaseApi api = new SupabaseApi();
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;

    private String accessToken = "";
    private String refreshToken = "";
    private String userId = "";
    private String email = "";
    private boolean isAdmin = false;
    private boolean darkMode = false;
    private boolean loading = false;
    private JSONObject currentDriver;
    private JSONObject appSettings = new JSONObject();

    private JSONArray drivers = new JSONArray();
    private JSONArray orders = new JSONArray();
    private JSONArray tasks = new JSONArray();
    private JSONArray ledger = new JSONArray();
    private JSONArray shifts = new JSONArray();
    private JSONArray ratings = new JSONArray();
    private JSONArray branches = new JSONArray();
    private JSONArray customers = new JSONArray();
    private JSONArray zones = new JSONArray();
    private JSONArray vehicles = new JSONArray();
    private JSONArray expenses = new JSONArray();
    private JSONArray complaints = new JSONArray();
    private JSONArray locations = new JSONArray();
    private JSONArray documents = new JSONArray();

    private String workspaceName = "تاج الملكة";
    private int currentTab = 0; // home, orders, operations, finance, settings
    private String orderFilter = "active";
    private String pendingProofOrderId = "";
    private JSONObject pendingProofBody;

    private static final int PURPLE = 0xFF5B21B6;
    private static final int PURPLE_DARK = 0xFF3B0764;
    private static final int PURPLE_LIGHT = 0xFFF3E8FF;
    private static final int GOLD = 0xFFF4B740;
    private static final int GREEN = 0xFF16A34A;
    private static final int BLUE = 0xFF2563EB;
    private static final int RED = 0xFFDC2626;
    private static final int ORANGE = 0xFFF97316;
    private static final int SLATE = 0xFF64748B;
    private static final int REQ_LOCATION = 7001;
    private static final int REQ_CAMERA = 7002;
    private static final String NOTIFY_CHANNEL = "mandoub_orders";
    private static final String TRACK_BASE = SupabaseApi.BASE_URL + "/functions/v1/track-order?token=";

    private final Runnable autoRefresh = new Runnable() {
        @Override public void run() {
            if (!accessToken.isEmpty() && !loading) refreshSilently();
            int seconds = Math.max(15, appSettings.optInt("refresh_seconds", 30));
            handler.postDelayed(this, seconds * 1000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("mandoub_v4", MODE_PRIVATE);
        darkMode = prefs.getBoolean("dark_mode", false);
        createNotificationChannel();
        applySystemBars();

        String savedRefresh = prefs.getString("refresh_token", "");
        if (!savedRefresh.isEmpty()) {
            showLoading("جاري فتح لوحة التشغيل...");
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
        } else showLogin();
    }

    @Override protected void onResume() {
        super.onResume();
        handler.removeCallbacks(autoRefresh);
        handler.postDelayed(autoRefresh, 30000);
    }

    @Override protected void onPause() {
        handler.removeCallbacks(autoRefresh);
        super.onPause();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel c = new NotificationChannel(NOTIFY_CHANNEL, "طلبات التوصيل", NotificationManager.IMPORTANCE_HIGH);
            c.setDescription("تنبيهات الطلبات الجديدة والعاجلة");
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(c);
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 7003);
        }
    }

    private void applySystemBars() {
        getWindow().setStatusBarColor(darkMode ? 0xFF180B2B : PURPLE_DARK);
        getWindow().setNavigationBarColor(darkMode ? 0xFF0E0915 : Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(darkMode ? 0 : View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    }

    private void showLogin() {
        loading = false;
        LinearLayout root = pageRoot();
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(22), dp(44), dp(22), dp(28));

        TextView crown = text("♛", 36);
        crown.setTextColor(GOLD);
        crown.setGravity(Gravity.CENTER);
        crown.setBackground(gradient(new int[]{PURPLE_DARK, PURPLE}, 30));
        root.addView(crown, new LinearLayout.LayoutParams(dp(82), dp(82)));

        TextView logo = title("تاج الملكة", 30);
        logo.setGravity(Gravity.CENTER);
        logo.setPadding(0, dp(14), 0, 0);
        root.addView(logo, matchWrap());
        TextView sub = text("خدمات التوصيل • إدارة ذكية للطلبات والمناديب", 14);
        sub.setTextColor(mutedColor()); sub.setGravity(Gravity.CENTER); sub.setPadding(0, dp(5), 0, dp(22));
        root.addView(sub, matchWrap());

        LinearLayout card = cardBox();
        card.setPadding(dp(18), dp(20), dp(18), dp(18));
        card.addView(title("تسجيل الدخول", 21));
        TextView h = text("الحساب مرتبط بقاعدة Supabase الخاصة بك فقط", 13); h.setTextColor(mutedColor()); h.setPadding(0, dp(3), 0, dp(10)); card.addView(h);
        EditText mail = field("البريد الإلكتروني"); mail.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS); card.addView(mail);
        EditText pass = field("كلمة المرور"); pass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD); card.addView(pass);
        Button login = primaryButton("تسجيل الدخول"); login.setOnClickListener(v -> signIn(mail, pass)); card.addView(login, matchWrapMargins(0, dp(10), 0, 0));
        root.addView(card, matchWrap());

        Button first = linkButton("تهيئة أول حساب مدير"); first.setOnClickListener(v -> showFirstAdminDialog()); root.addView(first, matchWrapMargins(0, dp(12), 0, 0));
        TextView note = text("المدير ينشئ حسابات المناديب مباشرة بدون رسائل تأكيد بريد، لذلك لا تعتمد إضافة المناديب على حد إرسال الإيميلات.", 12);
        note.setTextColor(mutedColor()); note.setGravity(Gravity.CENTER); note.setPadding(dp(8), dp(12), dp(8), 0); root.addView(note, matchWrap());

        ScrollView s = new ScrollView(this); s.setFillViewport(true); s.setBackgroundColor(bgColor()); s.addView(root); setContentView(s);
    }

    private void showFirstAdminDialog() {
        LinearLayout f = dialogForm();
        EditText mail = field("بريد المدير");
        EditText pass = field("كلمة المرور - 6 أحرف على الأقل"); pass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText code = field("كود تفعيل المدير");
        f.addView(mail); f.addView(pass); f.addView(code);
        AlertDialog d = new AlertDialog.Builder(this).setTitle("تهيئة أول مدير").setView(f).setNegativeButton("إلغاء", null).setPositiveButton("إنشاء", null).create();
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String m=mail.getText().toString().trim(), p=pass.getText().toString(), c=code.getText().toString().trim();
            if(m.isEmpty()||p.length()<6||c.isEmpty()){toast("أكمل البيانات");return;}
            d.dismiss(); showLoading("جاري إنشاء المدير...");
            io.execute(() -> {
                try {
                    SupabaseApi.AuthResult r=api.signUp(m,p);
                    if(r.accessToken==null||r.accessToken.isEmpty()) throw new Exception("أكد البريد أولاً ثم سجل الدخول");
                    saveSession(r); api.claimAdmin(accessToken,c); loadIdentityAndDashboard();
                } catch(Exception e){clearSession();runOnUiThread(()->{showLogin();toast(friendlyError(e));});}
            });
        })); d.show();
    }

    private void signIn(EditText mail, EditText pass) {
        String m=mail.getText().toString().trim(), p=pass.getText().toString();
        if(m.isEmpty()||p.length()<6){toast("اكتب البريد وكلمة المرور");return;}
        showLoading("جاري تسجيل الدخول...");
        io.execute(() -> {
            try { saveSession(api.signIn(m,p)); loadIdentityAndDashboard(); }
            catch(Exception e){clearSession();runOnUiThread(()->{showLogin();toast(friendlyError(e));});}
        });
    }

    private void loadIdentityAndDashboard() throws Exception {
        JSONArray membership = api.getRows(accessToken, "app_members?select=role&user_id=eq." + enc(userId));
        isAdmin = membership.length() > 0 && "admin".equals(membership.getJSONObject(0).optString("role"));
        currentDriver = null;
        if (!isAdmin) {
            JSONArray mine = api.getRows(accessToken, "drivers?select=*&email=eq." + enc(email));
            if (mine.length()==0) throw new Exception("هذا الحساب غير مضاف كمندوب");
            currentDriver=mine.getJSONObject(0);
        }
        refreshData();
    }

    private JSONArray safeGet(String path) {
        try { return api.getRows(accessToken, path); } catch(Exception e){ return new JSONArray(); }
    }

    private void refreshData() throws Exception {
        JSONArray ws=safeGet("workspace?select=name&id=eq.1"); if(ws.length()>0) workspaceName=ws.optJSONObject(0).optString("name",workspaceName);
        JSONArray settings=safeGet("app_settings?select=*&id=eq.1"); if(settings.length()>0) appSettings=settings.optJSONObject(0);
        drivers=safeGet("drivers?select=*&order=name.asc");
        orders=safeGet("orders?select=*&order=created.desc&limit=500");
        tasks=safeGet("tasks?select=*&order=created_at.desc&limit=200");
        ledger=safeGet("cash_ledger?select=*&order=created_at.desc&limit=1000");
        shifts=safeGet("driver_shifts?select=*&order=started_at.desc&limit=200");
        ratings=safeGet("ratings?select=*&order=created_at.desc&limit=200");
        branches=safeGet("branches?select=*&active=eq.true&order=name.asc");
        zones=safeGet("service_zones?select=*&active=eq.true&order=name.asc");
        if(isAdmin){
            customers=safeGet("customers?select=*&order=created_at.desc&limit=500");
            vehicles=safeGet("vehicles?select=*&order=created_at.desc&limit=200");
            expenses=safeGet("expenses?select=*&order=created_at.desc&limit=500");
            complaints=safeGet("complaints?select=*&order=created_at.desc&limit=200");
            locations=safeGet("driver_locations?select=*&order=captured_at.desc&limit=200");
            documents=safeGet("driver_documents?select=*&order=created_at.desc&limit=300");
        }
        cacheData();
        notifyIfNeeded();
        loading=false;
        runOnUiThread(this::renderApp);
    }

    private void refreshSilently(){
        loading=true;
        io.execute(() -> {
            try { refreshData(); }
            catch(Exception e){ loading=false; runOnUiThread(() -> toast("تعذر التحديث الآن")); }
        });
    }

    private void cacheData(){
        prefs.edit().putString("orders_cache",orders.toString()).putString("drivers_cache",drivers.toString()).putString("tasks_cache",tasks.toString()).putString("ledger_cache",ledger.toString()).apply();
    }

    private void renderApp(){
        applySystemBars();
        LinearLayout screen=new LinearLayout(this); screen.setOrientation(LinearLayout.VERTICAL); screen.setBackgroundColor(bgColor());
        screen.addView(buildHeader(),matchWrap());
        ScrollView sc=new ScrollView(this); sc.setFillViewport(true);
        LinearLayout content=pageRoot(); content.setPadding(dp(14),dp(14),dp(14),dp(22));
        if(currentTab==0) buildHome(content);
        else if(currentTab==1) buildOrders(content);
        else if(currentTab==2) buildOperations(content);
        else if(currentTab==3) buildFinance(content);
        else buildSettings(content);
        sc.addView(content); screen.addView(sc,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));
        screen.addView(buildBottomNav(),matchWrap()); setContentView(screen);
    }

    private View buildHeader(){
        LinearLayout h=new LinearLayout(this); h.setOrientation(LinearLayout.HORIZONTAL); h.setGravity(Gravity.CENTER_VERTICAL); h.setPadding(dp(17),dp(14),dp(17),dp(14)); h.setBackground(gradient(new int[]{PURPLE_DARK,PURPLE},0));
        TextView mark=text("♛",26); mark.setTextColor(GOLD); mark.setGravity(Gravity.CENTER); h.addView(mark,new LinearLayout.LayoutParams(dp(42),dp(42)));
        LinearLayout info=new LinearLayout(this); info.setOrientation(LinearLayout.VERTICAL); info.setPadding(dp(10),0,0,0);
        TextView name=title(workspaceName,20); name.setTextColor(Color.WHITE); info.addView(name);
        String who=isAdmin?"مدير النظام":currentDriver==null?"مندوب":currentDriver.optString("name","مندوب");
        TextView role=text(who+" • "+email,11); role.setTextColor(0xFFE9D5FF); info.addView(role); h.addView(info,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        Button refresh=iconButton("↻"); refresh.setOnClickListener(v->reloadDashboard()); h.addView(refresh,new LinearLayout.LayoutParams(dp(44),dp(44)));
        return h;
    }

    private void buildHome(LinearLayout root){
        TextView hello=title(isAdmin?"صباح التشغيل 👋":"رحلة موفقة اليوم 👋",24); root.addView(hello);
        TextView sub=text(isAdmin?pressureText():"تابع طلباتك وعهدتك ومهامك من مكان واحد",13); sub.setTextColor(mutedColor()); sub.setPadding(0,dp(4),0,dp(14)); root.addView(sub);
        if(isAdmin) buildAdminDashboard(root); else buildDriverDashboard(root);
    }

    private void buildAdminDashboard(LinearLayout root){
        LinearLayout r1=new LinearLayout(this); r1.setOrientation(LinearLayout.HORIZONTAL);
        r1.addView(statCard("طلبات اليوم",String.valueOf(countToday()),PURPLE),weighted(1,0));
        r1.addView(statCard("نشطة",String.valueOf(countActive()),ORANGE),weighted(1,dp(8))); root.addView(r1,matchWrapMargins(0,0,0,dp(8)));
        LinearLayout r2=new LinearLayout(this); r2.setOrientation(LinearLayout.HORIZONTAL);
        r2.addView(statCard("تم التوصيل",String.valueOf(countStatus("delivered")),GREEN),weighted(1,0));
        r2.addView(statCard("متأخرة",String.valueOf(overdueCount()),RED),weighted(1,dp(8))); root.addView(r2,matchWrapMargins(0,0,0,dp(14)));

        LinearLayout quick=cardBox(); quick.setPadding(dp(15),dp(14),dp(15),dp(15)); quick.addView(title("إجراءات سريعة",18));
        LinearLayout a=new LinearLayout(this); a.setOrientation(LinearLayout.HORIZONTAL); a.setPadding(0,dp(10),0,0);
        Button newOrder=primaryButton("+ طلب"); newOrder.setOnClickListener(v->showAddOrderDialog()); a.addView(newOrder,weightedHeight(1,dp(46),0));
        Button newDriver=goldButton("+ مندوب"); newDriver.setOnClickListener(v->showAddDriverDialog()); a.addView(newDriver,weightedHeight(1,dp(46),dp(8)));
        Button dispatch=secondaryButton("توزيع ذكي"); dispatch.setOnClickListener(v->autoDispatchAll()); a.addView(dispatch,weightedHeight(1,dp(46),dp(8))); quick.addView(a);
        root.addView(quick,matchWrapMargins(0,0,0,dp(14)));

        root.addView(sectionTitle("تنبيهات التشغيل"));
        int shown=0;
        for(int i=0;i<orders.length()&&shown<5;i++){
            JSONObject o=orders.optJSONObject(i); if(o==null)continue;
            if("urgent".equals(o.optString("priority"))||isOverdue(o)){root.addView(alertOrderRow(o),matchWrapMargins(0,dp(8),0,0));shown++;}
        }
        if(shown==0) root.addView(emptyBox("لا توجد طلبات عاجلة أو متأخرة 🎉"),matchWrapMargins(0,dp(8),0,dp(14)));
        else root.addView(spacer(8));

        root.addView(sectionTitle("أداء اليوم"),matchWrapMargins(0,dp(10),0,dp(8)));
        LinearLayout perf=cardBox(); perf.setPadding(dp(15),dp(14),dp(15),dp(14));
        perf.addView(metricLine("إجمالي التحصيل المسجل", money(totalLedgerKind("collection"))));
        perf.addView(metricLine("العهدة الحالية", money(totalDriverBalance())));
        perf.addView(metricLine("المناديب", drivers.length()+" مسجل • "+activeDriverCount()+" نشط"));
        perf.addView(metricLine("الشكاوى المفتوحة", String.valueOf(openComplaints())));
        root.addView(perf);
    }

    private void buildDriverDashboard(LinearLayout root){
        JSONObject open=openShift();
        LinearLayout shift=cardBox(); shift.setPadding(dp(15),dp(14),dp(15),dp(14)); shift.addView(title("الوردية",18));
        TextView state=text(open==null?"أنت خارج الدوام":"الوردية بدأت • "+shortTime(open.optString("started_at")),13); state.setTextColor(open==null?mutedColor():GREEN); state.setPadding(0,dp(5),0,dp(10)); shift.addView(state);
        LinearLayout buttons=new LinearLayout(this); buttons.setOrientation(LinearLayout.HORIZONTAL);
        Button toggle=open==null?primaryButton("بدء الدوام"):dangerButton("إنهاء الدوام"); toggle.setOnClickListener(v->{if(open==null)startShift();else endShift(open.optString("id"));}); buttons.addView(toggle,weightedHeight(1,dp(46),0));
        Button loc=secondaryButton("إرسال موقعي"); loc.setOnClickListener(v->sendMyLocation()); buttons.addView(loc,weightedHeight(1,dp(46),dp(8)));
        Button route=goldButton("الجولة"); route.setOnClickListener(v->openMyRoute()); buttons.addView(route,weightedHeight(1,dp(46),dp(8))); shift.addView(buttons); root.addView(shift,matchWrapMargins(0,0,0,dp(14)));

        LinearLayout r=new LinearLayout(this); r.setOrientation(LinearLayout.HORIZONTAL);
        r.addView(statCard("طلبات نشطة",String.valueOf(countActive()),PURPLE),weighted(1,0));
        r.addView(statCard("العهدة",money(driverBalance(currentDriver.optString("id"))),ORANGE),weighted(1,dp(8))); root.addView(r,matchWrapMargins(0,0,0,dp(14)));
        root.addView(sectionTitle("طلباتك الآن"));
        int shown=0; for(int i=0;i<orders.length()&&shown<4;i++){JSONObject o=orders.optJSONObject(i);if(o!=null&&isActiveStatus(o.optString("status"))){root.addView(orderCard(o),matchWrapMargins(0,dp(8),0,0));shown++;}}
        if(shown==0)root.addView(emptyBox("لا توجد طلبات نشطة"),matchWrapMargins(0,dp(8),0,dp(12)));
        root.addView(sectionTitle("المهام"),matchWrapMargins(0,dp(12),0,dp(8))); buildTaskList(root,4);
    }

    private void buildOrders(LinearLayout root){
        LinearLayout head=new LinearLayout(this); head.setOrientation(LinearLayout.HORIZONTAL); head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(sectionTitle("الطلبات"),new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        if(isAdmin){Button add=primaryButton("+ طلب جديد");add.setOnClickListener(v->showAddOrderDialog());head.addView(add,new LinearLayout.LayoutParams(dp(118),dp(44)));} root.addView(head);
        root.addView(filterStrip(),matchWrapMargins(0,dp(10),0,dp(12)));
        int shown=0; for(int i=0;i<orders.length();i++){JSONObject o=orders.optJSONObject(i);if(o!=null&&matchesFilter(o)){root.addView(orderCard(o),matchWrapMargins(0,0,0,dp(10)));shown++;}}
        if(shown==0)root.addView(emptyBox("لا توجد طلبات بهذا التصنيف"));
    }

    private View filterStrip(){
        HorizontalScrollView sc=new HorizontalScrollView(this); sc.setHorizontalScrollBarEnabled(false);
        LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
        String[][] items={{"active","نشطة"},{"all","الكل"},{"new","جديدة"},{"urgent","عاجلة"},{"scheduled","مجدولة"},{"delivered","تمت"},{"returned","مرتجعة"},{"cancelled","ملغية"}};
        for(String[] item:items){Button b=chipButton(item[1],orderFilter.equals(item[0]));String key=item[0];b.setOnClickListener(v->{orderFilter=key;renderApp();});row.addView(b,new LinearLayout.LayoutParams(dp(92),dp(40)));}
        sc.addView(row);return sc;
    }

    private boolean matchesFilter(JSONObject o){
        String s=o.optString("status");
        if("all".equals(orderFilter))return true;
        if("active".equals(orderFilter))return isActiveStatus(s);
        if("urgent".equals(orderFilter))return "urgent".equals(o.optString("priority"))&&isActiveStatus(s);
        if("scheduled".equals(orderFilter))return !o.optString("scheduled_at").isEmpty()&&isActiveStatus(s);
        return orderFilter.equals(s);
    }

    private View orderCard(JSONObject o){
        LinearLayout card=cardBox(); card.setPadding(dp(14),dp(13),dp(14),dp(13));
        if("urgent".equals(o.optString("priority")))card.setBackground(round(surfaceColor(),20,0xFFF59E0B,2));
        LinearLayout top=new LinearLayout(this);top.setOrientation(LinearLayout.HORIZONTAL);top.setGravity(Gravity.CENTER_VERTICAL);
        TextView no=text("#TAJ-"+o.optLong("order_number"),12);no.setTextColor(PURPLE);no.setTypeface(Typeface.DEFAULT_BOLD);top.addView(no);
        TextView customer=title("  "+o.optString("customer","-"),17);top.addView(customer,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        top.addView(statusPill(o.optString("status")));card.addView(top);
        if(isOverdue(o)){TextView late=text("⚠ الطلب متأخر عن الوقت المستهدف",12);late.setTextColor(RED);late.setPadding(0,dp(6),0,0);card.addView(late);}
        TextView addr=text(o.optString("district")+" • "+o.optString("address"),13);addr.setTextColor(mutedColor());addr.setPadding(0,dp(7),0,0);card.addView(addr);
        String second=money(o.optInt("amount"))+" • توصيل "+money(o.optInt("fee"))+" • "+("cash".equals(o.optString("payment"))?"كاش":"مدفوع");
        if(isAdmin)second+=" • "+driverName(o.optString("driver_id"));TextView m=text(second,12);m.setTextColor(mutedColor());m.setPadding(0,dp(5),0,0);card.addView(m);
        if(!o.optString("scheduled_at").isEmpty()){TextView sch=text("موعد: "+prettyDate(o.optString("scheduled_at")),12);sch.setTextColor(BLUE);sch.setPadding(0,dp(4),0,0);card.addView(sch);}
        LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);actions.setPadding(0,dp(11),0,0);
        Button call=miniButton("اتصال");call.setOnClickListener(v->openDial(o.optString("phone")));actions.addView(call,weightedHeight(1,dp(40),0));
        Button wa=miniButton("واتساب");wa.setOnClickListener(v->openWhatsApp(o));actions.addView(wa,weightedHeight(1,dp(40),dp(5)));
        Button maps=miniButton("الخريطة");maps.setOnClickListener(v->openMaps(o));actions.addView(maps,weightedHeight(1,dp(40),dp(5)));
        Button more=miniButton("تفاصيل");more.setOnClickListener(v->showOrderDetails(o));actions.addView(more,weightedHeight(1,dp(40),dp(5)));card.addView(actions);
        LinearLayout row2=new LinearLayout(this);row2.setOrientation(LinearLayout.HORIZONTAL);row2.setPadding(0,dp(7),0,0);
        if(isAdmin){
            Button assign=secondaryButton(o.optString("driver_id").isEmpty()?"تعيين مندوب":"تغيير المندوب");assign.setOnClickListener(v->showAssignDialog(o));row2.addView(assign,weightedHeight(1,dp(42),0));
            Button smart=goldButton("توزيع ذكي");smart.setOnClickListener(v->autoAssignOrder(o));row2.addView(smart,weightedHeight(1,dp(42),dp(6)));
            Button share=primaryButton("رابط العميل");share.setOnClickListener(v->shareTracking(o));row2.addView(share,weightedHeight(1,dp(42),dp(6)));
        } else if(isActiveStatus(o.optString("status"))){
            Button next=primaryButton(nextStatusLabel(o.optString("status")));next.setOnClickListener(v->advanceStatus(o));row2.addView(next,weightedHeight(1,dp(42),0));
            Button fail=secondaryButton("تعذر التسليم");fail.setOnClickListener(v->showFailedDelivery(o));row2.addView(fail,weightedHeight(1,dp(42),dp(6)));
        }
        if(row2.getChildCount()>0)card.addView(row2);return card;
    }

    private View alertOrderRow(JSONObject o){
        LinearLayout row=cardBox();row.setPadding(dp(13),dp(11),dp(13),dp(11));row.setBackground(round(surfaceColor(),16,RED,1));
        TextView t=title("#TAJ-"+o.optLong("order_number")+" • "+o.optString("customer"),15);row.addView(t);
        TextView d=text(o.optString("district")+" • "+statusArabic(o.optString("status")),12);d.setTextColor(mutedColor());d.setPadding(0,dp(3),0,0);row.addView(d);
        row.setOnClickListener(v->showOrderDetails(o));return row;
    }

    private void buildOperations(LinearLayout root){
        if(!isAdmin){root.addView(sectionTitle("المهام والوردية"));buildDriverOperations(root);return;}
        LinearLayout head=new LinearLayout(this);head.setOrientation(LinearLayout.HORIZONTAL);head.setGravity(Gravity.CENTER_VERTICAL);head.addView(sectionTitle("مركز الإدارة"),new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        Button add=primaryButton("+ مندوب");add.setOnClickListener(v->showAddDriverDialog());head.addView(add,new LinearLayout.LayoutParams(dp(108),dp(44)));root.addView(head,matchWrapMargins(0,0,0,dp(12)));

        LinearLayout modules=cardBox();modules.setPadding(dp(12),dp(12),dp(12),dp(12));modules.addView(title("الإدارة السريعة",17));
        String[] labels={"العملاء","الفروع","المناطق والأسعار","السيارات","المهام","الشكاوى","الوثائق","مواقع المناديب"};
        for(int i=0;i<labels.length;i+=2){LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);r.setPadding(0,dp(7),0,0);for(int j=i;j<i+2&&j<labels.length;j++){Button b=secondaryButton(labels[j]);int k=j;b.setOnClickListener(v->openModule(k));r.addView(b,weightedHeight(1,dp(46),j==i?0:dp(7)));}modules.addView(r);}root.addView(modules,matchWrapMargins(0,0,0,dp(14)));

        root.addView(sectionTitle("المناديب"));
        for(int i=0;i<drivers.length();i++){JSONObject d=drivers.optJSONObject(i);if(d!=null)root.addView(driverCard(d),matchWrapMargins(0,dp(8),0,0));}
        if(drivers.length()==0)root.addView(emptyBox("لم تضف مناديب بعد"),matchWrapMargins(0,dp(8),0,0));
    }

    private void buildDriverOperations(LinearLayout root){
        JSONObject open=openShift();
        LinearLayout shift=cardBox();shift.setPadding(dp(14),dp(14),dp(14),dp(14));shift.addView(title("الوردية",17));
        shift.addView(text(open==null?"خارج الدوام":"بدأت "+prettyDate(open.optString("started_at")),13));
        Button b=open==null?primaryButton("بدء الوردية"):dangerButton("إنهاء الوردية");b.setOnClickListener(v->{if(open==null)startShift();else endShift(open.optString("id"));});shift.addView(b,matchWrapMargins(0,dp(9),0,0));root.addView(shift,matchWrapMargins(0,dp(10),0,dp(14)));
        root.addView(sectionTitle("مهامي"));buildTaskList(root,100);
    }

    private View driverCard(JSONObject d){
        LinearLayout card=cardBox();card.setPadding(dp(14),dp(13),dp(14),dp(13));
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);
        TextView avatar=text(initialOf(d.optString("name")),18);avatar.setGravity(Gravity.CENTER);avatar.setTypeface(Typeface.DEFAULT_BOLD);avatar.setTextColor(Color.WHITE);avatar.setBackground(gradient(new int[]{PURPLE,GOLD},24));row.addView(avatar,new LinearLayout.LayoutParams(dp(48),dp(48)));
        LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);info.setPadding(dp(11),0,0,0);info.addView(title(d.optString("name"),16));TextView ph=text(d.optString("phone"),12);ph.setTextColor(mutedColor());info.addView(ph);TextView st=text("الحالة: "+driverStatusAr(d.optString("status"))+" • عهدة "+money(driverBalance(d.optString("id"))),12);st.setTextColor(mutedColor());info.addView(st);row.addView(info,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        Button call=miniButton("اتصال");call.setOnClickListener(v->openDial(d.optString("phone")));row.addView(call,new LinearLayout.LayoutParams(dp(78),dp(40)));card.addView(row);
        LinearLayout acts=new LinearLayout(this);acts.setOrientation(LinearLayout.HORIZONTAL);acts.setPadding(0,dp(8),0,0);Button route=miniButton("جولته");route.setOnClickListener(v->openRouteForDriver(d.optString("id")));acts.addView(route,weightedHeight(1,dp(40),0));Button settle=miniButton("تصفية عهدة");settle.setOnClickListener(v->showSettlementDialog(d));acts.addView(settle,weightedHeight(1,dp(40),dp(6)));Button task=miniButton("مهمة");task.setOnClickListener(v->showAddTaskDialog(d.optString("id")));acts.addView(task,weightedHeight(1,dp(40),dp(6)));card.addView(acts);return card;
    }

    private void buildTaskList(LinearLayout root,int max){
        int shown=0;for(int i=0;i<tasks.length()&&shown<max;i++){JSONObject t=tasks.optJSONObject(i);if(t==null||"done".equals(t.optString("status"))||"cancelled".equals(t.optString("status")))continue;LinearLayout c=cardBox();c.setPadding(dp(13),dp(11),dp(13),dp(11));c.addView(title(t.optString("title"),15));TextView desc=text(t.optString("description"),12);desc.setTextColor(mutedColor());c.addView(desc);TextView meta=text(priorityAr(t.optString("priority"))+" • "+taskStatusAr(t.optString("status")),11);meta.setTextColor(PURPLE);meta.setPadding(0,dp(4),0,0);c.addView(meta);if(!isAdmin){Button next=primaryButton(taskNextLabel(t.optString("status")));next.setOnClickListener(v->advanceTask(t));c.addView(next,matchWrapMargins(0,dp(8),0,0));}root.addView(c,matchWrapMargins(0,dp(8),0,0));shown++;}if(shown==0)root.addView(emptyBox("لا توجد مهام مفتوحة"),matchWrapMargins(0,dp(8),0,0));
    }

    private void buildFinance(LinearLayout root){
        root.addView(sectionTitle("العهدة والمالية"));
        long collections=totalLedgerKind("collection"), settlements=totalLedgerKind("settlement"), exps=totalLedgerKind("expense");
        LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);r.setPadding(0,dp(10),0,0);r.addView(statCard("تحصيل",money(collections),GREEN),weighted(1,0));r.addView(statCard("تسويات",money(settlements),BLUE),weighted(1,dp(7)));root.addView(r,matchWrapMargins(0,0,0,dp(8)));
        LinearLayout r2=new LinearLayout(this);r2.setOrientation(LinearLayout.HORIZONTAL);r2.addView(statCard("مصروفات",money(exps),RED),weighted(1,0));r2.addView(statCard("العهدة",money(isAdmin?totalDriverBalance():driverBalance(currentDriver.optString("id"))),ORANGE),weighted(1,dp(7)));root.addView(r2,matchWrapMargins(0,0,0,dp(14)));
        if(isAdmin){LinearLayout q=new LinearLayout(this);q.setOrientation(LinearLayout.HORIZONTAL);Button expense=primaryButton("+ مصروف");expense.setOnClickListener(v->showExpenseDialog());q.addView(expense,weightedHeight(1,dp(46),0));Button export=secondaryButton("تصدير التقرير");export.setOnClickListener(v->shareReportCsv());q.addView(export,weightedHeight(1,dp(46),dp(8)));root.addView(q,matchWrapMargins(0,0,0,dp(14)));root.addView(sectionTitle("عهد المناديب"));for(int i=0;i<drivers.length();i++){JSONObject d=drivers.optJSONObject(i);if(d!=null)root.addView(financeDriverRow(d),matchWrapMargins(0,dp(8),0,0));}}
        else {root.addView(sectionTitle("آخر الحركات"),matchWrapMargins(0,dp(8),0,dp(5)));int n=0;for(int i=0;i<ledger.length()&&n<20;i++){JSONObject l=ledger.optJSONObject(i);if(l!=null){root.addView(ledgerRow(l),matchWrapMargins(0,dp(7),0,0));n++;}}}
    }

    private View financeDriverRow(JSONObject d){
        LinearLayout c=cardBox();c.setPadding(dp(13),dp(12),dp(13),dp(12));LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);r.setGravity(Gravity.CENTER_VERTICAL);TextView n=title(d.optString("name"),15);r.addView(n,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));TextView b=title(money(driverBalance(d.optString("id"))),16);b.setTextColor(driverBalance(d.optString("id"))>appSettings.optInt("max_driver_cash",1000)?RED:PURPLE);r.addView(b);c.addView(r);Button settle=miniButton("تسجيل تسوية");settle.setOnClickListener(v->showSettlementDialog(d));c.addView(settle,matchWrapMargins(0,dp(8),0,0));return c;
    }

    private View ledgerRow(JSONObject l){
        LinearLayout c=cardBox();c.setPadding(dp(12),dp(10),dp(12),dp(10));String k=ledgerKindAr(l.optString("kind"));TextView t=text(k+" • "+money(l.optInt("amount")),14);t.setTypeface(Typeface.DEFAULT_BOLD);c.addView(t);TextView d=text(l.optString("notes")+" • "+prettyDate(l.optString("created_at")),11);d.setTextColor(mutedColor());c.addView(d);return c;
    }

    private void buildSettings(LinearLayout root){
        root.addView(sectionTitle("الإعدادات"),matchWrapMargins(0,0,0,dp(10)));
        if(isAdmin){
            LinearLayout company=cardBox();company.setPadding(dp(14),dp(13),dp(14),dp(13));company.addView(title("المنشأة",17));EditText n=field("اسم المنشأة");n.setText(workspaceName);company.addView(n);Button save=primaryButton("حفظ الاسم");save.setOnClickListener(v->saveWorkspaceName(n.getText().toString().trim()));company.addView(save,matchWrapMargins(0,dp(7),0,0));root.addView(company,matchWrapMargins(0,0,0,dp(10)));
            root.addView(settingsOperationsCard(),matchWrapMargins(0,0,0,dp(10)));
        }
        LinearLayout app=cardBox();app.setPadding(dp(14),dp(13),dp(14),dp(13));app.addView(title("المظهر والتطبيق",17));CheckBox dark=new CheckBox(this);dark.setText("الوضع الداكن");dark.setTextColor(textColor());dark.setChecked(darkMode);dark.setOnCheckedChangeListener((x,c)->{if(c==darkMode)return;darkMode=c;prefs.edit().putBoolean("dark_mode",c).apply();renderApp();});app.addView(dark);Button password=secondaryButton("تغيير كلمة المرور");password.setOnClickListener(v->showChangePassword());app.addView(password,matchWrapMargins(0,dp(6),0,0));Button refresh=secondaryButton("تحديث البيانات");refresh.setOnClickListener(v->reloadDashboard());app.addView(refresh,matchWrapMargins(0,dp(6),0,0));Button logout=dangerButton("تسجيل الخروج");logout.setOnClickListener(v->{clearSession();showLogin();});app.addView(logout,matchWrapMargins(0,dp(6),0,0));root.addView(app,matchWrapMargins(0,0,0,dp(10)));
        TextView ver=text("Taj Delivery • v4.0.0 • Supabase",11);ver.setTextColor(mutedColor());ver.setGravity(Gravity.CENTER);ver.setPadding(0,dp(12),0,0);root.addView(ver);
    }

    private View settingsOperationsCard(){
        LinearLayout c=cardBox();c.setPadding(dp(14),dp(13),dp(14),dp(13));c.addView(title("تشغيل التوصيل",17));
        EditText sla=field("SLA الطلب بالدقائق");sla.setInputType(InputType.TYPE_CLASS_NUMBER);sla.setText(String.valueOf(appSettings.optInt("order_sla_minutes",45)));c.addView(sla);
        EditText urgent=field("SLA العاجل بالدقائق");urgent.setInputType(InputType.TYPE_CLASS_NUMBER);urgent.setText(String.valueOf(appSettings.optInt("urgent_sla_minutes",20)));c.addView(urgent);
        EditText maxCash=field("حد عهدة المندوب");maxCash.setInputType(InputType.TYPE_CLASS_NUMBER);maxCash.setText(String.valueOf(appSettings.optInt("max_driver_cash",1000)));c.addView(maxCash);
        EditText geo=field("نطاق إثبات التسليم بالمتر");geo.setInputType(InputType.TYPE_CLASS_NUMBER);geo.setText(String.valueOf(appSettings.optInt("geofence_meters",200)));c.addView(geo);
        CheckBox tracking=settingCheck("تتبع المندوب مفعّل",appSettings.optBoolean("tracking_enabled",true));c.addView(tracking);
        CheckBox proof=settingCheck("إثبات التسليم مطلوب",appSettings.optBoolean("proof_required",true));c.addView(proof);
        CheckBox otp=settingCheck("طلب OTP عند التسليم",appSettings.optBoolean("otp_required",false));c.addView(otp);
        CheckBox customerTrack=settingCheck("رابط تتبع العميل",appSettings.optBoolean("customer_tracking_enabled",true));c.addView(customerTrack);
        CheckBox notify=settingCheck("التنبيهات",appSettings.optBoolean("notifications_enabled",true));c.addView(notify);
        CheckBox auto=settingCheck("التوزيع التلقائي للطلبات",appSettings.optBoolean("auto_dispatch_enabled",false));c.addView(auto);
        Button save=primaryButton("حفظ إعدادات التشغيل");save.setOnClickListener(v->{try{JSONObject b=new JSONObject();b.put("order_sla_minutes",Integer.parseInt(sla.getText().toString()));b.put("urgent_sla_minutes",Integer.parseInt(urgent.getText().toString()));b.put("max_driver_cash",Integer.parseInt(maxCash.getText().toString()));b.put("geofence_meters",Integer.parseInt(geo.getText().toString()));b.put("tracking_enabled",tracking.isChecked());b.put("proof_required",proof.isChecked());b.put("otp_required",otp.isChecked());b.put("customer_tracking_enabled",customerTrack.isChecked());b.put("notifications_enabled",notify.isChecked());b.put("auto_dispatch_enabled",auto.isChecked());runBusy("جاري حفظ الإعدادات...",()->{api.updateRows(accessToken,"app_settings?id=eq.1",b);refreshData();});}catch(Exception e){toast("راجع الأرقام");}});c.addView(save,matchWrapMargins(0,dp(8),0,0));return c;
    }

    private View buildBottomNav(){
        LinearLayout nav=new LinearLayout(this);nav.setOrientation(LinearLayout.HORIZONTAL);nav.setPadding(dp(5),dp(6),dp(5),dp(8));nav.setBackgroundColor(surfaceColor());
        String[] admin={"الرئيسية","الطلبات","الإدارة","المالية","الإعدادات"};String[] driver={"الرئيسية","الطلبات","المهام","العهدة","الإعدادات"};String[] labs=isAdmin?admin:driver;
        for(int i=0;i<labs.length;i++){Button b=navButton(labs[i],currentTab==i);int k=i;b.setOnClickListener(v->{currentTab=k;renderApp();});nav.addView(b,weightedHeight(1,dp(50),i==0?0:dp(2)));}return nav;
    }

    private void showAddDriverDialog(){
        LinearLayout f=dialogForm();EditText name=field("اسم المندوب");EditText mail=field("البريد الإلكتروني");EditText phone=field("رقم الجوال");EditText pass=field("كلمة مرور مؤقتة");pass.setText(generatePassword());
        Spinner branch=spinner(labelsWithEmpty(branches,"بدون فرع","name"));EditText zone=field("منطقة المندوب - اختياري");EditText commission=field("عمولة الطلب - اختياري");commission.setInputType(InputType.TYPE_CLASS_NUMBER);
        f.addView(name);f.addView(mail);f.addView(phone);f.addView(pass);f.addView(label("الفرع"));f.addView(branch);f.addView(zone);f.addView(commission);
        TextView note=text("الحساب يُنشأ مباشرة ولا يتم إرسال رسالة تأكيد بريد.",11);note.setTextColor(mutedColor());f.addView(note);
        showFormDialog("إضافة مندوب",f,()->{String n=name.getText().toString().trim(),e=mail.getText().toString().trim().toLowerCase(),p=phone.getText().toString().trim(),pw=pass.getText().toString();if(n.isEmpty()||e.isEmpty()||p.isEmpty()||pw.length()<6)throw new Exception("أكمل بيانات المندوب");runBusy("جاري إنشاء حساب المندوب...",()->{api.createDriverAccount(accessToken,n,e,p,pw);JSONArray found=api.getRows(accessToken,"drivers?select=id&email=eq."+enc(e));if(found.length()>0){JSONObject up=new JSONObject();if(branch.getSelectedItemPosition()>0)up.put("branch_id",branches.optJSONObject(branch.getSelectedItemPosition()-1).optString("id"));up.put("zone",zone.getText().toString().trim());up.put("commission_value",parseInt(commission.getText().toString(),0));api.updateRows(accessToken,"drivers?id=eq."+enc(found.optJSONObject(0).optString("id")),up);}refreshData();runOnUiThread(()->showCredentials(e,pw));});});
    }

    private void showCredentials(String e,String p){String value="بيانات دخول مندوب\nالبريد: "+e+"\nكلمة المرور: "+p;new AlertDialog.Builder(this).setTitle("تم إنشاء الحساب").setMessage(value).setNegativeButton("إغلاق",null).setPositiveButton("نسخ",(d,w)->{ClipboardManager cb=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);cb.setPrimaryClip(ClipData.newPlainText("driver",value));toast("تم النسخ");}).show();}

    private void showAddOrderDialog(){
        LinearLayout f=dialogForm();EditText customer=field("اسم العميل");EditText phone=field("رقم العميل");EditText address=field("العنوان");EditText district=field("الحي");EditText maps=field("رابط Google Maps");EditText amount=field("قيمة الطلب");amount.setInputType(InputType.TYPE_CLASS_NUMBER);EditText fee=field("رسوم التوصيل - اتركها فارغة لاستخدام سعر المنطقة");fee.setInputType(InputType.TYPE_CLASS_NUMBER);EditText schedule=field("موعد اختياري: 2026-10-05 20:30");EditText items=field("عدد القطع");items.setInputType(InputType.TYPE_CLASS_NUMBER);items.setText("1");EditText notes=field("ملاحظات");
        Spinner payment=spinner(new String[]{"كاش عند الاستلام","مدفوع"});Spinner priority=spinner(new String[]{"عادي","منخفض","مهم","عاجل"});Spinner type=spinner(new String[]{"طلب عادي","كيك","ضيافة","زهور","طلب حساس"});Spinner zone=spinner(labelsWithEmpty(zones,"بدون منطقة","name"));Spinner branch=spinner(labelsWithEmpty(branches,"بدون فرع","name"));List<String> dl=new ArrayList<>();dl.add("توزيع ذكي");dl.add("بدون مندوب");for(int i=0;i<drivers.length();i++)dl.add(drivers.optJSONObject(i).optString("name"));Spinner driver=spinner(dl.toArray(new String[0]));CheckBox fragile=settingCheck("طلب حساس / يحتاج عناية",false);
        f.addView(customer);f.addView(phone);f.addView(address);f.addView(district);f.addView(maps);f.addView(amount);f.addView(fee);f.addView(label("طريقة الدفع"));f.addView(payment);f.addView(label("الأولوية"));f.addView(priority);f.addView(label("نوع الطلب"));f.addView(type);f.addView(label("منطقة التوصيل"));f.addView(zone);f.addView(label("الفرع"));f.addView(branch);f.addView(label("المندوب"));f.addView(driver);f.addView(schedule);f.addView(items);f.addView(fragile);f.addView(notes);
        showFormDialog("إضافة طلب جديد",f,()->{String c=customer.getText().toString().trim(),ph=phone.getText().toString().trim(),ad=address.getText().toString().trim(),di=district.getText().toString().trim();if(c.isEmpty()||ph.isEmpty()||ad.isEmpty()||di.isEmpty())throw new Exception("أكمل بيانات الطلب");int a=parseInt(amount.getText().toString(),0);int ff=parseInt(fee.getText().toString(),-1);if(ff<0&&zone.getSelectedItemPosition()>0)ff=zones.optJSONObject(zone.getSelectedItemPosition()-1).optInt("delivery_fee",0);if(ff<0)ff=0;final int finalFee=ff;runBusy("جاري إنشاء الطلب...",()->{JSONObject existing=findCustomerByPhone(ph);if(existing!=null&&existing.optBoolean("blocked"))throw new Exception("هذا العميل محظور");String customerId="";if(existing==null){JSONObject cb=new JSONObject();cb.put("name",c);cb.put("phone",ph);existing=api.insertRow(accessToken,"customers",cb);customerId=existing.optString("id");JSONObject ab=new JSONObject();ab.put("customer_id",customerId);ab.put("address",ad);ab.put("district",di);ab.put("maps_url",maps.getText().toString().trim());api.insertRow(accessToken,"customer_addresses",ab);}else customerId=existing.optString("id");JSONObject b=new JSONObject();b.put("customer",c);b.put("phone",ph);b.put("address",ad);b.put("district",di);b.put("maps_url",maps.getText().toString().trim());b.put("amount",a);b.put("fee",finalFee);b.put("payment",payment.getSelectedItemPosition()==0?"cash":"paid");b.put("status","new");b.put("notes",notes.getText().toString().trim());b.put("customer_id",customerId);b.put("priority",new String[]{"normal","low","high","urgent"}[priority.getSelectedItemPosition()]);b.put("order_type",new String[]{"normal","cake","catering","flowers","fragile"}[type.getSelectedItemPosition()]);b.put("fragile",fragile.isChecked());b.put("item_count",Math.max(1,parseInt(items.getText().toString(),1)));if(branch.getSelectedItemPosition()>0)b.put("branch_id",branches.optJSONObject(branch.getSelectedItemPosition()-1).optString("id"));String sch=schedule.getText().toString().trim();if(!sch.isEmpty())b.put("scheduled_at",parseSchedule(sch));if(driver.getSelectedItemPosition()>1)b.put("driver_id",drivers.optJSONObject(driver.getSelectedItemPosition()-2).optString("id"));else b.put("driver_id",JSONObject.NULL);JSONObject created=api.insertRow(accessToken,"orders",b);if(driver.getSelectedItemPosition()==0)autoAssignOrderSync(created);refreshData();});});
    }

    private void showAssignDialog(JSONObject order){
        if(drivers.length()==0){toast("أضف مندوب أولاً");return;}List<String> labels=new ArrayList<>();for(int i=0;i<drivers.length();i++)labels.add(drivers.optJSONObject(i).optString("name"));Spinner s=spinner(labels.toArray(new String[0]));new AlertDialog.Builder(this).setTitle("تعيين المندوب").setView(s).setNegativeButton("إلغاء",null).setPositiveButton("تعيين",(d,w)->{JSONObject dr=drivers.optJSONObject(s.getSelectedItemPosition());runBusy("جاري التعيين...",()->{JSONObject b=new JSONObject();b.put("driver_id",dr.optString("id"));api.updateRows(accessToken,"orders?id=eq."+enc(order.optString("id")),b);refreshData();});}).show();
    }

    private void autoAssignOrder(JSONObject order){runBusy("جاري اختيار أفضل مندوب...",()->{autoAssignOrderSync(order);refreshData();});}
    private void autoAssignOrderSync(JSONObject order) throws Exception {JSONObject best=bestDriver(order);if(best==null)throw new Exception("لا يوجد مندوب متاح");JSONObject b=new JSONObject();b.put("driver_id",best.optString("id"));api.updateRows(accessToken,"orders?id=eq."+enc(order.optString("id")),b);}
    private JSONObject bestDriver(JSONObject order){JSONObject best=null;int bestScore=Integer.MAX_VALUE;for(int i=0;i<drivers.length();i++){JSONObject d=drivers.optJSONObject(i);if(d==null)continue;int active=0;for(int j=0;j<orders.length();j++){JSONObject o=orders.optJSONObject(j);if(o!=null&&d.optString("id").equals(o.optString("driver_id"))&&isActiveStatus(o.optString("status")))active++;}int score=active*10;if("offline".equals(d.optString("status")))score+=5;String z=d.optString("zone");if(!z.isEmpty()&&!order.optString("district").contains(z))score+=3;if(score<bestScore){bestScore=score;best=d;}}return best;}
    private void autoDispatchAll(){runBusy("جاري توزيع الطلبات...",()->{int count=0;for(int i=0;i<orders.length();i++){JSONObject o=orders.optJSONObject(i);if(o!=null&&"new".equals(o.optString("status"))&&o.optString("driver_id").isEmpty()){try{autoAssignOrderSync(o);count++;}catch(Exception ignored){}}}final int c=count;refreshData();runOnUiThread(()->toast("تم توزيع "+c+" طلب"));});}

    private void showOrderDetails(JSONObject o){
        StringBuilder s=new StringBuilder();s.append("طلب #TAJ-").append(o.optLong("order_number")).append("\n\n").append("العميل: ").append(o.optString("customer")).append("\nالجوال: ").append(o.optString("phone")).append("\nالعنوان: ").append(o.optString("district")).append(" - ").append(o.optString("address")).append("\nالحالة: ").append(statusArabic(o.optString("status"))).append("\nالأولوية: ").append(priorityAr(o.optString("priority"))).append("\nالمبلغ: ").append(money(o.optInt("amount"))).append("\nرسوم التوصيل: ").append(money(o.optInt("fee"))).append("\nالمندوب: ").append(driverName(o.optString("driver_id"))).append("\nالملاحظات: ").append(o.optString("notes","-")).append("\nالمحاولات: ").append(o.optInt("attempts"));
        AlertDialog d=new AlertDialog.Builder(this).setTitle("تفاصيل الطلب").setMessage(s.toString()).setNegativeButton("إغلاق",null).setPositiveButton("سجل الحركة",(x,w)->showOrderHistory(o)).setNeutralButton(isAdmin?"إجراءات":"رسالة",(x,w)->{if(isAdmin)showOrderActions(o);else showMessageDialog(o);}).create();d.show();
    }

    private void showOrderActions(JSONObject o){String[] items={"توزيع ذكي","إلغاء الطلب","تسجيل مرتجع","نسخ الطلب","تعديل ETA","رسالة داخلية"};new AlertDialog.Builder(this).setTitle("إجراءات الطلب").setItems(items,(d,which)->{if(which==0)autoAssignOrder(o);else if(which==1)promptReason(o,"cancelled");else if(which==2)promptReason(o,"returned");else if(which==3)duplicateOrder(o);else if(which==4)promptEta(o);else showMessageDialog(o);}).show();}
    private void promptReason(JSONObject o,String status){EditText reason=field(status.equals("cancelled")?"سبب الإلغاء":"سبب المرتجع");new AlertDialog.Builder(this).setTitle(status.equals("cancelled")?"إلغاء الطلب":"تسجيل مرتجع").setView(reason).setNegativeButton("رجوع",null).setPositiveButton("حفظ",(d,w)->runBusy("جاري التحديث...",()->{JSONObject b=new JSONObject();b.put("status",status);if(status.equals("cancelled"))b.put("cancelled_reason",reason.getText().toString().trim());else b.put("return_reason",reason.getText().toString().trim());api.updateRows(accessToken,"orders?id=eq."+enc(o.optString("id")),b);refreshData();})).show();}
    private void duplicateOrder(JSONObject o){runBusy("جاري نسخ الطلب...",()->{JSONObject b=new JSONObject();String[] keys={"customer","phone","address","district","amount","fee","payment","driver_id","notes","maps_url","customer_id","branch_id","priority","item_count","order_type","fragile"};for(String k:keys){if(o.has(k)&&!o.isNull(k))b.put(k,o.get(k));}b.put("status","new");JSONObject.NULL.toString();api.insertRow(accessToken,"orders",b);refreshData();});}
    private void promptEta(JSONObject o){EditText e=field("ETA بالدقائق");e.setInputType(InputType.TYPE_CLASS_NUMBER);e.setText(String.valueOf(o.optInt("eta_minutes",20)));new AlertDialog.Builder(this).setTitle("وقت الوصول المتوقع").setView(e).setNegativeButton("إلغاء",null).setPositiveButton("حفظ",(d,w)->runBusy("جاري الحفظ...",()->{JSONObject b=new JSONObject();b.put("eta_minutes",parseInt(e.getText().toString(),20));api.updateRows(accessToken,"orders?id=eq."+enc(o.optString("id")),b);refreshData();})).show();}

    private void showOrderHistory(JSONObject o){showLoading("جاري تحميل سجل الطلب...");io.execute(()->{try{JSONArray ev=api.getRows(accessToken,"order_events?select=*&order_id=eq."+enc(o.optString("id"))+"&order=created_at.desc");JSONArray msg=api.getRows(accessToken,"order_messages?select=*&order_id=eq."+enc(o.optString("id"))+"&order=created_at.desc");StringBuilder s=new StringBuilder();for(int i=0;i<ev.length();i++){JSONObject e=ev.optJSONObject(i);s.append("• ").append(e.optString("note",e.optString("event_type"))).append("\n  ").append(prettyDate(e.optString("created_at"))).append("\n\n");}if(msg.length()>0){s.append("— الرسائل —\n");for(int i=0;i<msg.length();i++){JSONObject m=msg.optJSONObject(i);s.append(m.optString("sender_email")).append(": ").append(m.optString("message")).append("\n");}}String value=s.length()==0?"لا يوجد سجل حتى الآن":s.toString();runOnUiThread(()->{renderApp();new AlertDialog.Builder(this).setTitle("سجل الطلب #TAJ-"+o.optLong("order_number")).setMessage(value).setPositiveButton("إغلاق",null).show();});}catch(Exception e){runOnUiThread(()->{renderApp();toast(friendlyError(e));});}});}
    private void showMessageDialog(JSONObject o){EditText e=field("اكتب رسالة مرتبطة بالطلب");new AlertDialog.Builder(this).setTitle("رسالة داخلية").setView(e).setNegativeButton("إلغاء",null).setPositiveButton("إرسال",(d,w)->runBusy("جاري الإرسال...",()->{JSONObject b=new JSONObject();b.put("order_id",o.optString("id"));b.put("sender_email",email);b.put("message",e.getText().toString().trim());api.insertRow(accessToken,"order_messages",b);refreshData();})).show();}

    private void advanceStatus(JSONObject o){String s=o.optString("status");if("onway".equals(s)){showDeliveryProofDialog(o);return;}String next="new".equals(s)?"received":"onway";runBusy("جاري تحديث الطلب...",()->{JSONObject b=new JSONObject();b.put("status",next);if("onway".equals(next)&&o.optInt("eta_minutes",0)==0)b.put("eta_minutes",20);api.updateRows(accessToken,"orders?id=eq."+enc(o.optString("id")),b);refreshData();});}
    private void showDeliveryProofDialog(JSONObject o){LinearLayout f=dialogForm();EditText note=field("ملاحظة التسليم");EditText otp=field("OTP - إن كان مطلوباً");otp.setInputType(InputType.TYPE_CLASS_NUMBER);CheckBox photo=settingCheck("التقاط صورة إثبات",appSettings.optBoolean("proof_required",true));f.addView(note);if(appSettings.optBoolean("otp_required",false))f.addView(otp);f.addView(photo);new AlertDialog.Builder(this).setTitle("إثبات التسليم").setView(f).setNegativeButton("إلغاء",null).setPositiveButton("تأكيد",(d,w)->{try{JSONObject b=new JSONObject();b.put("status","delivered");b.put("proof_note",note.getText().toString().trim());if(appSettings.optBoolean("otp_required",false)){String code=otp.getText().toString().trim();if(code.length()<4){toast("اكتب OTP صحيح");return;}b.put("proof_otp",code);}if(photo.isChecked()){pendingProofOrderId=o.optString("id");pendingProofBody=b;Intent camera=new Intent(MediaStore.ACTION_IMAGE_CAPTURE);startActivityForResult(camera,REQ_CAMERA);}else completeDelivery(o.optString("id"),b);}catch(Exception e){toast(friendlyError(e));}}).show();}
    private void completeDelivery(String orderId,JSONObject body){runBusy("جاري تأكيد التسليم...",()->{api.updateRows(accessToken,"orders?id=eq."+enc(orderId),body);refreshData();});}
    private void showFailedDelivery(JSONObject o){EditText reason=field("السبب: العميل لا يرد / العنوان خطأ / رفض الطلب...");new AlertDialog.Builder(this).setTitle("تعذر التسليم").setView(reason).setNegativeButton("إلغاء",null).setPositiveButton("تسجيل مرتجع",(d,w)->runBusy("جاري التحديث...",()->{JSONObject b=new JSONObject();b.put("status","returned");b.put("return_reason",reason.getText().toString().trim());b.put("attempts",o.optInt("attempts")+1);api.updateRows(accessToken,"orders?id=eq."+enc(o.optString("id")),b);refreshData();})).show();}

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){super.onActivityResult(requestCode,resultCode,data);if(requestCode==REQ_CAMERA&&resultCode==RESULT_OK&&pendingProofBody!=null&&!pendingProofOrderId.isEmpty()){Bitmap bmp=data==null?null:(Bitmap)data.getExtras().get("data");if(bmp==null){toast("تعذر قراءة الصورة");return;}showLoading("جاري رفع صورة التسليم...");io.execute(()->{try{ByteArrayOutputStream out=new ByteArrayOutputStream();bmp.compress(Bitmap.CompressFormat.JPEG,85,out);String path=pendingProofOrderId+"/"+UUID.randomUUID()+".jpg";api.uploadBytes(accessToken,"delivery-proof",path,out.toByteArray(),"image/jpeg");pendingProofBody.put("proof_photo_url",path);api.updateRows(accessToken,"orders?id=eq."+enc(pendingProofOrderId),pendingProofBody);pendingProofBody=null;pendingProofOrderId="";refreshData();}catch(Exception e){runOnUiThread(()->{renderApp();toast(friendlyError(e));});}});}}

    private void openModule(int which){if(which==0)showCustomers();else if(which==1)showBranches();else if(which==2)showZones();else if(which==3)showVehicles();else if(which==4)showTasksAdmin();else if(which==5)showComplaints();else if(which==6)showDocuments();else showLatestLocations();}
    private void showCustomers(){StringBuilder s=new StringBuilder();for(int i=0;i<customers.length()&&i<50;i++){JSONObject c=customers.optJSONObject(i);s.append(c.optBoolean("vip")?"★ ":"").append(c.optString("name")).append(" • ").append(c.optString("phone")).append(c.optBoolean("blocked")?" • محظور":"").append("\n");}new AlertDialog.Builder(this).setTitle("العملاء ("+customers.length()+")").setMessage(s.length()==0?"لا يوجد عملاء":s.toString()).setNegativeButton("إغلاق",null).setPositiveButton("+ عميل",(d,w)->showAddCustomerDialog()).show();}
    private void showAddCustomerDialog(){LinearLayout f=dialogForm();EditText n=field("الاسم");EditText p=field("الجوال");EditText notes=field("ملاحظات");CheckBox vip=settingCheck("VIP",false);CheckBox blocked=settingCheck("حظر العميل",false);f.addView(n);f.addView(p);f.addView(notes);f.addView(vip);f.addView(blocked);showFormDialog("إضافة عميل",f,()->runBusy("جاري الحفظ...",()->{JSONObject b=new JSONObject();b.put("name",n.getText().toString().trim());b.put("phone",p.getText().toString().trim());b.put("notes",notes.getText().toString().trim());b.put("vip",vip.isChecked());b.put("blocked",blocked.isChecked());api.insertRow(accessToken,"customers",b);refreshData();}));}
    private void showBranches(){StringBuilder s=new StringBuilder();for(int i=0;i<branches.length();i++){JSONObject b=branches.optJSONObject(i);s.append("• ").append(b.optString("name")).append(" • ").append(b.optString("phone")).append("\n");}new AlertDialog.Builder(this).setTitle("الفروع").setMessage(s.length()==0?"لا توجد فروع":s.toString()).setNegativeButton("إغلاق",null).setPositiveButton("+ فرع",(d,w)->showAddBranchDialog()).show();}
    private void showAddBranchDialog(){LinearLayout f=dialogForm();EditText n=field("اسم الفرع");EditText p=field("الجوال");EditText a=field("العنوان");EditText m=field("رابط الخريطة");f.addView(n);f.addView(p);f.addView(a);f.addView(m);showFormDialog("إضافة فرع",f,()->runBusy("جاري الحفظ...",()->{JSONObject b=new JSONObject();b.put("name",n.getText().toString().trim());b.put("phone",p.getText().toString().trim());b.put("address",a.getText().toString().trim());b.put("maps_url",m.getText().toString().trim());api.insertRow(accessToken,"branches",b);refreshData();}));}
    private void showZones(){StringBuilder s=new StringBuilder();for(int i=0;i<zones.length();i++){JSONObject z=zones.optJSONObject(i);s.append("• ").append(z.optString("name")).append(" • ").append(z.optBoolean("free_delivery")?"مجاني":money(z.optInt("delivery_fee"))).append("\n");}new AlertDialog.Builder(this).setTitle("المناطق ورسوم التوصيل").setMessage(s.length()==0?"لا توجد مناطق":s.toString()).setNegativeButton("إغلاق",null).setPositiveButton("+ منطقة",(d,w)->showAddZoneDialog()).show();}
    private void showAddZoneDialog(){LinearLayout f=dialogForm();EditText n=field("اسم المنطقة / الحي");EditText fee=field("رسوم التوصيل");fee.setInputType(InputType.TYPE_CLASS_NUMBER);CheckBox free=settingCheck("توصيل مجاني",false);f.addView(n);f.addView(fee);f.addView(free);showFormDialog("إضافة منطقة",f,()->runBusy("جاري الحفظ...",()->{JSONObject b=new JSONObject();b.put("name",n.getText().toString().trim());b.put("delivery_fee",parseInt(fee.getText().toString(),0));b.put("free_delivery",free.isChecked());api.insertRow(accessToken,"service_zones",b);refreshData();}));}
    private void showVehicles(){StringBuilder s=new StringBuilder();for(int i=0;i<vehicles.length();i++){JSONObject v=vehicles.optJSONObject(i);s.append("• ").append(v.optString("plate")).append(" • ").append(v.optString("model")).append(" • ").append(driverName(v.optString("driver_id"))).append("\n");}new AlertDialog.Builder(this).setTitle("السيارات").setMessage(s.length()==0?"لا توجد سيارات":s.toString()).setNegativeButton("إغلاق",null).setPositiveButton("+ سيارة",(d,w)->showAddVehicleDialog()).show();}
    private void showAddVehicleDialog(){LinearLayout f=dialogForm();EditText plate=field("رقم اللوحة");EditText model=field("الموديل");Spinner driver=spinner(labelsWithEmpty(drivers,"بدون مندوب","name"));EditText ins=field("انتهاء التأمين YYYY-MM-DD");EditText reg=field("انتهاء الاستمارة YYYY-MM-DD");EditText service=field("الصيانة القادمة YYYY-MM-DD");f.addView(plate);f.addView(model);f.addView(label("المندوب"));f.addView(driver);f.addView(ins);f.addView(reg);f.addView(service);showFormDialog("إضافة سيارة",f,()->runBusy("جاري الحفظ...",()->{JSONObject b=new JSONObject();b.put("plate",plate.getText().toString().trim());b.put("model",model.getText().toString().trim());if(driver.getSelectedItemPosition()>0)b.put("driver_id",drivers.optJSONObject(driver.getSelectedItemPosition()-1).optString("id"));if(!ins.getText().toString().trim().isEmpty())b.put("insurance_expiry",ins.getText().toString().trim());if(!reg.getText().toString().trim().isEmpty())b.put("registration_expiry",reg.getText().toString().trim());if(!service.getText().toString().trim().isEmpty())b.put("next_service_at",service.getText().toString().trim());api.insertRow(accessToken,"vehicles",b);refreshData();}));}
    private void showTasksAdmin(){StringBuilder s=new StringBuilder();for(int i=0;i<tasks.length()&&i<50;i++){JSONObject t=tasks.optJSONObject(i);s.append("• ").append(t.optString("title")).append(" • ").append(taskStatusAr(t.optString("status"))).append(" • ").append(driverName(t.optString("driver_id"))).append("\n");}new AlertDialog.Builder(this).setTitle("المهام").setMessage(s.length()==0?"لا توجد مهام":s.toString()).setNegativeButton("إغلاق",null).setPositiveButton("+ مهمة",(d,w)->showAddTaskDialog("")).show();}
    private void showAddTaskDialog(String driverId){LinearLayout f=dialogForm();EditText title=field("عنوان المهمة");EditText desc=field("التفاصيل");Spinner driver=spinner(labelsWithEmpty(drivers,"بدون مندوب","name"));Spinner priority=spinner(new String[]{"عادي","مهم","عاجل"});if(!driverId.isEmpty()){for(int i=0;i<drivers.length();i++)if(driverId.equals(drivers.optJSONObject(i).optString("id")))driver.setSelection(i+1);}f.addView(title);f.addView(desc);f.addView(label("المندوب"));f.addView(driver);f.addView(label("الأولوية"));f.addView(priority);showFormDialog("إضافة مهمة",f,()->runBusy("جاري الحفظ...",()->{JSONObject b=new JSONObject();b.put("title",title.getText().toString().trim());b.put("description",desc.getText().toString().trim());if(driver.getSelectedItemPosition()>0)b.put("driver_id",drivers.optJSONObject(driver.getSelectedItemPosition()-1).optString("id"));b.put("priority",new String[]{"normal","high","urgent"}[priority.getSelectedItemPosition()]);api.insertRow(accessToken,"tasks",b);refreshData();}));}
    private void showComplaints(){StringBuilder s=new StringBuilder();for(int i=0;i<complaints.length()&&i<50;i++){JSONObject c=complaints.optJSONObject(i);s.append("• ").append(c.optString("title")).append(" • ").append(c.optString("status")).append("\n");}new AlertDialog.Builder(this).setTitle("الشكاوى").setMessage(s.length()==0?"لا توجد شكاوى":s.toString()).setNegativeButton("إغلاق",null).setPositiveButton("+ شكوى",(d,w)->showAddComplaintDialog()).show();}
    private void showAddComplaintDialog(){LinearLayout f=dialogForm();EditText title=field("عنوان الشكوى");EditText details=field("التفاصيل");Spinner driver=spinner(labelsWithEmpty(drivers,"بدون مندوب","name"));f.addView(title);f.addView(details);f.addView(label("المندوب"));f.addView(driver);showFormDialog("إضافة شكوى",f,()->runBusy("جاري الحفظ...",()->{JSONObject b=new JSONObject();b.put("title",title.getText().toString().trim());b.put("details",details.getText().toString().trim());if(driver.getSelectedItemPosition()>0)b.put("driver_id",drivers.optJSONObject(driver.getSelectedItemPosition()-1).optString("id"));api.insertRow(accessToken,"complaints",b);refreshData();}));}
    private void showDocuments(){StringBuilder s=new StringBuilder();for(int i=0;i<documents.length()&&i<80;i++){JSONObject d=documents.optJSONObject(i);s.append("• ").append(driverName(d.optString("driver_id"))).append(" • ").append(d.optString("kind")).append(" • ").append(d.optString("expiry_date","بدون تاريخ")).append("\n");}new AlertDialog.Builder(this).setTitle("وثائق المناديب").setMessage(s.length()==0?"لا توجد وثائق":s.toString()).setNegativeButton("إغلاق",null).setPositiveButton("+ وثيقة",(d,w)->showAddDocumentDialog()).show();}
    private void showAddDocumentDialog(){LinearLayout f=dialogForm();Spinner driver=spinner(labelsWithEmpty(drivers,"اختر مندوب","name"));Spinner kind=spinner(new String[]{"رخصة قيادة","إقامة","تأمين","استمارة","أخرى"});EditText no=field("رقم الوثيقة");EditText exp=field("تاريخ الانتهاء YYYY-MM-DD");f.addView(driver);f.addView(kind);f.addView(no);f.addView(exp);showFormDialog("إضافة وثيقة",f,()->{if(driver.getSelectedItemPosition()==0)throw new Exception("اختر مندوب");runBusy("جاري الحفظ...",()->{JSONObject b=new JSONObject();b.put("driver_id",drivers.optJSONObject(driver.getSelectedItemPosition()-1).optString("id"));b.put("kind",kind.getSelectedItem().toString());b.put("document_number",no.getText().toString().trim());if(!exp.getText().toString().trim().isEmpty())b.put("expiry_date",exp.getText().toString().trim());api.insertRow(accessToken,"driver_documents",b);refreshData();});});}
    private void showLatestLocations(){Map<String,JSONObject> latest=new HashMap<>();for(int i=0;i<locations.length();i++){JSONObject l=locations.optJSONObject(i);String id=l.optString("driver_id");if(!latest.containsKey(id))latest.put(id,l);}String[] names=new String[latest.size()];List<JSONObject> vals=new ArrayList<>();int i=0;for(Map.Entry<String,JSONObject> e:latest.entrySet()){JSONObject l=e.getValue();names[i++]=driverName(e.getKey())+" • "+shortTime(l.optString("captured_at"));vals.add(l);}if(names.length==0){toast("لا توجد مواقع مرسلة بعد");return;}new AlertDialog.Builder(this).setTitle("آخر مواقع المناديب").setItems(names,(d,w)->{JSONObject l=vals.get(w);Uri u=Uri.parse("geo:"+l.optDouble("latitude")+","+l.optDouble("longitude")+"?q="+l.optDouble("latitude")+","+l.optDouble("longitude"));startActivity(new Intent(Intent.ACTION_VIEW,u));}).show();}

    private void showSettlementDialog(JSONObject d){EditText amount=field("المبلغ الذي سلّمه المندوب");amount.setInputType(InputType.TYPE_CLASS_NUMBER);EditText note=field("ملاحظة");LinearLayout f=dialogForm();f.addView(amount);f.addView(note);new AlertDialog.Builder(this).setTitle("تصفية عهدة • "+d.optString("name")).setView(f).setNegativeButton("إلغاء",null).setPositiveButton("حفظ",(x,w)->runBusy("جاري تسجيل التسوية...",()->{JSONObject b=new JSONObject();b.put("driver_id",d.optString("id"));b.put("kind","settlement");b.put("amount",parseInt(amount.getText().toString(),0));b.put("notes",note.getText().toString().trim());api.insertRow(accessToken,"cash_ledger",b);refreshData();})).show();}
    private void showExpenseDialog(){LinearLayout f=dialogForm();Spinner driver=spinner(labelsWithEmpty(drivers,"مصروف عام","name"));Spinner category=spinner(new String[]{"بنزين","مواقف","صيانة","تعويض","أخرى"});EditText amount=field("المبلغ");amount.setInputType(InputType.TYPE_CLASS_NUMBER);EditText note=field("ملاحظة");f.addView(driver);f.addView(category);f.addView(amount);f.addView(note);showFormDialog("تسجيل مصروف",f,()->runBusy("جاري الحفظ...",()->{JSONObject e=new JSONObject();if(driver.getSelectedItemPosition()>0)e.put("driver_id",drivers.optJSONObject(driver.getSelectedItemPosition()-1).optString("id"));e.put("category",category.getSelectedItem().toString());e.put("amount",parseInt(amount.getText().toString(),0));e.put("notes",note.getText().toString().trim());api.insertRow(accessToken,"expenses",e);if(driver.getSelectedItemPosition()>0){JSONObject l=new JSONObject();l.put("driver_id",drivers.optJSONObject(driver.getSelectedItemPosition()-1).optString("id"));l.put("kind","expense");l.put("amount",parseInt(amount.getText().toString(),0));l.put("notes",category.getSelectedItem()+" • "+note.getText().toString().trim());api.insertRow(accessToken,"cash_ledger",l);}refreshData();}));}

    private void startShift(){if(currentDriver==null)return;runBusy("جاري بدء الوردية...",()->{JSONObject b=new JSONObject();b.put("driver_id",currentDriver.optString("id"));api.insertRow(accessToken,"driver_shifts",b);JSONObject st=new JSONObject();st.put("status","active");api.updateRows(accessToken,"drivers?id=eq."+enc(currentDriver.optString("id")),st);refreshData();});}
    private void endShift(String id){runBusy("جاري إنهاء الوردية...",()->{JSONObject b=new JSONObject();b.put("ended_at",OffsetDateTime.now().toString());api.updateRows(accessToken,"driver_shifts?id=eq."+enc(id),b);JSONObject st=new JSONObject();st.put("status","offline");api.updateRows(accessToken,"drivers?id=eq."+enc(currentDriver.optString("id")),st);refreshData();});}
    private JSONObject openShift(){if(currentDriver==null)return null;String id=currentDriver.optString("id");for(int i=0;i<shifts.length();i++){JSONObject s=shifts.optJSONObject(i);if(s!=null&&id.equals(s.optString("driver_id"))&&(s.isNull("ended_at")||s.optString("ended_at").isEmpty()))return s;}return null;}
    private void sendMyLocation(){if(currentDriver==null)return;if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},REQ_LOCATION);return;}try{LocationManager lm=(LocationManager)getSystemService(LOCATION_SERVICE);Location l=lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);if(l==null)l=lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);if(l==null){toast("فعّل GPS وافتح الخرائط لحظة ثم حاول مرة أخرى");return;}final Location loc=l;runBusy("جاري إرسال الموقع...",()->{JSONObject b=new JSONObject();b.put("driver_id",currentDriver.optString("id"));b.put("latitude",loc.getLatitude());b.put("longitude",loc.getLongitude());b.put("accuracy",loc.getAccuracy());api.insertRow(accessToken,"driver_locations",b);refreshData();});}catch(Exception e){toast("تعذر قراءة الموقع");}}
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] results){super.onRequestPermissionsResult(requestCode,permissions,results);if(requestCode==REQ_LOCATION&&results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)sendMyLocation();}

    private void advanceTask(JSONObject t){String s=t.optString("status");String next="new".equals(s)?"accepted":"accepted".equals(s)?"in_progress":"done";runBusy("جاري تحديث المهمة...",()->{JSONObject b=new JSONObject();b.put("status",next);if("done".equals(next))b.put("completed_at",OffsetDateTime.now().toString());api.updateRows(accessToken,"tasks?id=eq."+enc(t.optString("id")),b);refreshData();});}

    private void openMyRoute(){if(currentDriver==null)return;openRouteForDriver(currentDriver.optString("id"));}
    private void openRouteForDriver(String driverId){List<JSONObject> list=new ArrayList<>();for(int i=0;i<orders.length();i++){JSONObject o=orders.optJSONObject(i);if(o!=null&&driverId.equals(o.optString("driver_id"))&&isActiveStatus(o.optString("status")))list.add(o);}if(list.isEmpty()){toast("لا توجد طلبات نشطة للمندوب");return;}String destination=list.get(list.size()-1).optString("address")+" "+list.get(list.size()-1).optString("district");StringBuilder url=new StringBuilder("https://www.google.com/maps/dir/?api=1&travelmode=driving&destination=").append(Uri.encode(destination));if(list.size()>1){url.append("&waypoints=");for(int i=0;i<list.size()-1&&i<8;i++){if(i>0)url.append("%7C");url.append(Uri.encode(list.get(i).optString("address")+" "+list.get(i).optString("district")));}}try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url.toString())));}catch(Exception e){toast("تعذر فتح الجولة");}}

    private void openDial(String phone){try{startActivity(new Intent(Intent.ACTION_DIAL,Uri.parse("tel:"+phone)));}catch(Exception e){toast("تعذر فتح الاتصال");}}
    private void openMaps(JSONObject o){String u=o.optString("maps_url").trim();Uri uri=u.isEmpty()?Uri.parse("geo:0,0?q="+Uri.encode(o.optString("address")+" "+o.optString("district"))):Uri.parse(u);try{startActivity(new Intent(Intent.ACTION_VIEW,uri));}catch(Exception e){toast("تعذر فتح الخرائط");}}
    private void openWhatsApp(JSONObject o){String digits=o.optString("phone").replaceAll("[^0-9]","");String msg="مرحباً "+o.optString("customer")+"، بخصوص طلبكم #TAJ-"+o.optLong("order_number")+" من "+workspaceName;try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://wa.me/"+digits+"?text="+Uri.encode(msg))));}catch(Exception e){toast("تعذر فتح واتساب");}}
    private void shareTracking(JSONObject o){String token=o.optString("tracking_token");if(token.isEmpty()){toast("رابط التتبع غير متاح لهذا الطلب");return;}String text="تتبع طلبك #TAJ-"+o.optLong("order_number")+" من "+workspaceName+"\n"+TRACK_BASE+token;Intent i=new Intent(Intent.ACTION_SEND);i.setType("text/plain");i.putExtra(Intent.EXTRA_TEXT,text);startActivity(Intent.createChooser(i,"إرسال رابط التتبع"));}

    private void showChangePassword(){EditText p=field("كلمة المرور الجديدة");p.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);new AlertDialog.Builder(this).setTitle("تغيير كلمة المرور").setView(p).setNegativeButton("إلغاء",null).setPositiveButton("حفظ",(d,w)->{String value=p.getText().toString();if(value.length()<6){toast("6 أحرف على الأقل");return;}runBusy("جاري تغيير كلمة المرور...",()->{api.updatePassword(accessToken,value);runOnUiThread(()->toast("تم تغيير كلمة المرور"));refreshData();});}).show();}
    private void saveWorkspaceName(String name){if(name.isEmpty()){toast("اكتب الاسم");return;}runBusy("جاري الحفظ...",()->{JSONObject b=new JSONObject();b.put("name",name);api.updateRows(accessToken,"workspace?id=eq.1",b);workspaceName=name;refreshData();});}
    private void shareReportCsv(){StringBuilder c=new StringBuilder("order,customer,status,driver,amount,fee,payment,created\n");for(int i=0;i<orders.length();i++){JSONObject o=orders.optJSONObject(i);c.append("TAJ-").append(o.optLong("order_number")).append(',').append(csv(o.optString("customer"))).append(',').append(o.optString("status")).append(',').append(csv(driverName(o.optString("driver_id")))).append(',').append(o.optInt("amount")).append(',').append(o.optInt("fee")).append(',').append(o.optString("payment")).append(',').append(o.optString("created")).append('\n');}Intent in=new Intent(Intent.ACTION_SEND);in.setType("text/csv");in.putExtra(Intent.EXTRA_TEXT,c.toString());startActivity(Intent.createChooser(in,"مشاركة التقرير"));}

    private void reloadDashboard(){if(loading)return;showLoading("جاري تحديث لوحة التشغيل...");loading=true;io.execute(()->{try{refreshData();}catch(Exception e){loading=false;runOnUiThread(()->{renderApp();toast(friendlyError(e));});}});}
    private interface Work{void run()throws Exception;}
    private void runBusy(String msg,Work w){if(loading)return;loading=true;showLoading(msg);io.execute(()->{try{w.run();}catch(Exception e){loading=false;runOnUiThread(()->{renderApp();toast(friendlyError(e));});}});}
    private void showLoading(String m){LinearLayout r=pageRoot();r.setGravity(Gravity.CENTER);ProgressBar p=new ProgressBar(this);r.addView(p,new LinearLayout.LayoutParams(dp(52),dp(52)));TextView t=text(m,14);t.setTextColor(mutedColor());t.setGravity(Gravity.CENTER);t.setPadding(0,dp(12),0,0);r.addView(t);setContentView(r);}

    private void saveSession(SupabaseApi.AuthResult r){accessToken=nz(r.accessToken);refreshToken=nz(r.refreshToken);userId=nz(r.userId);email=nz(r.email).toLowerCase(Locale.ROOT);prefs.edit().putString("refresh_token",refreshToken).apply();}
    private void clearSession(){accessToken=refreshToken=userId=email="";isAdmin=false;currentDriver=null;prefs.edit().remove("refresh_token").apply();handler.removeCallbacks(autoRefresh);}

    private void notifyIfNeeded(){if(!appSettings.optBoolean("notifications_enabled",true))return;long newest=0;JSONObject target=null;for(int i=0;i<orders.length();i++){JSONObject o=orders.optJSONObject(i);if(o==null||!"new".equals(o.optString("status")))continue;if(!isAdmin&&currentDriver!=null&&!currentDriver.optString("id").equals(o.optString("driver_id")))continue;long n=o.optLong("order_number");if(n>newest){newest=n;target=o;}}long seen=prefs.getLong(isAdmin?"seen_admin":"seen_driver",0);if(target!=null&&newest>seen){prefs.edit().putLong(isAdmin?"seen_admin":"seen_driver",newest).apply();if(Build.VERSION.SDK_INT<33||checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED){Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,NOTIFY_CHANNEL):new Notification.Builder(this);b.setSmallIcon(android.R.drawable.ic_dialog_map).setContentTitle("طلب توصيل جديد #TAJ-"+newest).setContentText(target.optString("customer")+" • "+target.optString("district")).setAutoCancel(true);((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify((int)(newest%Integer.MAX_VALUE),b.build());}}}

    private JSONObject findCustomerByPhone(String phone){String p=phone.replaceAll("\\s","");for(int i=0;i<customers.length();i++){JSONObject c=customers.optJSONObject(i);if(c!=null&&c.optString("phone").replaceAll("\\s","").equals(p))return c;}return null;}
    private int countToday(){int c=0;for(int i=0;i<orders.length();i++){JSONObject o=orders.optJSONObject(i);if(o!=null&&isToday(o.optString("created")))c++;}return c;}
    private int countActive(){int c=0;for(int i=0;i<orders.length();i++){JSONObject o=orders.optJSONObject(i);if(o!=null&&isActiveStatus(o.optString("status")))c++;}return c;}
    private int countStatus(String status){int c=0;for(int i=0;i<orders.length();i++){JSONObject o=orders.optJSONObject(i);if(o!=null&&status.equals(o.optString("status")))c++;}return c;}
    private int overdueCount(){int c=0;for(int i=0;i<orders.length();i++){JSONObject o=orders.optJSONObject(i);if(o!=null&&isOverdue(o))c++;}return c;}
    private boolean isOverdue(JSONObject o){if(!isActiveStatus(o.optString("status")))return false;int limit="urgent".equals(o.optString("priority"))?appSettings.optInt("urgent_sla_minutes",20):appSettings.optInt("order_sla_minutes",45);return ageMinutes(o.optString("created"))>limit;}
    private int activeDriverCount(){Set<String>s=new HashSet<>();for(int i=0;i<shifts.length();i++){JSONObject sh=shifts.optJSONObject(i);if(sh!=null&&(sh.isNull("ended_at")||sh.optString("ended_at").isEmpty()))s.add(sh.optString("driver_id"));}return s.size();}
    private int openComplaints(){int c=0;for(int i=0;i<complaints.length();i++){JSONObject x=complaints.optJSONObject(i);if(x!=null&&!"resolved".equals(x.optString("status"))&&!"closed".equals(x.optString("status")))c++;}return c;}
    private long totalLedgerKind(String kind){long n=0;for(int i=0;i<ledger.length();i++){JSONObject l=ledger.optJSONObject(i);if(l!=null&&kind.equals(l.optString("kind")))n+=l.optInt("amount");}return n;}
    private long driverBalance(String id){long n=0;for(int i=0;i<ledger.length();i++){JSONObject l=ledger.optJSONObject(i);if(l==null||!id.equals(l.optString("driver_id")))continue;String k=l.optString("kind");long a=l.optInt("amount");if("collection".equals(k)||"adjustment".equals(k))n+=a;else if("settlement".equals(k)||"expense".equals(k))n-=a;}return n;}
    private long totalDriverBalance(){long n=0;for(int i=0;i<drivers.length();i++){JSONObject d=drivers.optJSONObject(i);if(d!=null)n+=driverBalance(d.optString("id"));}return n;}
    private String pressureText(){int active=countActive(), available=Math.max(1,activeDriverCount());double per=(double)active/available;if(per>=5)return "ضغط مرتفع • "+active+" طلب نشط على "+available+" مندوب";if(per>=3)return "ضغط متوسط • راقب الطلبات المتأخرة";return "التشغيل مستقر • "+active+" طلب نشط";}

    private boolean isActiveStatus(String s){return !"delivered".equals(s)&&!"cancelled".equals(s)&&!"returned".equals(s);}
    private boolean isToday(String iso){try{return OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.of("Asia/Riyadh")).toLocalDate().equals(LocalDate.now(ZoneId.of("Asia/Riyadh")));}catch(Exception e){return false;}}
    private long ageMinutes(String iso){try{return Duration.between(OffsetDateTime.parse(iso).toInstant(),java.time.Instant.now()).toMinutes();}catch(Exception e){return 0;}}
    private String parseSchedule(String v){LocalDateTime dt=LocalDateTime.parse(v,DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));return dt.atZone(ZoneId.of("Asia/Riyadh")).toOffsetDateTime().toString();}
    private String prettyDate(String iso){if(iso==null||iso.isEmpty()||"null".equals(iso))return "-";try{return OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.of("Asia/Riyadh")).format(DateTimeFormatter.ofPattern("dd/MM • HH:mm"));}catch(Exception e){return iso;}}
    private String shortTime(String iso){if(iso==null||iso.isEmpty())return "-";try{return OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.of("Asia/Riyadh")).format(DateTimeFormatter.ofPattern("HH:mm"));}catch(Exception e){return "-";}}
    private int parseInt(String s,int def){try{return Integer.parseInt(s.trim());}catch(Exception e){return def;}}
    private String driverName(String id){if(id==null||id.isEmpty()||"null".equals(id))return "غير معين";for(int i=0;i<drivers.length();i++){JSONObject d=drivers.optJSONObject(i);if(d!=null&&id.equals(d.optString("id")))return d.optString("name","مندوب");}return "مندوب";}
    private String statusArabic(String s){switch(s){case"received":return"تم الاستلام";case"onway":return"في الطريق";case"delivered":return"تم التوصيل";case"returned":return"مرتجع";case"cancelled":return"ملغي";default:return"جديد";}}
    private String priorityAr(String s){switch(s){case"low":return"منخفض";case"high":return"مهم";case"urgent":return"عاجل";default:return"عادي";}}
    private String driverStatusAr(String s){switch(s){case"active":return"نشط";case"busy":return"مشغول";default:return"غير متصل";}}
    private String taskStatusAr(String s){switch(s){case"accepted":return"مقبولة";case"in_progress":return"قيد التنفيذ";case"done":return"منتهية";case"cancelled":return"ملغاة";default:return"جديدة";}}
    private String taskNextLabel(String s){if("new".equals(s))return"قبول المهمة";if("accepted".equals(s))return"بدء التنفيذ";return"إنهاء المهمة";}
    private String nextStatusLabel(String s){if("new".equals(s))return"استلام الطلب";if("received".equals(s))return"بدء التوصيل";return"تم التسليم";}
    private String ledgerKindAr(String s){switch(s){case"collection":return"تحصيل";case"settlement":return"تسوية";case"expense":return"مصروف";case"bonus":return"حافز";case"commission":return"عمولة";default:return"تعديل";}}
    private String money(long n){return n+" ر.س";}
    private String friendlyError(Exception e){String m=e.getMessage()==null?"حدث خطأ":e.getMessage();String l=m.toLowerCase(Locale.ROOT);if(l.contains("invalid login")||l.contains("invalid credentials"))return"البريد أو كلمة المرور غير صحيحة";if(l.contains("email not confirmed"))return"الحساب غير مفعّل";if(l.contains("already registered")||l.contains("already exists"))return"البريد مسجل بالفعل";if(l.contains("network")||l.contains("unable to resolve host"))return"تحقق من الإنترنت";return m.length()>180?"حدث خطأ أثناء الاتصال بقاعدة البيانات":m;}

    private void showFormDialog(String title,LinearLayout form,ThrowingAction action){ScrollView s=new ScrollView(this);s.addView(form);AlertDialog d=new AlertDialog.Builder(this).setTitle(title).setView(s).setNegativeButton("إلغاء",null).setPositiveButton("حفظ",null).create();d.setOnShowListener(x->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{action.run();d.dismiss();}catch(Exception e){toast(friendlyError(e));}}));d.show();}
    private interface ThrowingAction{void run()throws Exception;}

    private LinearLayout pageRoot(){LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.VERTICAL);r.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));r.setBackgroundColor(bgColor());return r;}
    private LinearLayout dialogForm(){LinearLayout f=new LinearLayout(this);f.setOrientation(LinearLayout.VERTICAL);f.setPadding(dp(20),dp(8),dp(20),dp(12));return f;}
    private LinearLayout cardBox(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setBackground(round(surfaceColor(),20,borderColor(),1));return c;}
    private View statCard(String label,String value,int color){LinearLayout c=cardBox();c.setPadding(dp(10),dp(13),dp(10),dp(13));TextView v=title(value,20);v.setTextColor(color);v.setGravity(Gravity.CENTER);c.addView(v);TextView l=text(label,11);l.setTextColor(mutedColor());l.setGravity(Gravity.CENTER);c.addView(l);return c;}
    private View metricLine(String k,String v){LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);r.setPadding(0,dp(7),0,dp(7));TextView a=text(k,13);a.setTextColor(mutedColor());r.addView(a,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));TextView b=title(v,13);r.addView(b);return r;}
    private TextView sectionTitle(String v){return title(v,19);}
    private View emptyBox(String v){TextView e=text(v,14);e.setTextColor(mutedColor());e.setGravity(Gravity.CENTER);e.setPadding(dp(14),dp(28),dp(14),dp(28));e.setBackground(round(surfaceColor(),18,borderColor(),1));return e;}
    private View spacer(int h){View v=new View(this);v.setLayoutParams(new LinearLayout.LayoutParams(1,dp(h)));return v;}
    private TextView title(String v,int s){TextView t=text(v,s);t.setTypeface(Typeface.DEFAULT_BOLD);return t;}
    private TextView text(String v,int s){TextView t=new TextView(this);t.setText(v);t.setTextSize(s);t.setTextColor(textColor());t.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);return t;}
    private TextView label(String v){TextView t=text(v,12);t.setTextColor(mutedColor());t.setPadding(0,dp(8),0,dp(3));return t;}
    private EditText field(String hint){EditText e=new EditText(this);e.setHint(hint);e.setHintTextColor(darkMode?0xFF87799B:0xFF9A8AAE);e.setTextColor(textColor());e.setTextSize(14);e.setSingleLine(true);e.setPadding(dp(13),dp(10),dp(13),dp(10));e.setBackground(round(darkMode?0xFF21162D:0xFFFAF8FD,14,borderColor(),1));e.setLayoutParams(matchWrapMargins(0,dp(5),0,dp(5)));return e;}
    private CheckBox settingCheck(String text,boolean checked){CheckBox c=new CheckBox(this);c.setText(text);c.setTextColor(textColor());c.setTextSize(13);c.setChecked(checked);return c;}
    private Spinner spinner(String[] values){Spinner s=new Spinner(this);s.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,values));return s;}
    private String[] labelsWithEmpty(JSONArray arr,String first,String key){String[] out=new String[arr.length()+1];out[0]=first;for(int i=0;i<arr.length();i++)out[i+1]=arr.optJSONObject(i).optString(key);return out;}
    private Button primaryButton(String t){return styledButton(t,PURPLE,Color.WHITE,12);}
    private Button goldButton(String t){return styledButton(t,GOLD,PURPLE_DARK,12);}
    private Button secondaryButton(String t){return styledButton(t,darkMode?0xFF2D2038:0xFFF1ECF7,textColor(),12);}
    private Button miniButton(String t){return styledButton(t,darkMode?0xFF2D2038:0xFFF6F2FA,textColor(),10);}
    private Button dangerButton(String t){return styledButton(t,RED,Color.WHITE,12);}
    private Button linkButton(String t){return styledButton(t,Color.TRANSPARENT,PURPLE,12);}
    private Button iconButton(String t){return styledButton(t,0x22FFFFFF,Color.WHITE,18);}
    private Button chipButton(String t,boolean sel){return styledButton(t,sel?PURPLE:(darkMode?0xFF2B1E36:0xFFF2ECF8),sel?Color.WHITE:textColor(),10);}
    private Button navButton(String t,boolean sel){return styledButton(t,sel?(darkMode?0xFF3C1E62:PURPLE_LIGHT):Color.TRANSPARENT,sel?(darkMode?0xFFE9D5FF:PURPLE):mutedColor(),10);}
    private Button styledButton(String t,int bg,int fg,int size){Button b=new Button(this);b.setText(t);b.setTextSize(size);b.setTextColor(fg);b.setAllCaps(false);b.setGravity(Gravity.CENTER);b.setPadding(dp(6),0,dp(6),0);b.setBackground(round(bg,13,0,0));return b;}
    private TextView statusPill(String s){int bg=0xFFEDE9FE,fg=PURPLE;if("received".equals(s)){bg=0xFFFEF3C7;fg=0xFF92400E;}else if("onway".equals(s)){bg=0xFFDBEAFE;fg=BLUE;}else if("delivered".equals(s)){bg=0xFFDCFCE7;fg=GREEN;}else if("returned".equals(s)){bg=0xFFFFE4E6;fg=RED;}else if("cancelled".equals(s)){bg=0xFFE2E8F0;fg=SLATE;}TextView p=text(statusArabic(s),10);p.setTextColor(fg);p.setGravity(Gravity.CENTER);p.setPadding(dp(8),dp(4),dp(8),dp(4));p.setBackground(round(bg,20,0,0));return p;}
    private GradientDrawable round(int color,int radius,int stroke,int sw){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));if(sw>0)g.setStroke(dp(sw),stroke);return g;}
    private GradientDrawable gradient(int[] colors,int radius){GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,colors);g.setCornerRadius(dp(radius));return g;}
    private int bgColor(){return darkMode?0xFF100A16:0xFFF8F6FB;}
    private int surfaceColor(){return darkMode?0xFF1B1224:Color.WHITE;}
    private int textColor(){return darkMode?0xFFF7F0FF:0xFF2A1838;}
    private int mutedColor(){return darkMode?0xFFAA9BB8:0xFF786887;}
    private int borderColor(){return darkMode?0xFF392847:0xFFE9E0F0;}
    private LinearLayout.LayoutParams matchWrap(){return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);}
    private LinearLayout.LayoutParams matchWrapMargins(int l,int t,int r,int b){LinearLayout.LayoutParams p=matchWrap();p.setMargins(l,t,r,b);return p;}
    private LinearLayout.LayoutParams weighted(float w,int left){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,w);p.setMargins(left,0,0,0);return p;}
    private LinearLayout.LayoutParams weightedHeight(float w,int h,int left){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,h,w);p.setMargins(left,0,0,0);return p;}
    private String initialOf(String n){return n==null||n.trim().isEmpty()?"م":n.trim().substring(0,1);}
    private String generatePassword(){return "M"+UUID.randomUUID().toString().replace("-","").substring(0,9)+"1";}
    private String nz(String s){return s==null?"":s;}
    private String enc(String v){return URLEncoder.encode(v==null?"":v,StandardCharsets.UTF_8);}
    private String csv(String v){return "\""+(v==null?"":v.replace("\"","\"\""))+"\"";}
    private int dp(int v){return(int)(v*getResources().getDisplayMetrics().density+0.5f);}
    private void toast(String m){Toast.makeText(this,m,Toast.LENGTH_LONG).show();}

    @Override protected void onDestroy(){handler.removeCallbacks(autoRefresh);io.shutdownNow();super.onDestroy();}
}
