import java.io.Serializable;


/**
 * @author vsaravag
 * Class to send a request to application servers
 */
@SuppressWarnings("serial")
public class Request implements Serializable{
	
	private Cloud.FrontEndOps.Request r = null;
	private long timestamp = 0;
	
	// Constructor to create request object
	public Request (Cloud.FrontEndOps.Request r, long timestamp){
		this.r = r;
		this.timestamp = timestamp;
	}
	
	// Get the request
	public Cloud.FrontEndOps.Request getRequest(){
		return r;
	}
	
	// Get request timestamp
	public long getTimestamp(){
		return timestamp;
	}
	
}
