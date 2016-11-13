package com.franckyl.yav1.lockout;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.location.Location;
import android.util.Log;
import android.widget.Toast;

import com.franckyl.yav1.YaV1;
import com.franckyl.yav1.YaV1CurrentPosition;
import com.franckyl.yav1.utils.YaV1GpsPos;

/**
 * Created by franck on 4/19/14.
 */
public class LockoutDb
{
    public static final int    DB_VERSION     = 3;
    public static final String DB_NAME        = "yav1lockout_v2.db";
    public static final String TB_LOCKOUT     = "lockouts";

    public static final String COLUMN_ID        = "_id";
    public static final String COLUMN_FLAG      = "flag";
    public static final String COLUMN_LAT       = "lat";
    public static final String COLUMN_LON       = "lon";
    public static final String COLUMN_FREQUENCY = "frequency";
    public static final String COLUMN_BEARING   = "bearing";
    public static final String COLUMN_SPEED     = "speed";
    public static final String COLUMN_SEEN      = "seen";
    public static final String COLUMN_MISSED    = "missed";
    public static final String COLUMN_PARAM1    = "param1";
    public static final String COLUMN_PARAM2    = "param2";
    public static final String COLUMN_TIME      = "timestamp";

    // DB create statement

    private static final String DATABASE_CREATE = "create table "
            + TB_LOCKOUT + "(" + COLUMN_ID + " integer primary key, "
            + COLUMN_FLAG + " integer, "
            + COLUMN_FREQUENCY + " integer, "
            + COLUMN_LAT + " real, "
            + COLUMN_LON + " real, "
            + COLUMN_SPEED + " real, "
            + COLUMN_BEARING + " integer, "
            + COLUMN_SEEN + " integer, "
            + COLUMN_MISSED + " integer, "
            + COLUMN_PARAM1 + " integer, "
            + COLUMN_PARAM2 + " integer default 0, "
            + COLUMN_TIME + " integer);";

    // utility string

    private static final String[] ALL_COLUMNS         = new String[] {COLUMN_ID, COLUMN_FLAG, COLUMN_LAT, COLUMN_LON,
                                                                      COLUMN_FREQUENCY, COLUMN_BEARING, COLUMN_SPEED,
                                                                      COLUMN_SEEN, COLUMN_MISSED, COLUMN_PARAM1, COLUMN_PARAM2, COLUMN_TIME};

    // compiled statement

    private SQLiteStatement mInsertStmt;
    private SQLiteStatement mUpdateStmt;
    private SQLiteStatement mDeleteStmt;

    // Db and so on

    private Context        mContext;
    private DbHelper       mDbHelper;
    private SQLiteDatabase mDb = null;

    // our id's

    //public static int       mAreaId       = 0;
    public static int       mLockoutId    = 0;

    private double          mDeltaLat     = Double.NaN;
    private double          mDeltaLon     = 0;

    // our DbHelper class

    public class DbHelper extends SQLiteOpenHelper
    {
        public DbHelper(Context context)
        {
            super(context, DB_NAME, null, DB_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db)
        {
            db.execSQL(DATABASE_CREATE);

            // create the initial ids;
            createIdRecord(db);
            Log.d("Valentine", "Id created Lockout " + mLockoutId);
        }
        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)
        {
            if(oldVersion == 1 && newVersion == 2)
            {
                // add the timestamp column
                db.execSQL("ALTER TABLE " + TB_LOCKOUT + " ADD COLUMN " + COLUMN_TIME + " INTEGER DEFAULT 0");
                // update 0 time to now
                db.execSQL("UPDATE " + TB_LOCKOUT + " SET " + COLUMN_TIME + " = " + System.currentTimeMillis() / 1000 + " WHERE " + COLUMN_TIME + " = 0");
            }
            else
            {
                Log.w(DbHelper.class.getName(),
                        "Upgrading database from version " + oldVersion + " to "
                                + newVersion + ", which will destroy all old data");
                db.execSQL("DROP TABLE IF EXISTS " + TB_LOCKOUT);
                onCreate(db);
                Toast.makeText(YaV1.sContext, "The lockout database has been reset", Toast.LENGTH_LONG).show();
            }
        }
    }

    // constructor

    public LockoutDb(Context context)
    {
        mContext  = context;
        mDbHelper = new DbHelper(mContext);
    }

    // open the Db

    public void open() throws SQLException
    {
        mDb =  mDbHelper.getWritableDatabase();
        mDb.enableWriteAheadLogging();

        // we would read our id's
        initIds();
        // compile the statements
        compileStatement();
    }

    // reset our Db

    public void reset()
    {
        mDb.execSQL("DROP TABLE " + TB_LOCKOUT);
        mDbHelper.onCreate(mDb);
    }

    // close our DB

    public void close()
    {
        // we would update our id's
        updateIdRecord();

        mDbHelper.close();
    }

    // get the database path

    public static String getDbCompleteName()
    {
        return YaV1.sContext.getApplicationInfo().dataDir + "/databases/" + DB_NAME;
    }

    // read the Lockout area that are in the radius

    public LockoutList readShortList()
    {
        if(Double.isNaN(mDeltaLat))
        {
            // compute the delta for max and min coordinates
            computeDelta();
        }

        // we read the data from current Location +/- radius
        LockoutList ll = new LockoutList();

        double latMin = YaV1CurrentPosition.lat;
        double lonMin = YaV1CurrentPosition.lon;
        double latMax = latMin + mDeltaLat;
        double lonMax = latMax + mDeltaLon;
        latMin -= mDeltaLat;
        lonMin -= mDeltaLon;

        Cursor cursor = mDb.query(TB_LOCKOUT, new String[] {COLUMN_ID, COLUMN_FLAG, COLUMN_LAT, COLUMN_LON, COLUMN_BEARING, COLUMN_FREQUENCY, COLUMN_SPEED, COLUMN_SEEN, COLUMN_MISSED, COLUMN_PARAM1, COLUMN_PARAM2, COLUMN_TIME},
                COLUMN_ID + "> 1 AND " + COLUMN_LAT + " >= " + Double.toString(latMin) + " AND " + COLUMN_LAT + " <= " + Double.toString(latMax) +
                        " AND " + COLUMN_LON + " >= " + Double.toString(lonMin) + " AND " + COLUMN_LON + " <= "  + Double.toString(lonMax), null, null, null, null);

        try
        {
            if(cursor.moveToFirst())
            {
                do
                {
                    Lockout l = new Lockout(cursor.getInt(0), cursor.getInt(1), cursor.getDouble(2), cursor.getDouble(3),
                                            cursor.getInt(4), cursor.getInt(5), cursor.getFloat(6), cursor.getInt(7),
                                            cursor.getInt(8), cursor.getInt(9), cursor.getInt(10), cursor.getLong(11));

                    // l.resetAfterRead();
                    ll.put(l.getId(), l);

                }while(cursor.moveToNext());
            }
            else
                Log.d("Valentine Lockout", "Db empty");
        }
        finally
        {
            cursor.close();
        }

        Log.d("Valentine Lockout", "End Query DB for short list size " + ll.size());
        return ll;
    }

    // read the Lockout area that are in the radius

    public Lockout readLockout(int lockoutId)
    {
        Cursor cursor = mDb.query(TB_LOCKOUT, new String[] {COLUMN_ID, COLUMN_FLAG, COLUMN_LAT, COLUMN_LON, COLUMN_BEARING, COLUMN_FREQUENCY, COLUMN_SPEED, COLUMN_SEEN, COLUMN_MISSED, COLUMN_PARAM1, COLUMN_PARAM2, COLUMN_TIME},
                COLUMN_ID + "= " + lockoutId, null, null, null, null);

        Lockout l = null;
        try
        {
            if(cursor.moveToFirst())
            {
                l = new Lockout(cursor.getInt(0), cursor.getInt(1), cursor.getDouble(2), cursor.getDouble(3),
                                cursor.getInt(4), cursor.getInt(5), cursor.getFloat(6), cursor.getInt(7),
                                cursor.getInt(8), cursor.getInt(9), cursor.getInt(10), cursor.getLong(11));
            }
        }
        finally
        {
            cursor.close();
        }

        return l;
    }

    // compute the delta values

    private void computeDelta()
    {
        YaV1GpsPos pos = YaV1CurrentPosition.getPos();
        double lat = pos.lat;
        double lon = pos.lon;
        float   results[] = {0, 0, 0};

        Location.distanceBetween(lat, lon, lat+0.1, lon, results);
        mDeltaLat = results[0] * 10.0;
        Location.distanceBetween(lat, lon, lat, lon+0.1, results);
        mDeltaLon = results[0] * 10.0;

        // we have got our divisor for distance short list radius
        mDeltaLat = ( (double) LockoutParam.mListRadius) / mDeltaLat;
        mDeltaLon = ( (double) LockoutParam.mListRadius) / mDeltaLon;

        YaV1.DbgLog("Lockout delta for radius " + LockoutParam.mListRadius + " Lat " + mDeltaLat + " Lon " + mDeltaLon);
    }

    // get the id's when starting

    public void initIds()
    {
        Cursor cursor = mDb.query(TB_LOCKOUT, new String[] {COLUMN_ID, COLUMN_FLAG, COLUMN_BEARING},
                                                COLUMN_ID + "=1", null, null, null, null);

        if(cursor != null)
        {
            if(cursor.moveToFirst())
            {
                // get the ids
                //mAreaId    = cursor.getInt(1);
                mLockoutId = cursor.getInt(2);
                //LockoutData.mAreaId    = mAreaId;
                LockoutData.mLockoutId = mLockoutId;
                //Log.d("Valentine", "Reading id Lockout " + mLockoutId);
                YaV1.DbgLog("Reading next id Lockout " + mLockoutId);
            }
            else
            {
                Log.d("Valentine Lockout", "Error moving cursor first on initIds");
                YaV1.DbgLog("Reading next id Lockout failed, no cursor on first");
            }
            cursor.close();
            verifyId();
        }
        else
        {
            // we have a problem
            Log.d("Valentine Lockout", "Error reading Id's");
        }
    }

    // check if the id is correct

    private void verifyId()
    {
        Cursor cursor = mDb.rawQuery("SELECT MAX(" + COLUMN_ID + ") FROM " + TB_LOCKOUT + " WHERE 1;", null);
        if(cursor != null)
        {
            if(cursor.moveToFirst())
            {
                int id = cursor.getInt(0);

                //Log.d("Valentine Lockout", "Reading max id from table " + id + " Current from row 1 " + mLockoutId);
                YaV1.DbgLog("Reading Lockout max id from table " + id + " Current from row 1 " + mLockoutId);
                if(id > mLockoutId)
                {
                    mLockoutId = id + 1;
                    YaV1.DbgLog("Reading Lockout max id is greater than lockout from row 1, reset to " + mLockoutId);
                }
            }
            else
            {
                Log.d("Valentine Lockout", "Reading max Id from table, cursor failed");
            }
            cursor.close();
        }
    }
    // create the initial ids

    private void createIdRecord(SQLiteDatabase db)
    {
        ContentValues value= new ContentValues();

        value.put(COLUMN_ID, 1);
        value.put(COLUMN_FLAG, 1);
        value.put(COLUMN_BEARING, 1);
        value.put(COLUMN_LAT, 0.0);
        value.put(COLUMN_LON, 0.0);
        value.put(COLUMN_FREQUENCY, 0);
        value.put(COLUMN_SPEED, 0.0);
        value.put(COLUMN_SEEN, 0);
        value.put(COLUMN_MISSED, 0);
        value.put(COLUMN_PARAM1, 0);
        value.put(COLUMN_PARAM2, 0);
        value.put(COLUMN_TIME, System.currentTimeMillis() / 1000);
        db.insert(TB_LOCKOUT, null, value);
        LockoutData.mLockoutId =  mLockoutId = 1;
    }

    // update the id's

    private void updateIdRecord()
    {
        // if(LockoutData.mAreaId != mAreaId || LockoutData.mLockoutId != mLockoutId)
        if(LockoutData.mLockoutId != mLockoutId)
        {
            ContentValues value= new ContentValues();
            mLockoutId = LockoutData.mLockoutId;
            value.put(COLUMN_BEARING, mLockoutId);
            value.put(COLUMN_TIME, System.currentTimeMillis() / 1000);
            mDb.update(TB_LOCKOUT, value, "_id = 1", null);
            Log.d("Valentine Lockout", "Id updated Lockout " + mLockoutId);
            YaV1.DbgLog("Lockout Id updated Lockout " + mLockoutId);
        }
    }

    // compile our statement

    private void compileStatement()
    {
        String str = "INSERT INTO " + TB_LOCKOUT + "(";
        String v   = "VALUES(";
        for(int i =0; i < ALL_COLUMNS.length; i++)
        {
            if(i < ALL_COLUMNS.length - 1)
            {
                str += ALL_COLUMNS[i] + ",";
                v += "?,";
            }
            else
            {
                str += ALL_COLUMNS[i] + ")";
                v += "?)";
            }
        }

        mInsertStmt = mDb.compileStatement(str + " " + v);

        // for the update ...
        str = "UPDATE " + TB_LOCKOUT + " SET " + COLUMN_SEEN + " =?, " + COLUMN_MISSED + " =?, " + COLUMN_PARAM1 + " =?, " + COLUMN_PARAM2 + " =?, " +COLUMN_FLAG + "=?,"  + COLUMN_TIME + "=? WHERE " + COLUMN_ID + "=?";
        mUpdateStmt = mDb.compileStatement(str);

        // for the delete
        str = "DELETE FROM " + TB_LOCKOUT + " WHERE " + COLUMN_ID + "=?";
        mDeleteStmt = mDb.compileStatement(str);
    }

    // delete a single Area

    public boolean deleteLockout(int id)
    {
        boolean rc = true;
        mDb.beginTransactionNonExclusive();

        try
        {
            stackDelete(id);
            mDb.setTransactionSuccessful();
        }
        catch(SQLException exc)
        {
            Log.d("Valentine Lockout", "Exception delete lockout id " + id + " Exception : " + exc.toString());
            YaV1.DbgLog("Lockout Db Exception delete lockout id " + id + " Exception : " + exc.toString());
            rc = false;
        }
        finally
        {
            mDb.endTransaction();
        }

        return rc;
    }

    // update or insert an area

    public boolean updateLockout(Lockout l, boolean retry)
    {
        boolean rc = true;
        int     f = l.mFlag;
        boolean retryUpdate = false;


        mDb.beginTransactionNonExclusive();

        try
        {
            if((l.mFlag & l.LOCKOUT_NEW) > 0)
            {
                stackInsert(l);
            }
            else
            {
                // update
                stackUpdate(l);
            }

            mDb.setTransactionSuccessful();
            if(retry)
            {
                YaV1.DbgLog("Valentine lockout Update from retry success");
            }
        }
        catch(SQLException exc)
        {
            // check for error code 19
            if(exc.toString().contains("code 19"))
            {
                if(!retry)
                {
                    YaV1.DbgLog("Valentine lockout Final Save SQL exception " + exc.toString() + " We will retry update");
                    retryUpdate = true;
                }
                else
                {
                    YaV1.DbgLog("Valentine lockout Final Save SQL exception " + exc.toString() + " From retry");
                }
            }

            YaV1.DbgLog("Valentine lockout SQL exception id " + l.getId() + " Flag " + f + " Exception" + exc.toString());
            rc = false;
        }
        finally
        {
            mDb.endTransaction();
        }

        if(retryUpdate)
        {
            l.resetFlag();
            l.mFlag |= l.LOCKOUT_UPDATE;
            rc = updateLockout(l, true);
        }

        return rc;
    }

    // final save

    public void finalSave(LockoutArea ll)
    {
        int     id;
        int     nb = 0;
        Lockout l;
        mDb.beginTransactionNonExclusive();

        try
        {
            for(int i=0; i < ll.size(); i++)
            {
                l = ll.valueAt(i);
                if(l == null)
                    continue;

                if( (l.mFlag & l.LOCKOUT_NEW) > 0)
                {
                    stackInsert(l);
                    ++nb;
                }
                else if( (l.mFlag & l.LOCKOUT_UPDATE) > 0)
                {
                    stackUpdate(l);
                    ++nb;
                }
            }

            // commit
            mDb.setTransactionSuccessful();
        }
        catch(SQLException exc)
        {
            YaV1.DbgLog("Valentine lockout Final Save SQL exception " + exc.toString());
        }
        finally
        {
            mDb.endTransaction();
        }

        YaV1.DbgLog("Valentine lockout Final Save statements " + nb);
    }

    // get the number of lockout and number of learning

    public boolean getCount()
    {
        Cursor result;
        boolean rc = true;
        result = mDb.rawQuery("SELECT COUNT(*) FROM " + TB_LOCKOUT + " WHERE " + COLUMN_SEEN + " >= " + LockoutParam.mMinSeen, null);

        if(result.moveToFirst())
            LockoutData.sLockoutCount = result.getInt(0);
        else
            rc = false;

        result.close();

        result = mDb.rawQuery("SELECT COUNT(*) FROM " + TB_LOCKOUT + " WHERE " + COLUMN_SEEN + " < " + LockoutParam.mMinSeen + " AND " + COLUMN_ID + " > 1", null);

        if(result.moveToFirst())
            LockoutData.sLockoutLearning = result.getInt(0);
        else
            rc = false;

        result.close();
        return rc;
    }

    // exec a delete statement

    private void stackDelete(int id)
    {
        mDeleteStmt.clearBindings();
        mDeleteStmt.bindString(1, Integer.toString(id));
        mDeleteStmt.execute();
    }

    // stack an update

    private void stackUpdate(Lockout l)
    {
        // reset the flags
        l.resetFlag();
        // set the time stamp
        l.mTimeStamp = System.currentTimeMillis() / 1000;
        mUpdateStmt.clearBindings();
        mUpdateStmt.bindLong(1, l.mSeen);
        mUpdateStmt.bindLong(2, l.mMissed);
        mUpdateStmt.bindLong(3, l.mParam1);
        mUpdateStmt.bindLong(4, l.mParam2);
        mUpdateStmt.bindLong(5, l.mFlag);
        mUpdateStmt.bindLong(6, l.mTimeStamp);
        mUpdateStmt.bindLong(7, l.getId());
        mUpdateStmt.execute();
    }

    // stack an insert

    private void stackInsert(Lockout l)
    {
        l.resetFlag();
        l.mTimeStamp = System.currentTimeMillis() / 1000;
        mInsertStmt.clearBindings();
        mInsertStmt.bindLong(1, l.getId());
        mInsertStmt.bindLong(2, l.mFlag);
        mInsertStmt.bindDouble(3, l.mLat);
        mInsertStmt.bindDouble(4, l.mLon);
        mInsertStmt.bindLong(5, l.mFrequency);
        mInsertStmt.bindLong(6, l.mBearing);
        mInsertStmt.bindDouble(7, (double) l.mSpeed);
        mInsertStmt.bindLong(8, l.mSeen);
        mInsertStmt.bindLong(9, l.mMissed);
        mInsertStmt.bindLong(10, l.mParam1);
        mInsertStmt.bindLong(11, l.mParam2);
        mInsertStmt.bindLong(12, l.mTimeStamp);
        mInsertStmt.execute();
    }
}
