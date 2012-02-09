package dk.creen.websitewatcher.control;

import dk.creen.websitewatcher.model.Website;
import dk.creen.websitewatcher.model.Website.States;

public abstract interface WebsiteStateChangeListener {

	public void fire(Website website, States state);
	
}
