package fr.isae.mae2.ss.java.comm.game;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.Callable;

/**
 * This is a little game played over a symmetric socket.
 * First give the name, separator, then a unit index, separator, unit name, separator, and do that 100 times.
 * The combat is a success if you played 0 and the other played a 1, or 1 and 2, or 2 and 0.
 * 
 * Note: you could cheat and read the input of the other player before playing... But if both do that you'll end up in a deadlock...
 *
 * @author Cedric Mayer, 2019
 * @version $Id
 */
public class GameSimpleFights implements Callable<Boolean> {
    /** Do not modify this! Number of fights */
    private static final int NUMBER_OF_FIGHTS = 100;
    /** Default port opened between computers */
    private static final int DEFAULT_PORT = 8080;
    /** Do not modify this! Separator between fields: a line feed, usable with {@link BufferedReader#readLine()} */
    private static final String SEPARATOR = "\n";
    
    
    
    
    
    
    
    /** The different ships, you can change the names :-) */
    private static final String[] SHIPS = new String[] {"TIEFIGHTER", "BOMBER", "DESTROYER"};
    // {"Infantry", "Cavalry", "Canons"}
    // {"Stone", "Scissors", "Paper"}
    /** The results of a battle (0 draw, 1 success, 2 failure) */
    private static final String[] RESULT = new String[] {"Draw", "Success!", "Failure :-("};
    /** Your name: change it */
    private final String MY_NAME = System.getProperty("user.name");

    
    
    
    
    
    
    /** input to read what the other player sent */
    private final BufferedReader input;
    /** output to send something to the other player */
    private final BufferedWriter output;
    /** name of the other player */
    private final String against;
    /** a counter */
    private int turn = 0;
    
    /** Constructor : an instance for one socket
     *
     * @param socket    the socket
     * @throws IOException  If an error happens
     */
    public GameSimpleFights(Socket socket) throws IOException {
        // opening the input and output
        input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        output = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
        
        // send your name
        // FIXME send your name (with separator) with output.write(...)
        // flush so that the other player can receive your input
        output.flush();
        
        // read the name of the other player
        against = input.readLine();
        Thread.currentThread().setName("Thread-GameSimpleFights-Against-" + against);
    }
    
    /**
     * Compute the next unit to send
     *
     * @return  The next unit to send
     */
    private int computeNextUnit() {
        // here you can compute the next unit to send
        // FIXME please do not use Math.random nor Random
        return turn % SHIPS.length;
    }
    
    private void lastUnitPlayed(int unit) {
        // you can store the last unit played by the other player, so that you have the whole history
        // FIXME if needed
    }
    
    /**
     * One loop
     *
     * @param round the current round
     * @return  the battle result
     * @throws IOException  If there is a problem
     */
    private int oneLoop(int round) throws IOException {
        // what unit to send next ?
        int unit = computeNextUnit();
        String unitName = SHIPS[unit];
        
        // send it, unit then name 
        // FIXME (and do something important...)
        
        // read the one from the player
        String otherUnitIndexString = input.readLine();
        int otherUnitIndex = Integer.parseInt(otherUnitIndexString);
        String otherUnitName = input.readLine();
        // store last unit played
        lastUnitPlayed(otherUnitIndex);
        
        // compute result
        int battleResult = (3 + otherUnitIndex - unit) % 3;
        //boolean success = battleResult == 1;
        
        // display something
        System.out.println(String.format("  Round %s against %s: %s vs %s: %s", 
                round, against, unitName, otherUnitName, RESULT[battleResult]));
        
        // increase turn number
        turn++;
        
        // return the success 0:draw, 1:success, 2:failure
        return battleResult;
    }
    
    @Override
    public Boolean call() throws IOException {
        // list of battle results for the 3 possible outcomes: 0:draws, 1:successes, 2:failures
        // it's only for statistics, displayed below
        int[] nbResults = new int[3];
        
        // main loop
        // FIXME run 100 battles
        // FIXME store the result in nbResults
        
        // display battle results
        for (int index = 0; index < 3; index ++) {
            System.out.println(String.format("Battle against %s: %s/%s %s", against, nbResults[index], NUMBER_OF_FIGHTS, RESULT[index]));
        }
        
        // compute if success more than failures
        boolean battleSuccess = nbResults[1] > nbResults[2];
        // FIXME close sockets
        return battleSuccess;
    }
    
    /**
     * Main method
     *
     * @param args The arguments. If empty, runs the game locally
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            // local test
            try (ServerSocket serverSocket = new ServerSocket(DEFAULT_PORT);) {
                //server created
                    System.out.println("Local server ready");
                    new Thread(() -> {
                        Socket connection;
                        try {
                            // client created
                            System.out.println("Local client connection");
                            connection = new Socket("localhost", DEFAULT_PORT);
                            new GameSimpleFights(connection).call();
                            connection.close();
                        } catch (Exception e) {
                            throw new IllegalStateException(e);
                        }
                    }).start();
                    // start the game
                    Socket connection = serverSocket.accept();
                    System.out.println("Start of game");
                    new GameSimpleFights(connection).call();
                    connection.close();
            }
        } else if (args[0].equals("server")) {
            // a server
            // FIXME : create a server like above, with an infinite loop so that you can receive other players
        } else {
            // a client
            // FIXME : create a client like above, that connects to the computer named args[0]
        }
    }
}

