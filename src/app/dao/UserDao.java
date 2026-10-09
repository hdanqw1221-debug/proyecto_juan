package app.dao;

import app.model.RoleType;
import app.model.User;

import java.util.List;

public interface UserDao {
    long create(User user);
    void update(User user);
    void delete(long id);
    User findByUsername(String username);
    User findById(long id);
    List<User> listAll();
    void setRoles(long userId, List<RoleType> roles);
    List<RoleType> getRoles(long userId);
}
