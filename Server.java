import java.io.*;
import java.net.*;
import java.util.*;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.concurrent.locks.*;
import java.text.ParseException;

public class Server {

    // a list that keeps track of all active users
    static List<String> activeUsersInfo = new ArrayList<String>();
    static final String credFile = "credentials.txt";
    static final String logFile = "cse_userlog.txt";
    static final String msgFile = "messagelog.txt";
    static int nextMSGseqNum;
    static int nextLoginNum;
    
    
    public Server(String[] args) {
        
        // check the number of arguments
        if (args.length < 2) {
            System.out.println("Usage: java Server server_port number_of_consecutive_failed_attempts");
            System.exit(1);
        }

        // parse the args
        int serverPort = Integer.parseInt(args[0]);
        int nFailedAttempts = Integer.parseInt(args[1]);

        // create a socket class
        ServerSocket welcomeSocket = null;
        InetAddress clientAddr;
        String clientMSG, clientName;

        try {
            welcomeSocket = new ServerSocket(serverPort);
            // before we start getting request, store the next message sequence number
            nextMSGseqNum = nMSGs();
            nextLoginNum = nLogs();
            System.out.println("Server is ready...");
            
            
            while (true) {
                // accept the connection from connection queue
                Socket connSocket = welcomeSocket.accept();

                // only create a new client thread if the client is not acive
                if (connSocket != null) {
                    ClientThread clientThread = new ClientThread(connSocket, nFailedAttempts);
                    clientThread.start();
                }
            }
        } catch (IOException e4) {
            e4.printStackTrace();
        }
        
    }   // end of public Server()

    public static void main(String[] args) throws Exception {
        new Server(args);
    }

    static int nMSGs() {
        int i = 0;
        String line;
        String[] sp;
        try (BufferedReader fileReader = new BufferedReader(new FileReader(msgFile))) {
            while ((line = fileReader.readLine()) != null) {
                sp = line.split("; ");
                i = Integer.parseInt(sp[0]);
            }
        } catch (IOException e1) {
            e1.printStackTrace();
            System.out.println("I/O error at reading message log file");
            System.exit(1);
        }
        return i + 1;
    }

    static int nLogs() {
        int i = 0;
        String line;
        String[] sp;
        try (BufferedReader fileReader = new BufferedReader(new FileReader(logFile))) {
            while ((line = fileReader.readLine()) != null) {
                sp = line.split("; ");
                i = Integer.parseInt(sp[0]);
            }
        } catch (IOException e1) {
            e1.printStackTrace();
            System.out.println("I/O error at reading log file");
            System.exit(1);
        }
        return i + 1;
    }


    // ------------------------------- Client Thread --------------------------------
    class ClientThread extends Thread {
        public Socket conn;
        public InetAddress addr;
        public int nAttempts;
        public int curNattempts;
        public String username;
        public String loginTime;

        // constructor
        public ClientThread(Socket s, int n) {
            conn = s;
            addr = s.getInetAddress();
            nAttempts = n;
            curNattempts = n;
        }

        public void run() {

            BufferedReader fromClient = null;
            DataOutputStream toClient = null;
            String clientMSG;
            String[] splits;
            String ts1, ts;
            try {
                
                fromClient = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                toClient = new DataOutputStream(conn.getOutputStream());
                // assume at this time the client is active
                while (true) {
                    
                    // keep waiting for client message
                    clientMSG = fromClient.readLine();
                    
                    // determine which kind of command the client sends
                    splits = clientMSG.split("\\s+");
                    // get the timestamp when the user makes the request
                    ts1 = splits[splits.length - 2] + " " + splits[splits.length - 1];
                    // Client.java will check validity of the command
                    // invalid commands will not be sent to server
                    // e.g. msg 'DGT *******' will be aborted by Client.java 

                    // if the client asks to log out
                    if (splits[0].equals("OUT")) {
                        String userInfo = username + ";" + loginTime + ";" + addr.toString() + ";" + conn.getPort();
                        if (activeUsersInfo.contains(userInfo)) activeUsersInfo.remove(userInfo);
                        // change the log file
                        logLogout();
                        // tell the client logout is okay
                        toClient.writeBytes("OK\n");
                        System.out.println(splits[1] + " logged out");
                        break;
                    } else if (splits[0].equals("LOG")) {
                        
                        if (curNattempts <= 0) {
                            toClient.writeBytes("FREEZE\n");
                            curNattempts = 1;
                            continue;
                        } 
                        // if not supply with enough arguments
                        if (splits.length < 5) {
                            toClient.writeBytes("INCORRECT\n");
                        } else {
                            username = splits[1];
                            String password = splits[2];
                            loginTime = splits[3] + " " + splits[4];
                            
                            if (checkCredential(username, password)) {
                                toClient.writeBytes("OK\n");
                                // add client to active user
                                String userInfo = username + ";" + loginTime + ";" + addr.toString() + ";" + conn.getPort();
                                activeUsersInfo.add(userInfo);
                                
                                // write this login event to log file
                                logLogin(username, addr.toString(), conn.getPort());
                                continue;
                            }
                            toClient.writeBytes("INCORRECT\n");
                        }
                        curNattempts--;
                    } else if (splits[0].equals("MSG")) {
                        String msg = String.join(" ", Arrays.copyOfRange(splits, 1, splits.length - 2));
                        try {
                            // create a file writer, append mode
                            FileWriter writer = new FileWriter(msgFile, true);
                            writer.write(nextMSGseqNum + "; " + ts1 + "; " + username + "; " + msg + "; " + "No" + "\n");
                            writer.close();
                            nextMSGseqNum++;
                        } catch (IOException e) {
                            e.printStackTrace();
                            System.out.println("I/O error at writing a new message to message log file");
                            System.exit(1);
                        }
                        // tell the user the message post was ok
                        toClient.writeBytes((nextMSGseqNum - 1) + "\n");
                        System.out.println(username + " posted MSG #" + (nextMSGseqNum - 1) + " \"" + msg + "\" " + "at " + ts1);
                    } else if (splits[0].equals("DLT")) {
                        // client will check if number of args is correct

                        // the client will send msgNumber, timestamp of the msg and username
                        // the msg will be formatted as
                        //     DLT messagenumber timestamp username 
                        ts = splits[2] + ' ' + splits[3];
                        int msgNum = Integer.parseInt(splits[1].replace("#", ""));
                        if (deleteMSG(msgNum, ts, username, ts1)) {
                            // if the message was successfully deleted
                            // tell the client the deletion was okay
                            toClient.writeBytes("OK\n");
                        } else {
                            // else the deletion did not succeed
                            // e.g. a user tried to delete other's message
                            toClient.writeBytes("Bad\n");
                        }
                    } else if (splits[0].equals("EDT")) {
                        // the client will check if the number of args is correct

                        ts = splits[2] + " " + splits[3];
                        int msgNum = Integer.parseInt(splits[1]);
                        String newMSG = String.join(" ", Arrays.copyOfRange(splits, 4, splits.length - 2) );
                        if (editMSG(msgNum, ts, username, newMSG, ts1)) {
                            // if the message was successfully deleted
                            // tell the client the deletion was okay
                            toClient.writeBytes("OK\n");
                        } else {
                            // else the deletion did not succeed
                            // e.g. a user tried to edit other's message
                            toClient.writeBytes("Bad\n");
                        }
                    } else if (splits[0].equals("RDM")) {
                        ts = splits[1] + " " + splits[2];
                        readMSG(username, ts, toClient);
                    } else if (splits[0].equals("ATU")) {
                        ts = splits[1] + " " + splits[2];
                        System.out.println(username + " issued ATU command");
                        if (activeUsersInfo.size() < 2) {
                            toClient.writeBytes("No other active user\n");
                            toClient.writeBytes("end\n");
                            System.out.println("No other active user");
                        } else {
                            System.out.println("Return active user list: ");
                            for (String u : activeUsersInfo) {
                                String[] sp1 = u.split(";");
                                if (!sp1[0].equals(username)) {
                                    toClient.writeBytes(sp1[0] + " active since " + sp1[1] + ", with IP address: " + sp1[2] + " port " + sp1[3] + "\n");
                                    System.out.println(sp1[0] + " active since " + sp1[1] + ", with IP address: " + sp1[2] + " port " + sp1[3] + "\n");
                                }
                            }
                        }
                        toClient.writeBytes("end\n");
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    // close everythingggggg
                    fromClient.close();
                    toClient.close();
                } catch (IOException ee) {
                    ee.printStackTrace();
                }
                
            }
            
        } // end of run()


        public void readMSG(String user, String ts, DataOutputStream toClient) {
            // ts is the timestamp the client sent as the arg
            // not the time at which the client made the request
            List<String> messages = new ArrayList<String>();
            String m;
            SimpleDateFormat ts_format = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
            String line;
            try {
                Timestamp cur_ts = new Timestamp(ts_format.parse(ts).getTime());
                try {
                    BufferedReader fileReader = new BufferedReader(new FileReader(msgFile));
                    while((line = fileReader.readLine()) != null) {
                        // we compare the timestamp of each msg with the given timestamp
                        String[] sp = line.split("; ");
                        // we dont want to read the message posted by client itself
                        if (sp[2].equals(user)) continue;
                        try {
                            Timestamp temp_ts = new Timestamp(ts_format.parse(sp[1]).getTime());
                            if (temp_ts.after(cur_ts)) {
                                // if temp_ts is newer than cur_ts
                                // send the message to the client
                                if (sp[4].contains("yes")) {
                                    // if the msg was edited
                                    m = "#" + sp[0] + "; " + sp[2] + "; \"" + sp[3] + "\"; " + "edited at " + sp[1];
                                } else {
                                    // if the msg is never edited
                                    m = "#" + sp[0] + "; " + sp[2] + "; \"" + sp[3] + "\"; " + "posted at " + sp[1];
                                }
                                toClient.writeBytes(m + "\n");
                                messages.add(m);
                            }
                        } catch (ParseException pe) {
                            pe.printStackTrace();
                        }
                    }
                
                    System.out.println(user + " issued RDM command");
                    // if we did not find any message, tell the client
                    if (messages.size() == 0) {
                        toClient.writeBytes("no new message\n");
                        System.out.println("No new message was returned");
                    } else {
                        System.out.println("Returned messages:");
                        for (String s : messages) {
                            System.out.println(s);
                            toClient.writeBytes(s + "\n");
                        }
                        System.out.println("end\n");
                    }
                } catch (FileNotFoundException e1) {
                    e1.printStackTrace();
                    System.out.println("Message log file not found");
                    System.exit(1);
                } catch (IOException e2) {
                    e2.printStackTrace();
                    System.out.println("I/O error at reading message log file");
                    System.exit(1);
                }
            } catch (ParseException pe) {
                pe.printStackTrace();
            }
        }



        public boolean editMSG(int n, String ts, String poster, String newMSG, String ts1) {
            // read all lines of message log file into a list
            List<String> lines = new ArrayList<String>();
            String line;
            boolean foundMSG = false;
            int toEdit = 0;     // this is the line number of the line to be deleted
            try (BufferedReader fileReader = new BufferedReader(new FileReader(msgFile))) {
                while ((line = fileReader.readLine()) != null) {
                    // if we found the line to be deleted
                    String[] sp = line.split("; ");
                    if (sp[0].contains(String.valueOf(n)) && sp[1].contains(ts) && sp[2].contains(poster)) {
                        foundMSG = true;
                    }
                    lines.add(line);
                    if (!foundMSG) toEdit++;
                }
            } catch (FileNotFoundException e1) {
                e1.printStackTrace();
                System.out.println("Message log file not found");
                System.exit(1);
            } catch (IOException e2) {
                e2.printStackTrace();
                System.out.println("I/O error at reading message log file");
                System.exit(1);
            }

            if (!foundMSG) return false;
            try (FileWriter writer = new FileWriter(msgFile)) {
                // write the lines back to message log file
                for (int i = 0; i < lines.size(); i++) {
                    String newline;
                    if (i == toEdit) {
                        String[] sp = lines.get(i).split("; ");
                        // copy the line number and the new timestamp and the msg poster
                        newline = sp[0] + "; " + ts1 + "; " + poster;
                        newline += "; " + newMSG + "; ";
                        // since we edited the message, we need to write "yes" to indicate this edition
                        newline += "yes";
                    } else {
                        newline = lines.get(i);
                    }
                    writer.write(newline + "\n");
                }
                System.out.println(username + " edited MSG #" + n + " \"" + newMSG + "\" at " + ts1);
            } catch (FileNotFoundException e1) {
                e1.printStackTrace();
                System.out.println("Message log file not found");
                System.exit(1);
            } catch (IOException e2) {
                e2.printStackTrace();
                System.out.println("I/O error at writing message log file");
                System.exit(1);
            }

            return true;
        }


        public boolean deleteMSG(int n, String ts, String poster, String ts1) {
            List<String> lines = new ArrayList<String>();
            String line;
            boolean foundMSG = false;
            String msgToDeleted = "";
            try (BufferedReader fileReader = new BufferedReader(new FileReader(msgFile))) {
                // read all lines of message log file into a list
                while ((line = fileReader.readLine()) != null) {
                    // if we found the line to be deleted
                    String[] sp = line.split("; ");
                    if (sp[0].contains(String.valueOf(n)) && sp[1].contains(ts) && sp[2].contains(poster)) {
                        msgToDeleted = sp[3];
                        foundMSG = true;
                    } else {
                        // we only add the lines not to be deleted to the list
                        lines.add(line);
                    }
                }
                if (!foundMSG) return false;
            } catch (FileNotFoundException e1) {
                e1.printStackTrace();
                System.out.println("Message log file not found");
                System.exit(1);
            } catch (IOException e2) {
                e2.printStackTrace();
                System.out.println("I/O error at reading message log file");
                System.exit(1);
            }

            try (FileWriter writer = new FileWriter(msgFile)) {
                // write the lines back to message log file
                int i = 1;
                for (String s: lines) {
                    String[] sp = s.split("; ");
                    sp[0] = String.valueOf(i);
                    writer.write(String.join("; ", sp) + "\n");
                }
                System.out.println(username + " deleted MSG #" + i + " \"" + msgToDeleted + "\" at " + ts1);
            } catch (FileNotFoundException e1) {
                e1.printStackTrace();
                System.out.println("Message log file not found");
                System.exit(1);
            } catch (IOException e2) {
                e2.printStackTrace();
                System.out.println("I/O error at writing message log file");
                System.exit(1);
            }
            return true;
        }


        public void logMSG(String message) {
            try (FileWriter writer = new FileWriter(msgFile, true)) {
                // create a file writer, append mode
                writer.write(nextMSGseqNum + "; " + timestamp() + "; " + username + "; " + message + "; " + "no" + "\n");
                writer.close();
                nextMSGseqNum++;
            } catch (IOException e) {
                e.printStackTrace();
                System.out.println("I/O error at writing a new message to message log file");
                System.exit(1);
            }
        }

        

        public String timestamp() {
            // this function returns a current timestamp as a string
            SimpleDateFormat timeFormat = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
            Timestamp ts = new Timestamp(System.currentTimeMillis());
            return timeFormat.format(ts);
        }


        public void logLogin(String username, String clientIP, int portUDP) {
            // this file logs the user login event into the log file
            // open the log file writer
            
            try (FileWriter writer = new FileWriter(logFile, true)) {
                // create a file writer, append mode
                // write to the file
                writer.write(nextLoginNum + "; " + timestamp() + "; " + username + "; " + clientIP + "; " + portUDP + "\n");
                nextLoginNum++;
                if (writer != null) writer.close();
            } catch (IOException e) {
                System.out.println("I/O error at writing a login event to log file");
                System.exit(1);
            }

            
        }

        public void logLogout() {
            // this file logs the user logout event into the log file
            // open the log file writer
            ArrayList<String> lines = new ArrayList<String>();
            String line;
            try (BufferedReader fileReader = new BufferedReader(new FileReader(logFile))) {   
                while ((line = fileReader.readLine()) != null) {
                    if (!line.contains(username)) lines.add(line);
                }
            } catch (IOException e) {
                System.out.println("I/O error at writing a logout event to log file");
                System.exit(1);
            }

            try (FileWriter writer = new FileWriter(logFile)) {
                if (lines.size() == 0) return;
                // overwrite the log file
                for (int i = 0; i < lines.size(); i++) {
                    String[] splits = lines.get(i).split("; ");
                    splits[0] = String.valueOf(i);
                    writer.write(String.join("; ", splits) + "\n");
                }
            } catch (IOException e) {
                System.out.println("I/O error at writing a logout event to log file");
                System.exit(1);
            }
            
        }


        public boolean checkCredential(String username, String password) {
            // open the credential file
            String info = username + " " + password;
            String line;
            try (BufferedReader fileReader = new BufferedReader(new FileReader(credFile))) {
                while ((line = fileReader.readLine()) != null) {
                    if (line.equals(info)) return true;
                }
                fileReader.close();
            } catch (IOException e) {
                System.out.println("I/O error at reading credential file");
                System.exit(1);
            }
            return false;
        }


    }

}