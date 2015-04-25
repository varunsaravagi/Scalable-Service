import java.rmi.Remote;
import java.rmi.RemoteException;


/**
 * @author vsaravag
 * Interface for interaction between the bus and the tiers
 */

public interface TierInterface extends Remote{
	
	/**
	 * Sends the shutdown command to a server
	 * @throws RemoteException
	 */
	public void shutDown() throws RemoteException;
	
	/**
	 * Add request from frontend (id) to request queue
	 */
	public boolean addRequest(Request r, int id) throws RemoteException;
	
	/**
	 * Get request from queue and send to application server (id)
	 */
	public Request getRequest(int id) throws RemoteException;
	
	/**
	 * Allot role and id to a server
	 * 
	 */
	public int[] getRoleId() throws RemoteException;
}
