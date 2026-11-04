package com.angkin.keyflow;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public final class KineticHomeActivity extends Activity {
    private static final int PICK_PROJECT = 7101;
    private static final int PICK_PLUGIN = 7102;
    private int dp(float value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(8, 11, 18));
        getWindow().setNavigationBarColor(Color.rgb(8, 11, 18));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(8, 11, 18));
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(22), dp(24), dp(22), dp(30));
        scroll.addView(page);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        page.addView(header, new LinearLayout.LayoutParams(-1, dp(58)));
        TextView logo = text("K", 25, Color.WHITE, true);
        logo.setGravity(Gravity.CENTER);
        logo.setBackground(round(new int[]{0xff397df5, 0xff1851c7}, 14, 0));
        header.addView(logo, new LinearLayout.LayoutParams(dp(42), dp(42)));
        TextView title = text("Kinetic", 23, 0xfff6f8fc, true);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, -2, 1);
        titleLp.leftMargin = dp(12);
        header.addView(title, titleLp);
        TextView menu = text("☰", 24, 0xffe9eef9, false);
        menu.setGravity(Gravity.CENTER);
        menu.setBackground(round(new int[]{0xff151d2b}, 14, 0xff263247));
        header.addView(menu, new LinearLayout.LayoutParams(dp(48), dp(48)));
        menu.setOnClickListener(this::showMenu);

        TextView createLabel = text("CREATE", 11, 0xff8190a8, true);
        createLabel.setLetterSpacing(0.16f);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(-2, -2);
        labelLp.topMargin = dp(34);
        page.addView(createLabel, labelLp);

        LinearLayout create = new LinearLayout(this);
        create.setOrientation(LinearLayout.VERTICAL);
        create.setGravity(Gravity.CENTER_VERTICAL);
        create.setPadding(dp(22), dp(18), dp(22), dp(18));
        create.setBackground(round(new int[]{0xff245fc9, 0xff1d4ea7, 0xff142f69}, 25, 0xff477fe0));
        create.setClickable(true);
        create.setFocusable(true);
        LinearLayout.LayoutParams createLp = new LinearLayout.LayoutParams(-1, dp(170));
        createLp.topMargin = dp(12);
        page.addView(create, createLp);
        TextView plus = text("＋", 30, Color.WHITE, false);
        plus.setGravity(Gravity.CENTER);
        plus.setBackground(round(new int[]{0xff4c8eff}, 15, 0));
        create.addView(plus, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView newTitle = text("New Composition", 21, Color.WHITE, true);
        LinearLayout.LayoutParams newTitleLp = new LinearLayout.LayoutParams(-2, -2);
        newTitleLp.topMargin = dp(14);
        create.addView(newTitle, newTitleLp);
        TextView subtitle = text("Start a new creative project", 13, 0xffc2d5f5, false);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-2, -2);
        subLp.topMargin = dp(4);
        create.addView(subtitle, subLp);
        create.setOnClickListener(v -> openEditor(null));

        LinearLayout actions = new LinearLayout(this);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(-1, dp(58));
        actionsLp.topMargin = dp(14);
        page.addView(actions, actionsLp);
        TextView importButton = actionButton("Import Project File");
        LinearLayout.LayoutParams importLp = new LinearLayout.LayoutParams(0, -1, 1);
        importLp.rightMargin = dp(7);
        actions.addView(importButton, importLp);
        importButton.setOnClickListener(v -> pick(PICK_PROJECT));
        TextView pluginButton = actionButton("Install Plugin");
        LinearLayout.LayoutParams pluginLp = new LinearLayout.LayoutParams(0, -1, 1);
        pluginLp.leftMargin = dp(7);
        actions.addView(pluginButton, pluginLp);
        pluginButton.setOnClickListener(v -> pick(PICK_PLUGIN));

        LinearLayout projectsHeader = new LinearLayout(this);
        projectsHeader.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams projectsLp = new LinearLayout.LayoutParams(-1, -2);
        projectsLp.topMargin = dp(34);
        page.addView(projectsHeader, projectsLp);
        projectsHeader.addView(text("Your Projects", 19, 0xfff6f8fc, true), new LinearLayout.LayoutParams(0, -2, 1));
        projectsHeader.addView(text("See all", 13, 0xff72a9ff, false));
        LinearLayout empty = new LinearLayout(this);
        empty.setOrientation(LinearLayout.VERTICAL);
        empty.setGravity(Gravity.CENTER);
        empty.setBackground(round(new int[]{0xff111925}, 18, 0xff263247));
        LinearLayout.LayoutParams emptyLp = new LinearLayout.LayoutParams(-1, dp(112));
        emptyLp.topMargin = dp(14);
        page.addView(empty, emptyLp);
        empty.addView(text("No projects yet", 15, 0xffdce4f2, true));
        TextView emptyHint = text("Your saved projects will appear here", 12, 0xff8290a6, false);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(-2, -2);
        hintLp.topMargin = dp(5);
        empty.addView(emptyHint, hintLp);
        setContentView(scroll);
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }
    private TextView actionButton(String label) {
        TextView t = text(label, 13, Color.WHITE, true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(round(new int[]{0xff347cf0, 0xff2364d6}, 15, 0));
        t.setClickable(true); t.setFocusable(true);
        return t;
    }
    private GradientDrawable round(int[] colors, int radius, int stroke) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors);
        d.setCornerRadius(dp(radius));
        if (stroke != 0) d.setStroke(dp(1), stroke);
        return d;
    }
    private void pick(int request) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i, request);
    }
    private void openEditor(Uri uri) {
        Intent i = new Intent(this, MainActivity.class);
        if (uri != null) {
            i.setAction(Intent.ACTION_VIEW);
            i.setData(uri);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } else {
            i.setAction(Intent.ACTION_MAIN);
        }
        startActivity(i);
    }
    private void showMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add("Settings");
        popup.getMenu().add("New Composition");
        popup.setOnMenuItemClickListener(item -> {
            openEditor(null);
            return true;
        });
        popup.show();
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (request == PICK_PROJECT) openEditor(uri);
        else if (request == PICK_PLUGIN) Toast.makeText(this, "Open Settings > Plugins to finish installing this pack.", Toast.LENGTH_LONG).show();
    }
}