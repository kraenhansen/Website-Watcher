package dk.creen.websitewatcher;

import java.net.MalformedURLException;
import java.net.URL;
import java.sql.SQLException;

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.j256.ormlite.android.apptools.OrmLiteBaseActivity;

import dk.creen.websitewatcher.control.WebsiteStateChangeListener;
import dk.creen.websitewatcher.model.Website;
import dk.creen.websitewatcher.model.Website.States;
import dk.creen.websitewatcher.model.helpers.DatabaseHelper;

public class WWSettingsActivity extends OrmLiteBaseActivity<DatabaseHelper> {
	public static final String LOG_TAG = "Website Settings Activity";
	public static final String EXTRA_WEBSITE_ID = "website_id";
    private WWServiceConnection connection = new WWServiceConnection();
	private WWService.WebsiteServiceBinder binder = null;
    private Website website;
    private boolean deletingExit = false;
    
    private EditText UrlValue;
    private Spinner delayValue;
    private TextView lastRefreshValue;
    private Button statusButton;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.website_settings);
        
		UrlValue = (EditText)this.findViewById(R.id.url_value);
		delayValue = (Spinner)this.findViewById(R.id.delay_value);
		lastRefreshValue = (TextView)this.findViewById(R.id.last_refresh_value);
		statusButton = (Button)this.findViewById(R.id.status_button);
    }
    
    @Override
	protected void onPause() {
		super.onPause();
		this.unbindService(connection);

		if(!deletingExit) {
			this.saveData();
		}
	}

	@Override
	protected void onResume() {
		super.onResume();

        Intent serviceIntent = new Intent(this, WWService.class);
		this.startService(serviceIntent);
        this.bindService(serviceIntent, connection, Service.BIND_AUTO_CREATE);
	}
	
	public void visit(View view) {
		if(website != null && website.getUrl() != null) {
			Intent i = new Intent(Intent.ACTION_VIEW);
			i.setData(Uri.parse(website.getUrl().toExternalForm()));
			startActivity(i);
		}
	}
	
	public void delete(View view) {
		try {
			getHelper().getWebsiteDao().delete(website);
			deletingExit = true;
			this.finish();
			WWService.getInstance().reloadWebsites();
		} catch (SQLException e) {
			Log.e(LOG_TAG, "Failed to delete website from database");
			e.printStackTrace();
		}
	}
	
	public void statusButtonPressed(View view) {
		switch(website.getState()) {
			case UNCHANGED:
				binder.refreshWebsite(website);
				break;
			case CHANGED:
				binder.refreshWebsite(website);
				break;
			case UNKNOWN:
				binder.refreshWebsite(website);
				break;
			default:
				// Do nothing ...
				break;
		}
	}
	
	private void updatedState() {
		lastRefreshValue.setText(website.getLastRefreshedAt().toLocaleString());
		Drawable statusButtonImg;
		String statusButtonText;
		switch(website.getState()) {
			case UNCHANGED:
				statusButtonImg = getResources().getDrawable(R.drawable.website_state_unchanged);
				statusButtonText = getResources().getString(R.string.status_button_unchanged);
				break;
			case CHANGED:
				statusButtonImg = getResources().getDrawable(R.drawable.website_state_changed);
				statusButtonText = getResources().getString(R.string.status_button_changed);
				break;
			case REFRESHING:
				statusButtonImg = getResources().getDrawable(R.drawable.website_state_refreshing_base);
				statusButtonText = getResources().getString(R.string.status_button_refreshing);
				break;
			default:
				statusButtonImg = getResources().getDrawable(R.drawable.website_state_unknown);
				statusButtonText = getResources().getString(R.string.status_button_unknown);
				break;
		}
		statusButtonImg.setBounds(0, 0, 32, 32);
		statusButton.setCompoundDrawables(statusButtonImg, null, null, null);
		statusButton.setText(statusButtonText);
	}
	
	private void loadData() {
		if(website.getUrl() != null) {
			UrlValue.setText(website.getUrl().toExternalForm());
		}
		int[] delay_values = this.getResources().getIntArray(R.array.delay_values);
		int delay_option = delay_values.length-1; // Assume max.
		for(int i = 0; i <= delay_values.length; i++) {
			if(delay_values[i] >= website.getRefreshDelay()) {
				delay_option = i;
				break;
			}
		}
		delayValue.setSelection(delay_option);
		updatedState();
	}
	
	private void saveData() {
		// TODO: Find a way to only do this if it is not deleted.
		try {
			website.setUrl(new URL(UrlValue.getText().toString()));
			int[] delay_values = this.getResources().getIntArray(R.array.delay_values);
			website.setRefreshDelay(delay_values[delayValue.getSelectedItemPosition()]);
			getHelper().getWebsiteDao().update(website);
			
		} catch (SQLException e) {
			Log.e(LOG_TAG, "SQLError when updating the website to the database.");
			e.printStackTrace();
		} catch (MalformedURLException e) {
			Toast.makeText(this, R.string.error_malformed_url, Toast.LENGTH_LONG).show();
		}
	}
    
    // Define the Handler that receives messages from the thread and update the progress
    final Handler websiteChangeHandler = new Handler() {
        public void handleMessage(Message msg) {
        	updatedState();
        }
    };

	private class WWServiceConnection implements ServiceConnection {

		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			Log.i(LOG_TAG, "Just connected to service");
			binder = (WWService.WebsiteServiceBinder)service;

	        int website_id = WWSettingsActivity.this.getIntent().getIntExtra(EXTRA_WEBSITE_ID, -1);
			for(Website website: WWService.getWebsites()) {
				if(website.getId() == website_id) {
					WWSettingsActivity.this.website =  website;
				}
			}
			if(website != null) {
				loadData();
	        	website.addWebsiteStateChangeListener(new WebsiteStateChangeListener() {
					@Override
					public void fire(Website website, States state) {
						if(WWSettingsActivity.this.website == website) {
							websiteChangeHandler.sendMessage(websiteChangeHandler.obtainMessage());
						}
					}
				});
			} else {
				Log.e(LOG_TAG, "Unable to fetch website with id = "+website_id+" from database");
			}
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
			Log.i(LOG_TAG, "Just disconnected from service");
		}
    }
	
	
}
