package edu.indiana.dlib.hsi;

import java.util.ArrayList;
import java.util.List;

/**
 * This contains the results of a stat operation from HPSS
 * @author bdwheele
 *
 */
public class Stat {
	/**
	 * The types of files supported for a stat
	 * @author bdwheele
	 *
	 */
	public enum FType {
		FILE, DIR
	}
		
	/**
	 * The entry type
	 */
	public FType type;
	/**
	 * permissions
	 */
	public int mode;
	/**
	 * size
	 */
	public long size;
	/**
	 * file name.  May be absolute or relative, depending on how it was constructed
	 */
	public String name;
	/**
	 * The directory where the entry was found
	 */
	public String parent;
	/**
	 * time
	 */
	public long time;
	/**
	 * The number of links
	 */
	public int nlink;
	/**
	 * owner
	 */
	public String owner;
	/**
	 * group
	 */
	public String group;
	/**
	 * Class of service.  -1 for unknown (like for directories)
	 */
	public int cos = -1;
	
	/**
	 * Convert a -rwx* style string into a file type 
	 * @param mode
	 * @return either FType.DIR or FType.FILE
	 */
	public static FType modeStringToType(String mode) {
		return mode.startsWith("d")? FType.DIR : FType.FILE;
	}
	
	/**
	 * Convert a -rwx* style string to a numeric mode
	 * @param mode
	 * @return
	 */
	public static int modeStringToInt(String mode) {
		int result = 0;
		if(mode.length() < 10) return 0;
		for(int i = 1; i < 10; i++) {
			result *= 2;
			if(mode.charAt(i) != '-')  result++; 
		}
		return result;
	}
	
	/**
	 * Convert a "<day> <month> <year> <time>" string to a time
	 * @param time
	 * @return
	 */
	public static long textDateToTime(String time) {
		long result = 0;
		// TODO:  str2time
		return result;
	}
	
	/* Extended Information */	
	/**
	 * Storage level information
	 * @author bdwheele
	 *
	 */
	public static class StorageLevel {
		/**
		 * Storage level type
		 * @author bdwheele
		 *
		 */
		public enum SType {
			TAPE, DISK, UNKNOWN
		}		
		/**
		 * numeric level.  Lower numbers are closer to the user
		 */
		public int level;
		/**
		 * storage level type
		 */
		public SType type;
		/**
		 * Bytes at this level
		 */
		public long bytes;
		/**
		 * Tape for this file
		 */
		public String volume = null;
		/**
		 * Tape section for this file
		 */
		public int section = -1;
		/**
		 * Aggregate offset
		 */
		public long offset = -1;
		/**
		 * if the file has the same number of bytes at this level as the object, it is complete.
		 */
		public boolean isComplete = false;
		
		/**
		 * Convert an hpss storage level string to a type
		 * @param t
		 * @return
		 */
		public static SType textToStorageType(String t) {
			switch(t) {
			case "(disk)":
				return SType.DISK;
			case "(tape)":
				return SType.TAPE;
			default:
				return SType.UNKNOWN;
			}
		}

		/* (non-Javadoc)
		 * @see java.lang.Object#toString()
		 */
		@Override
		public String toString() {
			return "StorageLevel [level=" + level + ", type=" + type + ", bytes=" + bytes + ", volume=" + volume
					+ ", section=" + section + ", offset=" + offset + ", isComplete=" + isComplete + "]";
		}
		
		
	}
	
	/**
	 * Will be true if the storage levels data is populated
	 */
	public boolean extended = false;
	/**
	 * The storage levels
	 */
	public List<StorageLevel> levels = new ArrayList<>();

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "Stat [type=" + type + ", mode=" + String.format("%o", mode) + ", size=" + size + ", name=" + name + ", parent=" + parent
				+ ", time=" + time + ", nlink=" + nlink + ", owner=" + owner + ", group=" + group + ", cos=" + cos
				+ ", extended=" + extended + ", levels=" + levels + "]";
	}
	
	/**
	 * Check to see if this entry is a directory
	 * @return true if it is a directory.
	 */
	public boolean isDir() {		
		return type == Stat.FType.DIR;
	}
	
}
