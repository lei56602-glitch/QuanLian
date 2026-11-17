package com.quanlian.app;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQUEST_FILES = 1001;
    private final ArrayList<Uri> selectedUris = new ArrayList<>();
    private final ArrayList<String> selectedNames = new ArrayList<>();
    private final ArrayList<Long> selectedSizes = new ArrayList<>();
    private int page = 0;
    private int dp(float v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }

    private GradientDrawable bg(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }
    private LinearLayout column() {
        LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL);
        v.setPadding(dp(20), dp(22), dp(20), dp(20)); return v;
    }
    private LinearLayout card() {
        LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL);
        v.setPadding(dp(16), dp(16), dp(16), dp(16)); v.setBackground(bg(Color.rgb(28,45,75),18));
        v.setFocusable(true); v.setClickable(true); return v;
    }
    private void addGap(LinearLayout parent, int height) {
        View gap = new View(this); parent.addView(gap, new LinearLayout.LayoutParams(1, dp(height)));
    }
    private TextView heading(String value) {
        TextView t = text(value, 25, Color.WHITE, true); return t;
    }
    private Button actionButton(String label, int color) {
        Button b = new Button(this); b.setText(label); b.setTextColor(Color.WHITE);
        b.setAllCaps(false); b.setTextSize(16); b.setBackground(bg(color,16));
        b.setMinHeight(dp(54)); b.setFocusable(true); return b;
    }
    private void install(LinearLayout content) {
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.addView(content);
        setContentView(scroll);
    }
    private void addHeader(LinearLayout root, String title, String subtitle) {
        TextView back = text("‹  返回首页", 15, Color.rgb(160,195,230), true);
        back.setPadding(0,0,0,dp(18)); back.setFocusable(true); back.setOnClickListener(v -> showHome());
        root.addView(back);
        root.addView(heading(title)); addGap(root,6);
        root.addView(text(subtitle,14,Color.rgb(171,198,228),false)); addGap(root,22);
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(10,20,40));
        getWindow().setNavigationBarColor(Color.rgb(10,20,40));
        showHome();
    }

    private void showHome() {
        page = 0;
        LinearLayout root = column();
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(10,24,51),Color.rgb(13,48,82),Color.rgb(10,25,48)}));
        root.addView(text("全联",30,Color.WHITE,true));
        TextView tagline = text("让所有设备，自由交换文件",14,Color.rgb(171,198,228),false);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2,-2); tp.topMargin=dp(4); tp.bottomMargin=dp(22); root.addView(tagline,tp);

        LinearLayout actions = new LinearLayout(this); actions.setOrientation(LinearLayout.HORIZONTAL);
        Button send = actionButton("↑  发送文件",Color.rgb(31,119,220));
        Button receive = actionButton("↓  接收文件",Color.rgb(31,158,143));
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(0,dp(62),1); ap.rightMargin=dp(6);
        actions.addView(send,ap);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0,dp(62),1); bp.leftMargin=dp(6);
        actions.addView(receive,bp); root.addView(actions);
        send.setOnClickListener(v -> openFilePicker());
        receive.setOnClickListener(v -> showReceive());

        addGap(root,26); root.addView(text("附近设备",20,Color.WHITE,true)); addGap(root,10);
        LinearLayout nearby = card();
        nearby.addView(text("⌕  附近设备发现",18,Color.WHITE,true)); addGap(nearby,6);
        nearby.addView(text("设备自动发现服务正在开发中。连接功能接入后，这里会显示真实在线设备。",13,Color.rgb(183,199,222),false));
        root.addView(nearby);
        addGap(root,12);
        LinearLayout qr = card(); qr.addView(text("▣  扫描二维码连接",18,Color.WHITE,true)); addGap(qr,6);
        qr.addView(text("通过接收端二维码或连接码配对",13,Color.rgb(183,199,222),false));
        qr.setOnClickListener(v -> showReceive()); root.addView(qr);
        addGap(root,22); root.addView(text("最近选择",19,Color.WHITE,true)); addGap(root,8);
        if (selectedNames.isEmpty()) {
            root.addView(text("尚未选择文件",13,Color.rgb(150,177,207),false));
        } else {
            root.addView(text("上次选择了 " + selectedNames.size() + " 个文件",14,Color.WHITE,false));
        }
        addGap(root,24);
        TextView foot = text("本地优先传输  ·  无广告  ·  无强制账号",12,Color.rgb(150,177,207),false);
        foot.setGravity(Gravity.CENTER); root.addView(foot);
        install(root);
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "image/*","video/*","audio/*","application/pdf",
                "application/msword","application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.ms-excel","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.ms-powerpoint","application/vnd.openxmlformats-officedocument.presentationml.presentation",
                "application/zip","application/octet-stream"
        });
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(intent, REQUEST_FILES);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_FILES || resultCode != RESULT_OK || data == null) return;
        selectedUris.clear(); selectedNames.clear(); selectedSizes.clear();
        if (data.getClipData() != null) {
            for (int i=0; i<data.getClipData().getItemCount(); i++) addSelected(data.getClipData().getItemAt(i).getUri());
        } else if (data.getData() != null) {
            addSelected(data.getData());
        }
        showSelectedFiles();
    }

    private void addSelected(Uri uri) {
        String name = "未命名文件"; long size = -1;
        Cursor c = null;
        try {
            c = getContentResolver().query(uri, null, null, null, null);
            if (c != null && c.moveToFirst()) {
                int ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                int si = c.getColumnIndex(OpenableColumns.SIZE);
                if (ni >= 0) name = c.getString(ni);
                if (si >= 0 && !c.isNull(si)) size = c.getLong(si);
            }
        } catch (Exception ignored) { }
        finally { if (c != null) c.close(); }
        selectedUris.add(uri); selectedNames.add(name); selectedSizes.add(size);
    }

    private String formatSize(long bytes) {
        if (bytes < 0) return "大小未知";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024*1024) return String.format(Locale.getDefault(),"%.1f KB",bytes/1024.0);
        if (bytes < 1024L*1024*1024) return String.format(Locale.getDefault(),"%.1f MB",bytes/(1024.0*1024));
        return String.format(Locale.getDefault(),"%.2f GB",bytes/(1024.0*1024*1024));
    }

    private void showSelectedFiles() {
        page = 1;
        LinearLayout root = column();
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(10,24,51),Color.rgb(13,48,82),Color.rgb(10,25,48)}));
        addHeader(root,"已选择文件","检查文件后，下一步选择接收设备");
        long total = 0; boolean known = true;
        for (long s : selectedSizes) { if (s < 0) known=false; else total += s; }
        root.addView(text("已选择 " + selectedUris.size() + " 个文件 · " + (known ? formatSize(total) : "部分文件大小未知"),
                15,Color.rgb(190,215,242),true));
        addGap(root,14);
        for (int i=0; i<selectedNames.size(); i++) {
            LinearLayout item = card();
            item.addView(text(selectedNames.get(i),15,Color.WHITE,true)); addGap(item,5);
            item.addView(text(formatSize(selectedSizes.get(i)),12,Color.rgb(183,199,222),false));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1,-2); p.bottomMargin=dp(8); root.addView(item,p);
        }
        addGap(root,12);
        Button choose = actionButton("选择接收设备",Color.rgb(31,119,220));
        choose.setOnClickListener(v -> showDevicePicker()); root.addView(choose);
        Button more = actionButton("继续添加文件",Color.rgb(48,75,112));
        more.setOnClickListener(v -> openFilePicker());
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(-1,-2); mp.topMargin=dp(10); root.addView(more,mp);
        install(root);
    }

    private void showDevicePicker() {
        page = 2;
        LinearLayout root = column();
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(10,24,51),Color.rgb(13,48,82),Color.rgb(10,25,48)}));
        addHeader(root,"选择接收设备","选择要接收这批文件的设备");
        LinearLayout state = card();
        state.addView(text("正在寻找附近设备",18,Color.WHITE,true)); addGap(state,8);
        state.addView(text("真实局域网发现和设备连接服务正在接入。当前版本不会显示虚构设备，也不会假装文件已发送。",14,Color.rgb(183,199,222),false));
        root.addView(state); addGap(root,14);
        Button retry = actionButton("重新搜索",Color.rgb(31,119,220));
        retry.setOnClickListener(v -> showDevicePicker()); root.addView(retry);
        install(root);
    }

    private void showReceive() {
        page = 3;
        LinearLayout root = column();
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(10,24,51),Color.rgb(13,48,82),Color.rgb(10,25,48)}));
        addHeader(root,"接收文件","让附近设备找到你，或通过二维码建立连接");
        LinearLayout waiting = card();
        waiting.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView symbol = text("◎",54,Color.rgb(70,190,174),true); symbol.setGravity(Gravity.CENTER);
        waiting.addView(symbol); addGap(waiting,8);
        waiting.addView(text("接收模式",20,Color.WHITE,true)); addGap(waiting,8);
        waiting.addView(text("接收端页面已建立。二维码会在安全连接会话服务接入后生成；目前不会展示无法使用的假二维码。",14,Color.rgb(183,199,222),false));
        root.addView(waiting); addGap(root,14);
        Button home = actionButton("返回首页",Color.rgb(31,119,220));
        home.setOnClickListener(v -> showHome()); root.addView(home);
        install(root);
    }

    @Override public void onBackPressed() {
        if (page == 0) super.onBackPressed();
        else if (page == 1) showHome();
        else if (page == 2) showSelectedFiles();
        else showHome();
    }
}
