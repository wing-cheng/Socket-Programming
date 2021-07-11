# Program Design

## Server

- Server class constructor
  - welcome socket
  - accept connections from clients and create threads to handle client requests
  - initialise the next msg sequence number and next use login sequence number
- main thread
  - initialise a new Server class using the constructor above
- helper functions for server class constructor
- Client Thread
  - run() function which every thread class has
  - helper functions for client thread
  - a socket is passed to the thread and handle inside the run() function

## Client

- Client class constructor
  - read commands from user
  - send commands to server
- main thread
  - initialise a new Client class using the constructor above
- helper functions for client class constructor
- Peer thread
  - used for peer to peer file transmission



# Application Layer Message Format

## Client

### Log in

When a user wants to log in, the following msg will be sent to server

```
LOG <username> <passwrod> <timestamp>

e.g.
LOG Hans Jedi*knight 01-05-2021 13:00:00
```

If the login detail is correct, the following will be return

```
OK
```

If the login detail was incorrect, the server returns

```
INCORRECT
```

If the user fails to log in for too many times, the server returns

```
FREEZE
```

### Log out

When the user wants to logout

```
OUT <username> <timestamp>

e.g.
OUT Hans 01-05-2021 13:00:00
```

### Post a message

when the user posts the message, the following will be sent to the server

```
MSG <message> <timestamp>

e.g.
MSG hello world 01-05-2021 13:00:00
```

### Read messages

when the user wants to read messages, the following will be sent to server

timestamp1 is when the request is made, timestamp is the argument for RDM command

```
RDM <timestamp> <timestamp1>

e.g.
RDM 01-05-2021 13:00:00 01-05-2021 15:00:00
```

### Edit a message

the following will be sent to server

timestamp1 is the time when the request was made, timestamp was used to match the timestamp of the message to be edited

```
EDT <messagenumber> <timestamp> <newmessage> <timestamp1>
```

### Delete a message

the following will be sent to server

```
DLT <messagenumber> <timestamp> <timestamp1>

e.g.
DLT 2 01-05-2021 13:00:00 01-05-2021 15:00:00
```

### All active users

have to tell the server when the request was made

```
ATU <timestamp>

e.g.
ATU 01-05-2021 13:00:00
```

## Server

### Read messages

When a user makes a RDM request, the server will look at message log file and return lines that can be displayed to the user by the Client program without modification, for example

```
#1; Hans; "hello hans" posted at 23-04-2021 17:38:24

#3; Hans; "hello hans" edited at 23-04-2021 17:38:24
```

And "end" will be sent to the Client indicating the end of the response

### Edit / delete a message 

- a user tries to edit / delete a message to which the user is not authorised to
  - the server will return "Bad"
  - the client program will the user about the forbidden request
- if the user is authorised
  - the server will send "ok"
  - the client program will tell user that the message has been modified / removed



# How the System Works

## Sample usage

In the following examples, no '#' was included. Though the spec used '#'

### Post a message

```
MSG hello world
```

### Edit a message

```
EDT 1 01-05-2021 13:00:00 01-05-2021 15:00:00 hi world
```

### Read messages

```
RDM 01-05-2021 13:00:00
```

### Delete a message

```
DLT 1 01-05-2021 13:00:00
```

# Possible Improvements and Extensions

A possible improvement and extension to the program is that we should use a relational database system to store the login info, credentials and message log. The introduction of database into the system can effectively address the issues I mentioned below:

1. The way I edit a message is that I read all lines of message log file into a string list, modify the corresponding line and write the lines back to the file, but this can blow up the computer memory if the file is big, also this adds more computational complexity.

2. The spec requires me to delete the line containing the message from the message log file and move all subsequent messages up as well as update their message number, which is tedious and adds extra computational complexity which is totally unnecessary.
3. when a user requesting to read message, the query mechanism of a relational database can help us faster retrieve the messages we need.

# Borrowed External Code

My program design came from this website, link: 

https://www.tutorialspoint.com/javaexamples/net_multisoc.htm

```java
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Calendar;
 
public class NewClass {
   ServerSocket myServerSocket;
   boolean ServerOn = true;
   public NewClass() { 
      try {
         myServerSocket = new ServerSocket(8888);
      } catch(IOException ioe) { 
         System.out.println("Could not create server socket on port 8888. Quitting.");
         System.exit(-1);
      } 
		
      Calendar now = Calendar.getInstance();
      SimpleDateFormat formatter = new SimpleDateFormat(
         "E yyyy.MM.dd 'at' hh:mm:ss a zzz");
      System.out.println("It is now : " + formatter.format(now.getTime()));
      
      while(ServerOn) { 
         try { 
            Socket clientSocket = myServerSocket.accept();
            ClientServiceThread cliThread = new ClientServiceThread(clientSocket);
            cliThread.start(); 
         } catch(IOException ioe) { 
            System.out.println("Exception found on accept. Ignoring. Stack Trace :"); 
            ioe.printStackTrace(); 
         }  
      } 
      try { 
         myServerSocket.close(); 
         System.out.println("Server Stopped"); 
      } catch(Exception ioe) { 
         System.out.println("Error Found stopping server socket"); 
         System.exit(-1); 
      } 
   }
	
   public static void main (String[] args) { 
      new NewClass();        
   } 
	
   class ClientServiceThread extends Thread { 
      Socket myClientSocket;
      boolean m_bRunThread = true; 
      public ClientServiceThread() { 
         super(); 
      } 
		
      ClientServiceThread(Socket s) { 
         myClientSocket = s; 
      } 
		
      public void run() { 
         BufferedReader in = null; 
         PrintWriter out = null; 
         System.out.println(
            "Accepted Client Address - " + myClientSocket.getInetAddress().getHostName());
         try { 
            in = new BufferedReader(
               new InputStreamReader(myClientSocket.getInputStream()));
            out = new PrintWriter(
               new OutputStreamWriter(myClientSocket.getOutputStream()));
            
            while(m_bRunThread) { 
               String clientCommand = in.readLine(); 
               System.out.println("Client Says :" + clientCommand);
               
               if(!ServerOn) { 
                  System.out.print("Server has already stopped"); 
                  out.println("Server has already stopped"); 
                  out.flush(); 
                  m_bRunThread = false;
               } 
               if(clientCommand.equalsIgnoreCase("quit")) {
                  m_bRunThread = false;
                  System.out.print("Stopping client thread for client : ");
               } else if(clientCommand.equalsIgnoreCase("end")) {
                  m_bRunThread = false;
                  System.out.print("Stopping client thread for client : ");
                  ServerOn = false;
               } else {
                  out.println("Server Says : " + clientCommand);
                  out.flush(); 
               } 
            } 
         } catch(Exception e) { 
            e.printStackTrace(); 
         } 
         finally { 
            try { 
               in.close(); 
               out.close(); 
               myClientSocket.close(); 
               System.out.println("...Stopped"); 
            } catch(IOException ioe) { 
               ioe.printStackTrace(); 
            } 
         } 
      } 
   } 
}
```
