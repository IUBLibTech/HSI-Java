package edu.indiana.dlib.hsi;


import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A wrapper around the HSI binary to provide access to HPSS
 * @author bdwheele
 *
 */
public class Hsi implements AutoCloseable {		
	private String username;
	private String keytab;
	private String initDir;
	private String hsiBinary;
	private Logger log;
	private int defaultCOS = -1;
	private String localAddress = null;
	private String hsiCwd = null;
	private String hpssVersion = null;
	private Process hsiProcess = null;
	
	// System flags
	/**  No flags specified */
	public static final int NO_FLAGS = 0x00;  		
	/** Use the modification time rather than the last write time */
	public static final int STAT_USE_MTIME = 0x01; 
	/** Include the extended tape information */
	public static final int STAT_TAPEINFO = 0x02; 
	/** Recursive directory */
	public static final int DIR_RECURSIVE = 0x04;
	/** Set the name to the HPSS absolute path */
	public static final int DIR_ABS_PATH = 0x08;
	/** Set the name to a path relative to HSI's cwd */
	public static final int DIR_REL_PATH = 0x10;
	/** Chmod will only operate on files */
	public static final int CHMOD_FILES_ONLY = 0x20;
	/** Chmod will only operate on directories */
	public static final int CHMOD_DIRS_ONLY = 0x40; 
	/** Recursively chmod */
	public static final int CHMOD_RECURSIVE = DIR_RECURSIVE; 
	/** Recursively migrate */
	public static final int MIGRATE_RECURSIVE = DIR_RECURSIVE; 
	/** Force migration, regardless of storage properties */
	public static final int MIGRATE_FORCE = 0x80; 
	/** Purge the file after migration is complete */
	public static final int MIGRATE_PURGE = 0x100; 
	
	
	/**
	 * Create a new Hsi instance
	 * @param username - hsi username 
	 * @param initDir - root directory on hpss to use as a base (if null, '.' is used)
	 * @param keytab - user's keytab 
	 * @param hsiBinary - the hsi binary itself 
	 * @param log - logging object 
	 * @param localAddress - On multi-homed machines, this will be the IP that HSI will use for opening callback ports
	 * @throws HsiException
	 */
	public Hsi(String username, String initDir, String keytab, String hsiBinary, Logger log, String localAddress) throws HsiException {
		// TODO
		if(username == null) username = System.getProperty("user.name");
		if(initDir == null) initDir = ".";				
		if(log == null) log = Logger.getLogger(this.getClass().getName());		
		this.username = username;
		this.initDir = initDir;
		this.keytab = keytab;
		this.hsiBinary = hsiBinary;
		this.log = log;		
		this.localAddress = localAddress;
		// Let's fire up a connection to do something innocuous, to make sure it works.
		runHsiCommand("lpwd");		
	}
	
	
	@Override
	public void close() {
		if(hsiProcess.isAlive()) {
			hsiProcess.destroyForcibly();			
		}
		hsiProcess = null;
	}
	

	/**
	 * Get the logging object
	 * @return the log associated with this instance
	 */
	public Logger getLog() {
		return log;
	}

	/**
	 * Set the logging object
	 * @param log the log to set
	 */
	public void setLog(Logger log) {
		this.log = log;
	}

	/**
	 * Get the default class of service used for new files
	 * @return the defaultCOS
	 */
	public int getDefaultCOS() {
		return defaultCOS;
	}

	/**
	 * Set the default Class of Service for new files.
	 * @param defaultCOS the defaultCOS to set
	 */
	public void setDefaultCOS(int defaultCOS) {
		this.defaultCOS = defaultCOS;
	}

	/**
	 * Get the hsi callback address, if it was specified.
	 * @return the localAddress
	 */
	public String getLocalAddress() {
		return localAddress;
	}

	/**
	 * Get the username for this instance
	 * @return the username
	 */
	public String getUsername() {
		return username;
	}

	/**
	 * Get the keytab used for this instance
	 * @return the keytab used
	 */
	public String getKeytab() {
		return keytab;
	}

	/**
	 * Get the initial directory used for this instance 
	 * @return the initDir on hpss
	 */
	public String getInitDir() {
		return initDir;
	}

	/**
	 * Get the path of hsi binary used for this instance
	 * @return the hsi binary path
	 */
	public String getHsiBinary() {
		return hsiBinary;
	}
	
	/**
	 * Get the version of HPSS we're connected to.
	 * @return a value like "H743.0.2"
	 */
	public String getHPSSVersion() {
		return hpssVersion;
	}
	
	

	/**
	 * Run an HSI command.  If this thread doesn't have a corresponding HSI process
	 * (or if it has died), a new one will be created.  Unlike the perl implementation,
	 * It will NOT continue to retry until a successful connection -- it will throw an
	 * exception.
	 * @param args - the hsi command line
	 * @return The output of the command
	 * @throws HsiException 
	 */
	public List<String> runHsiCommand(String cmd) throws HsiException {
		if(hsiProcess == null) {
			ProcessBuilder pb = new ProcessBuilder(hsiBinary, 
					"-P", // pipe access 
					"-A", "keytab", // auth type
					"-k", this.keytab, // the keytab itself
					"-l", this.username); // the username
			if(localAddress != null) pb.environment().put("HPSS_HOSTNAME", localAddress);
			try {
				hsiProcess = pb.start();				
			} catch(IOException e) {
				throw new HsiException("Cannot start hsi" + e.getMessage());
			}			

			List<String> intro = _executeCommand(hsiProcess, "pwd;lscon;lpwd;glob;idletime -1" + (defaultCOS != -1? ";cos=" + defaultCOS : ""));
			for(String l: intro) {
				if(l.startsWith("pwd0:")) {
					hsiCwd = l.substring(6);
					log.finer("Setting cwd to " + hsiCwd);
				}
				if(l.startsWith("->")) {
					String[] parts = l.split("\\s+");
					hpssVersion = parts[4];
					log.finer("Setting hpssVersion to " + hpssVersion);
				}
			}							
		}
		
		List<String> result = _executeCommand(hsiProcess, cmd);
		return result;
	}
	
	/**
	 * Run an HSI command.  
	 * @see Hsi#runHsiCommand(String)
	 */
	public List<String> runHsiCommand(String... args) throws HsiException {
		return runHsiCommand(String.join(" ", args));
	}
	
	/**
	 * Actually issue a command and capture the output -- until we see the sentinel.
	 * @param proc - the HSI process to use
	 * @param cmd - the command to run on the command line
	 * @return - a list of lines which contains our data.
	 * @throws HsiException
	 */
	private List<String> _executeCommand(Process proc, String cmd) throws HsiException {		
		ArrayList<String> result = new ArrayList<>();
		BufferedReader stdout = new BufferedReader(new InputStreamReader(proc.getInputStream()));
		PrintWriter stdin = new PrintWriter(proc.getOutputStream());
		
		log.fine("running command: " + cmd + ";id");
		stdin.println(cmd + ";id");
		stdin.flush();
		String sentinel = "^uid=\\d+\\(.+";		
		while(proc.isAlive()) {
			try {
				if(stdout.ready()) {
					String line = stdout.readLine();
					log.finest("Got line: [" + line + "]");
					if(line.matches("^\\*\\*\\*.+")) {
						if(line.matches(".+getFile: no valid checksum for.*") ||
							line.matches(".+no data at heirarchy level.*") ||
							line.matches(".+ls:.+HPSS_NOENT.*") ||
							line.matches(".+Background stage failed with error -5.*") ||
							line.matches(".+setting nameserver attributes.+HPSS_EACCES.*") ||
							line.matches(".+stage: No such file or directory.*")) {
							// this is a benign error, so we can ignore it.
						} else {
							throw new HsiException(line);
						}
					} else if(line.matches(sentinel)) {
						return result;
					} else {
						result.add(line);
					}
				} else {
					Thread.sleep(100);
				}
			} catch(IOException e) {
				throw new HsiException(e);
			} catch(InterruptedException e) {
				/// do nothing.
			}
		}
		throw new HsiException("HSI process died.  rc: " + proc.exitValue());
	}


	

	
	/**
	 * Clean up a path by removing duplicate slashes, '.' entries, and interpreting the .. entries.
	 * @param path - a path to sanitize
	 * @return A sanitized path leading with '/'
	 */
	public static String cleanPath(String path) {
		if(path == null) {
			return "/";
		}
		List<String> stack = new ArrayList<>();
		for(String d: path.split("/")) {
			if(d.equals("") || d.equals(".")) {
				// do nothing.
			} else if(d.equals("..")) {
				// pop from the stack
				if(!stack.isEmpty()) stack.remove(stack.size() - 1);
			} else {
				// push onto the stack
				stack.add(d);
			}
		}
		return "/" + String.join("/", stack);
	}
	
	/**
	 * Parse the output of an HSI ls.  It contains more information than a regular LS 
	 * (tape and cos, for example) so it's a little tricky.  Since the format can be very different
	 * between -X and not using the flag, we have to handle both.  One thing that IS required is using the
	 * -D option which gives us a full date and time, rather than a rolling 6 month window.
	 * 
	 * @param hasStorage - should be true if the -X or -V option was specified with the ls command
	 * @param lines - the output of an hsi ls command
	 * @return A list of {@link Stat} objects for the entries in the ls
	 */
	private static List<Stat> parseLsOutput(boolean hasStorage, List<String> lines) {
		List<Stat> result = new ArrayList<>();
		String parent = null;
		while(lines.size() > 0) {
			String line = lines.remove(0);
			if(line.equals("")) continue; // skip blank line
			if(line.matches("^\\S.*:$")) {
				// this is a parent directory marker.
				parent = line.substring(0, line.length() - 1);
				continue;
			}
			if(line.startsWith("***")) {
				// this is an error -- just abort here
				break;
			}
			int p = hasStorage? (line.startsWith("d")? 12 : 14) : 11; // parts to split into
			int t = hasStorage? (line.startsWith("d")? 5 : 7) : 4; // position of size field
			Stat s = new Stat();
			String[] parts = line.split("\\s+", p);
			s.type = Stat.modeStringToType(parts[0]);
			s.mode = Stat.modeStringToInt(parts[0]);
			s.nlink = Integer.parseInt(parts[1]);
			s.owner = parts[2];
			s.group = parts[3];
			s.cos = (!hasStorage || line.startsWith("d"))? -1 : Integer.parseInt(parts[4]);
			s.size = Long.parseLong(parts[t]);							
			s.time = Stat.textDateToTime(parts[t + 3] + " " + parts[t + 2] + " " + parts[t + 5] + " " + parts[t + 4]);
			s.name = parts[t + 6];
			s.parent = parent;
			result.add(s);

			// check for any storage information
			if(lines.size() > 0 && lines.get(0).matches("^Storage\\s+VV.+")) {
				s.extended = true;
				lines.remove(0);  // Storage VV    Stripe
				lines.remove(0);  // Level   Count Width
				lines.remove(0);  // -------------------------
				line = lines.remove(0);
				while(!line.equals("")) {
					Stat.StorageLevel sl = new Stat.StorageLevel();
					line = line.replaceFirst("\\s+", "");  // remove leading space					
					String[] sParts = line.split("\\s+");
					sl.level = Integer.parseInt(sParts[0]);
					sl.type = Stat.StorageLevel.textToStorageType(sParts[1]);
					sl.bytes = sParts.length != 5? 0 : Long.parseLong(sParts[4]);
					sl.isComplete = sl.bytes == s.size;
					s.levels.add(sl);
					line = lines.remove(0);
					if(line.contains("Object ID:")) {
						line = lines.remove(0);
						if(line.contains("ServerDep:")) {
							line = lines.remove(0);
							if(line.contains("Pos:")) {
								line = line.replaceFirst("\\s+", "");
								String[] vParts = line.split("\\s+");
								sl.volume = vParts[4];
								if(vParts[1].contains("+")) {
									String[] xParts = vParts[1].split("\\+");
									sl.section = Integer.parseInt(xParts[0]);
									sl.offset = Long.parseLong(xParts[1]);
								} else {
									sl.section = Integer.parseInt(vParts[1]);
									sl.offset = 0;
								}
								
								line = lines.remove(0);
							}
						}
					}
				}
					
			}
		}
		return result;
	}
	
	/**
	 * Convert an Hsi path to an HPSS absolute path
	 * @param path
	 * @return A fully-qualified path
	 */
	public String absPath(String path) {
		if(hsiCwd == null) {
			try {
				runHsiCommand("lpwd");
			} catch(Exception e) {}
		}
		if(path.startsWith("/hpss/")) {
			return path;
		} else if(path.startsWith(initDir)) {
			return hsiCwd + "/" + path;
		} else {
			return hsiCwd + "/" + initDir + cleanPath(path);
		}
	}
	
	/** 
	 * Convert a path to relative.  Hopefully.
	 * @param path - the path to convert
	 * @return hopefully an Hsi relative path.
	 */
	public String relPath(String path) {
		if(hsiCwd == null) {
			try {
				runHsiCommand("lpwd");
			} catch(Exception e) {}
		}
		if(path.startsWith("/hpss/")) {
			path = path.substring(hsiCwd.length() + 1);
		}
		if(path.startsWith("/")) {
			path = path.substring(1);
		}
		if(path.startsWith(initDir)) {
			path = path.substring(initDir.length() + 1);
		}
		return path;
	}
	
	///////////////////////////////////////////////////////////////////////////
	// User Calls here
	///////////////////////////////////////////////////////////////////////////
	
	/**
	 * stat() a file or directory to get information
	 * @param path - the path to stat
	 * @param statFlags - flags in the STAT_* family
	 * @return A {@link Stat} object which optionally will contain tape information
	 * @throws HsiException
	 */
	public Stat stat(String path, int statFlags) throws HsiException {
		List<String> lines = runHsiCommand("ls", "-aldD",
				(statFlags & STAT_USE_MTIME) != 0? "-Tm" : "",
				(statFlags & STAT_TAPEINFO) != 0? "-X" : "",
				initDir + cleanPath(path));
		List<Stat> stats = parseLsOutput((statFlags & STAT_TAPEINFO) != 0, lines);
		return stats.get(0);
	}
	
	/**
	 * stat() a file using the default flags
	 * @see Hsi#stat(String, int)
	 */
	public Stat stat(String path) throws HsiException {
		return stat(path, NO_FLAGS);
	}
	
	/**
	 * Stat all of the entries in a directory, optionally recursing
	 * @param path - the path to scan
	 * @param pattern - optional regular expression to filter objects
	 * @param statFlags - flags in the DIR_* or STAT_* family 
	 * @return A list of {@list Stat} objects for the entries in path
	 * @throws HsiException
	 */
	public List<Stat> statDir(String path, String pattern, int statFlags) throws HsiException {
		if(pattern == null) pattern = ".+";
		path = cleanPath(path);
		List<Stat> results = new ArrayList<>();
		Stat root = stat(path);		
		if(!root.isDir()) {
			// In the event that a statDir is run on a non-directory, we just return the stat...in a list.		
			if((statFlags & DIR_REL_PATH) != 0) {
				root.name = path + "/" + root.name;
			} else if((statFlags & DIR_ABS_PATH) != 0) {
				root.name = absPath(path + "/" + root.name);
			}
			results.add(root);			
		} else {
			List<String> lines = runHsiCommand("ls", "-alD",
					(statFlags & STAT_TAPEINFO) != 0? "-X" : "",
					(statFlags & STAT_USE_MTIME) != 0? "-Tm" : "",
					initDir + cleanPath(path));
			List<Stat> entries = parseLsOutput((statFlags & STAT_TAPEINFO) != 0, lines);
			Pattern p = Pattern.compile("^" + pattern);
			for(Stat entry: entries) {
				Matcher m = p.matcher(entry.name);
				if(m.matches()) {
					if((statFlags & DIR_REL_PATH) != 0) {
						entry.name = path + "/" + entry.name;
					} else if((statFlags & DIR_ABS_PATH) != 0) {
						entry.name = absPath(path + "/" + entry.name);
					}
					results.add(entry);
					if((statFlags & DIR_RECURSIVE) != 0 && entry.isDir()) {
						results.addAll(statDir(path + "/" + entry.name, pattern, statFlags));
					}
				}
			}
		}		
		return results;		
	}
	
	/**
	 * Stat all of the entries in a directory, using the default {@code flags}
	 * @see Hsi#statDir(String, String, int)
	 */
	public List<Stat> statDir(String path, String pattern) throws HsiException {
		return statDir(path, pattern, NO_FLAGS);
	}
	
	/**
	 * Stat all of the entries in a directory, using the default {@code pattern}
	 * @see Hsi#statDir(String, String, int)
	 */
	public List<Stat> statDir(String path, int statFlags) throws HsiException {
		return statDir(path, null, statFlags);
	}
	
	/**
	 * Stat all of the entries in a directory, using the default {@code flags} and {@code pattern}
	 * @see Hsi#statDir(String, String, int)
	 */
	public List<Stat> statDir(String path) throws HsiException {
		return statDir(path, null, NO_FLAGS);
	}
	

	/**
	 * A fast listing for a directory.  It returns {@link Stat} objects, but they're
	 * barely filled out -- just parent, name, and type
	 * @param path - path to get listing for
	 * @param pattern - optional regex pattern for matching files, use null for all entries
	 * @param flags - DIR_* flags for processing.
	 * @return a list of files matching the pattern for the path
	 * @throws HsiException
	 */
	public List<Stat> listDir(String path, String pattern, int flags) throws HsiException {
		List<Stat> results = new ArrayList<>();
		List<String> lines = runHsiCommand("ls", "-1", 
											initDir + cleanPath(path));
		if(pattern == null) pattern = ".+";
		Pattern p = Pattern.compile(pattern);		
		for(String l: lines) {
			Matcher m = p.matcher(l);
			if(m.matches()) {
				Stat s = new Stat();
				s.parent = path;
				if(l.endsWith("/")) {
					s.type = Stat.FType.DIR;
					s.name = l.substring(0,  l.length() - 1);
					if((flags & DIR_RECURSIVE) != 0) {
						results.addAll(listDir(path + "/" + s.name, pattern, flags));
					}					
				} else {
					s.type = Stat.FType.FILE;
					s.name = l;
				}
				s.name = s.name.substring(s.name.lastIndexOf("/") + 1);
				if((flags & DIR_REL_PATH) != 0) {
					s.name = path + "/" + s.name;
				} else if((flags & DIR_ABS_PATH) != 0) {
					s.name = absPath(path + "/" + s.name);
				}				
				results.add(s);
			}			
		}
		return results;
	}
	
	/**
	 * A fast listing for a directory. Uses the default {@code flags} and {@code pattern}
	 * @see Hsi#listDir(String, String, int)
	 */
	public List<Stat> listDir(String path) throws HsiException {
		return listDir(path, null, NO_FLAGS);
	}

	/**
	 * A fast listing for a directory. Uses the default {@code flags}
	 * @see Hsi#listDir(String, String, int)
	 */
	public List<Stat> listDir(String path, String pattern) throws HsiException {
		return listDir(path, pattern, NO_FLAGS);
	}
	
	/**
	 * A fast listing for a directory. Uses the default {@code pattern}
	 * @see Hsi#listDir(String, String, int)
	 */
	public List<Stat> listDir(String path, int flags) throws HsiException {
		return listDir(path, null, flags);
	}
	
	/**
	 * Check to see if a file or directory exists
	 * @param path - the path to check
	 * @return true if the entry exists, false otherwise
	 * @throws HsiException
	 */
	public boolean exists(String path) throws HsiException {
		try {
			stat(path);
			return true;
		} catch(HsiException e) {
			if(e.getMessage().contains("HPSS_ENOENT")) {
				return false;
			} else {
				throw e;
			}
		}
	}
	
	/**
	 * Create a new directory, optionally with necessary parents
	 * @param path - the new directory to create
	 * @param parents - true if parents should be created
	 * @throws HsiException
	 */
	public void mkdir(String path, boolean parents) throws HsiException {
		runHsiCommand("mkdir", parents? "-p" : "", initDir + cleanPath(path));
	}

	/**
	 * Create a new directory
	 * @see Hsi#mkdir(String, boolean)
	 */
	public void mkdir(String path) throws HsiException {
		mkdir(path, false);
	}
	
	/**
	 * Remove a directory
	 * @param path - the directory to remove
	 * @throws HsiException
	 */
	public void rmdir(String path) throws HsiException {
		runHsiCommand("rmdir", initDir + cleanPath(path));
	}
	
	/**
	 * Delete a file
	 * @param path - the file to delete
	 * @throws HsiException
	 */
	public void delete(String path) throws HsiException {
		runHsiCommand("delete", initDir + cleanPath(path));
	}
	
	/**
	 * Rename (move) a file
	 * @param oldName - the source file
	 * @param newName - the destination file
	 * @param force - if true, force the move
	 * @throws HsiException
	 */
	public void rename(String oldName, String newName, boolean force) throws HsiException {
		runHsiCommand("mv", force? "-f" : "", initDir + cleanPath(oldName), initDir + cleanPath(newName));
	}
	
	/**
	 * Rename (move) a file
	 * @see Hsi#rename(String, String, boolean)
	 */
	public void rename(String oldName, String newName) throws HsiException {
		rename(oldName, newName, false);
	}
	
	/**
	 * Change the mode of an entry, using a symbolic name
	 * @param mode - symbolic mode operation
	 * @param path - the path to modify
	 * @param flags - flags in the CHMOD_* family
	 * @throws HsiException
	 */
	public void chmod(String mode, String path, int flags) throws HsiException {
		if(!mode.matches("^[ugoa]?([-+=]?(rwxXst)+")) {
			throw new HsiException("Invalid mode string");
		}
		if((flags & CHMOD_FILES_ONLY) != 0 && (flags & CHMOD_DIRS_ONLY) != 0) {
			throw new HsiException("Cannot specify files only AND dirs only");
		}
		runHsiCommand("chmod", 
				(flags & CHMOD_RECURSIVE) != 0? "-R" : "", 
				(flags & CHMOD_FILES_ONLY) != 0? "-f" : "",
				(flags & CHMOD_DIRS_ONLY) != 0? "-d" : "",
				mode, initDir + cleanPath(path));
	}
	
	/**
	 * Change the mode of an entry, using the symbolic name
	 * @see Hsi#chmod(String, String, int)
	 */
	public void chmod(String mode, String path) throws HsiException {
		chmod(mode, path, NO_FLAGS);
	}
	
	/** 
	 * Change the mode of an entry, using the numeric value.   
	 * @param mode - the mode to set for the entry
	 * @param path - the entry to modify
	 * @param flags - flags in the CHMOD_* family
	 * @throws HsiException
	 */
	public void chmod(int mode, String path, int flags) throws HsiException {
		if(mode < 0 || mode > 07777) {
			throw new HsiException("Mode must be between 0000 and 07777");
		}
		if((flags & CHMOD_FILES_ONLY) != 0 && (flags & CHMOD_DIRS_ONLY) != 0) {
			throw new HsiException("Cannot specify files only AND dirs only");
		}
		runHsiCommand("chmod", 
				(flags & CHMOD_RECURSIVE) != 0? "-R" : "", 
				(flags & CHMOD_FILES_ONLY) != 0? "-f" : "",
				(flags & CHMOD_DIRS_ONLY) != 0? "-d" : "",
				String.format("%o", mode), 
				initDir + cleanPath(path));		
	}
	
	/**
	 * Change the mode of an entry, using the numeric value
	 * @see Hsi#chmod(int, String, int)
	 */
	public void chmod(int mode, String path) throws HsiException {
		chmod(mode, path, NO_FLAGS);
	}
	
	/**
	 * Add an annotation to the path
	 * @param path - the path to annotate
	 * @param annotation - up to 250 characters of text, without double quotes.
	 * @throws HsiException
	 */
	public void annotate(String path, String annotation) throws HsiException {
		if(annotation.length() > 250) {
			throw new HsiException("Annotation must be shorter than 250 characters");
		}
		if(annotation.contains("\"")) {
			throw new HsiException("Annotation cannot contain the double quote (\") character");
		}
		runHsiCommand("annotate", "-A", "\"" + annotation + "\"",
					initDir + cleanPath(path));
	}
	
	/**
	 * Retrieve the annotation for a file
	 * @param path - the path to retrieve
	 * @return The annotation text, or an empty string
	 * @throws HsiException
	 */
	public String getAnnotation(String path) throws HsiException {
		List<String> lines = runHsiCommand("ls", "-Ad",
				initDir + cleanPath(path));
		for(String line: lines) {
			if(line.matches("Annotation: ")) {
				return line.substring(12);
			}
		}
		return "";
	}
	
	public void chacl() throws HsiException {
		
	}
	
	
	/**
	 * Change the Class of Service for a file.  The file may need to be retrieved from tape
	 * and copied to new tapes depending on the new class.
	 * @param path - The file to modify
	 * @param cos - the new class of service, or -1 to use the auto class
	 * @throws HsiException
	 */
	public void chcos(String path, int cos) throws HsiException {
		runHsiCommand("chcos",
					(cos == -1? "auto" : "" + cos),
					initDir + cleanPath(path));
	}
	
	/**
	 * Create a new hardlink
	 * @param srcPath - the existing file
	 * @param destPath - the new link
	 * @throws HsiException
	 */
	public void link(String srcPath, String destPath) throws HsiException {
		runHsiCommand("ln", "-f",
					initDir + cleanPath(srcPath),
					initDir + cleanPath(destPath));
	}
	
	/**
	 * Create a new symbolic link
	 * @param srcPath - the existing file
	 * @param destPath - the new symlink
	 * @throws HsiException
	 */
	public void symlink(String srcPath, String destPath) throws HsiException {
		runHsiCommand("ln", "-f", "-s",
				initDir + cleanPath(srcPath),
				initDir + cleanPath(destPath));		
	}
	
	/**
	 * Create a checksum for a file and retrieve the value.  If the file already has a checksum, it will be returned.
	 * @param path - the file to checksum
	 * @return The MD5 checksum for the file
	 * @throws HsiException
	 */
	public String createChecksum(String path) throws HsiException {
		List<String> lines = runHsiCommand("hashcreate", "-H", "md5",
				initDir + cleanPath(path));
		return lines.get(0).substring(0, 31);
	}
	
	/**
	 * Get the stored checksum for the file, optionally creating the checksum
	 * @param path - the path to get the checksum for
	 * @param create - if true, create the checksum if it has not been created.
	 * @return The MD5 checksum for the file, or null if it doesn't exist
	 * @throws HsiException
	 */
	public String getChecksum(String path, boolean create) throws HsiException {		
		List<String> lines = runHsiCommand("hashlist", "-h",
				initDir + cleanPath(path));
		if(lines.get(0).startsWith("(none)")) {
			if(create) {
				return createChecksum(path);
			} else {
				return null;
			}						
		} else {
			return lines.get(0).substring(0, 31);
		}				
	}
	
	/**
	 * Get the stored checksum for the file
	 * @see Hsi#getChecksum(String, boolean)
	 */
	public String getChecksum(String path) throws HsiException {
		return getChecksum(path, false);
	}
	
	/**
	 * Verify the contents against the stored checksum, optionally creating the checksum
	 * @param path - the path to checksum
	 * @param create - if true, create a new checksum for the entry
	 * @return true if the checksum has verified or if it is newly created.  false otherwise.
	 * @throws HsiException
	 */
	public boolean verifyChecksum(String path, boolean create) throws HsiException {
		List<String> lines = runHsiCommand("hashverify", 
				initDir + cleanPath(path));
		String line = lines.get(0);
		if(line.startsWith("no valid checksum found")) {
			if(create) {
				createChecksum(path);
				return true;
			} else {
				throw new HsiException("File does not have a checksum to verify");
			}
		}
		if(line.endsWith("OK")) {
			return true;
		} else {
			return false;
		}
	}
	
	/**
	 * Verify the contents against the stored checksum.
	 * @see Hsi#verifyChecksum(String, boolean)
	 */
	public boolean verifyChecksum(String path) throws HsiException {
		return verifyChecksum(path, false);
	}
	
	/**
	 * Get the disk usage for the path, in bytes
	 * @param path - the path to check
	 * @return The number of bytes used by this file, or all of the contents if the path is a directory
	 * @throws HsiException
	 */
	public long du(String path) throws HsiException {
		List<String> lines = runHsiCommand("du", "-n", "-s",
				initDir + cleanPath(path));
		return Long.parseLong(lines.get(2).split("\t")[0]);
	}
	
	/**
	 * Retrieve a file to the local filesystem.  If the localPath is a directory, the files will be retrieved into it.
	 * Otherwise, the retrieved file will be stored as the localPath.
	 * @param remotePath - the path on HPSS to retrieve
	 * @param localPath - the path to store the file(s)
	 * @param recursive - if true, do a recursive get.
	 * @throws HsiException
	 */
	public void get(String remotePath, String localPath, boolean recursive) throws HsiException {
		Path lPath = Paths.get(localPath);
		if(Files.isDirectory(lPath)) {
			runHsiCommand("lcwd", localPath);
			runHsiCommand("get", "-c", "on",
					recursive? "-R" : "",
					initDir + cleanPath(remotePath));
		} else {
			runHsiCommand("get", "-c", "on",
					recursive? "-R" : "",
					localPath, ":",
					initDir + cleanPath(remotePath));			
		}
	}
	
	/**
	 * Store a file from the local filesystem	 * 
	 * @param localPath - the local file
	 * @param remotePath - the destination on HPSS
	 * @param cos - the class of service to use, or -1 for the default.
	 * @throws HsiException
	 */
	public void put(String localPath, String remotePath, int cos) throws HsiException {
		runHsiCommand("put", 
				"-c", "on",
				"-H", "md5",
				localPath, ":",
				initDir + cleanPath(remotePath),
				cos != -1? "cos=" + cos : "");
	}
	
	/**
	 * Store a file from the local filesystem using the default COS
	 * @see Hsi#put(String, String, int)
	 */
	public void put(String localPath, String remotePath) throws HsiException {
		put(localPath, remotePath, defaultCOS);
	}
	
	/**
	 * Synchronously stage a list of files
	 * @param paths - the files to stage
	 * @throws HsiException
	 */
	public void stage(List<String> paths) throws HsiException {
		// Normalize the paths to absolute paths for ease.		
		for(int i = 0; i < paths.size(); i++) {
			paths.set(i, absPath(paths.get(i)));
		}
		
		if(paths.size() > 50) {
			try {
				// the list is pretty long, so we'll need to push it out to a file for input
				Path p = Files.createTempFile("stage-queue", ".txt");
				File file = p.toFile();
				PrintWriter pw = new PrintWriter(Files.newBufferedWriter(p));			
				pw.println("stage -w <<EOF");
				for(String f: paths) {
					pw.println(f);
				}
				pw.println("EOF");
				pw.close();
				file.deleteOnExit();
				runHsiCommand("in", p.toString());
				file.delete();
			} catch(IOException e) {
				throw new HsiException(e);
			}
		} else {
			// gotta do them backward because we're unshifting
			paths.add(0, "-w");
			paths.add(0, "stage");			
			runHsiCommand(paths.toArray(new String[0]));			
		}
	}
	
	/**
	 * Purge a file from the HPSS disk cache.
	 * <p>
	 * If the file has not migrated to tape, the purge request will be ignored.  If the file is in the process
	 * of migrating, this will hang until the migration is complete.
	 * @param path - the path to purge
	 * @param recursive - enable recursion
	 * @throws HsiException
	 */
	public void purge(String path, boolean recursive) throws HsiException {
		runHsiCommand("purge",
				recursive? "-R" : "",
				initDir + cleanPath(path));
	}
	
	/**
	 * Purge a file from teh HPSS disk cache
	 * @see Hsi#purge(String, boolean)
	 */
	public void purge(String path) throws HsiException {
		purge(path, false);
	}

	/**
	 * Migrate data from a higher level in the hierarchy to a lower level
	 * @param path - the path to migrate
	 * @param flags - flags in the MIGRATE_* family
	 * @throws HsiException
	 */
	public void migrate(String path, int flags) throws HsiException {
		runHsiCommand("migrate",
				(flags & MIGRATE_RECURSIVE) != 0? "-R" : "",
				(flags & MIGRATE_FORCE) != 0? "-F" : "",
				(flags & MIGRATE_PURGE) != 0? "-P" : "",
				initDir + cleanPath(path));
	}
	/**
	 * Determine if a file is completely in the HPSS disk cache
	 * @param path - the path to check
	 * @return true if there are the same number of bytes in the disk cache as the object size
	 * @throws HsiException
	 */
	public boolean isOnDisk(String path) throws HsiException {
		Stat s = stat(path, STAT_TAPEINFO);
		for(Stat.StorageLevel sl: s.levels) {
			if(sl.type == Stat.StorageLevel.SType.DISK && sl.isComplete) {
				return true;
			}
		}
		return false;
	}
	
	/**
	 * Determine if a file is completely on HPSS tape
	 * @param path - the path to check
	 * @return true if the same number bytes are on the first level of tape as the object size
	 * @throws HsiException
	 */
	public boolean isOnTape(String path) throws HsiException {
		Stat s = stat(path, STAT_TAPEINFO);
		for(Stat.StorageLevel sl: s.levels) {
			if(sl.type == Stat.StorageLevel.SType.TAPE && sl.isComplete) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Check to see if all tape levels have complete copies of the data
	 * @param path - the path to check
	 * @return true if all tape levels have the same number of bytes as the object size
	 * @throws HsiException
	 */
	public boolean migrationFinished(String path) throws HsiException {
		Stat s = stat(path, STAT_TAPEINFO);
		for(Stat.StorageLevel sl: s.levels) {			
			if(sl.type == Stat.StorageLevel.SType.TAPE && !sl.isComplete) {
				return false;
			}
		}
		return true;		
	}
	
}

