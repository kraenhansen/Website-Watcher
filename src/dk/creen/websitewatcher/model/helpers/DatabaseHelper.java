package dk.creen.websitewatcher.model.helpers;

import java.net.MalformedURLException;
import java.sql.SQLException;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.j256.ormlite.android.apptools.OrmLiteSqliteOpenHelper;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;

import dk.creen.websitewatcher.model.Website;

public class DatabaseHelper extends OrmLiteSqliteOpenHelper {

	public static String LOG_TAG = "Website Watcher DatabaseHelper";
	public static String DATABASE_NAME = "WebsiteWatcher.db";
	public static int DATABASE_VERSION = 1;
	
    public DatabaseHelper(Context context) {
    	super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

	@Override
	public void onCreate(SQLiteDatabase database, ConnectionSource connectionSource) {
		Log.i(LOG_TAG, "Creating database.");
		try {
			TableUtils.createTable(connectionSource, Website.class);
			Log.i(LOG_TAG, "Database tables created.");
			
			Dao<Website, Integer> websiteDao = this.getDao(Website.class);
			Website temp;
			temp = new Website("http://www.creen.dk/");
			websiteDao.create(temp);
			temp = new Website("http://xfiles.creen.dk/changingwebsite.php?a_lot_of_chars");
			websiteDao.create(temp);
			Log.i(LOG_TAG, "Dummy websites created.");
			
		} catch (MalformedURLException e) {
			Log.e(LOG_TAG, "The URL is malformed.");
		} catch (SQLException e) {
			Log.e(LOG_TAG, "SQLException when creating dummy websites.");
			e.printStackTrace();
		}
	}

	@Override
	public void onUpgrade(SQLiteDatabase database, ConnectionSource connectionSource, int oldVersion, int newVersion) {
		// TODO Auto-generated method stub

	}
	
	public Dao<Website, Integer> getWebsiteDao() throws SQLException {
		return this.getDao(Website.class);
	}

}
