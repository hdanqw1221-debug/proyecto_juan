package app.tools;

import org.mindrot.jbcrypt.BCrypt;

public class HashTool {
    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Uso: java -cp out;lib/* app.tools.HashTool <password>");
            return;
        }
        String hash = BCrypt.hashpw(args[0], BCrypt.gensalt(12));
        System.out.println(hash);
    }
}
