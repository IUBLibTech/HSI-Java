package edu.indiana.dlib.hsi;

import java.util.Scanner;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * A test suite for the Hsi classes
 * @author bdwheele
 *
 */
public class HsiTest {

	public static void main(String[] args) throws Exception {
		Scanner scanner = new Scanner(System.in);
		System.out.println("HPSS test username: ");
		String username = scanner.next();
		System.out.println("Test directory: ");
		String directory = scanner.next();
		scanner.close();
		System.out.println("Testing HsiFactory Static methods");
		System.out.println("  findKeytab: " + HsiFactory.findKeytab(System.getProperty("user.name")));
		System.out.println("  findHsiBinary: " + HsiFactory.findHsiBinary());
		System.out.println("Testing Hsi Static methods");
		System.out.println("  cleanPath(////hello/../world/./foo//bar): " + Hsi.cleanPath("////hello/../world/./foo//bar"));
		System.out.println("  cleanPath(barf): " + Hsi.cleanPath("barf"));
		System.out.println("Testing Hsi creation");
		Logger log = Logger.getAnonymousLogger();
		log.setLevel(Level.FINEST);
		Handler handler = new ConsoleHandler();
		handler.setLevel(Level.FINER);		
		log.addHandler(handler);
		HsiFactory hsiFactory = new HsiFactory(username, directory, null, null, log);
		System.out.println("  ping: " + hsiFactory.ping());
		Hsi hsi = hsiFactory.getHsi();
		System.out.println("  hsi: " + hsi);
		System.out.println("Testing Hsi helper methods");
		System.out.println("  getHPSSVersion: " + hsi.getHPSSVersion());
		System.out.println("  absPath(hello): " + hsi.absPath("hello"));
		System.out.println("  absPath(/hpss/d/l/dlib/test/foo): " + hsi.absPath("/hpss/d/l/dlib/test/foo"));
		System.out.println("  absPath(test/hello): " + hsi.absPath("test/hello"));
		System.out.println("  relPath(hello): " + hsi.relPath("hello"));
		System.out.println("  relPath(/hpss/d/l/dlib/test/foo): " + hsi.relPath("/hpss/d/l/dlib/test/foo"));
		System.out.println("  relpath(test/hello): " + hsi.relPath("test/hello"));
		
		System.out.println("");
		System.exit(1);
	}

}
