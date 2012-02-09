package dk.creen.websitewatcher;

import java.sql.SQLException;
import java.util.List;

import android.app.Activity;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.j256.ormlite.android.apptools.OrmLiteBaseListActivity;

import dk.creen.websitewatcher.control.WebsiteStateChangeListener;
import dk.creen.websitewatcher.model.Website;
import dk.creen.websitewatcher.model.helpers.DatabaseHelper;

public class WWMainActivity extends OrmLiteBaseListActivity<DatabaseHelper> {
	
	public static final String LOG_TAG = "Website Watcher Activity";
	private WWService.WebsiteServiceBinder binder = null;
    private WebsiteWatcherServiceConnection connection = new WebsiteWatcherServiceConnection();
	private List<Website> websites;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
		this.getListView().setOnItemClickListener(new OnItemClickListener(){
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
		        Intent activityIntent = new Intent(WWMainActivity.this, WWSettingsActivity.class);
		        activityIntent.putExtra(WWSettingsActivity.EXTRA_WEBSITE_ID, (int)id);
		        WWMainActivity.this.startActivity(activityIntent);
			}
		});
    }
    
    @Override
	protected void onPause() {
		super.onPause();
		this.unbindService(connection);
	}

	@Override
	protected void onResume() {
		super.onResume();
		
        Intent serviceIntent = new Intent(this, WWService.class);
        this.bindService(serviceIntent, connection, Service.BIND_AUTO_CREATE);
	}
	
	public void addNewWebsite(View view) {
		try {
			Website website = new Website();
			getHelper().getWebsiteDao().create(website);
			WWService.getInstance().reloadWebsites();
	        Intent activityIntent = new Intent(WWMainActivity.this, WWSettingsActivity.class);
	        activityIntent.putExtra(WWSettingsActivity.EXTRA_WEBSITE_ID, (int)website.getId());
	        WWMainActivity.this.startActivity(activityIntent);
		} catch (SQLException e) {
			Log.e(LOG_TAG, "SQLError creating a new website.");
			e.printStackTrace();
		}
	}
	
    public void changeServiceState(View view) {
    	WWService service = WWService.getInstance();
    	if(service.getState() == WWService.States.ONLINE) {
        	service.setState(WWService.States.OFFLINE);	
    	} else {
    		service.setState(WWService.States.ONLINE);
        }
        Log.i(LOG_TAG, "changeServiceState pressed");
        updateServiceStateChangeButton();
    }
    
    public void updateServiceStateChangeButton() {
    	String text;
    	Drawable icon;
    	if(WWService.getInstance().getState() == WWService.States.ONLINE) {
    		text = getResources().getString(R.string.change_service_state_online);
        	icon = this.getResources().getDrawable(R.drawable.connection_online);
    	} else {
    		text = getResources().getString(R.string.change_service_state_offline);
        	icon = this.getResources().getDrawable(R.drawable.connection_offline);
    	}
    	Button changeStateButton = (Button)this.findViewById(R.id.changeServiceStateButton);
    	changeStateButton.setText(text);
    	icon.setBounds(0, 0, 24, 24);
    	changeStateButton.setCompoundDrawables(icon, null, null, null);
    }
	
    /*
    public void refreshWebsites(View view) {
        Log.i(LOG_TAG, "refreshWebsites pressed");
        if(binder != null) {
        	binder.refreshWebsites();
        }
    }
    */

    // Define the Handler that receives messages from the thread and update the progress
    final Handler websiteChangeHandler = new Handler() {
        public void handleMessage(Message msg) {
        	Log.d(LOG_TAG, "handleMessage was called to notify the list adapter that the data has changed.");
			((BaseAdapter)WWMainActivity.this.getListAdapter()).notifyDataSetChanged();
        }
    };
    
    private class WebsiteWatcherServiceConnection implements ServiceConnection {

		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			Log.i(LOG_TAG, "Just connected to service");
			if(service instanceof WWService.WebsiteServiceBinder) {
				binder = (WWService.WebsiteServiceBinder)service;
				Log.i(LOG_TAG, "Service = "+binder.getService().toString());
				
				websites = WWService.getWebsites();
				BaseAdapter adapter = new WebsiteAdapter(WWMainActivity.this);
				WWMainActivity.this.setListAdapter(adapter);
				
				binder.addWebsiteStateChangeListener(new WebsiteStateChangeListener() {
					@Override
					public void fire(Website website, Website.States state) {
						Log.i(LOG_TAG, "WebsiteStateChangeListener fired!");
				    	Message msg = websiteChangeHandler.obtainMessage();
				    	websiteChangeHandler.sendMessage(msg);
					}
				});
				
				updateServiceStateChangeButton();
			} else {
				binder = null;
				Log.e(LOG_TAG, "Unknown service binder.");
			}
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
			Log.i(LOG_TAG, "Just disconnected from service");
		}
    }
	
    class WebsiteAdapter extends BaseAdapter {
    	
    	LayoutInflater inflater;
    	public WebsiteAdapter(Activity activity) {
    		inflater = activity.getLayoutInflater();
    	}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			//return super.getView(position, convertView, parent);
			// Cast or create a text
			RelativeLayout view = (RelativeLayout) convertView;
			if(getItemViewType(position) == 0) {
				if(view == null) {
					view = (RelativeLayout) inflater.inflate(R.layout.website_item, null);
				}
				
				Website website = (Website)this.getItem(position);
				
				if(website.getUrl() != null) {
					((TextView)view.findViewById(R.id.websiteTextView)).setText(website.getUrl().toExternalForm());
				} else {
					((TextView)view.findViewById(R.id.websiteTextView)).setText(R.string.unknown_website_url);
				}
				Drawable stateDrawable = getWebsiteStateDrawable(website);
				((ImageView)view.findViewById(R.id.stateImageView)).setImageDrawable(stateDrawable);
			} else {
				if(view == null) {
					view = (RelativeLayout) inflater.inflate(R.layout.website_item_new, null);
				}
			}
			
			return view;
		}

		@Override
		public int getItemViewType(int position) {
			if(position < websites.size()) {
				return 0;	
			} else {
				return 1;
			}
		}

		@Override
		public int getViewTypeCount() {
			return 2;
		}

		@Override
		public int getCount() {
			return websites.size()+1;
		}

		@Override
		public Object getItem(int position) {
			if(position < websites.size()) {
				return websites.get(position);	
			}
			return null;
		}

		@Override
		public long getItemId(int position) {
			if(position < websites.size()) {
				return ((Website)getItem(position)).getId();
			}
			return -1;
		}
    	
    }

	public Drawable getWebsiteStateDrawable(Website website) {
		switch(website.getState()) {
			case UNCHANGED:
				return getResources().getDrawable(R.drawable.website_state_unchanged);
			case CHANGED:
				return getResources().getDrawable(R.drawable.website_state_changed);
			case REFRESHING:
				return getResources().getDrawable(R.drawable.website_state_refreshing);
				//return getResources().getDrawable(R.drawable.website_state_refreshing_base);
			default:
				return getResources().getDrawable(R.drawable.website_state_unknown);
		}
	}
}