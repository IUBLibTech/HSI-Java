package edu.indiana.dlib.hsi;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;

public class HsiFactory {
	private String username;
	private String keytab;
	private String initDir;
	private String hsiBinary;
	private Logger log;
	private String localAddress = null;
	
	
	/**
	 * Create a new HsiFactory instance
	 * @param username - hsi username (if null, the current user is used)
	 * @param initDir - root directory on hpss to use as a base (if null, '.' is used)
	 * @param keytab - user's keytab (if null, will search for keytab using findKeytab())
	 * @param hsiBinary - the hsi binary itself (if null, it will search the path)
	 * @param log - logging object (if null, a new logger will be created)
	 * @param localAddress - On multi-homed machines, this will be the IP that HSI will use for opening callback ports
	 * @throws FileNotFoundException
	 */
	public HsiFactory(String username, String initDir, String keytab, String hsiBinary, Logger log, String localAddress) throws FileNotFoundException {
		if(username == null) username = System.getProperty("user.name");
		if(initDir == null) initDir = ".";
		if(keytab == null) keytab = findKeytab(username);
		if(keytab == null) throw new FileNotFoundException("Cannot find keytab");		
		if(hsiBinary == null) hsiBinary = findHsiBinary();
		if(hsiBinary == null) throw new FileNotFoundException("Cannot find HSI binary"); 
		if(log == null) log = Logger.getLogger(this.getClass().getName());
		
		this.username = username;
		this.initDir = initDir;
		this.keytab = keytab;
		this.hsiBinary = hsiBinary;
		this.log = log;		
		this.localAddress = localAddress;
	}
	
	/**
	 * Creates a new HsiFactory, using the default for {@code localAddress}
	 * 
	 * @see Hsi#Hsi(String,String,String,String,Logger,String)
	 */
	public HsiFactory(String username, String initDir, String keytab, String hsiBinary, Logger log) throws FileNotFoundException {
		this(username, initDir, keytab, hsiBinary, log, null);
	}
	

	/**
	 * Creates a new HsiFactory, using the default for {@code localAddress} and {@code log}
	 * 
	 * @see Hsi#Hsi(String,String,String,String,Logger,String)
	 */
	public HsiFactory(String username, String initDir, String keytab, String hsiBinary) throws FileNotFoundException {
		this(username, initDir, keytab, hsiBinary, null, null);
	}
	
	/**
	 * Creates a new HsiFactory, using the default for {@code localAddress}, {@code log}, and {@code hsiBinary}
	 * 
	 * @see Hsi#Hsi(String,String,String,String,Logger,String)
	 */
	public HsiFactory(String username, String initDir, String keytab) throws FileNotFoundException {
		this(username, initDir, keytab, null, null, null);
	}
	
	/**
	 * Creates a new HsiFactory, using the default for {@code localAddress}, {@code log}, {@code hsiBinary}, and {@code keytab}
	 * 
	 * @see Hsi#Hsi(String,String,String,String,Logger,String)
	 */
	public HsiFactory(String username, String initDir) throws FileNotFoundException {
		this(username, initDir, null, null, null, null);
	}
	
	/**
	 * Creates a new HsiFactory, using the default for {@code localAddress}, {@code log}, {@code hsiBinary}, {@code keytab}, 
	 * and {@code initDir}
	 * 
	 * @see Hsi#Hsi(String,String,String,String,Logger,String)
	 */
	public HsiFactory(String username) throws FileNotFoundException {
		this(username, null, null, null, null, null);
	}

	/**
	 * Creates a new HsiFactory, using the defaults for everything.
	 * 
	 * @see Hsi#Hsi(String,String,String,String,Logger,String)
	 */
	public HsiFactory() throws FileNotFoundException {
		this(null, null, null, null, null, null);
	}

	
	/**
	 * Look for an hsi keytab.  It should be ~/.hsi.keytab
	 * @param username - username for a home directory search
	 * @return the path to the keytab, or null if not found.
	 */
	static String findKeytab(String username) {
		Path p = Paths.get(System.getProperty("user.home"), ".hsi.keytab");
		if(Files.exists(p)) {
			return p.toString();
		}
		p = Paths.get("/home/" + username, ".hsi.keytab");
		if(Files.exists(p)) {
			return p.toString();
		}		
		return null;		
	}
	
	/**
	 * Search for an hsi binary along the default path
	 * @return the path to the hsi binary, or null if it isn't found
	 */
	static String findHsiBinary() {
		return(findHsiBinary(System.getenv("PATH")));
	}
	
	/**
	 * Search for an hsi binary along a given path
	 * @param path - a unix path to search
	 * @return the path for hsi or null if it isn't found
	 */
	static String findHsiBinary(String path) {
		for(String d: path.split(":")) {
			Path p = Paths.get(d, "hsi");
			if(Files.exists(p) && Files.isExecutable(p)) {
				return p.toString();
			}
		}
		return null;
	}

	
	/**
	 * Create a new HSI instance based on the factory parameters
	 * @return Hsi instance
	 * @throws HsiException
	 */
	public Hsi getHsi() throws HsiException {
		return new Hsi(username, initDir, keytab, hsiBinary, log, localAddress);
	}
	
	/**
	 * A lightweight ping of Hsi, used to check if the server is up
	 * @return true when the Hsi service is responding, false otherwise
	 * @throws HsiException
	 */
	public boolean ping() throws HsiException {
		ProcessBuilder pb = new ProcessBuilder(hsiBinary, 
				"-P", // pipe access 
				"-A", "keytab", // auth type
				"-k", this.keytab, // the keytab itself
				"-l", this.username, // the username
				"pwd");
		if(localAddress != null) pb.environment().put("HPSS_HOSTNAME", localAddress);
		try {
			Process proc = pb.start();
			int rc = proc.waitFor();
			if(rc == 0) {
				return true;
			} else {
				return false;				
			}
		} catch(InterruptedException e) {
			throw new HsiException("Ping interrupted");
		} catch(IOException e) {
			throw new HsiException("Cannot ping HPSS");
		}			
	}
	
	
	
}
