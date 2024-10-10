package fr.isae.mae2.ss.java.comm.game;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This is a little game played over a symmetric socket. First give the name,
 * separator, then a unit index, separator, unit name, separator, and do that
 * 100 times. The combat is a success if you played 0 and the other played a 1,
 * or 1 and 2, or 2 and 0.
 * 
 * Note: you could cheat and read the input of the other player before
 * playing... But if both do that you'll end up in a deadlock...
 *
 * @author Cedric Mayer, 2019
 * @version $Id
 */
public class GameSimpleFights implements Callable<Boolean> {
	/**
	 * Teacher player
	 */
	private static class TeacherPlayer extends GameSimpleFights {

		private String otherUnitName;
		private boolean cheating;
		private long currentTimeout = 100;
		private static long MAX_TIMEOUT = 1100;
		private final String opponentAddress;

		public TeacherPlayer(Socket socket, boolean cheat) throws IOException {
			super(socket);
			this.cheating = cheat;
			opponentAddress = socket.toString();
		}

		protected int[] cheat() throws IOException {
			// wait a little
			long start = System.currentTimeMillis();
			long timeoutms = currentTimeout;
			while (System.currentTimeMillis() - start < timeoutms) {
				try {
					Thread.sleep(10);
					if (input.ready()) {
						break;
					}
				} catch (InterruptedException e) {
					break;
				}
			}

			int otherUnitIndex = 0;
			int unit;
			boolean otherIndexRead = false;
			if (input.ready()) {
				String otherUnitIndexString = input.readLine();
				otherUnitIndex = Integer.parseInt(otherUnitIndexString);
				otherIndexRead = true;
				unit = (otherUnitIndex + 2) % 3;
			} else {
				currentTimeout += 100;
				unit = new Random().nextInt(SHIPS.length);
			}

			// what unit to send next ?
			String unitName = currentTimeout == MAX_TIMEOUT ? "Are you cheating?" :SHIPS[unit];

			// send it, unit then name
			output.write(unit + "");
			output.write(SEPARATOR);
			output.write(unitName);
			output.write(SEPARATOR);
			output.flush();

			// read the one from the player
			if (!otherIndexRead) {
				String otherUnitIndexString = input.readLine();
				if (otherUnitIndexString == null) {
					System.out.println("Bad end of stream with " + Thread.currentThread().getName());
				}
				otherUnitIndex = Integer.parseInt(otherUnitIndexString);
			}
			otherUnitName = input.readLine();

			// return the success
			return new int[] { unit, otherUnitIndex };
		}

		protected int[] dontCheat() throws IOException {
			// what unit to send next ?
			int unit = new Random().nextInt(SHIPS.length);
			String unitName = SHIPS[unit];

			// send it, unit then name
			output.write(unit + "");
			output.write(SEPARATOR);
			output.write(currentTimeout == MAX_TIMEOUT ? "Are you cheating?" : unitName);
			output.write(SEPARATOR);
			output.flush();

			// read the one from the player
			String otherUnitIndexString = input.readLine();
			if (otherUnitIndexString == null) {
				System.out.println("Bad end of normal stream with " + against + " on " + opponentAddress);
			}

			int otherUnitIndex = Integer.parseInt(otherUnitIndexString);
			otherUnitName = input.readLine();

			// return the success
			return new int[] { unit, otherUnitIndex };
		}

		@Override
		protected int oneLoop(int round) throws IOException {
			int[] units = cheating && new Random().nextInt(2) > 0 ? cheat() : dontCheat();
			int unit = units[0];
			String unitName = SHIPS[unit];
			int otherUnitIndex = units[1];
			// compute result
			int battleResult = (3 + otherUnitIndex - unit) % 3;
			// boolean success = battleResult == 1;

			// display something
			System.out.println(String.format("  Round %s against %s: %s vs %s: %s", round, against, unitName,
					otherUnitName, RESULT[battleResult]));

			if (currentTimeout > MAX_TIMEOUT) {
				System.out.println(
						"The other player " + against + " on " + opponentAddress + " is cheating! I stop here.");
				return 4;
			}

			// return the success
			return battleResult;
		}

	}

	/** Do not modify this! Number of fights */
	private static final int NUMBER_OF_FIGHTS = 100;
	/** Default port opened between computers */
	private static final int DEFAULT_PORT = 8080;
	/**
	 * Do not modify this! Separator between fields: a line feed, usable with
	 * {@link BufferedReader#readLine()}
	 */
	private static final String SEPARATOR = "\n";

	/** The different ships, you can change the names :-) */
	private static final String[] SHIPS = new String[] { "TIEFIGHTER", "BOMBER", "DESTROYER" };
	// {"Infantry", "Cavalry", "Canons"}
	// {"Stone", "Scissors", "Paper"}
	/** The results of a battle (0 draw, 1 success, 2 failure) */
	private static final String[] RESULT = new String[] { "Draw", "Success!", "Failure :-(" };
	/** Your name: change it */
	private final String MY_NAME = System.getProperty("user.name");

	/** input to read what the other player sent */
	protected final BufferedReader input;
	/** output to send something to the other player */
	protected final BufferedWriter output;
	/** name of the other player */
	protected final String against;

	/**
	 * Constructor : an instance for one socket
	 *
	 * @param socket the socket
	 * @throws IOException If an error happens
	 */
	public GameSimpleFights(Socket socket) throws IOException {
		// opening the input and output
		input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
		output = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

		// send your name
		output.write(MY_NAME + (socket.getLocalPort() == DEFAULT_PORT ? "-as-server" : ""));
		output.write(SEPARATOR);
		// flush so that the other player can receive your input
		output.flush();

		// read the name of the other player
		against = input.readLine() + (socket.getLocalPort() == DEFAULT_PORT ? "-as-client" : "");
		Thread.currentThread().setName("Thread-GameSimpleFights-Against-" + against);
	}

	/**
	 * Compute the next unit to send
	 *
	 * @return The next unit to send
	 */
	private int computeNextUnit() {
		// here you can compute the next unit to send
		// FIXME please do not use Math.random nor Random
		return (int) (Math.random() * SHIPS.length);
	}

	private void lastUnitPlayed(int unit) {
		// you can store the last unit played by the other player, so that you have the
		// whole history
		// FIXME if needed
	}

	/**
	 * One loop
	 *
	 * @param round the current round
	 * @return the battle result
	 * @throws IOException If there is a problem
	 */
	protected int oneLoop(int round) throws IOException {
		// what unit to send next ?
		int unit = computeNextUnit();
		String unitName = SHIPS[unit];

		// send it, unit then name
		output.write(unit + "");
		output.write(SEPARATOR);
		output.write(unitName);
		output.write(SEPARATOR);
		output.flush();

		// read the one from the player
		String otherUnitIndexString = input.readLine();
		int otherUnitIndex = Integer.parseInt(otherUnitIndexString);
		String otherUnitName = input.readLine();
		// store last unit played
		lastUnitPlayed(otherUnitIndex);

		// compute result
		int battleResult = (3 + otherUnitIndex - unit) % 3;
		// boolean success = battleResult == 1;

		// display something
		System.out.println(String.format("  Round %s against %s: %s vs %s: %s", round, against, unitName, otherUnitName,
				RESULT[battleResult]));

		// return the success
		return battleResult;
	}

	@Override
	public Boolean call() throws IOException {
		// list of battle results for the 3 possible outcomes: 0:draws, 1:successes,
		// failures
		int[] nbResults = new int[3];

		// main loop
		for (int battle = 1; battle <= NUMBER_OF_FIGHTS; battle++) {
			int result = oneLoop(battle);
			if (result > 2)
				return true;
			nbResults[result] += 1;
		}

		// display battle results
		for (int index = 0; index < 3; index++) {
			System.out.println(String.format("Battle against %s: %s/%s %s", against, nbResults[index], NUMBER_OF_FIGHTS,
					RESULT[index]));
		}

		// compute if success more than failures
		boolean battleSuccess = nbResults[1] > nbResults[2];
		input.close();
		output.close();
		return battleSuccess;
	}

	/**
	 * Main method
	 *
	 * @param args The arguments. If empty, runs the game locally
	 * @throws IOException
	 */
	public static void main(String... args) throws IOException {
		if (args.length == 0) {
			// local test
			try (ServerSocket serverSocket = new ServerSocket(DEFAULT_PORT);) {
				// server created
				System.out.println("Local server ready");
				new Thread(() -> {
					// client created
					System.out.println("Local client connection");
					try (Socket connection = new Socket("localhost", DEFAULT_PORT);) {
						//new TeacherPlayer(connection, true).call();
						new GameSimpleFights(connection).call();
						connection.close();
					} catch (Exception e) {
						throw new IllegalStateException(e);
					}
				}).start();
				// start the game
				Socket connection = serverSocket.accept();
				System.out.println("Start of game");
				new TeacherPlayer(connection, true).call();
				connection.close();
			}
		} else if ("auto".equals(args[0])) {
			// local test
			Thread tmain = new Thread(() -> {
				try {
					main("server");
				} catch (IOException e) {
					e.printStackTrace();
				}
			}, "Server");
			tmain.setDaemon(true);
			tmain.start();
			main("localhost");
		} else if (args[0].equals("server")) {
			// server code
			try (ServerSocket serverSocket = new ServerSocket(DEFAULT_PORT);) {
				// server created
				System.out.println("Public server ready");
				Map<String, AtomicInteger> students = new HashMap<>();
				while (true) {
					// start the game
					String who = null;
					try {
						Socket connection = serverSocket.accept();
						// client connected!
						who = connection.getRemoteSocketAddress().toString().split(":")[0].split("\\.")[3];
						if (!students.containsKey(who)) {
							students.put(who, new AtomicInteger());
						}
						students.get(who).incrementAndGet();
						System.out.println(
								"Start of game against " + who + " at " + connection.getRemoteSocketAddress() + " date " + new Date());
						final String whoFinal = who;
						new Thread(() -> {
							try {
								new TeacherPlayer(connection, true).call();
							} catch (IOException e) {
								System.out.println("Problem when fighting " + whoFinal + ": " + e);
							}
						}, "Game-with-" + connection.getRemoteSocketAddress()).start();
					} catch (Exception e) {
						System.err.println("Problem when starting the fight against " + who + ": " + e);
					}
				}
			}
		} else if (args[0].equals("teacher")) {
			// a teacher

			Map<String, AtomicInteger> students = new HashMap<>();
			for (String st : "01 02 03 04 05 06 07 08 09 10 11 12 13 14 15".split(" ")) {
				students.put("pcb61-09-" + st, new AtomicInteger());
			}

			while (true) {
				students.entrySet().stream().parallel().forEach(student -> {
					System.out.println("Contacting " + student.getKey());
					Socket connection;
					try {
						// client created
						connection = new Socket();
						connection.connect(new InetSocketAddress(student.getKey(), DEFAULT_PORT), 1000);
						new TeacherPlayer(connection, true).call();
						// new GameSimpleFights(connection).call();
						connection.close();
						student.getValue().incrementAndGet();
					} catch (Exception e) {
						if (!e.toString().contains("ConnectException")) {
							System.err.println("Unable to connect to " + student.getKey() + ": " + e.toString());
						}
					}
				});
				students.entrySet().stream().sorted(Comparator.comparing(u -> u.getKey()))
						.forEach(student -> System.out.println(student.getKey() + " : " + student.getValue()));
				try {
					Thread.sleep(60000);
				} catch (InterruptedException e) {
					break;
				}
			}

			// FIXME
		} else {
			System.out.println("Connecting to " + args[0]);
			Socket connection = new Socket(args[0], DEFAULT_PORT);
			try {
				new TeacherPlayer(connection, true).call();
				connection.close();
			} catch (Exception e) {
				System.err.println("Unable to contact " + args[0]);
				e.printStackTrace();
			}
		}
	}
}
