package cn.jdnjk.simpfun.utils;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONObject;

public class SftpCredentialStore extends SQLiteOpenHelper {
    private static final String DB_NAME = "sftp_credentials.db";
    private static final int DB_VERSION = 1;
    private static final String TABLE = "sftp_credentials";
    private static final long TTL_MILLIS = 14L * 24L * 60L * 60L * 1000L;

    private static SftpCredentialStore instance;

    public static synchronized SftpCredentialStore get(Context context) {
        if (instance == null) {
            instance = new SftpCredentialStore(context.getApplicationContext());
        }
        return instance;
    }

    private SftpCredentialStore(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " ("
                + "instance_id TEXT PRIMARY KEY, "
                + "host TEXT NOT NULL, "
                + "port INTEGER NOT NULL, "
                + "username TEXT NOT NULL, "
                + "password TEXT NOT NULL, "
                + "last_validated_at INTEGER NOT NULL, "
                + "updated_at INTEGER NOT NULL"
                + ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE);
        onCreate(db);
    }

    public synchronized Credential getValid(String instanceId) {
        if (instanceId == null || instanceId.trim().isEmpty()) {
            return null;
        }
        pruneExpired();
        long cutoff = System.currentTimeMillis() - TTL_MILLIS;
        try (Cursor cursor = getReadableDatabase().query(TABLE, null,
                "instance_id=? AND last_validated_at>=?",
                new String[]{instanceId, String.valueOf(cutoff)}, null, null, null, "1")) {
            if (!cursor.moveToFirst()) {
                return null;
            }
            return new Credential(
                    cursor.getString(cursor.getColumnIndexOrThrow("instance_id")),
                    cursor.getString(cursor.getColumnIndexOrThrow("host")),
                    cursor.getInt(cursor.getColumnIndexOrThrow("port")),
                    cursor.getString(cursor.getColumnIndexOrThrow("username")),
                    cursor.getString(cursor.getColumnIndexOrThrow("password"))
            );
        }
    }

    public synchronized void upsertFreshResult(String instanceId, JSONObject data) {
        Credential fresh = Credential.fromApiJson(instanceId, data);
        if (fresh == null) {
            return;
        }
        pruneExpired();
        SQLiteDatabase db = getWritableDatabase();
        long now = System.currentTimeMillis();
        try (Cursor cursor = db.query(TABLE, new String[]{"password"}, "instance_id=?", new String[]{fresh.instanceId}, null, null, null, "1")) {
            ContentValues values = new ContentValues();
            values.put("last_validated_at", now);
            if (cursor.moveToFirst()) {
                String oldPassword = cursor.getString(cursor.getColumnIndexOrThrow("password"));
                if (!fresh.password.equals(oldPassword)) {
                    values.put("password", fresh.password);
                    values.put("updated_at", now);
                }
                db.update(TABLE, values, "instance_id=?", new String[]{fresh.instanceId});
                return;
            }
        }

        ContentValues values = new ContentValues();
        values.put("instance_id", fresh.instanceId);
        values.put("host", fresh.host);
        values.put("port", fresh.port);
        values.put("username", fresh.username);
        values.put("password", fresh.password);
        values.put("last_validated_at", now);
        values.put("updated_at", now);
        db.insert(TABLE, null, values);
    }

    public synchronized void delete(String instanceId) {
        if (instanceId == null || instanceId.trim().isEmpty()) {
            return;
        }
        getWritableDatabase().delete(TABLE, "instance_id=?", new String[]{instanceId});
    }

    public synchronized void pruneExpired() {
        long cutoff = System.currentTimeMillis() - TTL_MILLIS;
        getWritableDatabase().delete(TABLE, "last_validated_at<?", new String[]{String.valueOf(cutoff)});
    }

    public static final class Credential {
        public final String instanceId;
        public final String host;
        public final int port;
        public final String username;
        public final String password;

        Credential(String instanceId, String host, int port, String username, String password) {
            this.instanceId = instanceId;
            this.host = host;
            this.port = port;
            this.username = username;
            this.password = password;
        }

        public static Credential fromApiJson(String instanceId, JSONObject data) {
            if (instanceId == null || instanceId.trim().isEmpty() || data == null) {
                return null;
            }
            JSONObject sftp = data.optJSONObject("data");
            if (sftp == null) {
                sftp = data;
            }
            String host = sftp.optString("ip", "").trim();
            String username = sftp.optString("user_name", "").trim();
            String password = sftp.optString("password", "");
            if (host.isEmpty() || username.isEmpty() || password.isEmpty()) {
                return null;
            }
            int port;
            try {
                port = Integer.parseInt(sftp.optString("port", "22"));
            } catch (NumberFormatException e) {
                port = 22;
            }
            return new Credential(instanceId, host, port, username, password);
        }

        public JSONObject toJson() {
            JSONObject json = new JSONObject();
            try {
                json.put("ip", host);
                json.put("port", String.valueOf(port));
                json.put("user_name", username);
                json.put("password", password);
            } catch (Exception ignored) {
            }
            return json;
        }
    }
}
