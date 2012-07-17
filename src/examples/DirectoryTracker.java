package examples;

import jargs.gnu.CmdLineParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FilenameFilter;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;

import org.apache.log4j.BasicConfigurator;

import com.turn.ttorrent.client.Client;
import com.turn.ttorrent.client.SharedTorrent;
import com.turn.ttorrent.common.Torrent;
import com.turn.ttorrent.tracker.TrackedTorrent;
import com.turn.ttorrent.tracker.Tracker;



public class DirectoryTracker
{
	public static final int DEFAULT_TRACKER_PORT = 6969;
	
	/**
	 * Display program usage on the given {@link PrintStream}.
	 * 
	 */
	private static void usage(PrintStream s) 
	{
		s.println("usage: DirectoryTracker [options] [directory]");
		s.println("Create a tracker for each files in directory.");
		s.println("Note: .torrent files are created or regenerated.");
		s.println();
		s.println("Available options:");
		s.println(" -h,--help 			Show this help and exit.");
		s.println(" -p,--port PORT 		Bind to port PORT.");
		s.println();
	}

	/**
	 * Main program function.
	 * 
	 * @param args
	 */
	public static void main(String[] args) 
	{
		BasicConfigurator.configure();
		
		CmdLineParser parser = new CmdLineParser();
		CmdLineParser.Option help = parser.addBooleanOption('h', "help");
		CmdLineParser.Option port = parser.addIntegerOption('p', "port");

		try {
			parser.parse(args);
		} catch (CmdLineParser.OptionException oe) {
			System.err.println(oe.getMessage());
			usage(System.err);
			System.exit(1);
		}	

		// Display help and exit if requested
		if (Boolean.TRUE.equals((Boolean)parser.getOptionValue(help))) {
			usage(System.out);
			System.exit(0);
		}

		Integer portValue = (Integer) parser.getOptionValue(port, Integer.valueOf(DEFAULT_TRACKER_PORT));
		String[] otherArgs = parser.getRemainingArgs();

		if (otherArgs.length > 1) {
			usage(System.err);
			System.exit(1);
		}

		// Get directory from command-line argument or default to current
		// directory
		String directory = otherArgs.length > 0 ? otherArgs[0] : ".";
		
		// Create the Tracker
		Tracker t = null;
		try {
			t = new Tracker(InetAddress.getLocalHost());
			t.start();
			System.out.println("Tracker started.");
			
			// Parse files in directory
			FilenameFilter filter = new FilenameFilter() {
				public boolean accept(File dir, String name) {
					List<String> accepted_ends = Arrays.asList(".avi", ".txt", ".mp3", ".ogg", ".flv");
					for (String end : accepted_ends) {
						if (name.endsWith(end)) {
							return true;
						}
					}
					
					return false;
				}
			};
				
			File parent = new File(directory);
			System.out.println("Analysing directory: "+directory);
			for (File f : parent.listFiles(filter)) {
				try {
					// Try to generate the .torrent file
					File torrent_file = new File(f.getParentFile(), f.getName()+".torrent");
					Torrent torrent = Torrent.create(new File(f.getAbsolutePath()), new URI(t.getAnnounceUrl().toString()), "createdByTtorrent");
					System.out.println("Created torrent "+torrent.getName()+" for file: "+f.getAbsolutePath());
					torrent.save(torrent_file);

					// Announce file to tracker
					TrackedTorrent tt = new TrackedTorrent(torrent);
					t.announce(tt);
					System.out.println("Torrent "+torrent.getName()+" announced");
					
					// Share torrent
					System.out.println("Sharing "+torrent.getName()+"...");
				    Client seeder = new Client(InetAddress.getLocalHost(), new SharedTorrent(torrent, parent, true));
				    seeder.share();
					
				} catch (Exception e) {
					System.err.println("Unable to describe, announce or share file: "+f.toString());
					e.printStackTrace(System.err);
				}
			}
			
			// Wait for user signal
			BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
			try {
				reader.readLine();
			} finally {
				reader.close();
			}
			
		} catch (Exception e) {
			System.err.println("Unable to start tracker.");
			e.printStackTrace(System.err);
			System.exit(1);
		} finally {
			if (t != null) {
				t.stop();
				System.out.println("Tracker stopped.");
			}
	    }
	}

}
