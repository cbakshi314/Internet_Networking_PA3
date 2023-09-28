//execute function on shutdown:
//https://www.geeksforgeeks.org/jvm-shutdown-hook-java/

import java.io.*;
import java.net.*;
import java.util.*;

/* TODO ideas:
    -make dedicated functions for sending and receiving a file
    -replace println() with or add "output()" instead of printing so that there is the option to send the output to the connected program, depending on primaryInput enum stored in curInputSource
    -add more print statements/log additions
    -cmd_help for more commands
    -major log
*/

public class tcp_comm {
    public enum primaryInput{USER, CONNECTED}
    public enum printTypes{
        NORM(0),
        DEBUG(1);

        private int indexValue;
        printTypes(int indexValue){
            this.indexValue = indexValue;
        }
        public int getIndexValue(){
            return indexValue;
        }
    }
    /*public enum commands{
        QUIT,
        CONNECT,
        WAIT_FOR_CONNECTION,
        DO,
        CMDHELP
    }*/

    static ArrayList<String> fullLog = new ArrayList<String>();
    static ArrayList<String> majorLog = new ArrayList<String>();
    static String l_name;
    static int sliceSize = 10000;
    static BufferedInputStream bufReader;
    static BufferedOutputStream bufWriter;
    static boolean[] showPrintTypes = {true, false};
    static String sessionId = "B";
    static Date sessionDate = new Date();
    static Queue<String> commands = new LinkedList<>();
    static boolean clientCurrently = true;
    static boolean isConnected = false;
    static primaryInput curInputSource = primaryInput.USER;

    static final byte[] byte_OKAY = {5, 5, 5, 5};
    static final byte[] byte_FAIL = {1, 1, 1, 1};



    //when as server:
    static ServerSocket l_serverSocket;
    static int l_portNum = 0;
    static Socket connectedClient;
    static DataOutputStream toClient;
    static BufferedInputStream fromClient;

    //when as client:
    static Socket l_clientSocket;
    static int con_portNum = 0; //port number of the server that we are connected to
    static String con_hostname;
    static DataOutputStream toServer;
    static BufferedInputStream fromServer;

    public static void main(String[] args) {
        comm_main(args);
    }


    public static ArrayList<String> getCommandsList(){
        ArrayList<String> output = new ArrayList<>();
        output.add("COMMAND   DESCRIPTION");
        output.add("=======================================================");
        output.add("q         closes the program");
        output.add("connect   [client] connects to a specified host and port number");
        output.add("w_f_c     [server] wait for a client to connect");
        //output.add("do        executes a command file (i.e. a program of sorts)");
        output.add("cmd_help  provides further info about certain commands");
        output.add("pr_cmd    display this list");
        //output.add("send      send a message or command");
        output.add("upload    send a file");
        output.add("download  retrieve a file");
        output.add("ping      ping the connected device");
        output.add("copy      make a copy of a file");
        //output.add("set       modify a program variable");

        return output;
    }

    public static void println(String str){
        System.out.println(str);
    }

    public static void println(String str, printTypes type){
        if(showPrintTypes[type.indexValue]){
            println("Type: " + type.toString());
            println("INDEX VALUE: " + type.indexValue);
            println("Bool val: " + showPrintTypes[type.indexValue]);
            println(str);
        }
    }

    public static void printArr(ArrayList<String> strArr){
        for (int i = 0; i < strArr.size(); i++) {
            println(strArr.get(i));
        }
    }

    public static void printArr(ArrayList<String> strArr, printTypes type){
        if(showPrintTypes[type.indexValue]) {
            for (int i = 0; i < strArr.size(); i++) {
                println(strArr.get(i));
            }
        }
    }

    static String byteArrToString(byte[] arr){
        String output = "[";
        for (int i = 0; i < arr.length; i++) {
            output += arr[i] + ",";
        }
        output += "]";
        return (output);
    }

    public static void printCommandsList(){
        printArr(getCommandsList());
    }



    //args structure:
    //0 - port number
    //1 - name
    //2 - hostname (or |X| for server)
    //3 - a file to read initial commands from (optional)

    public static void comm_main(String[] args) {   //i.e. main
        Runtime.getRuntime().addShutdownHook(new Thread(){public void run(){saveLog();}});

        println("Showing normal print statements.", printTypes.NORM);
        println("Showing debug print statements.", printTypes.DEBUG);

        sessionId += Long.toString((sessionDate.getTime()-1679849571252l)/1000);
        println("Session Id: " + sessionId, printTypes.NORM);
        l_name = "LOCAL"; //I'm not sure how else to get a name
        fullLog.add("START OF LOG");
        printCommandsList();
        boolean stop = false;

        BufferedReader userInputReader = new BufferedReader(new InputStreamReader(System.in));

        int safety = 50;
        while(!stop){
            if(safety-- < 0){
                println("SAFETY TRIGGERED!!!");
                addToLog("SAFETY TRIGGERED!!!", l_name);
                //comm_exit();
                break;
            }
            //get new commands either from user input or from connected; put them in the queue
            if(curInputSource == primaryInput.USER){
                println("Awaiting user input...");
                try {
                    addCommand(userInputReader.readLine());
                }catch (Exception e){

                }
            }else if(curInputSource == primaryInput.CONNECTED){
                println("Awaiting input from connected...");
                ArrayList<String> message = receiveMessage();
                String combined = "";
                for (int i = 0; i < message.size(); i++) {
                    if(i != 0){
                        combined += ' ';
                    }
                    combined += message.get(i);
                }
                addCommand(combined);
                println("Received: " + combined);
            }
            stop = executeCommand();
        }
        println("Exiting program...");


        addToLog("Program exited cleanly.", getSource());
        return;
    }

    public static void addCommand(String command){
        commands.add(command);
        println("Commands queue length: " + commands.size());
    }

    public static void addCommands(ArrayList<String> commandsToAdd){
        for(int i = 0; i < commandsToAdd.size(); i++){
            commands.add(commandsToAdd.get(i));
        }
    }

    public static String removeNextCommand(){
        return commands.remove();   //this is a queue, so this removes the OLDEST command, not the most recently added
    }

    //clear connections
    public static void comm_exit(){
        if(isConnected){
            sendMessage("disconnecting");
        }
        try {
            if(l_clientSocket != null) {
                l_clientSocket.close();
                addToLog("(" + getSource() + ") Closed client socket.", l_name);
            }
            if(l_serverSocket != null) {
                l_serverSocket.close();
                addToLog("(" + getSource() + ") Closed server socket.", l_name);
            }
        }catch (Exception e){
            println("Error when closing sockets: " + e.toString());
        }
        isConnected = false;
        curInputSource = primaryInput.USER;
        clientCurrently = true;
        addToLog("(" + getSource() + ") Completed communication clearing.", l_name);
    }

    //send one line of text
    public static boolean sendMessage(String message){
        ArrayList<String> list = new ArrayList<String>();
        list.add(message);
        return sendMessage(list);
    }

    //send a line or more of text
    public static boolean sendMessage(ArrayList<String> message) {
        String combined = "";

        //combine the arraylist into a single string, separated by new line characters
        for (int i = 0; i < message.size(); i++) {
            if(i != 0){
                combined += "\n";
            }
            combined += message.get(i);
        }

        addToLog("Sending message (combined form)::" + combined, l_name);

        //convert the string to a byte array
        byte[] byteArr = combined.getBytes();

        //send the byte array length two times
        //the receiver will detect if they are not equal and handle it by sending byte_FAIL back
        sendInt(byteArr.length);
        sendInt(byteArr.length);

        println("Waiting for 'okay' response...", printTypes.DEBUG);
        byte[] response = receiveBytesBasic(4);

        if(isEqual(response, byte_OKAY)){
            println("received 'okay'", printTypes.DEBUG);
            //yay!
            //send the array itself
            sendBytesBasic(byteArr);
            return true;
        }else if(isEqual(response, byte_FAIL)) {
            println("received 'fail'", printTypes.DEBUG);
            //noo!
            //resend once
            sendInt(byteArr.length);
            sendInt(byteArr.length);

            response = receiveBytesBasic(4);

            if(isEqual(response, byte_OKAY)){
                //yay!
                println("now received 'okay'", printTypes.DEBUG);
                sendBytesBasic(byteArr);
                return true;
            }else{
                println("bad response", printTypes.DEBUG);
                //well, that didn't work. oh well.
                return false;
            }
        }else{
            //uhh...that's not good
            println("received strange response", printTypes.DEBUG);
            println(byteArrToString(response), printTypes.DEBUG);
            return false;
        }
    }

    //send one integer
    public static boolean sendInt(int num){
        println("sending int: " + num, printTypes.DEBUG);
        byte[] byteArr = new byte[4];

        for (int i = 0; i < byteArr.length; i++) {
            byteArr[i] = (byte)(num >> 8*((byteArr.length-1)-i));
        }

        return sendBytesBasic(byteArr);
    }

    //send many bytes safely
    public static boolean sendBytes(byte[] bytes){
        ArrayList<String> message = new ArrayList<String>();
        println("Sending byte array of length " + bytes.length, printTypes.DEBUG);
        message.add("Sending: byte array.");            //what is going on
        message.add(Integer.toString(bytes.length));    //how many bytes to expect
        if(sendMessage(message)){
            //message sent successfully; time to send the file
            ArrayList<String> reply = receiveMessage(); //this will contain the slice size to use; anything else indicates an issue
            if(reply.get(0).equals("ready to receive")){
                int sliceSize = Integer.parseInt(reply.get(1)); //get the slice size, in bytes
                byte[] slice = new byte[sliceSize];
                byte[] remainder = new byte[bytes.length % sliceSize];
                for (int i = 0; i < bytes.length / sliceSize; i++) {
                    System.arraycopy(bytes, i*sliceSize, slice, 0, sliceSize);
                    sendBytesBasic(slice);
                    reply = receiveMessage();
                    if(!reply.get(0).equals("received slice")){
                        //something went wrong
                        //TODO what to do here...
                    }
                    //continue with the next slice
                }
                //and now time for the remainder
                System.arraycopy(bytes, bytes.length-remainder.length, remainder, 0, remainder.length);
                sendBytesBasic(remainder);
                reply = receiveMessage();
                if(reply.get(0).equals("received remainder")){
                    return true;    //success!
                }else {
                    return false;   //something went wrong...and we were so close...
                }
                //TODO? maybe this function is done.
            }else{
                return false;
            }
        }else{
            return false;
        }
    }

    //the lowest level of sending data
    protected static boolean sendBytesBasic(byte[] bytes){
        //the most basic, bare-bones level of sending information
        println("Basic sending byte array of length " + bytes.length, printTypes.DEBUG);
        println(byteArrToString(bytes), printTypes.DEBUG);

        if(clientCurrently){
            //send as a client to a server
            try{
                toServer.write(bytes);
                return true;
            }catch (Exception e){
                return false;
            }
        }else{
            //send as a server to a client
            try {
                toClient.write(bytes);
                return true;
            }catch (Exception e){
                return false;
            }
        }
    }

    public static ArrayList<String> receiveMessage(){
        println("Waiting to receive...", printTypes.DEBUG);

        //receive the size twice
        int size1 = receiveInt();
        int size2 = receiveInt();
        byte[] byteArr;
        String combined = "";
        ArrayList<String> message = new ArrayList<>();

        //check if they are equal
        if(size1 == size2){
            println("Equal sizes of: " + size1, printTypes.DEBUG);
            //successfully received size
            sendBytesBasic(byte_OKAY);

            //now receive the array
            byteArr = receiveBytesBasic(size1);
            println("received array", printTypes.DEBUG);
        }else{
            println("Unequal sizes of: " + size1 + "; and: " + size2, printTypes.DEBUG);
            //sizes are not equal
            sendBytesBasic(byte_FAIL);

            //try again
            size1 = receiveInt();
            size2 = receiveInt();
            if(size1 == size2){
                sendBytesBasic(byte_OKAY);

                //now receive the array
                byteArr = receiveBytesBasic(size1);
                println("received array", printTypes.DEBUG);
            }else{
                //failed twice
                sendBytesBasic(byte_FAIL);
                return message;
            }
        }

        //we now have the byte array, which we will now convert to a string
        combined = new String(byteArr);
        println("Received message: " + combined);
        addToLog("Received message (combined form)::" + combined, "CONNECTED");

        //now split the string on newline characters
        message = new ArrayList<>(Arrays.asList(combined.split("\n")));

        println("Message array length: " + message.size());
        return message; //return the array!
    }

    public static int receiveInt(){
        byte[] byteArr = new byte[4];
        int result = 0;

        byteArr = receiveBytesBasic(4);

        for (int i = 0; i < byteArr.length; i++) {
            result += byteArr[i];
            if(i != byteArr.length-1) {
                result = result << 8;
            }
        }

        return result;
    }

    public static byte[] receiveBytes(int size){
        byte[] bytes = new byte[size];   //the output array

        //start by sending the slice size we want to use
        int sliceSize = 10000;
        ArrayList<String> myReply = new ArrayList<>();
        myReply.add("ready to receive");
        myReply.add(Integer.toString(sliceSize));
        sendMessage(myReply);

        byte[] slice = new byte[sliceSize];
        byte[] remainder = new byte[bytes.length % sliceSize];

        //the slices:
        for (int i = 0; i < bytes.length / sliceSize; i++) {
            slice = receiveBytesBasic(sliceSize);
            System.arraycopy(slice, 0, bytes, i * sliceSize, sliceSize);
            sendMessage("received slice");
        }

        //the final part:
        remainder = receiveBytesBasic(remainder.length);
        System.arraycopy(remainder, 0, bytes, bytes.length - remainder.length, remainder.length);
        sendMessage("received remainder");

        return bytes;
    }

    public static byte[] receiveBytesBasic(int size){
        byte[] bytes = new byte[size];
        try{
            if(clientCurrently){
                //receive as a client from a server
                fromServer.read(bytes);
            }else{
                //receive as a server from a client
                fromClient.read(bytes);
            }
        }catch (Exception e){
            println("Problem when receiving bytes on basic level: " + e.toString());
            addToLog("Problem when receiving bytes on basic level: " + e.toString(), l_name);
        }
        return bytes;
    }

    //carry out a command
    public static boolean executeCommand(){
        String command = commands.remove();
        println("Command is \"" + command + "\".", printTypes.DEBUG);
        addToLog(command, getSource());
        if(command.length() >= 8 && command.substring(0, 8).equals("cmd_help")){
            if(command.substring(8).equals(" connect")){
                println("connect <hostname> <port number>");
                println("Connect to a server with hostname <hostname> and a port number <port number>.");
            }else if(command.substring(8).equals(" w_f_c")) {
                println("w_f_c <port number>");
                println("Create a server with port number <port number>. Note that this will switch control mode to the connecting client.");
            }else if(command.substring(8).equals(" upload")) {
                println("upload <filename>");
                println("Send a file to the connected device.");
            }else if(command.substring(8).equals(" download")) {
                println("download <filename>");
                println("Retrieve a file from the connected device.");
            }else if(command.substring(8).equals(" ping")){
                println("ping");
                println("Time how long it takes to send a short message to the connected device and receive a response.");
            }else{
                println("cmd_help <command string>");
                println("Use this command followed by other commands to get more information about the specified command, including the parameters required.");
            }
        }else if(command.length() >= 6 && command.substring(0, 6).equals("pr_cmd")){
            printArr(getCommandsList());
        }else if(command.length() >= 1 && command.substring(0, 1).equals("q")) {
            comm_exit();
            return true;
        }else if(command.length() >= 7 && command.substring(0, 7).equals("connect")) {
            comm_exit();    //shut down pre-existing connections, if any

            //get the hostname:
            con_hostname = "";
            try {
                int i = 8;
                char nextChar = command.charAt(i);
                while(true){
                    //goes until there is a space character next,
                    //or the end of the command string is reached (which is merely precautionary and should not occur under proper operating circumstances)
                    i++;
                    if(nextChar == ' '){
                        break;
                    }
                    con_hostname += nextChar;
                    if(i >= command.length()){
                        break;
                    }
                    nextChar = command.charAt(i);
                }

                //get the port number:
                con_portNum = Integer.parseInt(command.substring(i));
            }catch (Exception e){
                println("Invalid hostname or port number.");
                return false;
            }
            try{
                println("Attempting to connect to host \"" + con_hostname + "\" with port number \"" + con_portNum + "\"...");
                l_clientSocket = new Socket(con_hostname, con_portNum);
                isConnected = true;
                toServer = new DataOutputStream(l_clientSocket.getOutputStream());
                fromServer = new BufferedInputStream(l_clientSocket.getInputStream());
                println("Connection established!");
            }catch (Exception e){
                println("Failed to establish a connection to a server: " + e.toString());
            }
            return false;
        }else if(command.length() >= 5 && command.substring(0, 5).equals("w_f_c")) {
            comm_exit();    //shut down pre-existing connections, if any
            try {
                l_portNum = Integer.parseInt(command.substring(6));
            }catch (Exception e){
                println("Invalid port number.");
                return false;
            }
            try {
                l_serverSocket = new ServerSocket(l_portNum);
                println("Server created; awaiting connection...");
                addToLog("Created server with port number \"" + l_portNum + "\".", "USER");
                connectedClient = l_serverSocket.accept();
                println("Connection established!");
                addToLog("Connection established to a client.", l_name);
                isConnected = true;
                println("1", printTypes.DEBUG);
                fromClient = new BufferedInputStream(connectedClient.getInputStream());
                println("2", printTypes.DEBUG);
                toClient = new DataOutputStream(connectedClient.getOutputStream());
                println("3", printTypes.DEBUG);
                curInputSource = primaryInput.CONNECTED;
                println("returning", printTypes.DEBUG);
                clientCurrently = false;
                return false;
            }catch (Exception e){
                println("Failed to establish a connection to a client: " + e.toString());
            }
        }else if(command.length() >= 6 && command.substring(0, 6).equals("upload")){
            if(isConnected){
                FileIO fileIO = new FileIO();
                ArrayList<String> message = new ArrayList<>();
                message.add("Sending: file.");
                message.add(command.substring(7));
                if(fileIO.readFile(command.substring(7)) && sendMessage(message)){
                    //was able to read file and notify receiver successfully
                    if(sendBytes(fileIO.getContent())){
                        println("File transferred successfully.");
                        return false;
                    }
                }
                println("File did not transfer successfully.");
            }else {
                println("No connection established.");
            }
        }else if(command.length() >= 8 && command.substring(0, 8).equals("download")) {
            if (isConnected) {
                FileIO fileIO = new FileIO();
                sendMessage("upload " + command.substring(9));
                ArrayList<String> reply = receiveMessage();
                if (reply.get(0).equals("Sending: file.")) {
                    addToLog("Request for file properly received and acknowledged.", l_name);
                    reply = receiveMessage();
                    if (reply.get(0).equals("Sending: byte array.")) {
                        addToLog("Receiving requested file of size " + reply.get(1) + "...", l_name);
                        println("Receiving requested file of size " + reply.get(1) + "...");
                        fileIO.setContent(receiveBytes(Integer.parseInt(reply.get(1))));
                        fileIO.writeFile(command.substring(9));
                        println("File transferred successfully.");
                        return false;
                    }
                }
                println("File did not transfer successfully.");
            } else {
                println("No connection established.");
            }
        }else if(command.length() >= 4 && command.equals("ping")) {
            if (isConnected) {
                sendMessage("ping a");
                Date sendDate = new Date();
                addToLog("Sent ping at " + sendDate.getTime(), getSource());
                println("Sent ping at " + sendDate.getTime());
                if (receiveMessage().get(0).equals("ping b")) {
                    Date receiveDate = new Date();
                    addToLog("Received ping at " + receiveDate.getTime(), getSource());
                    println("Received ping at " + receiveDate.getTime());
                    addToLog("Ping time: " + (receiveDate.getTime() - sendDate.getTime()), getSource());
                    println("Ping time: " + (receiveDate.getTime() - sendDate.getTime()));
                }
            } else {
                println("No connection established.");
            }
        }else if(command.length() >= 4 && command.substring(0, 4).equals("copy")){
            FileIO reader = new FileIO();
            FileIO writer = new FileIO();
            if(reader.readFile(command.substring(5))){
                writer.setContent(reader.getContent());
                writer.writeFile("c_" + command.substring(5));
            }else{
                println("Failed to read file: \"" + command.substring(5) + "\" due to error message: " + reader.statusMessages.peek());
                addToLog("Failed to read file: \"" + command.substring(5) + "\" due to error message: " + reader.statusMessages.peek(), l_name);
            }

        }else if(curInputSource == primaryInput.CONNECTED && command.length() >= 14 && command.substring(0, 14).equals("Sending: file.")){
            FileIO fileIO = new FileIO();
            ArrayList<String> info = receiveMessage();
            if(info.get(0).equals("Sending: byte array.")){
                fileIO.setContent(receiveBytes(Integer.parseInt(info.get(1)))); //info.get(1) should have the array size
                fileIO.writeFile(command.substring(15));
                println("File transferred successfully.");
            }else{
                println("File did not transfer successfully.");
            }
        }else if(curInputSource == primaryInput.CONNECTED && command.length() >= 13 && command.substring(0, 13).equals("disconnecting")){
            isConnected = false;    //set this to false so that we don't send a "disconnecting" message back
            comm_exit();
        }else if(curInputSource == primaryInput.CONNECTED && command.length() >= 6 && command.equals("ping a")) {
            sendMessage("ping b");
        }else{
            println("Command not recognized. Available commands are:");
            printArr(getCommandsList());
        }

        return false;
    }

    //compare the contents of two byte arrays
    public static boolean isEqual(byte[] arr1, byte[] arr2){
        if(arr1.length != arr2.length){
            return false;
        }else{
            for (int i = 0; i < arr1.length; i++) {
                if(arr1[i] != arr2[i]){
                    return false;
                }
            }
            return true;
        }
    }

    //===============================================LOG STUFF:======================================//
    public static void addToLog(String line, String source){
        fullLog.add(source + " ~ " + line);
    }

    //save log as a file
    public static void saveLog(){
        String stringLog = "";
        for (int i = 0; i < fullLog.size(); i++) {
            if(i != 0){
                stringLog += "\n";
            }
            stringLog += fullLog.get(i);
        }
        FileIO logFile = new FileIO();
        logFile.setContent(stringLog.getBytes());
        logFile.writeFile("Log_" + sessionId + ".txt");
    }

    public static String getSource(){
        String source = "none";
        if(curInputSource == primaryInput.USER){
            source = "USER";
        }else if(curInputSource == primaryInput.CONNECTED){
            source = "CONNECTED";
        }
        return source;
    }

}


















