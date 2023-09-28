import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;


public class FileIO {
    //error messages: 4

    //reading from file:
    FileInputStream fileReader;
    BufferedInputStream bufReader;

    //writing to a file
    FileOutputStream fileWriter;

    public Queue<String> statusMessages = new LinkedList<>();
    private byte[] content;

    public byte[] getContent(){
        return content;
    }

    public void setContent(byte[] newContent){
        content = newContent;
    }

    public ArrayList<String> readCommandsFile(String filename){
        try {
            return (ArrayList) Files.readAllLines(Paths.get(filename));
        }catch(Exception e){
            return new ArrayList<String>();
        }
    }

    public boolean readFile(String filename){
        int fileSize = (int)(new File(filename).length());
        byte[] bytesArr = new byte[fileSize];
        try{
            fileReader = new FileInputStream(filename);
        }catch (Exception e){
            statusMessages.add("**ERR** : FileIO Error 4: Error when reading file to array using file input stream. Error: " + e.toString());
        }
        bufReader = new BufferedInputStream(fileReader);
        try {
            bufReader.read(bytesArr);
        }catch (Exception e){
            statusMessages.add("**ERR** : FileIO Error 1: Error when reading file to array using buffered reader. Errorr: " + e.toString());
            return false;
        }
        content = bytesArr;
        return true;
    }

    public boolean writeFile(String filename){
        try {
            fileWriter = new FileOutputStream(filename);
        }catch (Exception e){
            statusMessages.add("**ERR** : FileIO Error 2: Error when creating a new FileOutputStream with filename \"" + filename + "\". Error: " + e.toString());
            return false;
        }
        try {
            fileWriter.write(content);
        }catch (Exception e){
            statusMessages.add("**ERR** : FileIO Error 3: Error when writing to file with filename \"" + filename + "\". Error: " + e.toString());
            return false;
        }
        return true;
    }


}
