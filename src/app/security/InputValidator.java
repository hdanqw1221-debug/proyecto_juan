package app.security;

public class InputValidator {
    public static void notBlank(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("El campo " + fieldName + " es obligatorio");
        }
    }

    public static void maxLength(String value, int max, String fieldName) {
        if (value != null && value.length() > max) {
            throw new IllegalArgumentException("El campo " + fieldName + " supera " + max + " caracteres");
        }
    }

    public static void email(String value) {
        if (value == null || !value.contains("@") || value.trim().length() < 3) {
            throw new IllegalArgumentException("Email invalido");
        }
    }

    public static boolean isStrongPassword(String password) {
        if (password == null || password.length() < 6) return false;
        boolean hasUpper = false;
        boolean hasNumber = false;
        for (char c : password.toCharArray()) {
            if (Character.isUpperCase(c)) hasUpper = true;
            if (Character.isDigit(c)) hasNumber = true;
        }
        return hasUpper && hasNumber;
    }

    public static void requireStrongPassword(String password) {
        if (!isStrongPassword(password)) {
            throw new IllegalArgumentException("La contrasena es muy debil. Debe tener al menos 6 caracteres, incluir una mayuscula y un numero.");
        }
    }
}
