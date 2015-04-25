/* Sample code for basic Server */
import java.net.MalformedURLException;
import java.rmi.AlreadyBoundException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingDeque;


@SuppressWarnings("serial")
public class Server extends UnicastRemoteObject implements TierInterface {
	private static boolean master = false;
	private static ServerLib SL = null;
	private static String ip, port;


	private static int role = -1;
	private static int id = 0;

	//private static BusInterface bus = null;
	private static TierInterface masterServer = null;
	private static Cloud.DatabaseOps cache = null;

	// queue to keep the requests coming from frontends
	private static LinkedBlockingDeque<Request> requestBus = 
			new LinkedBlockingDeque<Request>();
	
	// count of high application load
	private static int highASLoad = 0;

	// count of low application load
	private static int lessASLoad = 0;

	// number of application servers to start
	//private static AtomicInteger appServersToStart = new AtomicInteger(0);

	private static int appServersToStart = 0;

	// number of frontend servers to start
	//private static AtomicInteger fEServersToStart = new AtomicInteger(0);

	private static int fEServersToStart = 0;//new AtomicInteger(0);

	// application servers starting initially based on time of day
	private static int initialAppServers = 0;

	// Ids to be given to the application servers starting
	private static ConcurrentLinkedQueue<Integer> VMAppIds = 
			new ConcurrentLinkedQueue<Integer>();

	// Ids to be given to the frontend servers starting
	private static ConcurrentLinkedQueue<Integer> VMFEIds = 
			new ConcurrentLinkedQueue<Integer>();

	// the total app servers running
	private static ConcurrentLinkedDeque<Integer> activeAppServers = new ConcurrentLinkedDeque<Integer>();

	// the total front end servers running
	private static ConcurrentLinkedDeque<Integer> activeFEServers = new ConcurrentLinkedDeque<Integer>();

	private static Object lock = new Object();
	
	private static long startTime = 0;
	
	private static int requestCount = 0;
	
	private static boolean processing = true;
	
	private static  Server svr = null;
	
	protected Server() throws RemoteException {
		super();

	}

	public static void main(String args[]) throws Exception {

		startTime = System.currentTimeMillis();

		// check for argument length. Should be 2
		if (args.length != 2) {
			throw new Exception("Need 2 args: <cloud_ip> <cloud_port>");
		}
		ip = args[0];
		port = args[1];

		// create instance of ServerLib
		SL = new ServerLib(args[0], Integer.parseInt(args[1]));

		// Try binding to the master service.
		// If it is bound, master is set as true, else false.
		master = isBound();

		if (master) {
			id = 1;
			role = Constants.MASTERSERVER;

			//initialAppServers = Constants.vmRequired[(int) time];
			initialAppServers = 2;
			for(int i =0; i< initialAppServers; i++){

				int id = SL.startVM();
				VMAppIds.add(id); // add the id of the server to the list.
				appServersToStart++;
				//appServersToStart.incrementAndGet();
			}

			//Start the Cache. This would bind the cache to RMI
			new CacheDB(ip, port);


			Thread t = new Thread(){
				public void run(){
					runASMaintenanceCycle();
				}
			};

			Thread t1 = new Thread(){
				public void run(){
					runFEMaintenanceCycle();
				}
			};
			t.start();
			t1.start();



		} else {
			// The server is not a master server.


			int roleId[] = new int[2];
			// get the id and role of the server
			roleId = masterServer.getRoleId();
			id = roleId[0];
			role = roleId[1];
			svr = new Server();
			Naming.bind(String.format("//%s:%s/%s", ip, port,
					Integer.toString(id)), svr);
			cache = (Cloud.DatabaseOps)Naming.lookup(String.format(
					"//%s:%s/%s", ip, port, Constants.CACHESERVICE));
		}


		// start working

		if (master) {
			// The master server registers itself with the load balancer so it
			// can receive requests
			SL.register_frontend();

			long time = System.currentTimeMillis();
			Cloud.FrontEndOps.Request r = null;

			try{
				while((r=SL.getNextRequest())!=null){

					if(System.currentTimeMillis() - startTime < Constants.INITIALDROPLIMIT){

						SL.drop(r);
						requestCount++;

						if(System.currentTimeMillis() - time > 1000){
							int size = activeAppServers.size();
							requestCount *= 3.5;// increase request count and approximate request coming per second

							double reqServers =  Math.floor((requestCount/(float)Constants.APPSERVERQUEUELIMIT) -size);			

							// number of application servers starting
							int serversStarting = VMAppIds.size();

							if(serversStarting < reqServers){ //if less number of application servers are starting

								// start the remaining servers
								for(int i=0;i<reqServers-serversStarting;i++){					
									int vmId = SL.startVM();
									VMAppIds.add(vmId);
									appServersToStart++;
								} //end for
							}// end if

							requestCount = 0;//-= VMAppIds.size()*Constants.APPSERVERQUEUELIMIT;

							int frontEndRunning = 1 + activeFEServers.size();

							double reqFE = Math.ceil((size+VMAppIds.size())/(float)Constants.FRONTENDAPPSERVERLIMIT);

							// find the number of frontend servers starting currently
							int fEStarting = VMFEIds.size(); 

							int totalFE = frontEndRunning + fEStarting;

							if(totalFE < reqFE ){ //start the required frontend servers
								for(int i=0;i<reqFE-totalFE;i++){
									System.out.println("Starting frontend");
									int vmId = SL.startVM();
									VMFEIds.add(vmId);
									fEServersToStart++;
								}
							}

							time = System.currentTimeMillis(); //reset the timer
						}
						continue;
					}
					Request req = new Request(r, System.currentTimeMillis() + 60);
					requestBus.putLast(req);
					synchronized(lock){
						requestCount++;
					}
				}
			}
			catch (Exception e){
				System.out.println("Exception " + e.getMessage());
			}

		}

		// if it is an application server
		if (role == Constants.SLAVEAPPSERVER) {

			// get request from the internal queue and process
			Request req = null;
			try{
				while(((req=masterServer.getRequest(id))!=null) && processing){
					Cloud.FrontEndOps.Request r = null;
					r = req.getRequest();
					Long requestDur = System.currentTimeMillis()-req.getTimestamp();
					if(r.isPurchase == true && requestDur > Constants.PURCHASETIMEOUT){
						SL.drop(r);
					}			
					else if(r.isPurchase == false && requestDur > Constants.BROWSETIMEOUT){
						SL.drop(r);
					}
					
					else{
						SL.processRequest(r, cache);
					}
				}// end while
			}
			catch (Exception e){
				System.out.println("Appserver Exception " + e.getMessage());

			}
		} // end if SLAVEAPPSERVER


		// if front end server
		if (role == Constants.SLAVEFESERVER) {
			SL.register_frontend();
			try{
				while(masterServer.addRequest(new Request(SL.getNextRequest(), System.currentTimeMillis()+60),id)
						&& processing);
			}
			catch(Exception e){
				System.out.println("Exception " + e.getMessage());
			} 

		}// end if SLAVEFESERVER

	}// end main

	/**
	 * Binds a service to the RMI Registry. Helps to identify the parent server
	 * process (one which was started first)
	 * 
	 * @param args
	 *            The <ip> and <port> of the RMI Registry
	 * @return True if the service is bound, False otherwise
	 * 
	 */
	public static boolean isBound() {
		try {
			// bind the master node
			Naming.bind(String.format("//%s:%s/%s", ip, port,
					Constants.MASTERSERVICE), new Server());
			master = true;
			return true;
		} catch (Exception e) {
			try {
				masterServer = (TierInterface)Naming.lookup(String.format(
						"//%s:%s/%s", ip, port, Constants.MASTERSERVICE));
			} catch (Exception e1) {
				e1.printStackTrace();
			}
			// cannot become master. 
			return false;
		}
	}


	@Override
	/**
	 * Shutdown the server
	 * @throws RemoteException
	 */
	public void shutDown() throws RemoteException {
		System.out.println("Received the shutdown command : Id " + id + ", Role " + role);
		processing = false;
		try{
		if(role == Constants.SLAVEFESERVER){ //for frontend, interrupt the next get and unregister
			SL.interruptGetNext();
			SL.unregister_frontend();
		}
		UnicastRemoteObject.unexportObject(svr, true);
		SL.shutDown();
		System.exit(1);
		}
		catch(Exception e) {
			System.out.println("Error shutting down " + e.getMessage());
		}
	}


	@Override

	/**
	 * Add the request from frontend to the request queue
	 */
	public boolean addRequest(Request req, int id){ 

		try {
			requestBus.putLast(req);
			synchronized(lock){
				requestCount++;
			}
			return true;
		} catch (Exception e) {
			return false;
		}

	} 


	@Override
	/**
	 * Get the request from the queue and send to the application server
	 * 
	 */
	public Request getRequest(int id) throws RemoteException {

		try {
			return requestBus.takeFirst();
		} catch (Exception e) {
			System.out.println("Get request Exception: id: " + 
					id + ", " + e.getMessage());
		}

		return null;
	}


	/**
	 * Run the application server maintenance cycle
	 */
	public static void runASMaintenanceCycle(){

		try {
			Thread.sleep(5000);
		} catch (Exception e1) {
			e1.printStackTrace();
		}
		while(true){

			double totalLength = 0;

			// the total length of the request queue 
			// also add the app servers which are currently processing a request
			synchronized(lock){
				totalLength = requestCount;
			}

			int size =	activeAppServers.size() + VMAppIds.size(); // the number of application servers running/starting

			if(size == 0){
				continue;
			}

			// calculate the average load
			float averageLoad = (float) (totalLength/size);

			if(averageLoad >= Constants.APPSERVERQUEUELIMIT){
				// increase the high load counter
				highASLoad++;
				// zero the low load counter
				lessASLoad = 0;
			}
			else {
				// average load is less than the application server queue limit.								
				lessASLoad++;
				// zero the high load counter
				highASLoad = 0;
			}

			if(averageLoad > Constants.APPSERVERQUEUELIMIT && highASLoad > Constants.APPSERVERHIGHLOADLIMIT){ 
				// the average load is greater for more than the high load limit set;
				
				highASLoad = 0;
				
				// calculate the number of appservers to start				
				double reqServers =  Math.ceil((totalLength/(float)Constants.APPSERVERQUEUELIMIT) -size);			

				// find the number of servers starting currently
				int serversStarting = VMAppIds.size();

				if(serversStarting < reqServers){ //if less number of servers are starting
					System.out.println("Time: "+ (System.currentTimeMillis()-startTime) +
							", High AS Load:: Average Load: "+averageLoad 
							+", Request Count: " + totalLength
							+", Starting servers: " + (reqServers-serversStarting)
							+", Running servers: " + size);
					// start the remaining servers
					for(int i=0;i<reqServers-serversStarting;i++){					
						int vmId = SL.startVM();
						VMAppIds.add(vmId);
						appServersToStart++;
					} //end for

				}// end if
			
			} // end if averageLoad

			else if (averageLoad < Constants.APPSERVERQUEUELIMIT && lessASLoad > Constants.APPSERVERLOWLOADLIMIT){
				// the average load is less for more than the low limit set up
				lessASLoad = 0;

				int totalAppServers = activeAppServers.size();

				//minimum number of app server should always run
				if(totalAppServers <= initialAppServers){
					continue;
				}

				// the number of appservers required for this load
				double reqServers = Math.ceil(((averageLoad * totalAppServers)/(float)Constants.APPSERVERQUEUELIMIT));															

				// the number of appservers to shutdown
				double shutDownNumber = totalAppServers - reqServers;

				// the number of appservers which would remain after the shutdown
				double remaining = totalAppServers - shutDownNumber;

				// the remaining appservers should be greater than or equal to the initial start number
				// readjust the shutdown number
				if(remaining < 1){
					continue;//shutDownNumber = shutDownNumber - (initialAppServers - remaining);
				}

				System.out.println("Time: "+ (System.currentTimeMillis()-startTime) + 
						", Low AS Load: ReqServers: " + reqServers + ", Shutdown Servers: " + shutDownNumber);
				
				for(int i = 0; i<shutDownNumber; i++){
					//send shutdown command here
					int appid = activeAppServers.pollLast();					
					try{
						TierInterface appServer = (TierInterface) Naming.lookup(String.format(
								"//%s:%s/%s", ip, port, Integer.toString(appid)));
						System.out.println("Sending shutdown command to Application Server : "+ appid);
						appServer.shutDown();
					} catch (Exception e) {
						
					}
				}
			}
			
			synchronized(lock){
				requestCount = 0;
			}
			

			try {
				Thread.sleep(Constants.APPSERVERMAINTENANCECYCLE);
			} catch (Exception e) {
				e.printStackTrace();
			}

		}

	}

	public static void runFEMaintenanceCycle(){
		try {
			Thread.sleep(5000);
		} catch (Exception e1) {
			e1.printStackTrace();
		}
		while(true){
			// number of application servers running
			int appServersRunning = activeAppServers.size();

			// number of application servers starting
			int appServersStarting = VMAppIds.size();

			// number of frontend servers running (master is always running as frontend)
			int frontEndRunning = 1 + activeFEServers.size();

			// required number of frontends
			double reqFE = Math.ceil((appServersRunning+appServersStarting)/(float)Constants.FRONTENDAPPSERVERLIMIT);

			// number of frontends starting currently
			int fEStarting = VMFEIds.size();

			int totalFE = frontEndRunning + fEStarting;

			if(totalFE < reqFE ){
				// start remaining frontends
				for(int i=0;i<reqFE-totalFE;i++){
					System.out.println("Starting frontend");
					int vmId = SL.startVM();
					VMFEIds.add(vmId);
					fEServersToStart++;
				}
				
			}
			else {
				// shutdown extra frontends
				double extra = totalFE-reqFE;
				for(int i=0; i < extra; i++){

					// get the id of the server to shutdown
					int fEId = 0;
					TierInterface fEServer;
					try {
						fEId = activeFEServers.pollLast();
						// lookup the server on RMI registry
						fEServer = (TierInterface) Naming.lookup(String.format(
								"//%s:%s/%s", ip, port, Integer.toString(fEId)));

						// send the shutdown command
						fEServer.shutDown();
						System.out.println("Sending shutdown command to Frontend Server : "+ fEId);

					} catch (Exception e) {
 
					}

				} // end for
			} // end else
			try {
				Thread.sleep(Constants.FRONTENDMAINTENANCECYCLE);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Allot role and Id to the server
	 */
	public synchronized int[] getRoleId() {

		int[] roleId = new int[2];
		// start all the application servers first. Then start the front end servers
		if (appServersToStart > 0) {

			// an application server needs to be created. Decrease the number by 1.
			appServersToStart--;

			roleId[0] = VMAppIds.remove();// get the id of the VM from the queue
			activeAppServers.add(roleId[0]); //put the id and initial length of the appserver in the map
			roleId[1] = Constants.SLAVEAPPSERVER;
			return roleId;
		}

		if (fEServersToStart > 0) {
			// a front end server needs to be created. Decrease the required number by 1.
			fEServersToStart--;

			roleId[0] = VMFEIds.remove(); // get the id of the VM from the queue
			activeFEServers.add(roleId[0]);
			roleId[1] = Constants.SLAVEFESERVER;
			return roleId;
		}

		// if nothing is to be started, return -1 as role id.
		roleId[0] = -1;
		return roleId;
	}//end method


}
