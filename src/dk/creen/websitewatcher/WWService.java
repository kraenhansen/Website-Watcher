package dk.creen.websitewatcher;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import android.widget.BaseAdapter;

import com.j256.ormlite.android.apptools.OrmLiteBaseService;

import dk.creen.websitewatcher.control.WebsiteStateChangeListener;
import dk.creen.websitewatcher.model.Website;
import dk.creen.websitewatcher.model.helpers.DatabaseHelper;

public class WWService extends OrmLiteBaseService<DatabaseHelper> implements WebsiteStateChangeListener {
	
	public static final String LOG_TAG = "Website Watcher Service";
	public enum States {
		OFFLINE,
		ONLINE
	}
	
	private static WWService singleton;
	private static RefresherRoutine refresher;
	private static List<Website> websites = new ArrayList<Website>();
	Queue<Website> jobs = new LinkedList<Website>();
    private final IBinder mBinder = new WebsiteServiceBinder();
	private ArrayList<WebsiteStateChangeListener> websiteStateChangeListeners = new ArrayList<WebsiteStateChangeListener>();
	private States state = States.OFFLINE;

	public WWService() {
		super();
	}

	public States getState() {
		return state;
	}

	public void setState(States state) {
		this.state = state;
	}

	@Override
	public IBinder onBind(Intent i) {
		return mBinder;
	}

	@Override
	public void onCreate() {
		// TODO Auto-generated method stub
		super.onCreate();
		Log.i(LOG_TAG, "Service created");
		if(singleton == null) {
			Log.i(LOG_TAG, "Singleton created");
			// Only run once pr. process
			singleton = this;
			
			// Initialize the database.
			InitializationRoutine inializer = new InitializationRoutine();
			inializer.execute();
			
			refresher = new RefresherRoutine();
			refresher.execute();
			
			reloadWebsites();
		}
	}
	
	public void reloadWebsites() {
		try {
			websites = getHelper().getWebsiteDao().queryForAll();
			for(Website website: websites) {
				website.addWebsiteStateChangeListener(this);
			}
		} catch (SQLException e) {
			Log.e(LOG_TAG, "Error fetching all websites.");
			e.printStackTrace();
		}
	}

	public static List<Website> getWebsites() {
		return websites;
	}

	public static WWService getInstance() {
		return singleton;
	}

	@Override
	public void fire(Website website, Website.States state) {
    	for(WebsiteStateChangeListener listener: websiteStateChangeListeners) {
    		listener.fire(website, state);
    	}
    	if(state == Website.States.CHANGED || state == Website.States.UNCHANGED) {
	    	String ns = Context.NOTIFICATION_SERVICE;
	    	NotificationManager mNotificationManager = (NotificationManager) getSystemService(ns);
	    	if(state == Website.States.CHANGED) {
		    	long when = System.currentTimeMillis();
		    	String tickerText = getResources().getString(R.string.website_has_changed);
		    	Notification notification = new Notification(R.drawable.website_state_changed, tickerText, when);
		    	
		    	Context context = getApplicationContext();
		    	CharSequence contentTitle = "A website has changed!";
		    	CharSequence contentText = website.toString();
		    	//Intent notificationIntent = new Intent(this, WWService.class);
		    	Intent notificationIntent = new Intent(this, WWSettingsActivity.class);
		    	notificationIntent.putExtra(WWSettingsActivity.EXTRA_WEBSITE_ID, website.getId());
		    	PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
		
		    	notification.defaults |= Notification.DEFAULT_VIBRATE;
		    	notification.defaults |= Notification.DEFAULT_LIGHTS;
		    	notification.flags |= Notification.FLAG_AUTO_CANCEL;
		    	notification.setLatestEventInfo(context, contentTitle, contentText, contentIntent);
		    	
		    	mNotificationManager.notify(website.getId(), notification);
	    	} else if(state == Website.States.UNCHANGED) {
	    		mNotificationManager.cancel(website.getId());
	    	}
    	}
	}

	
	class RefresherRoutine extends AsyncTask<Void, Integer, Void> {

		@Override
		protected Void doInBackground(Void... params) {
			while(true) {
				if(state == States.ONLINE) {
					Date now = new Date();
					for(Website website: websites) {
						// We only want to refresh websites that have been noticed (and resat) by the user.
						if(website.getState() != Website.States.CHANGED) {
							long delta = now.getTime()/1000 - website.getLastRefreshedAt().getTime()/1000;
							if(delta > website.getRefreshDelay() && !jobs.contains(website) && website.isValid()) {
								jobs.add(website);
							}
						}
					}
				
					// Refresh the next on queue.
					Website website;
					while((website = jobs.poll()) != null) {
						// Refresh!
						website.refresh();
						// Save the values, if a new date of last update was made.
						try {
							getHelper().getWebsiteDao().update(website);
						} catch (SQLException e) {
							Log.e(LOG_TAG, "SQLError saving the lastRefreshedAt value of a newly updated website.");
							e.printStackTrace();
						}
					}
				}
				
				// Wait ...				
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					Log.i(LOG_TAG, "Interrupted ...");
				}
			}
		}
	}
	
	class InitializationRoutine extends AsyncTask<DatabaseHelper, Integer, Void> {

		//private ProgressBar progressBar;
		public InitializationRoutine() {
			//this.progressBar = progressBar;
		}

		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			Log.i(LOG_TAG, "InitializationRoutine started.");
			//progressBar.setVisibility(View.VISIBLE);
		}

		@Override
		protected void onPostExecute(Void result) {
			super.onPostExecute(result);
			Log.i(LOG_TAG, "InitializationRoutine ended.");
			//progressBar.setVisibility(View.INVISIBLE);
		}

		@Override
		protected Void doInBackground(DatabaseHelper... params) {
			if(params.length > 0) {
				DatabaseHelper helper = params[0];
				// This will open/create databases.
				helper.getWritableDatabase();
			}
			return null;
		}
		
	}
	
    public class WebsiteServiceBinder extends Binder {
    	BaseAdapter adapter;
    	
    	WWService getService() {
            return WWService.getInstance();
        }

		public void addWebsiteStateChangeListener(WebsiteStateChangeListener websiteStateChangeListener) {
			websiteStateChangeListeners.add(websiteStateChangeListener);
		}

		public void removeWebsiteStateChangeListener(WebsiteStateChangeListener websiteStateChangeListener) {
			websiteStateChangeListeners.remove(websiteStateChangeListener);
		}

		public void refreshWebsites() {
			for(Website website: websites) {
				refreshWebsite(website);
			}
			/*
			refresher.refreshAll();
			*/
		}
		
		public void refreshWebsite(Website website) {
			website.refresh();
			/*
			if(refresher != null) {
				refresher.refreshWebsite(website);
			}
			*/
		}
    }
}
