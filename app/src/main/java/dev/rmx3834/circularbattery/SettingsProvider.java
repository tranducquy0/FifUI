package dev.rmx3834.circularbattery;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

import java.util.Map;

public final class SettingsProvider extends ContentProvider {
    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (!"read".equals(method) || getContext() == null) return null;
        Bundle values = new Bundle();
        for (Map.Entry<String, ?> entry : getContext()
                .getSharedPreferences(ModuleSettings.FILE, 0).getAll().entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Boolean) values.putBoolean(entry.getKey(), (Boolean) value);
            else if (value instanceof Integer) values.putInt(entry.getKey(), (Integer) value);
            else if (value instanceof Long) values.putLong(entry.getKey(), (Long) value);
            else if (value instanceof Float) values.putFloat(entry.getKey(), (Float) value);
            else if (value instanceof String) values.putString(entry.getKey(), (String) value);
        }
        return values;
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection,
                                  String[] selectionArgs, String sortOrder) { return null; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection,
                                String[] selectionArgs) { return 0; }
}
