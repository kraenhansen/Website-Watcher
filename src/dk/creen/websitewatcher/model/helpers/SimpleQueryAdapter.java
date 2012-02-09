package dk.creen.websitewatcher.model.helpers;

import java.sql.SQLException;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.util.Log;
import android.view.View;
import android.widget.ResourceCursorAdapter;

import com.j256.ormlite.android.AndroidCompiledStatement;
import com.j256.ormlite.android.AndroidDatabaseResults;
import com.j256.ormlite.android.apptools.OrmLiteSqliteOpenHelper;
import com.j256.ormlite.stmt.PreparedQuery;

/**
 * With great inspiration from Luke Meyer.
 * See: http://groups.google.com/group/ormlite-user/browse_thread/thread/49b4681c8a2fe692/afd7816010c400e2?show_docid=afd7816010c400e2
 * 
 */
public class SimpleQueryAdapter<T> extends ResourceCursorAdapter {

	protected PreparedQuery<T> query;
	protected ListItemBinder<T> binder;
	protected OrmLiteSqliteOpenHelper helper;
	protected Activity caller;

	public SimpleQueryAdapter(Activity caller, int layout, PreparedQuery<T> query, OrmLiteSqliteOpenHelper helper, ListItemBinder<T> binder) throws SQLException {
		super(caller, layout, ((AndroidCompiledStatement) query.compile(helper.getConnectionSource().getReadOnlyConnection())).getCursor());
		this.query = query;
		this.binder = binder;
		this.caller = caller;
		this.helper = helper;
		caller.startManagingCursor(getCursor());
	}

	@Override
	public void notifyDataSetChanged() {
		super.notifyDataSetChanged();
		Log.d("Kookie", "notifyDataSetChanged() called on SimpleQueryAdapter, cursor is "+getCursor());
	}
	
	public void refreshCursor() {
		Log.d("Kookie", "refreshCursor");
		try {
			caller.stopManagingCursor(getCursor());
			// Recompile query to get a new cursor.
			changeCursor(((AndroidCompiledStatement) query.compile(helper.getConnectionSource().getReadOnlyConnection())).getCursor());
			caller.startManagingCursor(getCursor());
		} catch (SQLException e) {
			Log.e("Kookie","notifyDataSetChanged(), error changing the cursor to a new one.");
			e.printStackTrace();
		}
	}

	/**
	 * What happens when a View should be populated with the data of the cursor?
	 */
	@Override
	public void bindView(View listItem, Context context, Cursor cursor) {
		try {
			T dataObject = query.mapRow(new AndroidDatabaseResults(cursor));
			binder.setItemContent(listItem, dataObject);
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}
	
	public interface ListItemBinder<T> {
        void setItemContent(View listItem, T dataObject);
	}
}
