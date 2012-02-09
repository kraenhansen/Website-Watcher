package dk.creen.websitewatcher.model;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Date;

import android.util.Log;

import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import dk.creen.websitewatcher.control.WebsiteStateChangeListener;

@DatabaseTable(tableName="websites")
public class Website {
	
	public enum States {
		UNKNOWN,
		UNCHANGED,
		REFRESHING,
		CHANGED
	};
	
	// Constants
	public static String LOG_TAG = "Website Watcher Website";
	public static int TIMEOUT = 10000;
	public static String USERAGENT = URLConnection.getDefaultRequestProperty("User-agent");
	
	// Persistant variables
	@DatabaseField(generatedId=true, columnName="_id")
	private int id;
	@DatabaseField
	private int hash;
	@DatabaseField(canBeNull=true, dataType=DataType.SERIALIZABLE)
	private URL url;
	@DatabaseField
	private Date lastRefreshedAt = new Date();
	@DatabaseField
	private long refreshDelay = 10;
	
	private States state = States.UNKNOWN;
	private ArrayList<WebsiteStateChangeListener> websiteStateChangeListeners = new ArrayList<WebsiteStateChangeListener>();
	
	public Website() {
		
	}
	
	public Website(URL url) {
		this();
		this.url = url;
	}
	
	public Website(String urlString) throws MalformedURLException {
		this(new URL(urlString));
	}

	@Override
	public String toString() {
		if(url == null) {
			return "?";
		} else {
			return url.toExternalForm();
		}
	}
	
	@Override
	public boolean equals(Object o) {
		if(o instanceof Website) {
			return id == ((Website)o).getId();
		}
		return false;
	}

	public int getId() {
		return id;
	}
	public void setId(int id) {
		this.id = id;
	}
	public URL getUrl() {
		return url;
	}
	
	public void setUrl(URL url) {
		this.url = url;
	}
	
	public States getState() {
		return state;
	}
	public void setState(States state) {
		if(this.state != state) {
			this.state = state;
			fireWebsiteStateChange(this.state);
		}
	}

	public void addWebsiteStateChangeListener(WebsiteStateChangeListener websiteStateChangeListener) {
		websiteStateChangeListeners.add(websiteStateChangeListener);
	}

	public void removeWebsiteStateChangeListener(WebsiteStateChangeListener websiteStateChangeListener) {
		websiteStateChangeListeners.remove(websiteStateChangeListener);
	}
    
    private void fireWebsiteStateChange(Website.States state) {
    	for(WebsiteStateChangeListener listener: websiteStateChangeListeners) {
    		listener.fire(this, state);
    	}
    }
    
	public void refresh() {
		Log.v(LOG_TAG, "Website::refresh called!");
		States prevState = getState();
		setState(States.REFRESHING);

		int prevHash = this.hash;
		int newHash = 0;
		boolean success = false;
		
		try {
			if(url == null) {
				success = false;
			} else {
				String sourceCode = "";
				
				URLConnection conn = url.openConnection();
				conn.setConnectTimeout(TIMEOUT);
				conn.setReadTimeout(TIMEOUT);
				//conn.setRequestProperty("User-agent", USERAGENT);
				InputStream is = conn.getInputStream();
				
				BufferedReader reader = new BufferedReader(new InputStreamReader(is));
				String inputLine;
				while((inputLine = reader.readLine()) != null) {
					sourceCode += inputLine;
				}
				reader.close();
				is.close();
				
				newHash = sourceCode.hashCode();
				success = true;
				
				lastRefreshedAt = new Date();
			}
		} catch (IOException e) {
			Log.e(LOG_TAG, "Problems reading the website: "+url.toExternalForm());
			e.printStackTrace();
		}
		
		Log.i(LOG_TAG, "Website "+this.toString()+" fetched (hash = "+newHash+")");
		this.hash = newHash;
		
		if(success == false) {
			// Connection errors.
			setState(States.UNKNOWN);
		} else if(prevState == States.UNKNOWN || prevHash == newHash) {
			setState(States.UNCHANGED);
		} else {
			setState(States.CHANGED);
		}
	}
	public Date getLastRefreshedAt() {
		return lastRefreshedAt;
	}
	public long getRefreshDelay() {
		return refreshDelay;
	}
	public void setRefreshDelay(long refreshDelay) {
		this.refreshDelay = refreshDelay;
	}

	public boolean isValid() {
		return this.getUrl() != null && this.refreshDelay >= 0;
	}
}
