import java.net.MalformedURLException;
import java.rmi.AlreadyBoundException;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author vsaravag
 * Cache to handle DB operations
 */

@SuppressWarnings("serial")
public class CacheDB extends UnicastRemoteObject implements Cloud.DatabaseOps{

	// in-memory DB hashmap
	private ConcurrentHashMap<String, String> DB = new ConcurrentHashMap<String, String>();

	private static ServerLib SL = null;

	// instance of original DB
	private static Cloud.DatabaseOps origDB = null;

	// lock object to maintain synchronization
	private static Object lock = new Object();

	/**
	 * Constructor to bind the cache to RMI
	 * and get the original DB
	 */
	public CacheDB(String ip, String port) throws RemoteException {
		System.out.println("Starting Cache");

		try {
			Naming.bind(String.format("//%s:%s/%s", ip, port,
					Constants.CACHESERVICE), this);
		} catch (MalformedURLException | AlreadyBoundException e) {
			e.printStackTrace();
		}
		SL = new ServerLib(ip, Integer.parseInt(port));
		origDB = SL.getDB();
	}


	@Override
	/**
	 * Get method. Return if found in cache, else pass through to DB
	 */
	public String get(String requestId) throws RemoteException {
		String str = null;
		if((str = (String)this.DB.get(requestId.trim()))!=null){
			return str;
		}
		else {	
			String result = origDB.get(requestId);
				this.DB.put(requestId.trim(), result);
				return result;
				

		}
	}

	@Override
	/**
	 * Set method. Pass through to the DB
	 */
	public boolean set(String arg0, String arg1, String arg2)
			throws RemoteException {

		return origDB.set(arg0, arg1, arg2);
	}

	@Override
	/**
	 * Transaction method. Pass through to the DB
	 */
	public boolean transaction(String request, float price, int qty)
			throws RemoteException {
		String str1 = request.trim();

		if(origDB.transaction(request, price, qty)){
			int origQty = Integer.parseInt((String)this.DB.get(str1 + "_qty"));
			int newQty = origQty - qty;

			this.DB.put(str1 + "_qty", "" + newQty);
			this.DB.put(str1 + "_price","" + price);
			return true;
		}
		return false;
	}

}
