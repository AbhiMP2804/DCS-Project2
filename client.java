import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class client {

    public static void main(String[] args) {

        if (args.length < 3) {
            System.out.println("ERROR!! PLEASE ENTER HOSTNAME AND PORT NUMBER and TERMINATE PORT.");
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
            System.out.println("ls pwd cd mkdir get put delete");
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

                if ("put".equalsIgnoreCase(message.split(" ")[0])) {
                    putFile(message.split(" ")[1], out);
                    System.out.println(in.readLine());
                    continue;
                }

                if ("delete".equalsIgnoreCase(message.split(" ")[0])) {
                    if (message.split(" ").length < 2) {
                        System.out.println("ERROR!! ENTER FILE/DIRECTORY NAME");
                        continue;
                    }
                }
                if ("cd".equalsIgnoreCase(message.split(" ")[0])) {
                    if (message.split(" ").length < 2) {
                        System.out.println("ERROR!! ENTER FILE/DIRECTORY NAME");
                        continue;
                    }
                }

                // Receive and print the server's response
                String response = in.readLine();
                System.out.println("myftp> " + response);
            }


        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String getFile(String fileName, BufferedReader in) {
        try {

            File f1 = new File(System.getProperty("user.dir") + "/downloads");
            // Check if the directory already exists
            if (!f1.exists() && !f1.isDirectory()) {
                f1.mkdir();
            }
            String response = in.readLine();

            if ("FILE_NOT_FOUND".equals(response)) {
                return "FILE NOT FOUND ON SERVER!";
            } else {
                // Create a FileWriter to write the received file
                String filePath = System.getProperty("user.dir") + "/downloads/" + fileName;
                FileWriter fileWriter = new FileWriter(filePath);
                // write the first line in the file
                fileWriter.write(response + "\n");
                // Read lines from the server until FILE_TRANSFER_COMPLETE is received
                String line;
                while ((line = in.readLine()) != null && !line.equals("FILE_TRANSFER_COMPLETE")) {
                    // Write each line to the file
                    fileWriter.write(line + "\n");
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
    
        Thread downloadThread = new Thread(new DownloadFileRunnable(fileName, hostName, terminatePort, "get"));
        downloadThread.start();
           
        return "File receiveing in Progress";
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
}

class DownloadFileRunnable extends Thread{
    private String fileName;
    private String hostName;
    private int terminatePort;
    String task;
    
    public DownloadFileRunnable(String fileName, String hostName, int terminatePort, String task) {
        this.fileName = fileName;
        this.hostName = hostName;
        this.terminatePort = terminatePort;
        this.task = task;
    }

    @Override
    public void run() {
        try {
            Socket sock = new Socket(hostName, terminatePort);
            BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream()));
            // Create the downloads directory if it doesn't exist
            File downloadsDir = new File("downloads");
            if (!downloadsDir.exists()) {
                downloadsDir.mkdir();
            }
            // Create the file to save the downloaded content
            File file = new File("downloads/" + fileName);
            FileWriter fileWriter = new FileWriter(file);


            // Read lines from the server until FILE_TRANSFER_COMPLETE is received
            String line;
            while ((line = in.readLine()) != null && !line.equals("FILE_TRANSFER_COMPLETE")) {
                if(line.equals("TRANSFER_TERMINATED")){
                    fileWriter.close();
                    file.delete();
                    sock.close();
                    return;
                }
                fileWriter.write(line + "\n");
            }
            // Close the file writer
            fileWriter.close();
            sock.close();
            System.out.println("File received successfully from new thread.");
            System.out.print("myftp> ");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
