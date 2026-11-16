package dev.rmx3834.circularbattery;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.content.SharedPreferences;

public final class SettingsActivity extends Activity {
    private static final int BACKGROUND = Color.rgb(246, 247, 249);
    private static final int SURFACE = Color.WHITE;
    private static final int ACCENT = Color.rgb(255, 89, 69);
    private static final int PRIMARY = Color.rgb(28, 31, 36);
    private static final int SECONDARY = Color.rgb(119, 124, 132);
    private SharedPreferences settings;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        settings = getSharedPreferences(ModuleSettings.FILE, MODE_PRIVATE);
        getWindow().setStatusBarColor(BACKGROUND);
        getWindow().setNavigationBarColor(BACKGROUND);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BACKGROUND);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(22), dp(26), dp(22), dp(32));
        scroll.addView(page);

        TextView title = text("FifUI", 30, PRIMARY, true);
        page.addView(title);
        TextView subtitle = text("Make your status bar yours", 15, SECONDARY, false);
        LinearLayout.LayoutParams subtitleParams = params(-1, -2);
        subtitleParams.topMargin = dp(5);
        subtitleParams.bottomMargin = dp(25);
        page.addView(subtitle, subtitleParams);

        addSection(page, "BATTERY");
        LinearLayout battery = card(page);
        addSwitch(battery, "Circular battery", "Replace the stock status-bar battery icon",
                settings.getBoolean(ModuleSettings.BATTERY_ENABLED, true), ModuleSettings.BATTERY_ENABLED);
        addSlider(battery, "Ring thickness", "Adjust the battery ring weight",
                settings.getInt(ModuleSettings.BATTERY_THICKNESS, 15), 5, 30, ModuleSettings.BATTERY_THICKNESS);
        addSwitch(battery, "Charging indicator", "Show a center dot while charging",
                settings.getBoolean(ModuleSettings.BATTERY_CHARGING, true), ModuleSettings.BATTERY_CHARGING);
        addSwitch(battery, "Power saver indicator", "Show a center line in power saver",
                settings.getBoolean(ModuleSettings.BATTERY_POWER_SAVE, true), ModuleSettings.BATTERY_POWER_SAVE);

        addSection(page, "STATUS BAR CLOCK");
        LinearLayout clock = card(page);
        addSwitch(clock, "Show clock", "Keep the status-bar clock visible", true);
        addSwitch(clock, "Show seconds", "Update the clock every second", false);
        addSwitch(clock, "Use 24-hour time", "Override the system time format", false);
        addSlider(clock, "Text size", "Set clock text size", 14, 10, 22);
        addSlider(clock, "Text weight", "Adjust clock text emphasis", 400, 300, 700);
        addSwitch(clock, "Custom color", "Use a fixed clock color", false);
        addColorChoices(clock);
        addFormatField(clock);

        TextView note = text("Changes apply after SystemUI restarts.", 13, SECONDARY, false);
        note.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams noteParams = params(-1, -2);
        noteParams.topMargin = dp(20);
        page.addView(note, noteParams);
        setContentView(scroll);
    }

    private void addSection(LinearLayout page, String label) {
        TextView heading = text(label, 12, SECONDARY, true);
        heading.setLetterSpacing(0.08f);
        LinearLayout.LayoutParams lp = params(-1, -2);
        lp.bottomMargin = dp(10);
        page.addView(heading, lp);
    }

    private LinearLayout card(LinearLayout page) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(4), dp(16), dp(4));
        GradientDrawable background = new GradientDrawable();
        background.setColor(SURFACE);
        background.setCornerRadius(dp(20));
        card.setBackground(background);
        card.setElevation(dp(1));
        LinearLayout.LayoutParams lp = params(-1, -2);
        lp.bottomMargin = dp(24);
        page.addView(card, lp);
        return card;
    }

    private void addSwitch(LinearLayout parent, String label, String detail, boolean checked, String key) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(13), 0, dp(13));
        TextView title = text(label, 15, PRIMARY, true);
        TextView description = text(detail, 12, SECONDARY, false);
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(title);
        LinearLayout.LayoutParams detailParams = params(-1, -2);
        detailParams.topMargin = dp(4);
        copy.addView(description, detailParams);
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        Switch toggle = new Switch(this);
        toggle.setChecked(checked);
        toggle.setThumbTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        toggle.setOnCheckedChangeListener((button, value) -> settings.edit().putBoolean(key, value).apply());
        row.addView(toggle);
        parent.addView(row, params(-1, -2));
        addDivider(parent);
    }

    private void addSlider(LinearLayout parent, String label, String detail, int value, int min, int max, String key) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(0, dp(13), 0, dp(13));
        block.addView(text(label, 15, PRIMARY, true));
        TextView description = text(detail, 12, SECONDARY, false);
        LinearLayout.LayoutParams detailParams = params(-1, -2);
        detailParams.topMargin = dp(4);
        block.addView(description, detailParams);
        SeekBar seek = new SeekBar(this);
        seek.setMax(max - min);
        seek.setProgress(value - min);
        seek.setProgressTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        seek.setThumbTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser) settings.edit().putInt(key, progress + min).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
        LinearLayout.LayoutParams seekParams = params(-1, -2);
        seekParams.topMargin = dp(4);
        block.addView(seek, seekParams);
        parent.addView(block, params(-1, -2));
        addDivider(parent);
    }

    private void addColorChoices(LinearLayout parent) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(13), 0, dp(13));
        TextView label = text("Clock color", 15, PRIMARY, true);
        row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        int[] colors = {Color.WHITE, Color.BLACK, ACCENT, Color.rgb(67, 135, 255), Color.rgb(38, 166, 112)};
        for (int color : colors) {
            View swatch = new View(this);
            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            dot.setColor(color);
            dot.setStroke(dp(1), Color.rgb(220, 222, 226));
            swatch.setBackground(dot);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(24), dp(24));
            lp.leftMargin = dp(7);
            row.addView(swatch, lp);
        }
        parent.addView(row);
        addDivider(parent);
    }

    private void addFormatField(LinearLayout parent) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(0, dp(13), 0, dp(15));
        block.addView(text("Time format", 15, PRIMARY, true));
        TextView example = text("Optional pattern, e.g. h:mm a", 12, SECONDARY, false);
        LinearLayout.LayoutParams lp = params(-1, -2);
        lp.topMargin = dp(4);
        block.addView(example, lp);
        android.widget.EditText input = new android.widget.EditText(this);
        input.setSingleLine(true);
        input.setTextSize(14);
        input.setHint("System default");
        input.setTextColor(PRIMARY);
        input.setHintTextColor(SECONDARY);
        block.addView(input, params(-1, -2));
        parent.addView(block);
    }

    private void addDivider(LinearLayout parent) {
        View divider = new View(this);
        divider.setBackgroundColor(Color.rgb(239, 240, 242));
        LinearLayout.LayoutParams lp = params(-1, dp(1));
        lp.leftMargin = dp(1);
        lp.rightMargin = dp(1);
        parent.addView(divider, lp);
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private LinearLayout.LayoutParams params(int width, int height) {
        return new LinearLayout.LayoutParams(width, height);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
