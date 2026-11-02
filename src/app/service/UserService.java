package app.service;

import app.dao.UserDao;
import app.dao.jdbc.RoleDaoJdbc;
import app.dao.jdbc.UserDaoJdbc;
import app.db.ConnectionFactory;
import app.model.RoleType;
import app.model.User;
import app.security.InputValidator;
import app.security.PasswordHasher;

import java.util.ArrayList;
import java.util.List;

public class UserService {
    private final UserDao userDao;
    private final PasswordHasher hasher;

    public UserService(ConnectionFactory factory, PasswordHasher hasher) {
        RoleDaoJdbc roleDao = new RoleDaoJdbc(factory);
        this.userDao = new UserDaoJdbc(factory, roleDao);
        this.hasher = hasher;
    }

    public User createUser(String username, String email, String plainPassword, boolean active) {
        InputValidator.notBlank(username, "username");
        InputValidator.notBlank(email, "email");
        InputValidator.email(email);
        InputValidator.notBlank(plainPassword, "password");
        User user = new User();
        user.setUsername(username.trim());
        user.setEmail(email.trim());
        user.setPasswordHash(hasher.hash(plainPassword));
        user.setActive(active);
        long id = userDao.create(user);
        user.setId(id);
        return user;
    }

    public void updateUser(long id, String username, String email, boolean active) {
        User user = userDao.findById(id);
        if (user == null) throw new IllegalArgumentException("Usuario no encontrado");
        InputValidator.notBlank(username, "username");
        InputValidator.notBlank(email, "email");
        InputValidator.email(email);
        user.setUsername(username.trim());
        user.setEmail(email.trim());
        user.setActive(active);
        userDao.update(user);
    }

    public void changePassword(long id, String newPassword) {
        InputValidator.notBlank(newPassword, "password");
        User user = userDao.findById(id);
        if (user == null) throw new IllegalArgumentException("Usuario no encontrado");
        user.setPasswordHash(hasher.hash(newPassword));
        userDao.update(user);
    }

    public void changeOwnPassword(long id, String currentPassword, String newPassword) {
        InputValidator.notBlank(currentPassword, "contrasena actual");
        InputValidator.notBlank(newPassword, "nueva contrasena");
        User user = userDao.findById(id);
        if (user == null) throw new IllegalArgumentException("Usuario no encontrado");
        if (!hasher.verify(currentPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("La contrasena actual no es correcta.");
        }
        if (currentPassword.equals(newPassword)) {
            throw new IllegalArgumentException("La nueva contrasena no puede ser identica a la actual.");
        }
        InputValidator.requireStrongPassword(newPassword);
        user.setPasswordHash(hasher.hash(newPassword));
        userDao.update(user);
    }

    public User findByUsername(String username) {
        return userDao.findByUsername(username);
    }

    public List<User> listUsers() {
        return userDao.listAll();
    }

    public void deleteUser(long id) {
        userDao.delete(id);
    }

    public void assignRoles(long userId, List<RoleType> roles) {
        userDao.setRoles(userId, roles);
    }

    public User findById(long id) {
        return userDao.findById(id);
    }

    public void ensureDefaultAdmin() {
        try {
            User admin = userDao.findByUsername("admin");
            if (admin == null) {
                User user = new User();
                user.setUsername("admin");
                user.setEmail("admin@unipamplona.edu.co");
                user.setPasswordHash(hasher.hash("admin123"));
                user.setActive(true);
                long id = userDao.create(user);
                userDao.setRoles(id, List.of(RoleType.ADMINISTRADOR));
            } else {
                List<RoleType> roles = new ArrayList<>(userDao.getRoles(admin.getId()));
                if (!roles.contains(RoleType.ADMINISTRADOR)) {
                    roles.add(RoleType.ADMINISTRADOR);
                    userDao.setRoles(admin.getId(), roles);
                }
            }
        } catch (Exception e) {
            System.err.println("Aviso al asegurar admin por defecto: " + e.getMessage());
        }
    }

    public List<RoleType> getRoles(long userId) {
        return userDao.getRoles(userId);
    }
}
