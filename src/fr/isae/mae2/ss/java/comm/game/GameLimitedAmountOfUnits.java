package fr.isae.mae2.ss.java.comm.game;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.Callable;

/**
 * This is a little game played over a symmetric socket.
 * First give the name, separator, then a unit index, separator, unit name, separator, and do that 100 times.
 * The combat is a success if you played 0 and the other played a 1, or 1 and 2, or 2 and 0.
 * 
 * Note: you could cheat and read the input of the other player before playing... But if both do that you'll end up in a deadlock...
 *
 * Different than {@link GameSimpleFights}, the server sends the number for each unit just after the name. If there is not a draw, a unit is deleted.
 * Game ends when someone has only one type of unit, the winner is the one with the most remaining units.
 * 
 * @author Cedric Mayer, 2019
 * @version $Id
 */
public class GameLimitedAmountOfUnits implements Callable<Boolean> {
    /** Default port opened between computers */
    private static final int DEFAULT_PORT = 8080;
    /** Do not modify this! Separator between fields: a line feed, usable with {@link BufferedReader#readLine()} */
    private static final String SEPARATOR = "\n";
    
    /** The different ships, you can change the names :-) */
    private static final String[] SHIPS = new String[] {"TIEFIGHTER", "BOMBER", "DESTROYER"};
    /** The different ships, you can change the units for your battles */
    private static final int[] STARTING_SHIPS = new int[] {100, 100, 100};
    // {"Infantry", "Cavalry", "Canons"}
    // {"Stone", "Cisors", "Paper"}
    /** The results of a battle (0 draw, 1 success, 2 failure) */
    private static final String[] RESULT = new String[] {"Draw", "Success!", "Failure :-("};
    /** Your name: change it */
    private final String MY_NAME = "DarthV" + new Random().nextInt() + "dor";

    /** input to read what the other player sent */
    private final BufferedReader input;
    /** output to send something to the other player */
    private final BufferedWriter output;
    /** name of the other player */
    private final String against;
    
    /** numbers of my ships */
    private final int[] myShips = Arrays.copyOf(STARTING_SHIPS, 3);
    /** number of other ships */
    private final int[] otherShips = Arrays.copyOf(STARTING_SHIPS, 3);
    
    /** Constructor : an instance for one socket
     *
     * @param socket    the socket
     * @throws IOException  If an error happens
     */
    public GameLimitedAmountOfUnits(Socket socket, boolean server) throws IOException {
        input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        output = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
        
        // send your name
        output.write(MY_NAME);
        output.write(SEPARATOR);
        // flush so that the other player can receive your input
        output.flush();

        // read the name of the other player
        against = input.readLine();
        Thread.currentThread().setName((server ? "Server" : "Client") + "-Thread-Game-Against-" + against);

        // send the units
        for (int unit = 0; unit < 3; unit++) {
            if (server) {
                // send information
                output.write("" + otherShips[unit]);
                output.write(SEPARATOR);
                output.flush();
            } else {
                // receive information
                int nb = Integer.parseInt(input.readLine());
                otherShips[unit] = myShips[unit] = nb;
            }
        }
    }

    /** Compute the next unit */
    private int computeNextUnit() {
        // here you can compute the next unit to send
        int ship = -1;
        while (ship < 0 || myShips[ship] == 0) {
            ship = (int) (Math.random() * SHIPS.length);
        }
        return ship;
    }
    
    private void lastUnitPlayed(int unit) {
        // you can store the last unit played, so that you have the whole history
        
    }
    
    private int oneLoop(int round) throws IOException {
        // what unit to send next ?
        int unit = computeNextUnit();
        String unitName = SHIPS[unit];
        
        // send it
        output.write("" + unit);
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
        
        // little checks
        assert otherShips[otherUnitIndex] > 1; // error of other player's program
        assert myShips[unit] > 1; // error of current developer!
        
        // compute result
        int battleResult = (3 + otherUnitIndex - unit) % 3;
        //boolean success = battleResult == 1;
        
        // display something
        System.out.println(String.format("  Round %s against %s: %s vs %s: %s", 
                round, against, unitName, otherUnitName, RESULT[battleResult]));
        
        // perform battle
        if (battleResult == 1) {
            otherShips[otherUnitIndex] --;
        }
        if (battleResult == 2) {
            myShips[unit] --;
        }
        
        // return the success
        return battleResult;
    }
    
    private static enum EndStatus {
        NOT_FINISHED, SUCCESS, FAILURE;
    }
    
    private EndStatus checkEndStatus() {
        int nb0_1 = 0;
        int nb0_2 = 0;
        int totalships1 = 0;
        int totalships2 = 0;
        // loop on all ships
        for (int unit = 0; unit < 3; unit++) {
            nb0_1 += myShips[unit] == 0 ? 1 : 0;
            totalships1 += myShips[unit];
            nb0_2 += otherShips[unit] == 0 ? 1 : 0;
            totalships2 += otherShips[unit];
        }
        // compute end status
        EndStatus toreturn;
        if (nb0_1 == 2 || nb0_2 == 2) {
            if (totalships1 > totalships2) {
                toreturn = EndStatus.SUCCESS;
            } else {
                // even if draw
                toreturn = EndStatus.FAILURE;
            }
        } else {
            toreturn = EndStatus.NOT_FINISHED;
        }
        return toreturn;
    }
    
    /**
     * Method to display an army
     *
     * @param array Array of ship population
     * @return  A {@link String} representing an army
     */
    private String displayShips(int array[]) {
        StringBuilder sb = new StringBuilder();
        
        int total = 0;
        // iteration over each unit
        for (int unit = 0; unit < 3; unit++) {
            if (array[unit] > 0) {
                sb.append(array[unit]).append(" ").append(SHIPS[unit]).append(", ");
            }
            total += array[unit];
        }
        // append the total
        sb.append("Remaining: ").append(total).append(" ships");
        return sb.toString();
    }
    
    @Override
    public Boolean call() throws IOException {
        int[] nbResults = new int[3];
        int round = 0;
        // main loop
        while (checkEndStatus() == EndStatus.NOT_FINISHED) {
            round ++;
            nbResults[oneLoop(round)] ++;
        }
        EndStatus endStatus = checkEndStatus();
        
        // display results
        for (int index = 0; index < 3; index ++) {
            System.out.println(String.format("Battle against %s: %s/%s %s", against, nbResults[index], round, RESULT[index]));
        }
        // final result
        System.out.println(String.format("End battle status against %s: %s, (%s vs %s)",
                against, endStatus, displayShips(myShips), displayShips(otherShips)));
        
        boolean battleSuccess = nbResults[1] > nbResults[2];
        input.close();
        output.close();
        return battleSuccess;
    }
    
    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            // local test
            try (ServerSocket serverSocket = new ServerSocket(DEFAULT_PORT);) {
                System.out.println("Local server ready");
                new Thread(() -> {
                    Socket connection;
                    try {
                        System.out.println("Local client connection");
                        connection = new Socket("localhost", DEFAULT_PORT);
                        new GameLimitedAmountOfUnits(connection, false).call();
                        connection.close();
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                }).start();
                
                Socket connection = serverSocket.accept();
                System.out.println("Start of game");
                new GameLimitedAmountOfUnits(connection, true).call();
                connection.close();
            }
        } else if (args[0].equals("server")) {
            // run as a server
            try (ServerSocket serverSocket = new ServerSocket(DEFAULT_PORT);) {
                while (true) {
                    System.out.println("Server ready");
                    Socket connection = serverSocket.accept();
                    new GameLimitedAmountOfUnits(connection, true).call();
                    connection.close();
                }
            }
        } else if (args.length > 0) {
            // run as a client
            System.out.println("Client connection to " + args[0]);
            Socket connection = new Socket(args[0], DEFAULT_PORT);
            new GameLimitedAmountOfUnits(connection, false).call();
            connection.close();
        }
    }
}

