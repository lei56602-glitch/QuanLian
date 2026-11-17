package com.quanlian.app;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {
    private int dp(float v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }
    private GradientDrawable bg(int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return t;
    }
    private LinearLayout card() {
        LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); v.setPadding(dp(16),dp(16),dp(16),dp(16));
        v.setBackground(bg(Color.rgb(28,45,75),18)); return v;
    }
    private void addCard(LinearLayout parent, String title, String sub, int iconColor) {
        LinearLayout c = card(); c.setFocusable(true); c.setClickable(true);
        TextView h = text(title,19,Color.WHITE,true); TextView s = text(sub,13,Color.rgb(183,199,222),false);
        c.addView(h); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1,-2); p.topMargin=dp(7); c.addView(s,p);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1,-2); cp.bottomMargin=dp(12); parent.addView(c,cp);
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); getWindow().setStatusBarColor(Color.rgb(10,20,40)); getWindow().setNavigationBarColor(Color.rgb(10,20,40));
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(20),dp(22),dp(20),dp(18));
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{Color.rgb(10,24,51),Color.rgb(13,48,82),Color.rgb(10,25,48)}));
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.addView(root);
        TextView brand = text("全联",30,Color.WHITE,true); root.addView(brand);
        TextView tagline = text("让所有设备，自由交换文件",14,Color.rgb(171,198,228),false); LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-2,-2); tp.topMargin=dp(4); tp.bottomMargin=dp(22); root.addView(tagline,tp);
        LinearLayout actions = new LinearLayout(this); actions.setOrientation(LinearLayout.HORIZONTAL);
        Button send = new Button(this); send.setText("↑  发送文件"); send.setTextColor(Color.WHITE); send.setBackground(bg(Color.rgb(31,119,220),16));
        Button receive = new Button(this); receive.setText("↓  接收文件"); receive.setTextColor(Color.WHITE); receive.setBackground(bg(Color.rgb(31,158,143),16));
        LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(0,dp(62),1); ap.rightMargin=dp(8); actions.addView(send,ap); LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,dp(62),1); bp.leftMargin=dp(8); actions.addView(receive,bp); root.addView(actions);
        TextView nearby=text("附近设备",20,Color.WHITE,true); LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-2,-2); np.topMargin=dp(26); np.bottomMargin=dp(12); root.addView(nearby,np);
        addCard(root,"⌕  正在寻找附近设备","Wi-Fi 自动发现 · 蓝牙辅助发现",0);
        addCard(root,"▣  扫描二维码连接","通过二维码或连接码快速配对",0);
        TextView files=text("快捷入口",20,Color.WHITE,true); LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-2,-2); fp.topMargin=dp(12); fp.bottomMargin=dp(12); root.addView(files,fp);
        addCard(root,"图片    视频    音乐    文档","选择文件后，可发送到一个或多个设备",0);
        TextView foot=text("本地优先传输  ·  无广告  ·  无强制账号",12,Color.rgb(150,177,207),false); foot.setGravity(Gravity.CENTER); LinearLayout.LayoutParams ft=new LinearLayout.LayoutParams(-1,-2); ft.topMargin=dp(10); root.addView(foot,ft);
        setContentView(scroll);
    }
}
