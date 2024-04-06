import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

public class myftp {

    private static Map<Integer, Thread> putThreads = new HashMap<>();

    public static void main(String[] args) {

        if (args.length < 3) {
            System.out.println("ERROR!! PLEASE ENTER HOSTNAME, PORT NUMBER, AND TERMINATE PORT.");
            return;
        }
        final int serverPort = Integer.parseInt(args[1]);
        final int terminatePort = Integer.parseInt(args[2]);
        final String hostName = args[0];

        try {

            Socket socket = new Socket(hostName, serverPort);
            BufferedReader userInput = new BufferedReader(new InputStreamReader(System.in));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            System.out.println("\nConnected to the server.");
            System.out.println("You can use the following commands: ");
            System.out.println("ls pwd cd mkdir get put delete terminate");
            System.out.println("Type 'exit' to close the connection.\n");

            while (true) {
                System.out.print("myftp> ");
                String message = userInput.readLine();

                // Send the message to the server
                out.println(message);

                if ("exit".equalsIgnoreCase(message)) {
                    System.out.println("Closing the connection.");
                    socket.close();
                    break;
                }

                if ("ls".equalsIgnoreCase(message)) {
                    String response = in.readLine();
                    String[] list = response.split(" ");
                    for (String item : list) {
                        System.out.println("\t" + item);
                    }
                    continue;
                }

                // Regular get command
                if ("get".equalsIgnoreCase(message.split(" ")[0])) {
                    if (message.split(" ").length == 3 && "&".equals(message.split(" ")[2])) {
                        System.out.println(in.readLine());
                        System.out.println(getFileAsync(message.split(" ")[1], hostName, terminatePort));
                    } else {
                        System.out.println(getFile(message.split(" ")[1], in));
                    }
                    continue;
                }

                // Regular put command
                if ("put".equalsIgnoreCase(message.split(" ")[0])) {
                    if (message.split(" ").length == 3 && "&".equals(message.split(" ")[2])) {
                        System.out.println(putFileAsync(message.split(" ")[1], hostName, terminatePort));
                    } else {
                        putFile(message.split(" ")[1], out);
                        System.out.println(in.readLine());
                    }
                    continue;
                }

                // Regular delete command
                if ("delete".equalsIgnoreCase(message.split(" ")[0])) {
                    if (message.split(" ").length < 2) {
                        System.out.println("ERROR!! ENTER FILE/DIRECTORY NAME");
                        continue;
                    }
                }
                // Regular cd command
                if ("cd".equalsIgnoreCase(message.split(" ")[0])) {
                    if (message.split(" ").length < 2) {
                        System.out.println("ERROR!! ENTER FILE/DIRECTORY NAME");
                        continue;
                    }
                }

                // Terminate command
                // Terminate command
                // Terminate command
                if ("terminate".equalsIgnoreCase(message.split(" ")[0])) {
                    // Check if the command includes the command ID
                    if (message.split(" ").length < 2) {
                        System.out.println("ERROR!! ENTER COMMAND ID");
                        continue;
                    }
                    // Parse the command ID from the input
                    int commandId = Integer.parseInt(message.split(" ")[1]);
                    // Check if the command ID exists in the putThreads map
                    if (putThreads.containsKey(commandId)) {
                        // Interrupt the thread associated with the command ID
                        putThreads.get(commandId).interrupt();
                        // Remove the thread from the map
                        putThreads.remove(commandId);
                        System.out.println("File transfer terminated.");
                    } else {
                        System.out.println("Invalid command ID.");
                    }
                    continue;
                }
                                // Receive and print the server's response
                String response = in.readLine();
                System.out.println("myftp> " + response);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("resource")
public static String getFile(String fileName, BufferedReader in) {
    try {
        File downloadsDir = new File(System.getProperty("user.dir") + "/downloads");
        // Check if the directory already exists
        if (!downloadsDir.exists() && !downloadsDir.isDirectory()) {
            downloadsDir.mkdir();
        }
        
        String filePath = System.getProperty("user.dir") + "/downloads/" + fileName;
        FileWriter fileWriter = new FileWriter(filePath);
        
        String response = in.readLine();

        if ("FILE_NOT_FOUND".equals(response)) {
            return "FILE NOT FOUND ON SERVER!";
        } else {
            // Write the received data to the file
            while (!"FILE_TRANSFER_COMPLETE".equals(response)) {
                fileWriter.write(response + "\n");
                response = in.readLine();
            }
            // Close the file writer
            fileWriter.close();
        }
    } catch (IOException e) {
        e.printStackTrace();
    }
    return "File received successfully.";
}

    
    public static String getFileAsync(String fileName, String hostName, int terminatePort) {
        Thread downloadThread = new Thread(new FileTransferRunnable(fileName, hostName, terminatePort, "get"));
        downloadThread.start();

        return "File receiving in Progress";
    }

    public static String putFile(String fileName, PrintWriter out) {
        try {
            String filePath = System.getProperty("user.dir") + "/" + fileName;
            File file = new File(filePath);
            if (file.exists()) {
                BufferedReader fileReader = new BufferedReader(new FileReader(file));
                String line = fileReader.readLine();
                do {
                    out.println(line);
                } while ((line = fileReader.readLine()) != null);
                // Close the file reader
                fileReader.close();
                // Notify the client that the file transfer is complete
                out.println("FILE_TRANSFER_COMPLETE");
                return "File upload Successful";
            } else {
                out.println("FILE_NOT_FOUND");
                return "FILE NOT EXIST";
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "File sent Successfully";
    }

    public static String putFileAsync(String fileName, String hostName, int terminatePort) {
        Thread putThread = new Thread(new FileTransferRunnable(fileName, hostName, terminatePort, "put"));
        putThread.start();
        int commandId = putThread.hashCode();
        putThreads.put(commandId, putThread);
        return "File sending in progress with Command ID: " + commandId;
    }
}

class FileTransferRunnable implements Runnable {
    private String fileName;
    private String hostName;
    private int port;
    private String action; // "get" for download, "put" for upload

    public FileTransferRunnable(String fileName, String hostName, int port, String action) {
        this.fileName = fileName;
        this.hostName = hostName;
        this.port = port;
        this.action = action;
    }

    @Override
    public void run() {
        try {
            Socket sock = new Socket(hostName, port);
            PrintWriter out = new PrintWriter(sock.getOutputStream(), true);
            if (action.equals("put")) {
                BufferedReader fileReader = new BufferedReader(new FileReader(new File(System.getProperty("user.dir"), fileName)));
                String line;
                while ((line = fileReader.readLine()) != null) {
                    if (Thread.interrupted()) {
                        System.out.println("File transfer interrupted.");
                        fileReader.close();
                        sock.close();
                        return;
                    }
                    out.println(line);
                }
                out.println("FILE_TRANSFER_COMPLETE");
                fileReader.close();
                System.out.println("File sent successfully.");
            } else if (action.equals("get")) {
                BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream()));
                File file = new File(System.getProperty("user.dir"), fileName);
                FileWriter fileWriter = new FileWriter(file);
                String line;
                while ((line = in.readLine()) != null && !line.equals("FILE_TRANSFER_COMPLETE")) {
                    fileWriter.write(line + "\n");
                }
                fileWriter.close();
                System.out.println("File received successfully.");
            }
            sock.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
