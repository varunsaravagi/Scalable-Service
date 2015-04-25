
/**
 * @author vsaravag
 * Constants class for the project
 */
public final class Constants {
	
	public static final int vmRequired[] = {1, 1, 1, 1, 1, 1, //application servers required according to time of day 
											1, 2, 2, 2, 2, 2,
											2, 2, 2, 2, 2, 2,
											2, 2, 2, 2, 2, 2
	    									};	
	
	public static final String 	MASTERSERVICE = "master"; //name of the service to which master is bound
	public static final String BUSSERVICE = "bus"; // name of the service to which bus is bound
	public static final String CACHESERVICE = "cache"; //name of the service on which cache is running
	
	public static final int MASTERSERVER = 0; //master server
	public static final int SLAVEFESERVER = 1; //slave 
	public static final int SLAVEAPPSERVER = 2; //slave application server
	
	public static final double APPSERVERQUEUELIMIT = 3.7;//3.86; //limit of a single application server
	
	
	public static final int INITIALDROPLIMIT = 5000; // initial time for which requests are to be dropped
	
	public static final int FRONTENDMAINTENANCECYCLE = 1000; //wait between two consecutive frontend maintenance cycles
	public static final int APPSERVERMAINTENANCECYCLE = 1000; //wait between two consecutive appserver maintenance cycles
	
	public static final long BROWSETIMEOUT = 750; //timeout for a browse request
	public static final long PURCHASETIMEOUT = 1490; //timeout for a purchase request
	
	public static final int APPSERVERHIGHLOADLIMIT = 0;//limit for consecutive high load on application server
	public static final int APPSERVERLOWLOADLIMIT = 6; //limit for consecutive low load on application server
	
	public static final int FRONTENDAPPSERVERLIMIT = 4; //number of application servers per frontend
	
}
