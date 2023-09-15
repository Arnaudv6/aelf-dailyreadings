package co.epitre.aelf_lectures.lectures.data;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.File;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.text.SimpleDateFormat;
import java.util.GregorianCalendar;
import java.util.Objects;
import java.util.concurrent.Callable;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.preference.PreferenceManager;
import android.util.Log;

import org.sqlite.database.sqlite.SQLiteDatabase;
import org.sqlite.database.sqlite.SQLiteException;
import org.sqlite.database.sqlite.SQLiteOpenHelper;
import org.sqlite.database.sqlite.SQLiteStatement;

/**
 * Internal cache manager (SQLite). There is one table per office and one line per day.
 * Each line tracks the
 * - office date
 * - office content (serialized list<LectureItem>)
 * - when this office was loaded               --> used for server initiated invalidation
 * - which version of the application was used --> used for upgrade initiated invalidation
 */

public final class AelfCacheHelper extends SQLiteOpenHelper {
    private static final String TAG = "AELFCacheHelper";
    private static final int DB_VERSION = 4;
    private static final String DB_NAME = "aelf_cache.db";
    private SharedPreferences preference = null;
    private Context ctx;

    private static final String DB_TABLE_CREATE = "CREATE TABLE IF NOT EXISTS `%s` (" +
            "date TEXT PRIMARY KEY," +
            "lectures BLOB," +
            "create_date TEXT," +
            "create_version INTEGER" +
            ")";
    private static final String DB_TABLE_SET = "INSERT OR REPLACE INTO `%s` VALUES (?,?,?,?)";

    @SuppressLint("SimpleDateFormat")
    private static final SimpleDateFormat keyFormatter = new SimpleDateFormat("yyyy-MM-dd");

    // TODO: prepare requests

    AelfCacheHelper(Context context) {
        super(context, context.getDatabasePath(DB_NAME).getAbsolutePath(), null, DB_VERSION);
        File dbDir = context.getDatabasePath(DB_NAME).getParentFile();
        if (dbDir != null) {
            dbDir.mkdirs();
        }
        preference = PreferenceManager.getDefaultSharedPreferences(context);
        ctx = context;
    }

    /**
     * Api
     */

    public void dropDatabase() {
        close();
        this.ctx.deleteDatabase(DB_NAME);
    }

    public long getDatabaseSize() {
        return this.ctx.getDatabasePath(DB_NAME).length();
    }

    @SuppressLint("SimpleDateFormat")
    private String computeKey(GregorianCalendar when) {
        if (when == null) {
            return "0000-00-00";
        }
        return keyFormatter.format(when.getTime());
    }

    // Retry code statement 3 times, recover from sqlite exceptions. Even if everything went well, close
    // the db in hope to mitigate concurrent access issues.
    private Object retry(Callable code) throws IOException {
        long maxAttempts = 3;
        while (maxAttempts-- > 0) {
            try {
                return code.call();
            } catch (SQLiteException e) {
                if (maxAttempts > 0) {
                    // If a migration did not go well, the best we can do is drop the database and re-create
                    // it from scratch. This is hackish but should allow more or less graceful recoveries.
                    Log.e(TAG, "Critical database error. Droping + Re-creating", e);
                    this.dropDatabase();
                }
            } catch (java.io.InvalidClassException e) {
                // Old cache --> act as missing
                return null;
            } catch (Exception e) {
                throw new IOException(e);
            } finally {
                close();
            }
        }

        return null;
    }



    synchronized void store(LecturesController.WHAT what, String when, Office office) throws IOException {
        final String key  = when;
        final String create_date = computeKey(new GregorianCalendar());
        final long create_version = preference.getInt("version", -1);

        // build blob
        final byte[] blob;
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream oos;
            oos = new ObjectOutputStream(bos);
            oos.writeObject(office);
            blob = bos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // insert into the database
        final String sql = String.format(DB_TABLE_SET, what.toString());
        retry(() -> {
            SQLiteStatement stmt;
            stmt = getWritableDatabase().compileStatement(sql);
            stmt.bindString(1, key);
            stmt.bindBlob(2, blob);
            stmt.bindString(3, create_date);
            stmt.bindLong(4, create_version);

            stmt.execute();

            return null;
        });
    }

    // cleaner helper method
    synchronized void truncateBefore(LecturesController.WHAT what, GregorianCalendar when) throws IOException {
        final String key = computeKey(when);
        final String table_name = what.toString();

        retry(() -> {
            SQLiteDatabase db = getWritableDatabase();
            db.delete(table_name, "`date` < ?", new String[] {key});
            return null;
        });
    }

    // cast is not checked when decoding the blob but we where responsible for its creation so... dont care
    @SuppressWarnings("unchecked")
    synchronized Office load(LecturesController.WHAT what, GregorianCalendar when, GregorianCalendar minLoadDate, Long minLoadVersion) throws IOException {
        final String key  = computeKey(when);
        final String table_name = what.toString();
        final String min_create_date = computeKey(minLoadDate);
        final String min_create_version = String.valueOf(minLoadVersion);

        // load from db
        Log.i(TAG, "Trying to load lecture from cache create_date>="+min_create_date+" create_version>="+min_create_version);
        return (Office)retry(() -> {
            SQLiteDatabase db = getReadableDatabase();
            Cursor cur = db.query(
                    table_name,                                                // FROM
                    new String[]{"lectures", "create_date", "create_version"}, // SELECT
                    "`date`=? AND `create_date` >= ? AND create_version >= ?", // WHERE
                    new String[]{key, min_create_date, min_create_version},    // params
                    null, null, null, "1"                                      // GROUP BY, HAVING, ORDER, LIMIT
            );

            // If there is no result --> exit
            if(cur == null || cur.getCount() == 0) {
                return null;
            }

            cur.moveToFirst();
            byte[] blob = cur.getBlob(0);

            Log.i(TAG, "Loaded lecture from cache create_date="+cur.getString(1)+" create_version="+cur.getLong(2));

            try {
                ByteArrayInputStream bis = new ByteArrayInputStream(blob);
                ObjectInputStream ois = new ObjectInputStream(bis);

                return ois.readObject();
            } catch (ClassNotFoundException | IOException e) {
                throw e;
            } finally {
                cur.close();
            }
        });
    }

    synchronized boolean has(LecturesController.WHAT what, GregorianCalendar when, GregorianCalendar minLoadDate, Long minLoadVersion) {
        String min_create_date = computeKey(minLoadDate);
        String min_create_version = String.valueOf(minLoadVersion);

        Log.i(TAG, "Checking if lecture is in cache with create_date>="+min_create_date+" create_version>="+min_create_version);
        try {
            return load(what, when, minLoadDate, minLoadVersion) != null;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Internal logic
     */
    
    private void createCache(SQLiteDatabase db, LecturesController.WHAT what) {
        String sql = String.format(DB_TABLE_CREATE, what);
        db.execSQL(sql);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        for (LecturesController.WHAT what : Objects.requireNonNull(LecturesController.WHAT.class.getEnumConstants())) {
            createCache(db, what);
        }
    }
    
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        this.dropDatabase(); // This is a cache, we can re-build it
    }

}