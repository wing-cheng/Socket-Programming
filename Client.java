import java.io.*;
import java.net.*;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;



public class Client {

    // the following variables are used in function validMD()
    static final int MSG = 1;
    static final int DLT = 2;
    static final int EDT = 3;
    static final int RDM = 4;
    static final int ATU = 5;
    static final int OUT = 6;
    static final int UPD = 7;
    static final int INVALID_CMD = 8;

    public Client(String[] args) {
        // check the number of given arguments
        if (args.length < 3) {
            System.out.println("Usage: java Client server_IP server_port client_udp_server_port");
            System.exit(1);
        }

        // parse the arguments
        String serverIP = args[0];
        int serverPort = Integer.parseInt(args[1]);
        int UDPport = Integer.parseInt(args[2]);

        // prompt the user to enter username and password
        String username, password;
        
        // make a socket for the connection to TCP server
        Socket toTCPserver = null;
        BufferedReader cmdReader = null;
        DataOutputStream toServer = null;
        BufferedReader fromServer = null;
        try {
            toTCPserver = new Socket(serverIP, serverPort);
            try {
                cmdReader = new BufferedReader(new InputStreamReader(System.in));
                toServer = new DataOutputStream(toTCPserver.getOutputStream());
                fromServer = new BufferedReader(new InputStreamReader(toTCPserver.getInputStream()));
                String ts, resp;
                while (true) {
                    // prompt the user to enter username
                    System.out.print("Enter username, press Enter to proceed: ");
                    username = cmdReader.readLine();
                    System.out.print("Enter password, press Enter to proceed: ");
                    password = cmdReader.readLine();

                    /*
                        send the msg to server for log in
                        the message has the format
                            LOG <username> <password> <timestamp>
                    */
                    // send the message to the TCP server
                    ts = timestamp();
                    toServer.writeBytes("LOG " + username + " " + password + " " + ts + "\n");
                    resp = fromServer.readLine();
                    if (resp.equals("OK")) {
                        // if login succeeds
                        System.out.println("Welcome! " + username);
                        break;
                    } else if (resp.equals("FREEZE")) {
                        // if consecutive failed login
                        // freeze the for 10 seconds
                        System.out.println("Your account has been blocked. Please try again later");
                        try {
                            TimeUnit.SECONDS.sleep(10);
                        } catch (InterruptedException ie) {
                            ie.printStackTrace();
                        } 
                        
                    } else {
                        // else prompt the user to enter username and password again
                        System.out.println("The username or password is incorrect, try again");
                    }
                    
                }

                // start sending requests to the server
                String msg, confirm;
                String[] splits;
                while (true) {
                    // get the message from the user
                    System.out.println("Usage: MSG <message>| DLT <messageNumber> <timestamp>| EDT <messagenumber> <timestamp> <message>| RDM <timestamp>| ATU | OUT | UPD <username> <filename>");
                    System.out.print("Enter a command:  ");
                    msg = cmdReader.readLine();
                    // if the command is invalid, show the error msg
                    // prompt the user to try again
                    // else re-structure the msg and send it to server
                    int valid = validCMD(msg);
                    ts = timestamp();
                    splits = msg.split("\\s+");
                    if (valid == INVALID_CMD) {
                        System.out.println("Invalid command, try again");
                        continue;
                    } else if (valid == OUT) {
                        // tell the server which user wants to logout
                        toServer.writeBytes("OUT " + username + "\n");
                        System.out.println("Good-bye, " + username);
                        if (fromServer != null) fromServer.close();
                        break;
                    } else if (valid == UPD) {
                        String peerIP;
                        int peerPort;
                        // prompt the user to enter peer's ip address and port number
                        System.out.print("Enter peer's IP address:  ");
                        peerIP = cmdReader.readLine();
                        System.out.print("Enter peer's port number:  ");
                        peerPort = Integer.parseInt(cmdReader.readLine());

                        // start a P2P thread
                        PeerThread peerThread = new PeerThread(splits[2], splits[1], peerIP, peerPort);
                        peerThread.start();
                    } else if (valid == DLT) {
                        toServer.writeBytes(msg + " " + ts + "\n");
                        // wait for server to confirm the deletion
                        while (true) {
                            confirm = fromServer.readLine();
                            if (confirm.contains("OK")) {
                                System.out.println("Message " + splits[1] + " deleted at " + ts);
                                break;
                            } else if (confirm.contains("Bad")) {
                                // server will reply with 'Bad' when user is not authorised to delete the message
                                System.out.println("Unauthorised to delete Message " + splits[1]);
                                break;
                            }
                        }
                    } else if (valid == EDT) {
                        toServer.writeBytes(msg + " " + ts + "\n");
                        resp = fromServer.readLine();
                        if (resp.contains("OK")) {
                            // tell the user that the edition was ok
                            System.out.println("Message " + splits[1] + " edited at " + ts);
                        } else {
                            System.out.println("Unauthorised to edit Message " + splits[1]);
                        }
                    } else if (valid == MSG) {
                        toServer.writeBytes(msg + " " + ts + "\n");
                        // the server should send back the message number
                        resp = fromServer.readLine();
                        System.out.println("Message #" + resp + " posted at " + ts);
                    } else if (valid == RDM) {
                        toServer.writeBytes(msg + " " + ts + "\n");
                        while ((resp = fromServer.readLine()) != null) {
                            System.out.println(resp);
                        }
                    } else if (valid == ATU) {
                        toServer.writeBytes(msg + " " + ts + "\n");
                        while (!(resp = fromServer.readLine()).contains("end")) {
                            System.out.println(resp);
                        }
                        continue;
                    }    

                }   
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    cmdReader.close();
                    fromServer.close();
                } catch (IOException ee) {
                    ee.printStackTrace();
                }
            }

        } catch (UnknownHostException ue) {
            ue.printStackTrace();
            System.out.println("Unknow host: " + serverIP);
            System.exit(1);
        } catch (IOException e2) {
            e2.printStackTrace();
            System.out.println("I/O error at creating socket to the server");
            System.exit(1);
        } 
        
    }   // end of Client()


    public static void main(String[] args) throws Exception {
        new Client(args);
    } // end of main thread


    static String timestamp() {
        // this function returns a current timestamp as a string
        SimpleDateFormat timeFormat = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
        Timestamp ts = new Timestamp(System.currentTimeMillis());
        return timeFormat.format(ts);
    }


    static int validCMD(String command) {
        // this function checks if 'command' is valid
        String[] splits = command.split("\\s+");
        if (splits[0].equals("MSG")) {
            if (splits.length < 2) return INVALID_CMD;
            return MSG;
        } else if (splits[0].equals("DLT")) {
            if (splits.length != 4) return INVALID_CMD;
            return DLT; 
        } else if (splits[0].equals("EDT")) {
            if (splits.length < 5) return INVALID_CMD;
            return EDT;
        } else if (splits[0].equals("RDM")) {
            if (splits.length != 3) return INVALID_CMD;
            return RDM;
        } else if (splits[0].equals("ATU")) {
            if (splits.length != 1) return INVALID_CMD;
            return ATU;
        } else if (splits[0].equals("OUT")) {
            if (splits.length != 1) return INVALID_CMD;
            return OUT;
        } else if (splits[0].equals("UPD")) {
            if (splits.length != 3) return INVALID_CMD;
            return UPD;
        }
        return INVALID_CMD;
    }   // end of main() func

    
    // ---------------------------------- Peer Thread --------------------------------
    class PeerThread extends Thread {

        public File fileToSend;
        public String filename;
        public String peerName;
        public InetSocketAddress peerAddr;
        public DatagramSocket serverSocket;

        // constructor
        public PeerThread(String file, String pname, String addr, int port) {
            
            fileToSend = new File(file);
            
            peerAddr = new InetSocketAddress(addr, port);
            try {
                serverSocket = new DatagramSocket(port);
            } catch (SocketException se) {
                se.printStackTrace();
                System.exit(1);
            }
            
            peerName = pname;
            filename = file;
        }

        public void run() {
            // create a datagramsocket to send the file
            byte[] fileData = new byte[(int) fileToSend.length()];
            try (FileInputStream fis = new FileInputStream(fileToSend)) {
                fis.read(fileData);
                DatagramPacket sendPacket = new DatagramPacket(fileData, fileData.length, peerAddr);
                serverSocket.send(sendPacket);
            } catch (IOException ioe) {
                ioe.printStackTrace();
                System.exit(1);
            }
            System.out.println("File " + filename + " was sent to " + peerName);
        }   // end of run()


    }   // end of class Peer Thread

}