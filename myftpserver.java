import java.io.*;
import java.net.*;
import java.util.*;

public class myftpserver {
    public static void main(String[] args) {

        if (args.length < 2) {
            System.out.println("ERROR!! PLEASE ENTER PORT AND TERMINATE PORT");
            return;
        }

        int portNum = Integer.parseInt(args[0]);
        int terminatePort = Integer.parseInt(args[1]);
        Map<Integer, String> terminateTable = new HashMap<>();

        try (ServerSocket serverSocket = new ServerSocket(portNum)) {

            ServerSocket sharingSocket = new ServerSocket(terminatePort);
            System.out.println("Server is listening on port " + portNum);

            while (true) {
                Socket clientSoc = serverSocket.accept();
                System.out.println("Client connected: " + clientSoc.getInetAddress());

                // Create a new thread for each client
                ClientHandler clientHandler = new ClientHandler(clientSoc, sharingSocket, terminateTable);
                clientHandler.start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

class GetFileThread extends Thread {
    private String filePath;
    private ServerSocket sharingSocket;
    private int commandID;
    private Map<Integer, String> terminateTable;

    public GetFileThread(String filePath, ServerSocket sharingSocket, int commandID,
                         Map<Integer, String> terminateTable) {
        this.filePath = filePath;
        this.sharingSocket = sharingSocket;
        this.commandID = commandID;
        this.terminateTable = terminateTable;
    }

    @Override
    public void run() {
        getFileAsync(filePath, sharingSocket, commandID, terminateTable);
    }

    private void getFileAsync(String filePath, ServerSocket sharingSocket, int commandID, Map<Integer, String> terminateTable) {
        try {
            Socket sharingSoc = sharingSocket.accept();
            PrintWriter out = new PrintWriter(sharingSoc.getOutputStream(), true);
            File file = new File(filePath);

            if (file.exists()) {
                BufferedReader fileReader = new BufferedReader(new FileReader(file));
                String line;
                while ((line = fileReader.readLine()) != null) {
                    out.println(line);

                    if ("N".equals(terminateTable.get(commandID))) {
                        out.println("TRANSFER_TERMINATED");
                        terminateTable.remove(commandID);
                        fileReader.close();
                        return;
                    }
                    Thread.sleep(4 * 1000);
                }
                fileReader.close();
                terminateTable.remove(commandID);
                out.println("FILE_TRANSFER_COMPLETE");
            } else {
                out.println("FILE_NOT_FOUND");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

class PutFileThread extends Thread {
    private String filePath;
    private ServerSocket sharingSocket;
    private int commandID;
    private Map<Integer, String> terminateTable;

    public PutFileThread(String filePath, ServerSocket sharingSocket, int commandID,
                         Map<Integer, String> terminateTable) {
        this.filePath = filePath;
        this.sharingSocket = sharingSocket;
        this.commandID = commandID;
        this.terminateTable = terminateTable;
    }

    @Override
    public void run() {
        try {
            Socket socket = sharingSocket.accept();
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

            // Read the file from the provided filePath
            File file = new File(filePath);
            if (!file.exists() || !file.isFile()) {
                out.println("FILE_NOT_FOUND");
                return;
            }

            BufferedReader fileReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            FileWriter fileWriter = new FileWriter(file);

            // Read lines sent by the client and write them to the file
            String line;
            while ((line = fileReader.readLine()) != null && !line.equals("FILE_TRANSFER_COMPLETE")) {
                if ("N".equals(terminateTable.get(commandID))) {
                    out.println("TRANSFER_TERMINATED");
                    terminateTable.remove(commandID);
                    fileWriter.close();
                    socket.close();
                    return;
                }
                fileWriter.write(line + "\n");
            }

            // Close file writer and socket
            fileWriter.close();
            socket.close();

            // Check if transfer was terminated
            if (terminateTable.containsKey(commandID) && "N".equals(terminateTable.get(commandID))) {
                // Delete the file
                if (!file.delete()) {
                    System.out.println("Failed to delete the file.");
                }
                System.out.println("File transfer terminated. File deleted.");
            } else {
                System.out.println("File received successfully.");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

class ClientHandler extends Thread {
    private Socket clientSocket;
    private final Map<Socket, String> clientDirectories = Collections.synchronizedMap(new HashMap<>());
    private ServerSocket sharingSocket;
    private Map<Integer, String> terminateTable;

    public ClientHandler(Socket clientSocket, ServerSocket sharingSocket, Map<Integer, String> terminateTable) {
        this.clientSocket = clientSocket;
        this.sharingSocket = sharingSocket;
        this.terminateTable = terminateTable;
    }

    @Override
    public void run() {
        String originalPath = System.getProperty("user.dir");

        try {
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

            // Set initial working directory for the client
            clientDirectories.put(clientSocket, originalPath);

            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                // PWD
                if ("pwd".equalsIgnoreCase(inputLine)) {
                    out.println(pwd());
                }

                // LS
                else if ("ls".equalsIgnoreCase(inputLine)) {
                    out.println(ls());
                }

                // CD
                else if ("cd".equalsIgnoreCase(inputLine.split(" ")[0])) {
                    out.println(cd(inputLine.split(" ")[1]));
                }

                // MKDIR
                else if ("mkdir".equalsIgnoreCase(inputLine.split(" ")[0])) {
                    out.println(makedir(inputLine.split(" ")[1]));
                }

                // GET
                else if ("get".equalsIgnoreCase(inputLine.split(" ")[0])) {
                    if (inputLine.split(" ").length == 3 && "&".equals(inputLine.split(" ")[2])) {
                        String fileName = inputLine.split(" ")[1];
                        String filePath = pwd() + "/" + fileName;
                        int min = 1;
                        int max = 1000;
                        Random random = new Random();
                        // Generate a command ID within the specified range
                        int cmdID = random.nextInt((max - min) + 1) + min;

                        terminateTable.put(cmdID, "Y");
                        out.println("COMMAND ID: " + cmdID);
                        GetFileThread getFileThread = new GetFileThread(filePath, sharingSocket, cmdID, terminateTable);
                        getFileThread.start();
                    } else if (inputLine.split(" ").length == 2) {
                        out.println(getFile(inputLine.split(" ")[1], out));
                    }
                    continue;
                } else if ("terminate".equalsIgnoreCase(inputLine.split(" ")[0])) {
                    if (inputLine.split(" ").length != 2) {
                        out.println("ERROR!! ENTER COMMAND ID");
                    } else {
                        String comIDinString = inputLine.split(" ")[1];
                        int cmdID = Integer.valueOf(comIDinString);
                        if (terminateTable.containsKey(cmdID)) {
                            terminateTable.put(cmdID, "N");
                            out.println("Command: " + cmdID + " TERMINATED successfully");
                        } else {
                            out.println("ERROR!! COMMAND ID NOT EXIST");
                        }
                    }
                }

                // PUT
                else if ("put".equalsIgnoreCase(inputLine.split(" ")[0])) {
                    // Handle put command with '&' symbol
                    if (inputLine.split(" ").length == 3 && "&".equals(inputLine.split(" ")[2])) {
                        String fileName = inputLine.split(" ")[1];
                        String filePath = originalPath + "/uploads/" + fileName;
                        int min = 1;
                        int max = 1000;
                        Random random = new Random();
                        int cmdID = random.nextInt((max - min) + 1) + min; // Generate a unique command ID
                        terminateTable.put(cmdID, "Y"); // Mark command as active
                        out.println("COMMAND ID: " + cmdID); // Send command ID to client
                        PutFileThread putFileThread = new PutFileThread(filePath, sharingSocket, cmdID,
                                terminateTable);
                        putFileThread.start(); // Start a new thread for file transfer
                    } else {
                        out.println(putFile(inputLine.split(" ")[1], in, originalPath));
                    }
                    continue;
                } else if ("delete".equalsIgnoreCase(inputLine.split(" ")[0])) {
                    out.println(deleteFile(inputLine.split(" ")[1]));
                } else {
                    out.println("ENTER A VALID COMMAND");
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                clientSocket.close();
                // Remove client-specific working directory on connection close
                clientDirectories.remove(clientSocket);
                System.out.println("Connection with client closed: " + clientSocket.getInetAddress());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public String getFile(String fileName, PrintWriter out) {
        try {
            String filePath = pwd() + "/" + fileName;
            File file = new File(filePath);

            if (file.exists()) {
                BufferedReader fileReader = new BufferedReader(new FileReader(file));
                String line;
                while ((line = fileReader.readLine()) != null) {
                    out.println(line);
                }
                fileReader.close();
                return "FILE_TRANSFER_COMPLETE";
            } else {
                return "FILE_NOT_FOUND";
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return "File received successfully";
    }

    public String putFile(String fileName, BufferedReader in, String originalPath) {
        try {
            File f1 = new File(originalPath + "/uploads");
            // Check if the directory already exists
            if (!f1.exists() && !f1.isDirectory()) {
                f1.mkdir();
            }
            String response = in.readLine();
            if ("FILE_NOT_FOUND".equals(response)) {
                return "FILE NOT FOUND ON CLIENT";
            } else {
                String filePath = originalPath + "/uploads/" + fileName;
                FileWriter fileWriter = new FileWriter(filePath);

                fileWriter.write(response + "\n");

                String line;
                while ((line = in.readLine()) != null && !line.equals("FILE_TRANSFER_COMPLETE")) {
                    fileWriter.write(line + "\n");
                }
                fileWriter.close();
                return "File Received Successfully";
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "File Received Successfully";
    }

    public String deleteFile(String fileName) {

        File file = new File(pwd(), fileName);

        if (!file.exists()) {
            return "ERROR!! FILE NOT EXIST";
        }
        if (file.delete()) {
            return "File deleted Successfully";
        } else {
            return "ERROR!! DELETE OPERATION FAILED";
        }
    }

    public String pwd() {
        return clientDirectories.get(clientSocket);
    }

    public String ls() {
        StringBuilder fileList = new StringBuilder();
        File parentDir = new File(pwd());
        String elements[] = parentDir.list();

        for (String element : elements) {
            fileList.append(element).append(" ");
        }
        return fileList.toString();
    }

    public String cd(String newDir) {
        String[] list1 = pwd().split("/");
        String finalPath;
        if (newDir == null)
            return "ERROR!! ENTER A DIRECTORY NAME";
        if (newDir.equals("..")) {
            if (list1.length <= 2) {
                for (String item : list1) {
                    System.out.println(item);
                }
                return "ERROR!! ALREADY IN ROOT DIRECTORY";
            }
            list1 = Arrays.copyOf(list1, list1.length - 1);

            finalPath = String.join("/", list1);
            clientDirectories.put(clientSocket, finalPath);
        } else {
            String newDirPath = pwd() + "/" + newDir;
            File f1 = new File(newDirPath);

            if (!f1.exists()) {
                return "DIRECTORY NOT FOUND!!!";
            } else if (!f1.isDirectory()) {
                return "NOT A DIRECTORY!!!";
            }
            if (f1.exists() && f1.isDirectory()) {
                list1 = Arrays.copyOf(list1, list1.length + 1);
                list1[list1.length - 1] = newDir;
                finalPath = String.join("/", list1);
                clientDirectories.put(clientSocket, finalPath);
            }
        }
        return "Directory changed Successful";
    }

    public String makedir(String newDir) {
        File f1 = new File(pwd(), newDir);
        // Check if the directory already exists
        if (f1.exists()) {
            return "DIRECTORY ALREADY EXIST!!";
        }
        boolean directoryCreated = f1.mkdir();

        if (directoryCreated) {
            return "Directory created successfully:";
        } else {
            return "FAILED TO CREATE DIRECTORY.";
        }
    }
}
