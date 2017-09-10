package ru.ifmo;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;

public class Main implements Runnable {
    private static AtomicInteger C = new AtomicInteger(0);
    private static String processID;
    private static ServerSocket serverSocket;
    private static HashMap<String, String> processID2IP = new HashMap<>();
    private static HashMap<String, Integer> processID2Port = new HashMap<>();
    private static PrintWriter consoleWriter = new PrintWriter(System.out);

    public static void main(String[] args) {
        retrieveProcessID(args);
        retrieveConfiguration(args);

        try {
            serverSocket = new ServerSocket(processID2Port.get(processID));
        } catch (IOException e) {
            consoleWriter.println("Couldn't create socket listening current process's <port>, cause:" + e.getMessage());
            consoleWriter.flush();
            return;
        }

        parallelHandleSocketInput();
        handleConsoleInput();
    }

    private static void retrieveProcessID(String[] args) {
        processID = args[0];
    }

    private static void retrieveConfiguration(String[] args) {
        try (Scanner scanner = new Scanner(new File(args[1]))) {
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();

                if (line.isEmpty()) continue;
                assert line.startsWith("process.");

                String processID = line.substring(line.indexOf('.') + 1, line.indexOf('='));
                String ip = line.substring(line.indexOf('=') + 1, line.indexOf(':'));
                String port = line.substring(line.indexOf(':') + 1);

                processID2IP.put(processID, ip);
                processID2Port.put(processID, Integer.valueOf(port));
            }
        } catch (FileNotFoundException e) {
            consoleWriter.println("Something went wrong with config file, cause:" + e.getMessage());
            consoleWriter.flush();
        }
    }

    private static void parallelHandleSocketInput() {
        new Thread(new Main()).start();
    }

    private static void handleConsoleInput() {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            String line = scanner.nextLine();

            if (line.isEmpty()) continue;
            assert line.startsWith("send to:");

            String receivingProcessID = line.substring(line.indexOf("to:") + 3, line.indexOf(" msg:"));
            String message = line.substring(line.indexOf("msg:") + 4);

            sendMessage(receivingProcessID, message);
        }
    }

    private static void sendMessage(String receivingProcessID, String message) {
        try (Socket socket = new Socket(processID2IP.get(receivingProcessID), processID2Port.get(receivingProcessID));
             OutputStream outputStream = socket.getOutputStream()) {

            outputStream.write((processID + ":" + message + ">" + C.incrementAndGet()).getBytes());
            outputStream.flush();
        } catch (Exception e) {
            consoleWriter.println("Couldn't send message:" + message + " to:" + receivingProcessID
                    + ", cause:" + e.getMessage());
            consoleWriter.flush();
        }
    }

    @Override
    public void run() {
        while (true) {
            try (Socket socket = serverSocket.accept();
                 Scanner scanner = new Scanner(socket.getInputStream())) {

                String line = scanner.nextLine();
                int sendingProcessID = Integer.valueOf(line.substring(0, line.indexOf(':')));
                int message = Integer.valueOf(line.substring(line.indexOf(':') + 1, line.indexOf('>')));
                int time = Integer.valueOf(line.substring(line.indexOf('>') + 1));

                int oldC, newC;
                do {
                    oldC = C.get();
                    newC = Math.max(oldC, time) + 1;
                } while (!C.compareAndSet(oldC, newC));

                consoleWriter.println("received from:" + sendingProcessID + " msg:" + message
                        + " time:" + newC);
                consoleWriter.flush();
            } catch (IOException e) {
                consoleWriter.println("Something went wrong with accepting connections, cause:" + e.getMessage());
                consoleWriter.flush();
                return;
            }
        }
    }
}
